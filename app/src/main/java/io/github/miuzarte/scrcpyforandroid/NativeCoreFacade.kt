package io.github.miuzarte.scrcpyforandroid

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.core.net.toUri
import io.github.miuzarte.scrcpyforandroid.nativecore.AnnexBDecoder
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.nativecore.ScrcpyAudioPlayer
import io.github.miuzarte.scrcpyforandroid.nativecore.ScrcpySessionManager
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

class NativeCoreFacade(private val appContext: Context) {

    private val adbService = NativeAdbService(appContext)
    private val sessionManager = ScrcpySessionManager(adbService)
    private val executor = Executors.newSingleThreadExecutor()
    private val surfaceMap = ConcurrentHashMap<String, Surface>()
    private val surfaceIdentityMap = ConcurrentHashMap<String, Int>()
    private val decoderMap = ConcurrentHashMap<String, AnnexBDecoder>()
    private val videoSizeListeners = CopyOnWriteArraySet<(Int, Int) -> Unit>()
    private val videoFpsListeners = CopyOnWriteArraySet<(Float) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bootstrapLock = Any()
    private val bootstrapPackets = ArrayDeque<CachedPacket>()

    @Volatile
    private var latestConfigPacket: CachedPacket? = null
    private var packetCount: Long = 0

    @Volatile
    private var audioPlayer: ScrcpyAudioPlayer? = null

    @Volatile
    private var currentSessionInfo: ScrcpySessionInfo? = null

    fun close() {
        releaseAllDecoders()
        runCatching { sessionManager.stop() }
        runCatching { adbService.close() }
        executor.shutdown()
    }

    fun registerVideoSurface(tag: String, surface: Surface) {
        val newId = System.identityHashCode(surface)
        val oldId = surfaceIdentityMap[tag]
        if (oldId != null && oldId == newId && decoderMap.containsKey(tag)) {
            return
        }
        Log.i(TAG, "registerVideoSurface(): tag=$tag surfaceId=$newId oldSurfaceId=$oldId")
        surfaceMap[tag] = surface
        surfaceIdentityMap[tag] = newId
        val session = currentSessionInfo ?: return
        ensureVideoConsumerAttached()
        val decoder = decoderMap[tag]
        if (decoder != null) {
            val switched = decoder.switchOutputSurface(surface)
            Log.i(TAG, "registerVideoSurface(): switchOutputSurface tag=$tag success=$switched")
            if (switched) {
                return
            }
        }
        createOrReplaceDecoder(tag, surface, session)
    }

    fun unregisterVideoSurface(tag: String, surface: Surface? = null) {
        val currentId = surfaceIdentityMap[tag]
        val requestId = surface?.let { System.identityHashCode(it) }
        if (requestId != null && currentId != null && requestId != currentId) {
            Log.i(
                TAG,
                "unregisterVideoSurface(): skip stale request tag=$tag requestSurfaceId=$requestId currentSurfaceId=$currentId"
            )
            return
        }
        Log.i(TAG, "unregisterVideoSurface(): tag=$tag surfaceId=$requestId")
        surfaceMap.remove(tag)
        surfaceIdentityMap.remove(tag)
        if (currentSessionInfo == null) {
            decoderMap.remove(tag)?.release()
        }
    }

    fun adbPair(host: String, port: Int, pairingCode: String): Boolean {
        return ioCall { adbService.pair(host, port, pairingCode) }
    }

    fun adbDiscoverPairingService(
        timeoutMs: Long = 12_000,
        includeLanDevices: Boolean = true,
    ): Pair<String, Int>? {
        return ioCall { adbService.discoverPairingService(timeoutMs, includeLanDevices) }
    }

    fun adbDiscoverConnectService(
        timeoutMs: Long = 12_000,
        includeLanDevices: Boolean = true,
    ): Pair<String, Int>? {
        return ioCall { adbService.discoverConnectService(timeoutMs, includeLanDevices) }
    }

    fun adbConnect(host: String, port: Int): Boolean = ioCall { adbService.connect(host, port) }

    fun adbDisconnect(): Boolean {
        ioCall { adbService.disconnect() }
        return true
    }

    fun adbIsConnected(): Boolean = ioCall { adbService.isConnected() }

    fun adbShell(command: String): String = ioCall { adbService.shell(command) }

    fun setAdbKeyName(name: String) {
        adbService.keyName = name
    }

    fun scrcpyStart(request: ScrcpyStartRequest): ScrcpySessionInfo {
        return ioCall {
            Log.i(TAG, "scrcpyStart(): request codec=${request.videoCodec} audio=${request.audio}")
            val serverJar = if (request.customServerUri.isNullOrBlank()) {
                extractAssetToCache(request.serverAsset)
            } else {
                extractUriToCache(request.customServerUri.toUri())
            }

            val info = sessionManager.start(
                serverJar.toPath(),
                ScrcpySessionManager.ScrcpyStartOptions(
                    serverVersion = request.serverVersion,
                    serverRemotePath = request.serverRemotePath,
                    video = !request.noVideo,
                    audio = request.audio,
                    control = !request.noControl,
                    maxSize = request.maxSize,
                    maxFps = request.maxFps,
                    videoBitRate = request.videoBitRate,
                    videoCodec = request.videoCodec,
                    audioBitRate = request.audioBitRate,
                    audioCodec = request.audioCodec,
                    videoEncoder = request.videoEncoder,
                    videoCodecOptions = request.videoCodecOptions,
                    audioEncoder = request.audioEncoder,
                    audioCodecOptions = request.audioCodecOptions,
                    audioDup = request.audioDup,
                    audioSource = request.audioSource,
                    videoSource = request.videoSource,
                    cameraId = request.cameraId,
                    cameraFacing = request.cameraFacing,
                    cameraSize = request.cameraSize,
                    cameraAr = request.cameraAr,
                    cameraFps = request.cameraFps,
                    cameraHighSpeed = request.cameraHighSpeed,
                    newDisplay = request.newDisplay,
                    displayId = request.displayId,
                    crop = request.crop,
                ),
            )
            if (request.turnScreenOff) {
                if (request.noControl) {
                    Log.w(TAG, "scrcpyStart(): turnScreenOff ignored because control is disabled")
                } else {
                    runCatching { sessionManager.setDisplayPower(on = false) }
                        .onFailure { e -> Log.w(TAG, "scrcpyStart(): set display power failed", e) }
                }
            }
            val session = ScrcpySessionInfo(
                width = info.width,
                height = info.height,
                deviceName = info.deviceName,
                codec = info.codecName,
                controlEnabled = info.controlEnabled,
            )
            currentSessionInfo = session
            releaseAllDecoders()
            synchronized(bootstrapLock) {
                bootstrapPackets.clear()
                latestConfigPacket = null
            }
            if (!request.noVideo) {
                surfaceMap.forEach { (tag, surface) ->
                    Log.i(TAG, "scrcpyStart(): bind decoder to tag=$tag")
                    createOrReplaceDecoder(tag, surface, session)
                }
            }
            packetCount = 0
            if (!request.noVideo) {
                ensureVideoConsumerAttached()
            }

            // Audio player
            audioPlayer?.release()
            audioPlayer = null
            if (info.audioCodecId != 0 && !request.noAudioPlayback) {
                Log.i(
                    TAG,
                    "scrcpyStart(): create audio player codecId=0x${
                        info.audioCodecId.toUInt().toString(16)
                    }"
                )
                val player = ScrcpyAudioPlayer(info.audioCodecId)
                audioPlayer = player
                sessionManager.attachAudioConsumer { packet ->
                    player.feedPacket(packet.data, packet.ptsUs, packet.isConfig)
                }
            } else {
                Log.i(TAG, "scrcpyStart(): audio playback disabled for this session")
            }

            session
        }
    }

    fun scrcpyStop(): Boolean {
        ioCall {
            releaseAllDecoders()
            synchronized(bootstrapLock) {
                bootstrapPackets.clear()
                latestConfigPacket = null
            }
            currentSessionInfo = null
            sessionManager.clearVideoConsumer()
            sessionManager.clearAudioConsumer()
            sessionManager.stop()
            audioPlayer?.release()
            audioPlayer = null
        }
        return true
    }

    fun scrcpyListEncoders(
        customServerUri: String?,
        remotePath: String,
        serverVersion: String = "3.3.4"
    ): ScrcpyEncoderLists {
        return ioCall {
            val serverJar = if (customServerUri.isNullOrBlank()) {
                extractAssetToCache(DEFAULT_SERVER_ASSET)
            } else {
                extractUriToCache(customServerUri.toUri())
            }
            val result = sessionManager.listEncoders(
                serverJarPath = serverJar.toPath(),
                options = ScrcpySessionManager.ScrcpyStartOptions(
                    serverVersion = serverVersion,
                    serverRemotePath = remotePath,
                ),
            )
            ScrcpyEncoderLists(
                videoEncoders = result.videoEncoders,
                audioEncoders = result.audioEncoders,
                videoEncoderTypes = result.videoEncoderTypes,
                audioEncoderTypes = result.audioEncoderTypes,
                rawOutput = result.rawOutput,
            )
        }
    }

    fun scrcpyListCameraSizes(
        customServerUri: String?,
        remotePath: String,
        serverVersion: String = "3.3.4"
    ): ScrcpyCameraSizeLists {
        return ioCall {
            val serverJar = if (customServerUri.isNullOrBlank()) {
                extractAssetToCache(DEFAULT_SERVER_ASSET)
            } else {
                extractUriToCache(customServerUri.toUri())
            }
            val result = sessionManager.listCameraSizes(
                serverJarPath = serverJar.toPath(),
                options = ScrcpySessionManager.ScrcpyStartOptions(
                    serverVersion = serverVersion,
                    serverRemotePath = remotePath,
                ),
            )
            ScrcpyCameraSizeLists(
                sizes = result.sizes,
                rawOutput = result.rawOutput,
            )
        }
    }

    fun scrcpyIsStarted(): Boolean = ioCall { sessionManager.isStarted() }

    fun getLastScrcpyServerCommand(): String? = ioCall { sessionManager.getLastServerCommand() }

    fun scrcpyInjectKeycode(action: Int, keycode: Int, repeat: Int = 0, metaState: Int = 0) {
        ioExecute {
            runCatching { sessionManager.injectKeycode(action, keycode, repeat, metaState) }
        }
    }

    fun scrcpyInjectText(text: String) {
        ioExecute {
            runCatching { sessionManager.injectText(text) }
        }
    }

    fun scrcpyInjectTouch(
        action: Int,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float,
        pointerId: Long = 0L,
        actionButton: Int = 1,
        buttons: Int = 1,
    ) {
        ioExecute {
            runCatching {
                sessionManager.injectTouch(
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
        }
    }

    fun scrcpyInjectScroll(
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        hScroll: Float,
        vScroll: Float,
        buttons: Int = 0,
    ) {
        ioExecute {
            runCatching {
                sessionManager.injectScroll(
                    x,
                    y,
                    screenWidth,
                    screenHeight,
                    hScroll,
                    vScroll,
                    buttons
                )
            }
        }
    }

    fun addVideoSizeListener(listener: (Int, Int) -> Unit) {
        videoSizeListeners.add(listener)
    }

    fun removeVideoSizeListener(listener: (Int, Int) -> Unit) {
        videoSizeListeners.remove(listener)
    }

    fun addVideoFpsListener(listener: (Float) -> Unit) {
        videoFpsListeners.add(listener)
    }

    fun removeVideoFpsListener(listener: (Float) -> Unit) {
        videoFpsListeners.remove(listener)
    }

    fun scrcpyBackOrScreenOn(action: Int = 0) {
        ioExecute {
            runCatching { sessionManager.pressBackOrScreenOn(action) }
        }
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

    companion object {
        private const val TAG = "NativeCoreFacade"
        private const val DEFAULT_SERVER_ASSET = "bin/scrcpy-server-v3.3.4"
        private const val MAX_BOOTSTRAP_PACKETS = 90

        @Volatile
        private var instance: NativeCoreFacade? = null

        fun get(context: Context): NativeCoreFacade {
            return instance ?: synchronized(this) {
                instance ?: NativeCoreFacade(context.applicationContext).also { instance = it }
            }
        }

        fun defaultStartRequest(
            customServerUri: String?,
            maxSize: Int,
            videoBitRate: Int,
            remotePath: String,
            videoCodec: String = "h264",
            audio: Boolean = true,
            audioCodec: String = "opus",
            audioBitRate: Int = 128_000,
            maxFps: Float = 0f,
            noControl: Boolean = false,
            videoEncoder: String = "",
            videoCodecOptions: String = "",
            audioEncoder: String = "",
            audioCodecOptions: String = "",
            audioDup: Boolean = false,
            audioSource: String = "",
            videoSource: String = "display",
            cameraId: String = "",
            cameraFacing: String = "",
            cameraSize: String = "",
            cameraAr: String = "",
            cameraFps: Int = 0,
            cameraHighSpeed: Boolean = false,
            noAudioPlayback: Boolean = false,
            requireAudio: Boolean = false,
            turnScreenOff: Boolean = false,
            noVideo: Boolean = false,
            newDisplay: String = "",
            displayId: Int? = null,
            crop: String = "",
        ): ScrcpyStartRequest {
            return ScrcpyStartRequest(
                serverAsset = DEFAULT_SERVER_ASSET,
                customServerUri = customServerUri,
                serverVersion = "3.3.4",
                serverRemotePath = remotePath,
                maxSize = maxSize,
                videoBitRate = videoBitRate,
                videoCodec = videoCodec,
                audio = audio,
                audioCodec = audioCodec,
                audioBitRate = audioBitRate,
                maxFps = maxFps,
                noControl = noControl,
                videoEncoder = videoEncoder,
                videoCodecOptions = videoCodecOptions,
                audioEncoder = audioEncoder,
                audioCodecOptions = audioCodecOptions,
                audioDup = audioDup,
                audioSource = audioSource,
                videoSource = videoSource,
                cameraId = cameraId,
                cameraFacing = cameraFacing,
                cameraSize = cameraSize,
                cameraAr = cameraAr,
                cameraFps = cameraFps,
                cameraHighSpeed = cameraHighSpeed,
                noAudioPlayback = noAudioPlayback,
                requireAudio = requireAudio,
                turnScreenOff = turnScreenOff,
                noVideo = noVideo,
                newDisplay = newDisplay,
                displayId = displayId,
                crop = crop,
            )
        }

        fun nowLogPrefix(): String {
            val stamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            return "[$stamp]"
        }
    }

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

    private fun createOrReplaceDecoder(tag: String, surface: Surface, session: ScrcpySessionInfo) {
        decoderMap.remove(tag)?.release()
        val mime = when (session.codec.lowercase()) {
            "h264" -> "video/avc"
            "h265" -> "video/hevc"
            "av1" -> "video/av01"
            else -> "video/avc"
        }
        Log.i(
            TAG,
            "createOrReplaceDecoder(): tag=$tag codec=$mime size=${session.width}x${session.height}"
        )
        val decoder = AnnexBDecoder(
            width = session.width,
            height = session.height,
            outputSurface = surface,
            mimeType = mime,
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
        decoderMap[tag] = decoder
        replayBootstrapPackets(decoder)
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

    private fun cacheBootstrapPacket(packet: ScrcpySessionManager.VideoPacket) {
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

    private fun ensureVideoConsumerAttached() {
        sessionManager.attachVideoConsumer { packet ->
            cacheBootstrapPacket(packet)
            packetCount += 1
            if (packetCount == 1L || packetCount % 120L == 0L) {
                Log.i(
                    TAG,
                    "videoFeed(): packets=$packetCount key=${packet.isKeyFrame} cfg=${packet.isConfig} decoders=${decoderMap.size}"
                )
            }
            decoderMap.forEach { (tag, decoder) ->
                if (!surfaceIdentityMap.containsKey(tag)) {
                    return@forEach
                }
                runCatching {
                    decoder.feedAnnexB(
                        packet.data,
                        packet.ptsUs,
                        packet.isKeyFrame,
                        packet.isConfig
                    )
                }
            }
        }
    }

    private fun releaseAllDecoders() {
        decoderMap.values.forEach { decoder ->
            runCatching { decoder.release() }
        }
        decoderMap.clear()
    }

    private fun <T> ioCall(task: () -> T): T {
        return try {
            executor.submit<T> { task() }.get()
        } catch (e: ExecutionException) {
            val cause = e.cause
            if (cause is Exception) {
                throw cause
            }
            throw RuntimeException(cause ?: e)
        }
    }

    private fun ioExecute(task: () -> Unit) {
        executor.execute(task)
    }
}

data class ScrcpyStartRequest(
    val serverAsset: String,
    val customServerUri: String?,
    val serverVersion: String,
    val serverRemotePath: String,
    val maxSize: Int,
    val videoBitRate: Int,
    val videoCodec: String = "h264",
    val audio: Boolean = true,
    val audioCodec: String = "opus",
    val audioBitRate: Int = 128_000,
    val maxFps: Float = 0f,
    val noControl: Boolean = false,
    val videoEncoder: String = "",
    val videoCodecOptions: String = "",
    val audioEncoder: String = "",
    val audioCodecOptions: String = "",
    val audioDup: Boolean = false,
    val audioSource: String = "",
    val videoSource: String = "display",
    val cameraId: String = "",
    val cameraFacing: String = "",
    val cameraSize: String = "",
    val cameraAr: String = "",
    val cameraFps: Int = 0,
    val cameraHighSpeed: Boolean = false,
    val noAudioPlayback: Boolean = false,
    val requireAudio: Boolean = false,
    val turnScreenOff: Boolean = false,
    val noVideo: Boolean = false,
    val newDisplay: String = "",
    val displayId: Int? = null,
    val crop: String = "",
)

data class ScrcpyEncoderLists(
    val videoEncoders: List<String>,
    val audioEncoders: List<String>,
    val videoEncoderTypes: Map<String, String> = emptyMap(),
    val audioEncoderTypes: Map<String, String> = emptyMap(),
    val rawOutput: String = "",
)

data class ScrcpyCameraSizeLists(
    val sizes: List<String>,
    val rawOutput: String = "",
)

data class ScrcpySessionInfo(
    val width: Int,
    val height: Int,
    val deviceName: String,
    val codec: String,
    val controlEnabled: Boolean,
)
