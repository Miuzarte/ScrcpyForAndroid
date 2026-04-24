package io.github.miuzarte.scrcpyforandroid

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import io.github.miuzarte.scrcpyforandroid.nativecore.AnnexBDecoder
import io.github.miuzarte.scrcpyforandroid.nativecore.PersistentVideoRenderer
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.Codec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Facade that centralizes video rendering.
 *
 * Provides helpers for:
 * - Surface/Decoder management for video rendering
 * - Video size and FPS monitoring
 */
object NativeCoreFacade {
    private val sessionLifecycleMutex = Mutex()
    private val renderer = PersistentVideoRenderer()
    private var activeSurfaceId: Int? = null
    private var decoder: AnnexBDecoder? = null
    private val videoSizeListeners = CopyOnWriteArraySet<(Int, Int) -> Unit>()
    private val videoFpsListeners = CopyOnWriteArraySet<(Float) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bootstrapLock = Any()
    private val bootstrapPackets = ArrayDeque<CachedPacket>()

    @Volatile
    private var recordingSurfaceAttached = false

    @Volatile
    private var latestConfigPacket: CachedPacket? = null
    private var packetCount: Long = 0

    @Volatile
    private var currentSessionInfo: Scrcpy.Session.SessionInfo? = null

    suspend fun close() {
        sessionLifecycleMutex.withLock {
            releaseAllDecoders()
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
            if (activeSurfaceId == newId && decoder != null) {
                return
            }
            Log.i(TAG, "attachVideoSurface(): surfaceId=$newId oldSurfaceId=$activeSurfaceId")
            activeSurfaceId = newId
            renderer.attachDisplaySurface(surface)
            val session = currentSessionInfo ?: return
            val currentDecoder = decoder
            if (currentDecoder != null) {
                Log.i(TAG, "attachVideoSurface(): try switch decoder output to persistent surface")
                val switched = currentDecoder.switchOutputSurface(renderer.getDecoderSurface())
                Log.i(TAG, "attachVideoSurface(): switchOutputSurface success=$switched")
                if (switched) {
                    return
                }
            }
            createOrReplaceDecoder(session)
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
                    "detachVideoSurface(): skip stale request requestSurfaceId=$requestId currentSurfaceId=$currentId"
                )
                return
            }
            Log.i(
                TAG,
                "detachVideoSurface(): surfaceId=$requestId releaseDecoder=$releaseDecoder"
            )
            activeSurfaceId = null
            renderer.detachDisplaySurface(surface, releaseSurface = false)
            if (releaseDecoder) {
                Log.i(TAG, "detachVideoSurface(): releasing decoder with destroyed surface")
                decoder?.release()
                decoder = null
            }
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
            renderer.attachRecordSurface(surface, width, height, onFrameRendered)
            val session = currentSessionInfo
            if (session != null && decoder == null) {
                createOrReplaceDecoder(session)
            }
        }
    }

    suspend fun detachRecordingSurface(
        surface: Surface? = null,
        releaseSurface: Boolean = false,
    ) {
        sessionLifecycleMutex.withLock {
            recordingSurfaceAttached = false
            renderer.detachRecordSurface(surface, releaseSurface)
            if (activeSurfaceId == null) {
                decoder?.release()
                decoder = null
            }
        }
    }

    fun addVideoSizeListener(listener: (Int, Int) -> Unit) = videoSizeListeners.add(listener)
    fun removeVideoSizeListener(listener: (Int, Int) -> Unit) = videoSizeListeners.remove(listener)
    fun addVideoFpsListener(listener: (Float) -> Unit) = videoFpsListeners.add(listener)
    fun removeVideoFpsListener(listener: (Float) -> Unit) = videoFpsListeners.remove(listener)

    /**
     * Called by Scrcpy.kt when a session starts.
     * Sets up video decoders for registered surfaces.
     */
    suspend fun onScrcpySessionStarted(
        session: Scrcpy.Session.SessionInfo,
        sessionMgr: Scrcpy.Session
    ) = sessionLifecycleMutex.withLock {
        currentSessionInfo = session
        releaseAllDecoders()
        synchronized(bootstrapLock) {
            bootstrapPackets.clear()
            latestConfigPacket = null
        }
        if (activeSurfaceId != null || recordingSurfaceAttached) {
            Log.i(TAG, "onScrcpySessionStarted(): bind decoder to persistent surface")
            createOrReplaceDecoder(session)
        }
        packetCount = 0
        sessionMgr.attachVideoConsumer { packet ->
            cacheBootstrapPacket(packet)
            packetCount += 1
            if (packetCount == 1L || packetCount % 120L == 0L) {
                Log.i(
                    TAG,
                    "videoFeed(): packets=$packetCount key=${packet.isKeyFrame} cfg=${packet.isConfig} decoder=${decoder != null}"
                )
            }
            val currentDecoder = decoder ?: return@attachVideoConsumer
            runCatching {
                currentDecoder.feedAnnexB(
                    packet.data,
                    packet.ptsUs,
                    packet.isKeyFrame,
                    packet.isConfig
                )
            }
        }
    }

    /**
     * Called by Scrcpy.kt when a session stops.
     * Cleans up decoders and resets state.
     */
    suspend fun onScrcpySessionStopped() = sessionLifecycleMutex.withLock {
        releaseAllDecoders()
        synchronized(bootstrapLock) {
            bootstrapPackets.clear()
            latestConfigPacket = null
        }
        currentSessionInfo = null
        recordingSurfaceAttached = false
    }

    private const val TAG = "NativeCoreFacade"
    private const val MAX_BOOTSTRAP_PACKETS = 90

    private data class CachedPacket(
        val data: ByteArray,
        val ptsUs: Long,
        val isConfig: Boolean,
        val isKeyFrame: Boolean,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CachedPacket

            if (ptsUs != other.ptsUs) return false
            if (isConfig != other.isConfig) return false
            if (isKeyFrame != other.isKeyFrame) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = ptsUs.hashCode()
            result = 31 * result + isConfig.hashCode()
            result = 31 * result + isKeyFrame.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    /**
     * Create or replace the active decoder bound to [surface] for [session].
     *
     * - Chooses MIME type from `session.codec` and constructs an [AnnexBDecoder].
     * - The decoder's `onOutputSizeChanged` callback publishes size changes to
     *   registered listeners on the main thread.
     * - Newly created decoders are fed with any cached bootstrap packets to allow
     *   faster playback startup.
     */
    private fun createOrReplaceDecoder(session: Scrcpy.Session.SessionInfo) {
        val surface = renderer.getDecoderSurface()
        decoder?.release()
        decoder = null
        Log.i(
            TAG,
            "createOrReplaceDecoder(): " +
                    "codec=${session.codec?.string ?: "null"}, " +
                    "size=${session.width}x${session.height}, " +
                    "persistent=true"
        )
        val newDecoder = AnnexBDecoder(
            width = session.width,
            height = session.height,
            outputSurface = surface,
            mimeType = when (session.codec) {
                Codec.H264 -> "video/avc"
                Codec.H265 -> "video/hevc"
                Codec.AV1 -> "video/av01"
                else -> "video/avc"
            },
            onOutputSizeChanged = { width, height ->
                val current = currentSessionInfo
                if (current == null || (current.width == width && current.height == height)) {
                    return@AnnexBDecoder
                }
                Log.i(
                    TAG,
                    "videoSizeChanged(): ${current.width}x${current.height} -> ${width}x${height}"
                )
                currentSessionInfo = current.copy(width = width, height = height)
                mainHandler.post {
                    videoSizeListeners.forEach { listener ->
                        runCatching { listener(width, height) }
                    }
                }
            },
            onFpsUpdated = { fps ->
                mainHandler.post {
                    videoFpsListeners.forEach { listener ->
                        runCatching { listener(fps) }
                    }
                }
            },
        )
        decoder = newDecoder
        replayBootstrapPackets(newDecoder)
    }

    private fun replayBootstrapPackets(decoder: AnnexBDecoder) {
        val snapshot = synchronized(bootstrapLock) { bootstrapPackets.toList() }
        if (snapshot.isEmpty()) {
            return
        }
        Log.i(TAG, "replayBootstrapPackets(): count=${snapshot.size}")
        snapshot.forEach { packet ->
            runCatching {
                decoder.feedAnnexB(packet.data, packet.ptsUs, packet.isKeyFrame, packet.isConfig)
            }
        }
    }

    private fun cacheBootstrapPacket(packet: Scrcpy.Session.VideoPacket) {
        val cached = CachedPacket(
            data = packet.data.copyOf(),
            ptsUs = packet.ptsUs,
            isConfig = packet.isConfig,
            isKeyFrame = packet.isKeyFrame,
        )
        synchronized(bootstrapLock) {
            if (cached.isConfig) {
                latestConfigPacket = cached
                bootstrapPackets.clear()
                bootstrapPackets.addLast(cached)
                return
            }

            if (cached.isKeyFrame) {
                bootstrapPackets.clear()
                latestConfigPacket?.let { bootstrapPackets.addLast(it) }
                bootstrapPackets.addLast(cached)
                return
            }

            while (bootstrapPackets.size >= MAX_BOOTSTRAP_PACKETS) {
                bootstrapPackets.removeFirst()
            }
            bootstrapPackets.addLast(cached)
        }
    }

    private fun releaseAllDecoders() {
        runCatching { decoder?.release() }
        decoder = null
    }
}
