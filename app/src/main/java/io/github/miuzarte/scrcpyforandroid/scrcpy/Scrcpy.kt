package io.github.miuzarte.scrcpyforandroid.scrcpy

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.KeyEvent
import androidx.core.net.toUri
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.constants.Defaults
import io.github.miuzarte.scrcpyforandroid.nativecore.AdbSocketStream
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.nativecore.ScrcpyAudioPlayer
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.CameraFacing
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.Codec
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.EncoderType
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.ListOptions
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.EventLogger.logEvent
import io.github.miuzarte.scrcpyforandroid.services.LocalInputService
import io.github.miuzarte.scrcpyforandroid.services.NativeAacRecorder
import io.github.miuzarte.scrcpyforandroid.services.NativeMp4Recorder
import io.github.miuzarte.scrcpyforandroid.services.NativeWavRecorder
import io.github.miuzarte.scrcpyforandroid.services.RecordingFileResolver
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStreamReader
import java.util.ArrayDeque
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.random.nextUInt

/**
 * High-level scrcpy client API.
 * 
 * Manages scrcpy sessions including:
 * - Server jar extraction and deployment
 * - Session lifecycle (start/stop)
 * - Audio playback
 * - Screen control
 * 
 * @param appContext Android context
 * @param serverAsset Asset path for the default server jar
 * @param customServerUri Optional custom server URI (overrides serverAsset)
 * @param serverVersion Server version string
 * @param serverRemotePath Remote path where server jar will be pushed on device
 */
class Scrcpy(
    private val appContext: Context,

    private val serverAsset: String = DEFAULT_SERVER_ASSET,
    private val customServerUri: String? = null,
    private val serverVersion: String = DEFAULT_SERVER_VERSION,
    private val serverRemotePath: String = DEFAULT_REMOTE_PATH,
    private val lowLatency: Boolean = false,
) {

    private val session = Session(::handleRemoteClipboardText)
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clipboardSyncLock = Any()
    private val clipboardManager by lazy {
        appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    @Volatile
    private var clipboardListenerRegistered = false

    @Volatile
    private var lastKnownLocalClipboardText: String? = null

    @Volatile
    private var pendingRemoteClipboardText: String? = null
    private val clipboardChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        handleLocalClipboardChanged()
    }

    private val _currentSessionState = MutableStateFlow<Session.SessionInfo?>(null)
    val currentSessionState: StateFlow<Session.SessionInfo?> = _currentSessionState.asStateFlow()

    @Volatile
    private var isRunning: Boolean = false

    @Volatile
    private var audioPlayer: ScrcpyAudioPlayer? = null

    @Volatile
    private var mp4Recorder: NativeMp4Recorder? = null

    @Volatile
    private var wavRecorder: NativeWavRecorder? = null

    @Volatile
    private var aacRecorder: NativeAacRecorder? = null

    val listings = Listings()

    companion object {
        private const val TAG = "Scrcpy"

        const val DEFAULT_SERVER_ASSET = "bin/scrcpy-server-v3.3.4"
        const val DEFAULT_SERVER_ASSET_NAME = "scrcpy-server-v3.3.4"
        const val DEFAULT_SERVER_VERSION = "3.3.4"
        const val DEFAULT_REMOTE_PATH = "/data/local/tmp/scrcpy-server.jar"

        // Regex patterns for parsing server output
        private val VIDEO_ENCODER_INFO_REGEX =
            Regex("""--video-codec=(\S+)\s+--video-encoder=(\S+)\s+\((hw|sw)\)(\s+\[vendor])?(?:\s+\(alias for (\S+)\))?""")
        private val AUDIO_ENCODER_INFO_REGEX =
            Regex("""--audio-codec=(\S+)\s+--audio-encoder=(\S+)\s+\((hw|sw)\)(\s+\[vendor])?(?:\s+\(alias for (\S+)\))?""")
        private val DISPLAY_REGEX =
            Regex("""--display-id=(\d+)\s+\((\d+)x(\d+)\)""")
        private val CAMERA_SIZE_REGEX =
            Regex("""\b([1-9][0-9]{1,4}x[1-9][0-9]{1,4})\b""")
        private val CAMERA_INFO_REGEX =
            Regex("""--camera-id=(\S+)\s+\(([^,]+),\s*([0-9]+x[0-9]+),\s*fps=\[([0-9,\s]+)\]\)""")
        private val APP_REGEX =
            Regex("""^\s*([*-])\s+(.+?)\s{2,}([A-Za-z0-9._]+)\s*$""", RegexOption.MULTILINE)
        private val RECENT_TASK_PACKAGE_REGEX =
            Regex("""\bcmp=([A-Za-z0-9._]+)/""")

        fun generateScid(): UInt {
            // Only use 31 bits to avoid issues with signed values on the Java-side
            return (Random.nextUInt() and 0x7FFFFFFFu)
        }
    }

    suspend fun start(options: ClientOptions): Session.SessionInfo = withContext(Dispatchers.IO) {
        if (isRunning) {
            throw IllegalStateException("Scrcpy session is already running")
        }

        Log.i(TAG, "Initializing scrcpy session")

        try {
            // Validate options
            options.validate()

            // Generate session ID
            val scid = generateScid()
            Log.d(TAG, "scid=0x${scid.toString(16)}")

            val serverJar = if (customServerUri.isNullOrBlank()) {
                extractAssetToCache(serverAsset)
            } else {
                extractUriToCache(customServerUri.toUri())
            }

            // Execute server
            val rawInfo = executeServer(
                serverJar = serverJar,
                options = options,
                scid = scid,
            )
            val currentTarget = AppRuntime.currentConnectionTarget
            val info = rawInfo.copy(
                host = currentTarget?.host ?: "",
                port = currentTarget?.port ?: Defaults.ADB_PORT,
            )

            // Turn screen off if requested
            if (options.turnScreenOff) {
                if (!options.control) {
                    Log.w(TAG, "start(): turnScreenOff ignored because control is disabled")
                } else {
                    runCatching { session.setDisplayPower(on = false) }
                        .onFailure { e -> Log.w(TAG, "start(): set display power failed", e) }
                }
            }

            // Create session info
            _currentSessionState.value = info.copy(
                legacyPaste = options.legacyPaste,
                mouseHover = options.mouseHover,
                killAdbOnClose = options.killAdbOnClose,
                videoPlayback = options.videoPlayback,
                audioPlayback = options.audioPlayback,
                keyInjectMode = options.keyInjectMode,
                forwardKeyRepeat = options.forwardKeyRepeat,
            )
            isRunning = true
            startClipboardSync()

            // Setup video consumer (notify NativeCoreFacade to setup decoders)
            if (options.video) {
                NativeCoreFacade.onScrcpySessionStarted(info, session)
            }

            // Setup audio player
            audioPlayer?.release()
            audioPlayer = null
            mp4Recorder?.release()
            mp4Recorder = null
            wavRecorder?.release()
            wavRecorder = null
            aacRecorder?.release()
            aacRecorder = null
            if (info.audioCodecId != 0 && options.audioPlayback) {
                Log.i(
                    TAG,
                    "start(): create audio player codecId=0x${
                        info.audioCodecId.toUInt().toString(16)
                    }"
                )
                val player = ScrcpyAudioPlayer(appContext, info.audioCodecId, lowLatency)
                audioPlayer = player
                session.attachAudioConsumer { packet ->
                    player.feedPacket(packet.data, packet.ptsUs, packet.isConfig)
                }
            } else {
                Log.i(TAG, "start(): audio playback disabled for this session")
            }

            if (options.recordFilename.isNotBlank()) {
                val recordFile = RecordingFileResolver.resolve(options, info)
                when (options.recordFormat) {
                    ClientOptions.RecordFormat.MP4,
                    ClientOptions.RecordFormat.M4A -> {
                        val recorder = NativeMp4Recorder(
                            outputFile = recordFile,
                            includeVideo = options.video,
                            includeAudio = options.audio && info.audioCodec != null,
                            inputAudioCodec = info.audioCodec,
                            width = info.width,
                            height = info.height,
                            videoBitRate = options.videoBitRate,
                            audioBitRate = options.audioBitRate,
                        )
                        recorder.start()
                        mp4Recorder = recorder
                        if (options.audio && info.audioCodec != null) {
                            session.attachAudioConsumer { packet ->
                                recorder.feedAudioPacket(packet.data, packet.ptsUs, packet.isConfig)
                            }
                        }
                    }

                    ClientOptions.RecordFormat.WAV -> {
                        val codec = info.audioCodec
                            ?: throw IllegalStateException("WAV recording requires an audio stream")
                        val recorder = NativeWavRecorder(
                            outputFile = recordFile,
                            inputCodec = codec,
                        )
                        wavRecorder = recorder
                        session.attachAudioConsumer { packet ->
                            recorder.feedPacket(packet.data, packet.ptsUs, packet.isConfig)
                        }
                    }

                    ClientOptions.RecordFormat.AAC -> {
                        val codec = info.audioCodec
                            ?: throw IllegalStateException("AAC recording requires an audio stream")
                        val recorder = NativeAacRecorder(
                            outputFile = recordFile,
                            inputCodec = codec,
                            bitRate = options.audioBitRate,
                        )
                        aacRecorder = recorder
                        session.attachAudioConsumer { packet ->
                            recorder.feedPacket(packet.data, packet.ptsUs, packet.isConfig)
                        }
                    }

                    else -> Unit
                }
                Log.i(TAG, "start(): recording -> ${recordFile.absolutePath}")
            }

            Log.i(
                TAG, "start(): Session started successfully - device=${info.deviceName}, " +
                        "video=${if (options.video) "${info.codec?.string ?: "null"} ${info.width}x${info.height}" else "off"}, " +
                        "audio=${if (options.audio) options.audioCodec.string else "off"}, " +
                        "control=${options.control}"
            )

            return@withContext info

        } catch (e: Exception) {
            Log.e(TAG, "start(): Failed to start scrcpy session", e)
            audioPlayer?.release()
            audioPlayer = null
            runCatching { mp4Recorder?.release() }
            mp4Recorder = null
            runCatching { wavRecorder?.release() }
            wavRecorder = null
            runCatching { aacRecorder?.release() }
            aacRecorder = null
            isRunning = false
            _currentSessionState.value = null
            throw e
        }
    }

    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        if (!isRunning) {
            Log.w(TAG, "stop(): No active session to stop")
            return@withContext false
        }

        Log.i(TAG, "stop(): Stopping scrcpy session")

        return@withContext try {
            session.clearVideoConsumer()
            session.clearAudioConsumer()
            mp4Recorder?.release()
            mp4Recorder = null
            wavRecorder?.release()
            wavRecorder = null
            aacRecorder?.release()
            aacRecorder = null
            NativeCoreFacade.onScrcpySessionStopped()
            session.stop()
            audioPlayer?.release()
            audioPlayer = null
            isRunning = false
            _currentSessionState.value = null
            stopClipboardSync()
            Log.i(TAG, "stop(): Session stopped successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "stop(): Failed to stop session", e)
            false
        }
    }

    fun isStarted(): Boolean = isRunning && session.isStarted()

    suspend fun startApp(name: String) = withContext(Dispatchers.IO) {
        session.startApp(name)
    }

    suspend fun injectKeycode(
        action: Int,
        keycode: Int,
        repeat: Int = 0,
        metaState: Int = 0,
    ) = withContext(Dispatchers.IO) {
        session.injectKeycode(
            action = action,
            keycode = keycode,
            repeat = repeat,
            metaState = metaState,
        )
    }

    suspend fun injectText(text: String) = withContext(Dispatchers.IO) {
        session.injectText(text)
    }

    suspend fun setClipboard(text: String, paste: Boolean) = withContext(Dispatchers.IO) {
        session.setClipboard(text, paste)
    }

    suspend fun injectTouch(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float,
        actionButton: Int = 0,
        buttons: Int = 0,
    ) = withContext(Dispatchers.IO) {
        session.injectTouch(
            action = action,
            pointerId = pointerId,
            x = x,
            y = y,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            pressure = pressure,
            actionButton = actionButton,
            buttons = buttons,
        )
    }

    suspend fun injectScroll(
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        hScroll: Float,
        vScroll: Float,
        buttons: Int,
    ) = withContext(Dispatchers.IO) {
        session.injectScroll(
            x = x,
            y = y,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            hScroll = hScroll,
            vScroll = vScroll,
            buttons = buttons,
        )
    }

    suspend fun pressBackOrTurnScreenOn(action: Int = KeyEvent.ACTION_DOWN) =
        withContext(Dispatchers.IO) {
            session.pressBackOrTurnScreenOn(action)
        }

    fun updateCurrentSessionSize(width: Int, height: Int) {
        val current = _currentSessionState.value ?: return
        if (current.width == width && current.height == height) return
        _currentSessionState.value = current.copy(width = width, height = height)
    }

    private fun startClipboardSync() {
        synchronized(clipboardSyncLock) {
            lastKnownLocalClipboardText = LocalInputService.getClipboardText(appContext)
            pendingRemoteClipboardText = null
            if (!clipboardListenerRegistered) {
                clipboardManager?.addPrimaryClipChangedListener(clipboardChangedListener)
                clipboardListenerRegistered = true
            }
        }
    }

    private fun stopClipboardSync() {
        synchronized(clipboardSyncLock) {
            if (clipboardListenerRegistered) {
                clipboardManager?.removePrimaryClipChangedListener(clipboardChangedListener)
                clipboardListenerRegistered = false
            }
            pendingRemoteClipboardText = null
        }
    }

    private fun handleLocalClipboardChanged() {
        val text = LocalInputService.getClipboardText(appContext)
        val shouldSync = synchronized(clipboardSyncLock) {
            if (text == lastKnownLocalClipboardText) {
                return@synchronized false
            }
            lastKnownLocalClipboardText = text
            if (text != null && text == pendingRemoteClipboardText) {
                pendingRemoteClipboardText = null
                return@synchronized false
            }
            true
        }
        if (!shouldSync) {
            return
        }
        if (!isRunning || !appSettings.bundleState.value.realtimeClipboardSyncToDevice) {
            return
        }
        val content = text?.takeIf { it.isNotBlank() } ?: return
        backgroundScope.launch {
            runCatching {
                session.setClipboard(content, paste = false)
            }.onFailure { error ->
                Log.w(TAG, "realtime clipboard sync failed", error)
            }
        }
    }

    private fun handleRemoteClipboardText(text: String) {
        val shouldWrite = synchronized(clipboardSyncLock) {
            if (text == lastKnownLocalClipboardText) {
                return@synchronized false
            }
            pendingRemoteClipboardText = text
            lastKnownLocalClipboardText = text
            true
        }
        if (!shouldWrite) {
            return
        }
        LocalInputService.setClipboardText(appContext, text)
    }

    inner class Listings {
        private val encodersMutex = Mutex()
        private val displaysMutex = Mutex()
        private val camerasMutex = Mutex()
        private val cameraSizesMutex = Mutex()
        private val appsMutex = Mutex()
        private val recentTasksMutex = Mutex()

        private val _refreshBusy = MutableStateFlow(false)
        private val _refreshVersion = MutableStateFlow(0)
        private val _refreshCounter = MutableStateFlow(0)

        @Volatile
        private var cachedVideoEncoders: List<EncoderInfo>? = null

        @Volatile
        private var cachedAudioEncoders: List<EncoderInfo>? = null

        @Volatile
        private var cachedDisplays: List<DisplayInfo>? = null

        @Volatile
        private var cachedCameras: List<CameraInfo>? = null

        @Volatile
        private var cachedCameraSizes: List<String>? = null

        @Volatile
        private var cachedApps: List<AppInfo>? = null

        @Volatile
        private var cachedAppsByPackage: Map<String, AppInfo> = emptyMap()

        @Volatile
        private var cachedRecentTasks: List<RecentTaskInfo>? = null

        val refreshBusyState: StateFlow<Boolean> = _refreshBusy.asStateFlow()
        val refreshVersionState: StateFlow<Int> = _refreshVersion.asStateFlow()
        val videoEncoders: List<EncoderInfo> get() = cachedVideoEncoders.orEmpty()
        val audioEncoders: List<EncoderInfo> get() = cachedAudioEncoders.orEmpty()
        val displays: List<DisplayInfo> get() = cachedDisplays.orEmpty()
        val cameras: List<CameraInfo> get() = cachedCameras.orEmpty()
        val cameraSizes: List<String> get() = cachedCameraSizes.orEmpty()
        val apps: List<AppInfo> get() = cachedApps.orEmpty()
        val recentTasks: List<RecentTaskInfo> get() = cachedRecentTasks.orEmpty()

        fun findCachedApp(packageName: String): AppInfo? = cachedAppsByPackage[packageName]

        suspend fun getVideoEncoders(forceRefresh: Boolean = false): List<EncoderInfo> {
            cachedVideoEncoders?.takeUnless { forceRefresh }?.let { return it }
            return getEncoders(forceRefresh).first
        }

        suspend fun getAudioEncoders(forceRefresh: Boolean = false): List<EncoderInfo> {
            cachedAudioEncoders?.takeUnless { forceRefresh }?.let { return it }
            return getEncoders(forceRefresh).second
        }

        suspend fun getEncoders(forceRefresh: Boolean = false)
                : Pair<List<EncoderInfo>, List<EncoderInfo>> {
            if (!forceRefresh && cachedVideoEncoders != null && cachedAudioEncoders != null)
                return cachedVideoEncoders.orEmpty() to cachedAudioEncoders.orEmpty()

            return encodersMutex.withLock {
                if (!forceRefresh && cachedVideoEncoders != null && cachedAudioEncoders != null)
                    return@withLock cachedVideoEncoders.orEmpty() to cachedAudioEncoders.orEmpty()

                runTrackedFetch {
                    val output = executeList(ListOptions.ENCODERS)
                    val (video, audio) = parseEncoders(output)
                    cachedVideoEncoders = video
                    cachedAudioEncoders = audio
                    logListPreview(
                        list = ListOptions.ENCODERS,
                        countSummary = "video=${video.size} audio=${audio.size}",
                        output = output,
                    )
                    video to audio
                }
            }
        }

        suspend fun getDisplays(forceRefresh: Boolean = false): List<DisplayInfo> {
            cachedDisplays?.takeUnless { forceRefresh }?.let { return it }
            return displaysMutex.withLock {
                cachedDisplays?.takeUnless { forceRefresh } ?: run {
                    runTrackedFetch {
                        val output = executeList(ListOptions.DISPLAYS)
                        val parsed = parseDisplays(output)
                        cachedDisplays = parsed
                        logListPreview(
                            list = ListOptions.DISPLAYS,
                            countSummary = "displays=${parsed.size}",
                            output = output,
                        )
                        parsed
                    }
                }
            }
        }

        suspend fun getCameras(forceRefresh: Boolean = false): List<CameraInfo> {
            cachedCameras?.takeUnless { forceRefresh }?.let { return it }
            return camerasMutex.withLock {
                cachedCameras?.takeUnless { forceRefresh } ?: run {
                    runTrackedFetch {
                        val output = executeList(ListOptions.CAMERAS)
                        val parsed = parseCameras(output)
                        cachedCameras = parsed
                        logListPreview(
                            list = ListOptions.CAMERAS,
                            countSummary = "cameras=${parsed.size}",
                            output = output,
                        )
                        parsed
                    }
                }
            }
        }

        suspend fun getCameraSizes(forceRefresh: Boolean = false): List<String> {
            cachedCameraSizes?.takeUnless { forceRefresh }?.let { return it }
            return cameraSizesMutex.withLock {
                cachedCameraSizes?.takeUnless { forceRefresh } ?: run {
                    runTrackedFetch {
                        val output = executeList(ListOptions.CAMERA_SIZES)
                        val parsed = parseCameraSizes(output)
                            .sortedWith(compareByDescending { size ->
                                size.substringBefore('x').toIntOrNull() ?: 0
                            })
                        cachedCameraSizes = parsed
                        logListPreview(
                            list = ListOptions.CAMERA_SIZES,
                            countSummary = "sizes=${parsed.size}",
                            output = output,
                        )
                        parsed
                    }
                }
            }
        }

        suspend fun getApps(forceRefresh: Boolean = false): List<AppInfo> {
            cachedApps?.takeUnless { forceRefresh }?.let { return it }
            return appsMutex.withLock {
                cachedApps?.takeUnless { forceRefresh } ?: run {
                    runTrackedFetch {
                        val output = executeList(ListOptions.APPS)
                        val parsed = parseApps(output)
                        cachedApps = parsed
                        cachedAppsByPackage = parsed.associateBy { it.packageName }
                        cachedRecentTasks = cachedRecentTasks?.map { task ->
                            task.copy(appLabel = cachedAppsByPackage[task.packageName]?.label)
                        }
                        logListPreview(
                            list = ListOptions.APPS,
                            countSummary = "apps=${parsed.size}",
                            output = output,
                        )
                        parsed
                    }
                }
            }
        }

        suspend fun getRecentTasks(forceRefresh: Boolean = false): List<RecentTaskInfo> {
            cachedRecentTasks?.takeUnless { forceRefresh }?.let { return it }
            return recentTasksMutex.withLock {
                cachedRecentTasks?.takeUnless { forceRefresh } ?: run {
                    runTrackedFetch {
                        val output = NativeAdbService.shell("dumpsys activity recents")
                        val parsed = parseRecentTasks(output).map { task ->
                            task.copy(appLabel = cachedAppsByPackage[task.packageName]?.label)
                        }
                        cachedRecentTasks = parsed
                        Log.i(TAG, "recentTasks(): parsed count=${parsed.size}")
                        parsed
                    }
                }
            }
        }

        private suspend fun <T> runTrackedFetch(block: suspend () -> T): T {
            _refreshCounter.update { it + 1 }
            _refreshBusy.value = true
            return try {
                block().also {
                    _refreshVersion.update { it + 1 }
                }
            } finally {
                val remaining = (_refreshCounter.value - 1).coerceAtLeast(0)
                _refreshCounter.value = remaining
                _refreshBusy.value = remaining > 0
            }
        }
    }

    private suspend fun executeList(list: ListOptions): String = withContext(Dispatchers.IO) {
        require(list != ListOptions.NULL) { "Nothing to do with ListOptions.NULL" }

        val serverJar = if (customServerUri.isNullOrBlank()) {
            extractAssetToCache(serverAsset)
        } else {
            extractUriToCache(customServerUri.toUri())
        }

        NativeAdbService.push(serverJar.toPath(), serverRemotePath)

        val scid = generateScid()
        val options = ClientOptions(
            video = false,
            audio = false,
            control = false,
            cleanup = false,
            list = list,
        )
        val serverParams = options.toServerParams(scid)
        val serverCommand = serverParams.build(
            "CLASSPATH=$serverRemotePath",
            "app_process",
            "/",
            "com.genymobile.scrcpy.Server",
            serverVersion,
        )

        Log.i(TAG, "listOptions(): cmd=$serverCommand")
        NativeAdbService.shell("$serverCommand 2>&1")
    }

    private fun logListPreview(list: ListOptions, countSummary: String, output: String) {
        val preview = output.lineSequence().take(32).joinToString("\n")
        Log.i(TAG, "listOptions($list): parsed $countSummary, outputPreview=\n$preview")
    }

    private fun parseEncoders(output: String): Pair<List<EncoderInfo>, List<EncoderInfo>> {
        val videoInfos = linkedMapOf<String, EncoderInfo>()
        val audioInfos = linkedMapOf<String, EncoderInfo>()

        VIDEO_ENCODER_INFO_REGEX.findAll(output).forEach { match ->
            val info = EncoderInfo(
                codec = Codec.fromString(match.groupValues[1], Codec.Type.VIDEO),
                id = match.groupValues[2],
                type = if (match.groupValues[3] == EncoderType.HARDWARE.s) {
                    EncoderType.HARDWARE
                } else {
                    EncoderType.SOFTWARE
                },
                isVendor = match.groupValues[4].isNotBlank(),
                aliasOf = match.groupValues[5].ifBlank { null },
            )
            videoInfos.putIfAbsent(info.id, info)
        }

        AUDIO_ENCODER_INFO_REGEX.findAll(output).forEach { match ->
            val info = EncoderInfo(
                codec = Codec.fromString(match.groupValues[1], Codec.Type.AUDIO),
                id = match.groupValues[2],
                type = if (match.groupValues[3] == EncoderType.HARDWARE.s) {
                    EncoderType.HARDWARE
                } else {
                    EncoderType.SOFTWARE
                },
                isVendor = match.groupValues[4].isNotBlank(),
                aliasOf = match.groupValues[5].ifBlank { null },
            )
            audioInfos.putIfAbsent(info.id, info)
        }

        return videoInfos.values.toList() to audioInfos.values.toList()
    }

    private fun parseDisplays(output: String): List<DisplayInfo> {
        val displays = LinkedHashSet<DisplayInfo>()
        DISPLAY_REGEX.findAll(output).forEach { match ->
            displays.add(
                DisplayInfo(
                    id = match.groupValues[1].toInt(),
                    width = match.groupValues[2].toInt(),
                    height = match.groupValues[3].toInt(),
                )
            )
        }
        return displays.toList()
    }

    private fun parseCameras(output: String): List<CameraInfo> {
        val cameras = LinkedHashSet<CameraInfo>()
        CAMERA_INFO_REGEX.findAll(output).forEach { match ->
            val facing = match.groupValues[2]
            val activeSize = match.groupValues[3]
            val fpsValues = match.groupValues[4]
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }

            cameras.add(
                CameraInfo(
                    id = match.groupValues[1],
                    facing = CameraFacing.fromString(facing),
                    activeSize = activeSize,
                    fps = fpsValues.map(Int::toUShort),
                )
            )
        }
        return cameras.toList()
    }

    private fun parseCameraSizes(output: String): List<String> {
        val sizes = LinkedHashSet<String>()
        CAMERA_SIZE_REGEX.findAll(output).forEach { match ->
            sizes.add(match.groupValues[1])
        }
        return sizes.toList()
    }

    private fun parseApps(output: String): List<AppInfo> {
        val apps = LinkedHashSet<AppInfo>()
        APP_REGEX.findAll(output).forEach { match ->
            apps.add(
                AppInfo(
                    system = match.groupValues[1] == "*",
                    label = match.groupValues[2].trim(),
                    packageName = match.groupValues[3].trim(),
                )
            )
        }
        return apps.toList()
    }

    private fun parseRecentTasks(output: String): List<RecentTaskInfo> {
        val packages = LinkedHashSet<String>()
        RECENT_TASK_PACKAGE_REGEX.findAll(output).forEach { match ->
            val packageName = match.groupValues[1].trim()
            if (packageName.isNotBlank()) {
                packages += packageName
            }
        }
        return packages.map { packageName ->
            RecentTaskInfo(
                packageName = packageName,
            )
        }
    }

    data class EncoderInfo(
        val codec: Codec,
        val id: String,
        val type: EncoderType,
        val isVendor: Boolean,
        val aliasOf: String? = null,
    )

    data class CameraInfo(
        val id: String,
        val facing: CameraFacing,
        val activeSize: String,
        val fps: List<UShort>,
    )

    data class DisplayInfo(
        val id: Int,
        val width: Int,
        val height: Int,
    )

    data class AppInfo(
        val system: Boolean?,
        val label: String?,
        val packageName: String,
    )

    data class RecentTaskInfo(
        val packageName: String,
        val appLabel: String? = null,
    )

    private suspend fun executeServer(
        serverJar: File,
        options: ClientOptions,
        scid: UInt,
    ): Session.SessionInfo {
        NativeAdbService.push(serverJar.toPath(), serverRemotePath)

        val serverParams = options.toServerParams(scid)

        val serverCommand = serverParams.build(
            "CLASSPATH=$serverRemotePath",
            "app_process",
            "/",
            "com.genymobile.scrcpy.Server",
            serverVersion,
        )
        Log.d(TAG, "Server command: $serverCommand")

        // Execute server (equivalent to sc_adb_execute in C)
        Log.i(TAG, "executeServer(): Starting scrcpy server")
        logEvent("scrcpy-server args: $serverCommand")
        val sessionInfo = session.start(
            serverCommand = serverCommand,
            scid = scid,
            options = options,
        )
        Log.i(TAG, "executeServer(): session.start() returned, checking if session is still active")
        if (!session.isStarted()) {
            Log.e(TAG, "executeServer(): WARNING - session was cleared immediately after start()!")
        }
        return sessionInfo
    }

    private fun extractAssetToCache(assetPath: String): File {
        val clean = assetPath.removePrefix("/")
        val source = appContext.assets.open(clean)
        val outputFile = File(appContext.cacheDir, File(clean).name)
        source.use { input ->
            outputFile.outputStream().use { output -> input.copyTo(output) }
        }
        return outputFile
    }

    private fun extractUriToCache(uri: Uri): File {
        val fileName = "custom-scrcpy-server.jar"
        val outputFile = File(appContext.cacheDir, fileName)
        appContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected server URI" }
            outputFile.outputStream().use { output -> input.copyTo(output) }
        }
        return outputFile
    }

    /**
     * Session manager for scrcpy protocol.
     * Handles socket communication, video/audio streaming, and control input.
     */
    class Session(
        private val onRemoteClipboardText: (String) -> Unit,
    ) {
        private val mutex = Mutex()

        @Volatile
        private var activeSession: ActiveSession? = null

        private val videoConsumers = linkedSetOf<(VideoPacket) -> Unit>()

        @Volatile
        private var videoReaderThread: Thread? = null

        private val audioConsumers = linkedSetOf<(AudioPacket) -> Unit>()

        @Volatile
        private var audioReaderThread: Thread? = null

        @Volatile
        private var controlReaderThread: Thread? = null

        private val serverLogBuffer = ArrayDeque<String>()

        suspend fun start(
            serverCommand: String,
            scid: UInt,
            options: ClientOptions,
        ): SessionInfo = mutex.withLock {
            stopInternal()
            serverLogBuffer.clear()
            val socketName = socketNameFor(scid.toInt())

            try {
                val serverStream = NativeAdbService.openShellStream(serverCommand)
                val serverLogThread = startServerLogThread(serverStream, socketName)
                Thread.sleep(SERVER_BOOT_DELAY_MS)

                val firstStream = openAbstractSocketWithRetry(socketName, expectDummyByte = true)
                val firstInput = DataInputStream(BufferedInputStream(firstStream.inputStream))

                var videoStream: AdbSocketStream? = null
                var videoInput: DataInputStream? = null
                var audioStream: AdbSocketStream? = null
                var audioInput: DataInputStream? = null
                var controlStream: AdbSocketStream? = null

                when {
                    options.video -> {
                        videoStream = firstStream
                        videoInput = firstInput
                    }

                    options.audio -> {
                        audioStream = firstStream
                        audioInput = firstInput
                    }

                    options.control -> {
                        controlStream = firstStream
                    }

                    else -> {
                        throw IllegalArgumentException("At least one of video/audio/control must be enabled")
                    }
                }

                if (options.video && videoStream == null) {
                    val vStream = openAbstractSocketWithRetry(socketName, expectDummyByte = false)
                    videoStream = vStream
                    videoInput = DataInputStream(BufferedInputStream(vStream.inputStream))
                }

                if (options.audio && audioStream == null) {
                    val aStream = openAbstractSocketWithRetry(socketName, expectDummyByte = false)
                    audioStream = aStream
                    audioInput = DataInputStream(BufferedInputStream(aStream.inputStream))
                }

                if (options.control && controlStream == null) {
                    controlStream = openAbstractSocketWithRetry(socketName, expectDummyByte = false)
                }

                val deviceName = readDeviceName(firstInput)
                val audioCodecId = if (options.audio) {
                    val aInput = checkNotNull(audioInput)
                    when (val streamCodecId = aInput.readInt()) {
                        AUDIO_DISABLED -> {
                            Log.w(TAG, "audio disabled by server")
                            if (options.requireAudio) {
                                throw IllegalStateException(
                                    "Audio is required but was disabled by the server"
                                )
                            }
                            0
                        }

                        AUDIO_ERROR -> {
                            Log.e(TAG, "audio stream configuration error from server")
                            if (options.requireAudio) {
                                throw IllegalStateException(
                                    "Audio is required but the server failed to configure audio capture"
                                )
                            }
                            0
                        }

                        else -> {
                            Log.i(
                                TAG,
                                "audio stream codec=0x${streamCodecId.toUInt().toString(16)}"
                            )
                            streamCodecId
                        }
                    }
                } else {
                    0
                }
                val videoCodecId: Int
                val width: Int
                val height: Int
                if (options.video) {
                    val vInput = checkNotNull(videoInput)
                    videoCodecId = vInput.readInt()
                    width = vInput.readInt()
                    height = vInput.readInt()
                } else {
                    videoCodecId = 0
                    width = 0
                    height = 0
                }

                val sessionInfo = SessionInfo(
                    deviceName = deviceName,
                    codecId = videoCodecId,
                    codec = Codec.fromId(videoCodecId, Codec.Type.VIDEO),
                    width = width,
                    height = height,
                    audioCodecId = audioCodecId,
                    audioCodec = Codec.fromId(audioCodecId, Codec.Type.AUDIO),
                    controlEnabled = controlStream != null,
                )

                val controlWriter = controlStream?.let { stream ->
                    ControlWriter(DataOutputStream(stream.outputStream))
                }
                val controlInput = controlStream?.let { stream ->
                    if (stream === firstStream) firstInput
                    else DataInputStream(BufferedInputStream(stream.inputStream))
                }

                val newSession = ActiveSession(
                    info = sessionInfo,
                    socketName = socketName,
                    serverStream = serverStream,
                    serverLogThread = serverLogThread,
                    videoStream = videoStream,
                    videoInput = videoInput,
                    audioStream = audioStream,
                    audioInput = audioInput,
                    controlStream = controlStream,
                    controlInput = controlInput,
                    controlWriter = controlWriter,
                )
                activeSession = newSession
                if (options.control && options.clipboardAutosync)
                    startControlReaderThread(newSession)

                return sessionInfo
            } catch (t: Throwable) {
                val tail = snapshotServerLogs()
                val detail = if (tail.isBlank()) "" else " | server_log_tail=\n$tail"
                throw IllegalStateException("scrcpy start failed: ${t.message}$detail", t)
            }
        }

        suspend fun attachVideoConsumer(consumer: (VideoPacket) -> Unit): Unit = mutex.withLock {
            val session = activeSession ?: throw IllegalStateException("scrcpy session not started")
            val vInput = session.videoInput ?: return
            val vStream = session.videoStream ?: return
            videoConsumers += consumer
            if (videoReaderThread?.isAlive == true) {
                return
            }

            videoReaderThread = thread(start = true, name = "scrcpy-video-reader") {
                try {
                    while (activeSession === session && !vStream.closed) {
                        try {
                            val ptsAndFlags = vInput.readLong()
                            val packetSize = vInput.readInt()
                            if (packetSize <= 0) {
                                continue
                            }

                            val payload = ByteArray(packetSize)
                            vInput.readFully(payload)

                            val config = (ptsAndFlags and PACKET_FLAG_CONFIG) != 0L
                            val keyFrame = (ptsAndFlags and PACKET_FLAG_KEY_FRAME) != 0L
                            val ptsUs = ptsAndFlags and PACKET_PTS_MASK
                            val packet = VideoPacket(
                                data = payload,
                                ptsUs = ptsUs,
                                isConfig = config,
                                isKeyFrame = keyFrame,
                            )
                            videoConsumers.forEach { it(packet) }
                        } catch (_: EOFException) {
                            break
                        } catch (_: InterruptedException) {
                            if (activeSession !== session || vStream.closed) {
                                break
                            }
                            Thread.interrupted()
                        } catch (e: Exception) {
                            Log.w(TAG, "video reader failed", e)
                            break
                        }
                    }
                } finally {
                }
            }
        }

        suspend fun clearVideoConsumer() = mutex.withLock { videoConsumers.clear() }

        suspend fun attachAudioConsumer(consumer: (AudioPacket) -> Unit): Unit = mutex.withLock {
            val session = activeSession ?: throw IllegalStateException("scrcpy session not started")
            val aInput = session.audioInput ?: return
            val aStream = session.audioStream ?: return
            audioConsumers += consumer
            if (audioReaderThread?.isAlive == true) return

            audioReaderThread = thread(start = true, name = "scrcpy-audio-reader") {
                try {
                    while (activeSession === session && !aStream.closed) {
                        try {
                            val ptsAndFlags = aInput.readLong()
                            val packetSize = aInput.readInt()
                            if (packetSize <= 0) continue

                            val payload = ByteArray(packetSize)
                            aInput.readFully(payload)

                            val isConfig = (ptsAndFlags and PACKET_FLAG_CONFIG) != 0L
                            val ptsUs = ptsAndFlags and PACKET_PTS_MASK
                            val packet = AudioPacket(
                                data = payload,
                                ptsUs = ptsUs,
                                isConfig = isConfig
                            )
                            audioConsumers.forEach { it(packet) }
                        } catch (_: EOFException) {
                            break
                        } catch (_: InterruptedException) {
                            if (activeSession !== session || aStream.closed) {
                                break
                            }
                            Thread.interrupted()
                        } catch (e: Exception) {
                            Log.w(TAG, "audio reader failed", e)
                            break
                        }
                    }
                } finally {
                }
            }
        }

        suspend fun clearAudioConsumer() = mutex.withLock { audioConsumers.clear() }

        suspend fun startApp(name: String) = mutex.withLock {
            try {
                requireControlWriter().startApp(name)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "startApp(): control channel not available", e)
                throw e
            }
        }

        suspend fun injectKeycode(
            action: Int,
            keycode: Int,
            repeat: Int = 0,
            metaState: Int = 0,
        ) = mutex.withLock {
            try {
                requireControlWriter().injectKeycode(action, keycode, repeat, metaState)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "injectKeycode(): control channel not available", e)
            }
        }

        suspend fun injectText(text: String) = mutex.withLock {
            try {
                requireControlWriter().injectText(text)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "injectText(): control channel not available", e)
            }
        }

        suspend fun setClipboard(text: String, paste: Boolean) = mutex.withLock {
            try {
                requireControlWriter().setClipboard(text, paste)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "setClipboard(): control channel not available", e)
            }
        }

        suspend fun injectTouch(
            action: Int,
            pointerId: Long,
            x: Int,
            y: Int,
            screenWidth: Int,
            screenHeight: Int,
            pressure: Float,
            actionButton: Int,
            buttons: Int,
        ) = mutex.withLock {
            try {
                requireControlWriter().injectTouch(
                    action,
                    pointerId,
                    x,
                    y,
                    screenWidth,
                    screenHeight,
                    pressure,
                    actionButton,
                    buttons
                )
            } catch (e: IllegalStateException) {
                Log.w(TAG, "injectTouch(): control channel not available", e)
            }
        }

        suspend fun injectScroll(
            x: Int,
            y: Int,
            screenWidth: Int,
            screenHeight: Int,
            hScroll: Float,
            vScroll: Float,
            buttons: Int,
        ) = mutex.withLock {
            try {
                requireControlWriter().injectScroll(
                    x,
                    y,
                    screenWidth,
                    screenHeight,
                    hScroll,
                    vScroll,
                    buttons
                )
            } catch (e: IllegalStateException) {
                Log.w(TAG, "injectScroll(): control channel not available", e)
            }
        }

        suspend fun pressBackOrTurnScreenOn(action: Int = KeyEvent.ACTION_DOWN) = mutex.withLock {
            try {
                requireControlWriter().pressBackOrTurnScreenOn(action)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "pressBackOrScreenOn(): control channel not available", e)
            }
        }

        suspend fun setDisplayPower(on: Boolean) = mutex.withLock {
            try {
                requireControlWriter().setDisplayPower(on)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "setDisplayPower(): control channel not available", e)
            }
        }

        suspend fun stop() = mutex.withLock {
            stopInternal()
        }

        private fun stopInternal() {
            val session = activeSession ?: return
            activeSession = null
            videoConsumers.clear()
            audioConsumers.clear()

            if (Thread.currentThread() !== videoReaderThread) {
                runCatching { videoReaderThread?.interrupt() }
                runCatching { videoReaderThread?.join(300) }
            }
            videoReaderThread = null

            if (Thread.currentThread() !== audioReaderThread) {
                runCatching { audioReaderThread?.interrupt() }
                runCatching { audioReaderThread?.join(300) }
            }
            audioReaderThread = null

            if (Thread.currentThread() !== controlReaderThread) {
                runCatching { controlReaderThread?.interrupt() }
                runCatching { controlReaderThread?.join(300) }
            }
            controlReaderThread = null

            runCatching { session.controlStream?.close() }
            runCatching { session.audioStream?.close() }
            runCatching { session.videoStream?.close() }
            runCatching { session.serverStream.close() }
            if (Thread.currentThread() !== session.serverLogThread) {
                runCatching { session.serverLogThread.interrupt() }
                runCatching { session.serverLogThread.join(300) }
            }
        }

        fun isStarted(): Boolean = activeSession != null

        private fun requireControlWriter(): ControlWriter {
            val session = activeSession
                ?: throw IllegalStateException("scrcpy control channel not available")
            return session.controlWriter
                ?: throw IllegalStateException("scrcpy control channel not available")
        }

        private fun startControlReaderThread(session: ActiveSession) {
            val controlInput = session.controlInput ?: return
            val controlStream = session.controlStream ?: return
            if (controlReaderThread?.isAlive == true) return

            controlReaderThread = thread(start = true, name = "scrcpy-control-reader") {
                try {
                    while (activeSession === session && !controlStream.closed) {
                        try {
                            when (controlInput.readUnsignedByte()) {
                                DEVICE_MSG_TYPE_CLIPBOARD -> {
                                    val size = controlInput.readInt()
                                    if (size !in 0..DEVICE_MSG_MAX_SIZE) {
                                        throw IllegalStateException("Invalid clipboard size: $size")
                                    }
                                    val payload = ByteArray(size)
                                    controlInput.readFully(payload)
                                    onRemoteClipboardText(payload.toString(Charsets.UTF_8))
                                }

                                DEVICE_MSG_TYPE_ACK_CLIPBOARD -> {
                                    controlInput.readLong()
                                }

                                DEVICE_MSG_TYPE_UHID_OUTPUT -> {
                                    controlInput.readUnsignedShort()
                                    val size = controlInput.readUnsignedShort()
                                    controlInput.skipBytes(size)
                                }

                                else -> {
                                    throw IllegalStateException("Unknown device message type")
                                }
                            }
                        } catch (_: EOFException) {
                            break
                        } catch (_: InterruptedException) {
                            if (activeSession !== session || controlStream.closed) {
                                break
                            }
                            Thread.interrupted()
                        } catch (e: Exception) {
                            Log.w(TAG, "control reader failed", e)
                            break
                        }
                    }
                } finally {
                }
            }
        }

        private fun startServerLogThread(
            serverStream: AdbSocketStream,
            socketName: String
        ): Thread {
            return thread(start = true, name = "scrcpy-server-log") {
                try {
                    BufferedReader(
                        InputStreamReader(
                            serverStream.inputStream,
                            Charsets.UTF_8
                        )
                    ).use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            synchronized(serverLogBuffer) {
                                if (serverLogBuffer.size >= SERVER_LOG_BUFFER_MAX_LINES) {
                                    serverLogBuffer.removeFirst()
                                }
                                serverLogBuffer.addLast(line)
                            }
                            logEvent(line)
                            Log.i(TAG, "[server:$socketName] $line")
                        }
                    }
                } catch (e: Exception) {
                    if (activeSession != null) {
                        Log.w(TAG, "server log thread failed", e)
                    }
                }
            }
        }

        private fun snapshotServerLogs(maxLines: Int = 120): String {
            val snapshot = synchronized(serverLogBuffer) {
                if (serverLogBuffer.isEmpty()) {
                    return ""
                }
                val take = maxLines.coerceIn(1, SERVER_LOG_BUFFER_MAX_LINES)
                serverLogBuffer.toList().takeLast(take)
            }
            return snapshot.joinToString("\n")
        }

        private suspend fun openAbstractSocketWithRetry(
            socketName: String,
            expectDummyByte: Boolean
        ): AdbSocketStream {
            var lastEx: Exception? = null
            repeat(CONNECT_RETRY_COUNT) { attempt ->
                try {
                    val stream = NativeAdbService.openAbstractSocket(socketName)
                    if (expectDummyByte) {
                        val value = stream.inputStream.read()
                        if (value < 0) {
                            stream.close()
                            throw EOFException("scrcpy dummy byte missing")
                        }
                    }
                    return stream
                } catch (e: Exception) {
                    lastEx = e
                    if (attempt < CONNECT_RETRY_COUNT - 1) Thread.sleep(CONNECT_RETRY_DELAY_MS)
                }
            }
            throw IllegalStateException("Unable to open scrcpy socket '$socketName'", lastEx)
        }

        private fun readDeviceName(input: DataInputStream): String {
            val buffer = ByteArray(DEVICE_NAME_FIELD_LENGTH)
            input.readFully(buffer)
            val firstZero = buffer.indexOf(0)
            val length = if (firstZero >= 0) firstZero else buffer.size
            return buffer.copyOf(length).toString(Charsets.UTF_8)
        }

        data class SessionInfo(
            val deviceName: String,
            val codecId: Int,
            val codec: Codec?,
            val width: Int,
            val height: Int,
            val audioCodecId: Int = 0,
            val audioCodec: Codec? = null,
            val controlEnabled: Boolean,
            val legacyPaste: Boolean = false,
            val mouseHover: Boolean = true,
            val killAdbOnClose: Boolean = false,
            val videoPlayback: Boolean = true,
            val audioPlayback: Boolean = true,
            val keyInjectMode: ClientOptions.KeyInjectMode = ClientOptions.KeyInjectMode.MIXED,
            val forwardKeyRepeat: Boolean = true,
            val host: String = "",
            val port: Int = Defaults.ADB_PORT,
        )

        data class VideoPacket(
            val data: ByteArray,
            val ptsUs: Long,
            val isConfig: Boolean,
            val isKeyFrame: Boolean,
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                other as VideoPacket
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

        data class AudioPacket(
            val data: ByteArray,
            val ptsUs: Long,
            val isConfig: Boolean,
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                other as AudioPacket
                if (ptsUs != other.ptsUs) return false
                if (isConfig != other.isConfig) return false
                if (!data.contentEquals(other.data)) return false
                return true
            }

            override fun hashCode(): Int {
                var result = ptsUs.hashCode()
                result = 31 * result + isConfig.hashCode()
                result = 31 * result + data.contentHashCode()
                return result
            }
        }

        private data class ActiveSession(
            val info: SessionInfo,
            val socketName: String,
            val serverStream: AdbSocketStream,
            val serverLogThread: Thread,
            val videoStream: AdbSocketStream?,
            val videoInput: DataInputStream?,
            val audioStream: AdbSocketStream?,
            val audioInput: DataInputStream?,
            val controlStream: AdbSocketStream?,
            val controlInput: DataInputStream?,
            val controlWriter: ControlWriter?,
        )

        private class ControlWriter(private val output: DataOutputStream) {
            @Synchronized
            fun injectKeycode(action: Int, keycode: Int, repeat: Int, metaState: Int) {
                output.writeByte(TYPE_INJECT_KEYCODE)
                output.writeByte(action)
                output.writeInt(keycode)
                output.writeInt(repeat)
                output.writeInt(metaState)
                output.flush()
            }

            @Synchronized
            fun injectText(text: String) {
                val bytes = text.toByteArray(Charsets.UTF_8)
                output.writeByte(TYPE_INJECT_TEXT)
                output.writeInt(bytes.size)
                output.write(bytes)
                output.flush()
            }

            @Synchronized
            fun setClipboard(text: String, paste: Boolean) {
                val bytes = text.toByteArray(Charsets.UTF_8)
                output.writeByte(TYPE_SET_CLIPBOARD)
                output.writeLong(CLIPBOARD_SEQUENCE_INVALID)
                output.writeByte(if (paste) 1 else 0)
                output.writeInt(bytes.size)
                output.write(bytes)
                output.flush()
            }

            @Synchronized
            fun injectTouch(
                action: Int,
                pointerId: Long,
                x: Int,
                y: Int,
                screenWidth: Int,
                screenHeight: Int,
                pressure: Float,
                actionButton: Int,
                buttons: Int,
            ) {
                output.writeByte(TYPE_INJECT_TOUCH_EVENT)
                output.writeByte(action)
                output.writeLong(pointerId)
                writePosition(x, y, screenWidth, screenHeight)
                output.writeShort(encodeUnsignedFixedPoint16(pressure))
                output.writeInt(actionButton)
                output.writeInt(buttons)
                output.flush()
            }

            @Synchronized
            fun injectScroll(
                x: Int,
                y: Int,
                screenWidth: Int,
                screenHeight: Int,
                hScroll: Float,
                vScroll: Float,
                buttons: Int
            ) {
                output.writeByte(TYPE_INJECT_SCROLL_EVENT)
                writePosition(x, y, screenWidth, screenHeight)
                output.writeShort(encodeSignedFixedPoint16(hScroll / 16f))
                output.writeShort(encodeSignedFixedPoint16(vScroll / 16f))
                output.writeInt(buttons)
                output.flush()
            }

            @Synchronized
            fun pressBackOrTurnScreenOn(action: Int) {
                output.writeByte(TYPE_BACK_OR_SCREEN_ON)
                output.writeByte(action)
                output.flush()
            }

            @Synchronized
            fun setDisplayPower(on: Boolean) {
                output.writeByte(TYPE_SET_DISPLAY_POWER)
                output.writeBoolean(on)
                output.flush()
            }

            @Synchronized
            fun startApp(name: String) {
                val normalized = name.trim()
                val bytes = normalized.toByteArray(Charsets.UTF_8)
                require(normalized.isNotBlank()) { "start app name is blank" }
                require(bytes.size <= 255) { "start app name is too long" }
                output.writeByte(TYPE_START_APP)
                output.writeByte(bytes.size)
                output.write(bytes)
                output.flush()
            }

            private fun writePosition(x: Int, y: Int, screenWidth: Int, screenHeight: Int) {
                output.writeInt(x)
                output.writeInt(y)
                output.writeShort(screenWidth)
                output.writeShort(screenHeight)
            }

            private fun encodeUnsignedFixedPoint16(value: Float): Int {
                val clamped = value.coerceIn(0f, 1f)
                return if (clamped >= 1f) {
                    0xffff
                } else {
                    (clamped * 65536f).roundToInt().coerceIn(0, 0xfffe)
                }
            }

            private fun encodeSignedFixedPoint16(value: Float): Int {
                val clamped = value.coerceIn(-1f, 1f)
                if (clamped >= 1f) {
                    return 0x7fff
                }
                if (clamped <= -1f) {
                    return -0x8000
                }
                return (clamped * 32768f).roundToInt().coerceIn(-0x8000, 0x7ffe)
            }
        }

        companion object {
            private const val SERVER_BOOT_DELAY_MS = 200L
            private const val SERVER_LOG_BUFFER_MAX_LINES = 400
            private const val CONNECT_RETRY_COUNT = 100
            private const val CONNECT_RETRY_DELAY_MS = 100L
            private const val DEVICE_NAME_FIELD_LENGTH = 64
            private const val PACKET_FLAG_CONFIG = 1L shl 63
            private const val PACKET_FLAG_KEY_FRAME = 1L shl 62
            private const val PACKET_PTS_MASK = (1L shl 62) - 1

            private const val AUDIO_DISABLED = 0
            private const val AUDIO_ERROR = 1
            private const val DEVICE_MSG_MAX_SIZE = 1 shl 18
            private const val DEVICE_MSG_TYPE_CLIPBOARD = 0
            private const val DEVICE_MSG_TYPE_ACK_CLIPBOARD = 1
            private const val DEVICE_MSG_TYPE_UHID_OUTPUT = 2

            private const val TYPE_INJECT_KEYCODE = 0
            private const val TYPE_INJECT_TEXT = 1
            private const val TYPE_INJECT_TOUCH_EVENT = 2
            private const val TYPE_INJECT_SCROLL_EVENT = 3
            private const val TYPE_BACK_OR_SCREEN_ON = 4
            private const val TYPE_SET_CLIPBOARD = 9
            private const val TYPE_SET_DISPLAY_POWER = 10
            private const val TYPE_START_APP = 16
            private const val CLIPBOARD_SEQUENCE_INVALID = 0L

            private fun socketNameFor(scid: Int): String {
                return "scrcpy_%08x".format(scid)
            }
        }
    }
}
