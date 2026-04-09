package io.github.miuzarte.scrcpyforandroid.scrcpy

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
import io.github.miuzarte.scrcpyforandroid.services.EventLogger.logEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val adbService: NativeAdbService,
    private val serverAsset: String = DEFAULT_SERVER_ASSET,
    private val customServerUri: String? = null,
    private val serverVersion: String = DEFAULT_SERVER_VERSION,
    private val serverRemotePath: String = DEFAULT_REMOTE_PATH,
) {
    private val session = Session(adbService)
    private val nativeCore: NativeCoreFacade = NativeCoreFacade.get(appContext)

    private val _currentSessionState = MutableStateFlow<Session.SessionInfo?>(null)
    val currentSessionState: StateFlow<Session.SessionInfo?> = _currentSessionState.asStateFlow()

    @Volatile
    private var isRunning: Boolean = false

    @Volatile
    private var audioPlayer: ScrcpyAudioPlayer? = null

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
            val info = executeServer(
                serverJar = serverJar,
                options = options,
                scid = scid,
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
            _currentSessionState.value = info
            isRunning = true

            // Setup video consumer (notify NativeCoreFacade to setup decoders)
            if (options.video) {
                nativeCore.onScrcpySessionStarted(info, session)
            }

            // Setup audio player
            audioPlayer?.release()
            audioPlayer = null
            if (info.audioCodecId != 0 && options.audioPlayback) {
                Log.i(
                    TAG,
                    "start(): create audio player codecId=0x${
                        info.audioCodecId.toUInt().toString(16)
                    }"
                )
                val player = ScrcpyAudioPlayer(info.audioCodecId)
                audioPlayer = player
                session.attachAudioConsumer { packet ->
                    player.feedPacket(packet.data, packet.ptsUs, packet.isConfig)
                }
            } else {
                Log.i(TAG, "start(): audio playback disabled for this session")
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
            nativeCore.onScrcpySessionStopped()
            session.clearVideoConsumer()
            session.clearAudioConsumer()
            session.stop()
            audioPlayer?.release()
            audioPlayer = null
            isRunning = false
            _currentSessionState.value = null
            Log.i(TAG, "stop(): Session stopped successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "stop(): Failed to stop session", e)
            false
        }
    }

    suspend fun close() {
        stop()
        adbService.close()
    }

    fun isStarted(): Boolean = isRunning && session.isStarted()

    fun getCurrentSession(): Session.SessionInfo? = currentSessionState.value

    fun updateCurrentSessionSize(width: Int, height: Int) {
        val current = _currentSessionState.value ?: return
        if (current.width == width && current.height == height) return
        _currentSessionState.value = current.copy(width = width, height = height)
    }

    sealed class ListResult {
        data class Encoders(
            val videoEncoders: List<String>,
            val audioEncoders: List<String>,
            val videoEncoderTypes: Map<String, String> = emptyMap(),
            val audioEncoderTypes: Map<String, String> = emptyMap(),
            val rawOutput: String = "",
        ) : ListResult()

        data class Displays(
            val displays: List<DisplayInfo>,
            val rawOutput: String = "",
        ) : ListResult()

        data class Cameras(
            val cameras: List<CameraInfo>,
            val rawOutput: String = "",
        ) : ListResult()

        data class CameraSizes(
            val sizes: List<String>,
            val rawOutput: String = "",
        ) : ListResult()

        data class Apps(
            val apps: List<AppInfo>,
            val rawOutput: String = "",
        ) : ListResult()
    }

    inner class Listings {
        private val encodersMutex = Mutex()
        private val displaysMutex = Mutex()
        private val camerasMutex = Mutex()
        private val cameraSizesMutex = Mutex()
        private val appsMutex = Mutex()

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

        val videoEncoders: List<EncoderInfo> get() = cachedVideoEncoders.orEmpty()
        val audioEncoders: List<EncoderInfo> get() = cachedAudioEncoders.orEmpty()
        val displays: List<DisplayInfo> get() = cachedDisplays.orEmpty()
        val cameras: List<CameraInfo> get() = cachedCameras.orEmpty()
        val cameraSizes: List<String> get() = cachedCameraSizes.orEmpty()
        val apps: List<AppInfo> get() = cachedApps.orEmpty()

        suspend fun getVideoEncoders(forceRefresh: Boolean = false): List<EncoderInfo> {
            cachedVideoEncoders?.takeUnless { forceRefresh }?.let { return it }
            return getEncoders(forceRefresh).first
        }

        suspend fun getAudioEncoders(forceRefresh: Boolean = false): List<EncoderInfo> {
            cachedAudioEncoders?.takeUnless { forceRefresh }?.let { return it }
            return getEncoders(forceRefresh).second
        }

        private suspend fun getEncoders(forceRefresh: Boolean = false)
                : Pair<List<EncoderInfo>, List<EncoderInfo>> {
            if (!forceRefresh && cachedVideoEncoders != null && cachedAudioEncoders != null)
                return cachedVideoEncoders.orEmpty() to cachedAudioEncoders.orEmpty()

            return encodersMutex.withLock {
                if (!forceRefresh && cachedVideoEncoders != null && cachedAudioEncoders != null)
                    return@withLock cachedVideoEncoders.orEmpty() to cachedAudioEncoders.orEmpty()

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

        suspend fun getDisplays(forceRefresh: Boolean = false): List<DisplayInfo> {
            cachedDisplays?.takeUnless { forceRefresh }?.let { return it }
            return displaysMutex.withLock {
                cachedDisplays?.takeUnless { forceRefresh } ?: run {
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

        suspend fun getCameras(forceRefresh: Boolean = false): List<CameraInfo> {
            cachedCameras?.takeUnless { forceRefresh }?.let { return it }
            return camerasMutex.withLock {
                cachedCameras?.takeUnless { forceRefresh } ?: run {
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

        suspend fun getCameraSizes(forceRefresh: Boolean = false): List<String> {
            cachedCameraSizes?.takeUnless { forceRefresh }?.let { return it }
            return cameraSizesMutex.withLock {
                cachedCameraSizes?.takeUnless { forceRefresh } ?: run {
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

        suspend fun getApps(forceRefresh: Boolean = false): List<AppInfo> {
            cachedApps?.takeUnless { forceRefresh }?.let { return it }
            return appsMutex.withLock {
                cachedApps?.takeUnless { forceRefresh } ?: run {
                    val output = executeList(ListOptions.APPS)
                    val parsed = parseApps(output)
                    cachedApps = parsed
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

    private suspend fun executeList(list: ListOptions): String = withContext(Dispatchers.IO) {
        require(list != ListOptions.NULL) { "Nothing to do with ListOptions.NULL" }

        val serverJar = if (customServerUri.isNullOrBlank()) {
            extractAssetToCache(serverAsset)
        } else {
            extractUriToCache(customServerUri.toUri())
        }

        adbService.push(serverJar.toPath(), serverRemotePath)

        val scid = generateScid()
        val options = ClientOptions(
            video = false,
            audio = false,
            control = false,
            cleanUp = false,
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
        adbService.shell("$serverCommand 2>&1")
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
        val system: Boolean,
        val label: String,
        val packageName: String,
    )

    private suspend fun executeServer(
        serverJar: File,
        options: ClientOptions,
        scid: UInt,
    ): Session.SessionInfo {
        adbService.push(serverJar.toPath(), serverRemotePath)

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
    class Session(private val adbService: NativeAdbService) {
        private val mutex = Mutex()

        @Volatile
        private var activeSession: ActiveSession? = null

        @Volatile
        private var videoConsumer: ((VideoPacket) -> Unit)? = null

        @Volatile
        private var videoReaderThread: Thread? = null

        @Volatile
        private var audioConsumer: ((AudioPacket) -> Unit)? = null

        @Volatile
        private var audioReaderThread: Thread? = null

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
                val serverStream = adbService.openShellStream(serverCommand)
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
                    controlEnabled = controlStream != null,
                )

                val controlWriter = controlStream?.let { stream ->
                    ControlWriter(DataOutputStream(stream.outputStream))
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
                    controlWriter = controlWriter,
                )
                activeSession = newSession
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
            videoConsumer = consumer
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
                            videoConsumer?.invoke(
                                VideoPacket(
                                    data = payload,
                                    ptsUs = ptsUs,
                                    isConfig = config,
                                    isKeyFrame = keyFrame,
                                ),
                            )
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

        suspend fun clearVideoConsumer() = mutex.withLock { videoConsumer = null }

        suspend fun attachAudioConsumer(consumer: (AudioPacket) -> Unit): Unit = mutex.withLock {
            val session = activeSession ?: throw IllegalStateException("scrcpy session not started")
            val aInput = session.audioInput ?: return
            val aStream = session.audioStream ?: return
            audioConsumer = consumer
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
                            audioConsumer?.invoke(
                                AudioPacket(
                                    data = payload,
                                    ptsUs = ptsUs,
                                    isConfig = isConfig
                                )
                            )
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

        suspend fun clearAudioConsumer() = mutex.withLock { audioConsumer = null }

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
            videoConsumer = null
            audioConsumer = null

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
                    val stream = adbService.openAbstractSocket(socketName)
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
            val controlEnabled: Boolean,
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

            private const val TYPE_INJECT_KEYCODE = 0
            private const val TYPE_INJECT_TEXT = 1
            private const val TYPE_INJECT_TOUCH_EVENT = 2
            private const val TYPE_INJECT_SCROLL_EVENT = 3
            private const val TYPE_BACK_OR_SCREEN_ON = 4
            private const val TYPE_SET_DISPLAY_POWER = 10

            private fun socketNameFor(scid: Int): String {
                return "scrcpy_%08x".format(scid)
            }
        }
    }
}
