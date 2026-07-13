package io.github.miuzarte.scrcpyforandroid

import android.util.Log
import android.view.Surface
import io.github.miuzarte.scrcpyforandroid.nativecore.DecoderCapabilities
import io.github.miuzarte.scrcpyforandroid.nativecore.PersistentVideoRenderer
import io.github.miuzarte.scrcpyforandroid.nativecore.VideoDecoderController
import io.github.miuzarte.scrcpyforandroid.scrcpy.ClientOptions
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.Codec
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Facade that centralizes video rendering and decoder management.
 *
 * Acts as a thin front-end over [VideoDecoderController] (decoder lifecycle, bootstrap
 * cache, error detection) and [PersistentVideoRenderer] (EGL / surface management).
 *
 * The facade owns the session lifecycle mutex and coordinates surface attach/detach
 * with decoder creation. It also publishes video size / FPS to listeners.
 *
 * Error recovery:
 * - When the local decoder cannot handle the video resolution (e.g. MTK AVC caps at
 *   2048 but scrcpy sends 2400), the facade can automatically restart the session with
 *   a lower `max_size` (controlled by the "downsize on decode error" app setting).
 * - When the decoder enters an unrecoverable error state at runtime (e.g. MTK OMX
 *   buffer conflict after rotation), the facade restarts the session.
 */
object NativeCoreFacade {
    private val sessionLifecycleMutex = Mutex()
    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val renderer = PersistentVideoRenderer()
    private val controller = VideoDecoderController(renderer)

    @Volatile
    private var activeSurfaceId: Int? = null

    @Volatile
    private var recordingSurfaceAttached = false

    // Reference to Scrcpy for reading currentSessionState (set by onScrcpySessionStarted)
    @Volatile
    private var scrcpyRef: Scrcpy? = null

    @Volatile
    private var packetCount: Long = 0

    // Cached ClientOptions for session restart (downgrade / error recovery)
    @Volatile
    private var cachedClientOptions: ClientOptions? = null

    // Guards against recursive restarts (restart triggers onScrcpySessionStopped/Started)
    @Volatile
    private var isRestarting = false

    suspend fun close() {
        sessionLifecycleMutex.withLock {
            controller.releaseAll()
            renderer.release()
        }
    }

    /**
     * Register the current rendering [surface].
     *
     * - If the surface is already active and the decoder exists, this is a no-op.
     * - If a decoder exists but cannot switch output surface, a new decoder is created
     *   and bound to the supplied surface.
     */
    suspend fun attachVideoSurface(surface: Surface) {
        sessionLifecycleMutex.withLock {
            if (!surface.isValid) {
                Log.w(TAG, "attachVideoSurface(): skip invalid surface")
                return
            }
            val newId = System.identityHashCode(surface)
            if (activeSurfaceId == newId && controller.isDecoderUsable()) {
                return
            }
            Log.i(TAG, "attachVideoSurface(): surfaceId=$newId oldSurfaceId=$activeSurfaceId")
            activeSurfaceId = newId
            controller.attachDisplaySurface(surface)
            val session = controller.getCurrentSessionInfo() ?: return
            if (controller.isDecoderUsable()) {
                Log.i(TAG, "attachVideoSurface(): try switch decoder output to persistent surface")
                if (controller.trySwitchDecoderSurface()) {
                    return
                }
            }
            controller.ensureDecoder(session)
        }
    }

    /**
     * Unregister the active rendering [surface].
     *
     * - If a stale surface reference is supplied (identity mismatch), the request is ignored.
     * - When [releaseDecoder] is false, only the active display target is cleared so a future
     *   surface can attempt to rebind via `setOutputSurface()`.
     * - When [releaseDecoder] is true, the current decoder is also released because the backing
     *   surface is being destroyed for real.
     */
    suspend fun detachVideoSurface(surface: Surface? = null, releaseDecoder: Boolean = false) {
        sessionLifecycleMutex.withLock {
            val currentId = activeSurfaceId
            val requestId = surface?.let { System.identityHashCode(it) }
            if (requestId != null && currentId != null && requestId != currentId) {
                Log.i(
                    TAG,
                    "detachVideoSurface(): skip stale request requestSurfaceId=$requestId currentSurfaceId=$currentId",
                )
                return
            }
            Log.i(
                TAG,
                "detachVideoSurface(): surfaceId=$requestId releaseDecoder=$releaseDecoder",
            )
            activeSurfaceId = null
            controller.detachDisplaySurface(surface, releaseDecoder)
        }
    }

    suspend fun attachRecordingSurface(
        surface: Surface,
        width: Int,
        height: Int,
        onFrameRendered: ((Long) -> Unit)? = null,
    ) {
        sessionLifecycleMutex.withLock {
            recordingSurfaceAttached = true
            controller.attachRecordSurface(surface, width, height, onFrameRendered)
            val session = controller.getCurrentSessionInfo()
            if (session != null && !controller.isDecoderUsable()) {
                controller.ensureDecoder(session)
            }
        }
    }

    suspend fun detachRecordingSurface(
        surface: Surface? = null,
        releaseSurface: Boolean = false,
    ) {
        sessionLifecycleMutex.withLock {
            recordingSurfaceAttached = false
            controller.detachRecordSurface(surface, releaseSurface)
            if (activeSurfaceId == null) {
                controller.releaseAll()
            }
        }
    }

    fun addVideoSizeListener(listener: (Int, Int) -> Unit) = controller.addVideoSizeListener(listener)
    fun removeVideoSizeListener(listener: (Int, Int) -> Unit) = controller.removeVideoSizeListener(listener)
    fun addVideoFpsListener(listener: (Float) -> Unit) = controller.addVideoFpsListener(listener)
    fun removeVideoFpsListener(listener: (Float) -> Unit) = controller.removeVideoFpsListener(listener)

    /**
     * Called by Scrcpy.kt when a session starts.
     * Sets up video decoders for registered surfaces.
     *
     * [options] is cached so the facade can restart the session (with a modified max_size)
     * when the decoder fails.
     */
    suspend fun onScrcpySessionStarted(
        session: Scrcpy.Session.SessionInfo,
        sessionMgr: Scrcpy.Session,
        scrcpy: Scrcpy,
        options: ClientOptions,
    ) = sessionLifecycleMutex.withLock {
        scrcpyRef = scrcpy
        cachedClientOptions = options
        isRestarting = false
        controller.releaseAll()
        controller.resetBootstrap()
        if (activeSurfaceId != null || recordingSurfaceAttached) {
            // v4.0: width/height come from first video session packet, not from initial metadata
            if (session.width > 0 && session.height > 0) {
                Log.i(TAG, "onScrcpySessionStarted(): bind decoder to persistent surface")
                controller.ensureDecoder(session)
            } else {
                Log.i(TAG, "onScrcpySessionStarted(): defer decoder until first video session packet (v4.0)")
            }
        }
        packetCount = 0
        sessionMgr.attachVideoConsumer { packet ->
            cacheAndFeed(packet)
        }
    }

    /**
     * Called by Scrcpy when a video session packet arrives with new dimensions.
     * Launches a coroutine to create or rebuild the decoder under the lifecycle mutex.
     *
     * Before creating the decoder, checks whether the local hardware decoder supports the
     * target resolution. If not, triggers a downgrade restart (when enabled) or shows an
     * error snackbar.
     */
    fun onVideoSizeChanged(width: Int, height: Int) {
        lifecycleScope.launch {
            sessionLifecycleMutex.withLock {
                if (isRestarting) return@withLock
                val scrcpy = scrcpyRef ?: return@withLock
                val info = scrcpy.currentSessionState.value ?: return@withLock
                if (info.width <= 0 || info.height <= 0) return@withLock

                val mime = info.codec?.mime ?: "video/avc"
                if (!DecoderCapabilities.isSizeSupported(mime, info.width, info.height)) {
                    Log.w(
                        TAG,
                        "onVideoSizeChanged(): decoder does not support ${info.width}x${info.height} for $mime",
                    )
                    handleUnsupportedSize(info.width, info.height, mime)
                    return@withLock
                }

                controller.rebuildDecoderForSize(info)
            }
        }
    }

    /**
     * Called by Scrcpy.kt when a session stops.
     * Cleans up decoders and resets state.
     */
    suspend fun onScrcpySessionStopped() = sessionLifecycleMutex.withLock {
        controller.releaseAll()
        scrcpyRef = null
        recordingSurfaceAttached = false
    }

    private fun cacheAndFeed(packet: Scrcpy.Session.VideoPacket) {
        packetCount += 1
        if (packetCount == 1L || packetCount % 120L == 0L) {
            Log.i(
                TAG,
                "videoFeed(): packets=$packetCount key=${packet.isKeyFrame} cfg=${packet.isConfig} usable=${controller.isDecoderUsable()}",
            )
        }
        val usable = controller.feed(packet)
        if (!usable && !isRestarting) {
            Log.e(TAG, "videoFeed(): decoder became unusable after feed, packets=$packetCount")
            handleDecoderError()
        }
    }

    /**
     * Handle the case where the local decoder does not support the target resolution.
     *
     * When "downsize on decode error" is enabled (default), restarts the session with a
     * lower `max_size` derived from [DecoderCapabilities.maxSupportedSize]. Otherwise,
     * shows an error snackbar and leaves the decoder uncreated (black screen, no crash).
     */
    private suspend fun handleUnsupportedSize(width: Int, height: Int, mime: String) {
        val downsizeEnabled = Storage.appSettings.bundleState.value.downsizeOnDecodeError
        if (!downsizeEnabled) {
            AppRuntime.snackbar(R.string.vm_decoder_init_failed, "${width}x${height}")
            return
        }
        val maxSize = DecoderCapabilities.maxSupportedSize(mime)
        Log.i(TAG, "handleUnsupportedSize(): downgrading to max_size=$maxSize")
        AppRuntime.snackbar(R.string.vm_decoder_unsupported_size, width, height, maxSize)
        downgradeAndRestart(maxSize)
    }

    /**
     * Handle a runtime decoder error (codec entered unrecoverable state).
     *
     * Restarts the scrcpy session so a fresh decoder is created from scratch.
     */
    private fun handleDecoderError() {
        if (isRestarting) return
        isRestarting = true
        Log.i(TAG, "handleDecoderError(): restarting session due to decoder error")
        AppRuntime.snackbar(R.string.vm_decoder_error_restarting)
        lifecycleScope.launch {
            sessionLifecycleMutex.withLock {
                restartSessionWith(cachedClientOptions)
            }
        }
    }

    /**
     * Restart the scrcpy session with a modified `max_size`.
     *
     * Persists the new max_size to [Storage.scrcpyOptions] so the user sees the change
     * in the UI, then stops and restarts the session.
     */
    private suspend fun downgradeAndRestart(maxSize: Int) {
        if (isRestarting) return
        isRestarting = true
        val options = cachedClientOptions ?: return
        val newOptions = options.copy(maxSize = maxSize.toUShort()).fix().validate()

        // Persist the new max_size so the UI reflects the downgrade.
        runCatching {
            val soBundle = Storage.scrcpyOptions.bundleState.value
            Storage.scrcpyOptions.saveBundle(soBundle.copy(maxSize = maxSize))
        }

        restartSessionWith(newOptions)
    }

    /**
     * Stop the current session and start a new one with [options].
     *
     * Must be called while holding [sessionLifecycleMutex]. The [isRestarting] flag
     * prevents recursive restarts — it is cleared in [onScrcpySessionStarted] when the
     * new session comes up.
     */
    private suspend fun restartSessionWith(options: ClientOptions?) {
        val scrcpy = scrcpyRef ?: return
        if (options == null) return
        Log.i(TAG, "restartSessionWith(): stopping current session")
        runCatching { scrcpy.stop(Scrcpy.StopReason.USER) }
        Log.i(TAG, "restartSessionWith(): starting new session with max_size=${options.maxSize}")
        runCatching { scrcpy.start(options) }
    }

    private const val TAG = "NativeCoreFacade"
}
