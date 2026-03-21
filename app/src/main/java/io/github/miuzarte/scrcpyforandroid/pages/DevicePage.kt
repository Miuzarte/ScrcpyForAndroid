package io.github.miuzarte.scrcpyforandroid.pages

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.ScrcpySessionInfo
import io.github.miuzarte.scrcpyforandroid.constants.AppDefaults
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.haptics.rememberAppHaptics
import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcut
import io.github.miuzarte.scrcpyforandroid.scaffolds.AppPageLazyColumn
import io.github.miuzarte.scrcpyforandroid.services.DevicePageSettings
import io.github.miuzarte.scrcpyforandroid.services.fetchConnectedDeviceInfo
import io.github.miuzarte.scrcpyforandroid.services.loadDevicePageSettings
import io.github.miuzarte.scrcpyforandroid.services.loadQuickDevices
import io.github.miuzarte.scrcpyforandroid.services.parseQuickTarget
import io.github.miuzarte.scrcpyforandroid.services.replaceQuickDevicePort
import io.github.miuzarte.scrcpyforandroid.services.saveDevicePageSettings
import io.github.miuzarte.scrcpyforandroid.services.saveQuickDevices
import io.github.miuzarte.scrcpyforandroid.services.updateQuickDeviceNameIfEmpty
import io.github.miuzarte.scrcpyforandroid.services.upsertQuickDevice
import io.github.miuzarte.scrcpyforandroid.widgets.ConfigPanel
import io.github.miuzarte.scrcpyforandroid.widgets.DeviceEditorScreen
import io.github.miuzarte.scrcpyforandroid.widgets.DeviceTile
import io.github.miuzarte.scrcpyforandroid.widgets.LogsPanel
import io.github.miuzarte.scrcpyforandroid.widgets.PairingCard
import io.github.miuzarte.scrcpyforandroid.widgets.PreviewCard
import io.github.miuzarte.scrcpyforandroid.widgets.QuickConnectCard
import io.github.miuzarte.scrcpyforandroid.widgets.SectionSmallTitle
import io.github.miuzarte.scrcpyforandroid.widgets.SortDropPayload
import io.github.miuzarte.scrcpyforandroid.widgets.SortTransferDirection
import io.github.miuzarte.scrcpyforandroid.widgets.SortableCardItem
import io.github.miuzarte.scrcpyforandroid.widgets.SortableCardList
import io.github.miuzarte.scrcpyforandroid.widgets.StatusCard
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonAction
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonActions
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

private const val ADB_CONNECT_TIMEOUT_MS = 3_000L
private const val ADB_KEEPALIVE_INTERVAL_MS = 3_000L
private const val ADB_KEEPALIVE_TIMEOUT_MS = 1_500L
private const val ADB_AUTO_RECONNECT_DISCOVER_TIMEOUT_MS = 1_200L
private const val ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS = 1_500L
private const val ADB_TCP_PROBE_TIMEOUT_MS = 600
private const val DEVICE_SHORTCUT_SEPARATOR = "\u001F"
private const val LOG_TAG = "DevicePage"

private val DeviceShortcutStateListSaver =
    listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<DeviceShortcut>, String>(
        save = { list ->
            list.map { item ->
                listOf(
                    item.id,
                    item.name,
                    item.host,
                    item.port.toString(),
                    if (item.online) "1" else "0",
                ).joinToString(DEVICE_SHORTCUT_SEPARATOR)
            }
        },
        restore = { saved ->
            saved.mapNotNull { line ->
                val parts = line.split(DEVICE_SHORTCUT_SEPARATOR)
                if (parts.size != 5) return@mapNotNull null
                val port = parts[3].toIntOrNull() ?: return@mapNotNull null
                DeviceShortcut(
                    id = parts[0],
                    name = parts[1],
                    host = parts[2],
                    port = port,
                    online = parts[4] == "1",
                )
            }.toMutableStateList()
        },
    )

private val StringStateListSaver =
    listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<String>, String>(
        save = { it.toList() },
        restore = { it.toMutableStateList() },
    )

@Composable
fun DeviceTabScreen(
    contentPadding: PaddingValues,
    nativeCore: NativeCoreFacade,
    snack: SnackbarHostState,
    scrollBehavior: ScrollBehavior,
    virtualButtonsOutside: List<String>,
    virtualButtonsInMore: List<String>,
    previewCardHeightDp: Int,
    customServerUri: String?,
    serverRemotePath: String,
    onServerRemotePathChange: (String) -> Unit,
    videoCodec: String,
    onVideoCodecChange: (String) -> Unit,
    audioEnabled: Boolean,
    onAudioEnabledChange: (Boolean) -> Unit,
    audioCodec: String,
    onAudioCodecChange: (String) -> Unit,
    noControl: Boolean,
    onNoControlChange: (Boolean) -> Unit,
    videoEncoder: String,
    onVideoEncoderChange: (String) -> Unit,
    videoCodecOptions: String,
    onVideoCodecOptionsChange: (String) -> Unit,
    audioEncoder: String,
    onAudioEncoderChange: (String) -> Unit,
    audioCodecOptions: String,
    onAudioCodecOptionsChange: (String) -> Unit,
    audioDup: Boolean,
    onAudioDupChange: (Boolean) -> Unit,
    audioSourcePreset: String,
    onAudioSourcePresetChange: (String) -> Unit,
    audioSourceCustom: String,
    onAudioSourceCustomChange: (String) -> Unit,
    videoSourcePreset: String,
    onVideoSourcePresetChange: (String) -> Unit,
    cameraIdInput: String,
    onCameraIdInputChange: (String) -> Unit,
    cameraFacingPreset: String,
    onCameraFacingPresetChange: (String) -> Unit,
    cameraSizePreset: String,
    onCameraSizePresetChange: (String) -> Unit,
    cameraSizeCustom: String,
    onCameraSizeCustomChange: (String) -> Unit,
    cameraArInput: String,
    onCameraArInputChange: (String) -> Unit,
    cameraFpsInput: String,
    onCameraFpsInputChange: (String) -> Unit,
    cameraHighSpeed: Boolean,
    onCameraHighSpeedChange: (Boolean) -> Unit,
    noAudioPlayback: Boolean,
    onNoAudioPlaybackChange: (Boolean) -> Unit,
    noVideo: Boolean,
    requireAudio: Boolean,
    onRequireAudioChange: (Boolean) -> Unit,
    turnScreenOff: Boolean,
    onTurnScreenOffChange: (Boolean) -> Unit,
    maxSizeInput: String,
    onMaxSizeInputChange: (String) -> Unit,
    maxFpsInput: String,
    onMaxFpsInputChange: (String) -> Unit,
    newDisplayWidth: String,
    onNewDisplayWidthChange: (String) -> Unit,
    newDisplayHeight: String,
    onNewDisplayHeightChange: (String) -> Unit,
    newDisplayDpi: String,
    onNewDisplayDpiChange: (String) -> Unit,
    displayIdInput: String,
    onDisplayIdInputChange: (String) -> Unit,
    cropWidth: String,
    onCropWidthChange: (String) -> Unit,
    cropHeight: String,
    onCropHeightChange: (String) -> Unit,
    cropX: String,
    onCropXChange: (String) -> Unit,
    cropY: String,
    onCropYChange: (String) -> Unit,
    videoEncoderOptions: List<String>,
    onVideoEncoderOptionsChange: (List<String>) -> Unit,
    onVideoEncoderTypeMapChange: (Map<String, String>) -> Unit,
    audioEncoderOptions: List<String>,
    onAudioEncoderOptionsChange: (List<String>) -> Unit,
    onAudioEncoderTypeMapChange: (Map<String, String>) -> Unit,
    cameraSizeOptions: List<String>,
    onCameraSizeOptionsChange: (List<String>) -> Unit,
    onSessionStartedChange: (Boolean) -> Unit,
    onRefreshEncodersActionChange: ((() -> Unit)?) -> Unit,
    onRefreshCameraSizesActionChange: ((() -> Unit)?) -> Unit,
    onClearLogsActionChange: ((() -> Unit)?) -> Unit,
    onCanClearLogsChange: (Boolean) -> Unit,
    onOpenReorderDevicesActionChange: ((() -> Unit)?) -> Unit,
    onOpenAdvancedPage: () -> Unit,
    onOpenFullscreenPage: (ScrcpySessionInfo) -> Unit,
    adbPairingAutoDiscoverOnDialogOpen: Boolean,
    adbAutoReconnectPairedDevice: Boolean,
    adbMdnsLanDiscoveryEnabled: Boolean,
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val virtualButtonLayout = remember(virtualButtonsOutside, virtualButtonsInMore) {
        VirtualButtonActions.resolveLayout(virtualButtonsOutside, virtualButtonsInMore)
    }
    val initialSettings = remember(context) { loadDevicePageSettings(context) }
    val scope = rememberCoroutineScope()
    val adbWorkerDispatcher = remember {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "adb-connect-worker").apply { isDaemon = true }
        }.asCoroutineDispatcher()
    }

    DisposableEffect(adbWorkerDispatcher) {
        onDispose {
            adbWorkerDispatcher.close()
        }
    }

    var busy by rememberSaveable { mutableStateOf(false) }
    var statusLine by rememberSaveable { mutableStateOf("未连接") }
    var adbConnected by rememberSaveable { mutableStateOf(false) }
    var currentTargetHost by rememberSaveable { mutableStateOf("") }
    var currentTargetPort by rememberSaveable { mutableIntStateOf(AppDefaults.ADB_PORT) }
    var connectedDeviceLabel by rememberSaveable { mutableStateOf("未连接") }
    var sessionInfoWidth by rememberSaveable { mutableIntStateOf(0) }
    var sessionInfoHeight by rememberSaveable { mutableIntStateOf(0) }
    var sessionInfoDeviceName by rememberSaveable { mutableStateOf("") }
    var sessionInfoCodec by rememberSaveable { mutableStateOf("") }
    var sessionInfoControlEnabled by rememberSaveable { mutableStateOf(false) }
    var sessionInfo by remember {
        mutableStateOf<ScrcpySessionInfo?>(null)
    }
    LaunchedEffect(
        sessionInfoWidth,
        sessionInfoHeight,
        sessionInfoDeviceName,
        sessionInfoCodec,
        sessionInfoControlEnabled
    ) {
        sessionInfo = if (sessionInfoDeviceName.isNotBlank()) {
            ScrcpySessionInfo(
                width = sessionInfoWidth,
                height = sessionInfoHeight,
                deviceName = sessionInfoDeviceName,
                codec = sessionInfoCodec,
                controlEnabled = sessionInfoControlEnabled,
            )
        } else {
            null
        }
    }
    var previewControlsVisible by rememberSaveable { mutableStateOf(false) }
    var editingDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeDeviceActionId by rememberSaveable { mutableStateOf<String?>(null) }
    var showReorderSheet by rememberSaveable { mutableStateOf(false) }
    var adbConnecting by rememberSaveable { mutableStateOf(false) }

    var connectHost by rememberSaveable { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf(AppDefaults.ADB_PORT.toString()) }
    var quickConnectInput by rememberSaveable { mutableStateOf(initialSettings.quickConnectInput) }
    var audioForwardingSupported by rememberSaveable { mutableStateOf(true) }
    var cameraMirroringSupported by rememberSaveable { mutableStateOf(true) }

    var bitRateMbps by rememberSaveable { mutableFloatStateOf(initialSettings.videoBitRateMbps) }
    var bitRateInput by rememberSaveable { mutableStateOf(initialSettings.videoBitRateInput) }
    var audioBitRateKbps by rememberSaveable { mutableIntStateOf(initialSettings.audioBitRateKbps) }
    val currentTarget = if (currentTargetHost.isNotBlank()) ConnectionTarget(
        currentTargetHost,
        currentTargetPort
    ) else null

    val eventLog = rememberSaveable(saver = StringStateListSaver) { mutableStateListOf() }
    val quickDevices =
        rememberSaveable(saver = DeviceShortcutStateListSaver) { mutableStateListOf() }
    val sessionReconnectBlacklistHosts = remember { mutableSetOf<String>() }

    LaunchedEffect(eventLog.size) {
        onCanClearLogsChange(eventLog.isNotEmpty())
    }

    fun logEvent(message: String, level: Int = Log.INFO, error: Throwable? = null) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "[$timestamp] $message"
        eventLog.add(0, line)
        if (eventLog.size > AppDefaults.EVENT_LOG_LINES) {
            eventLog.removeRange(AppDefaults.EVENT_LOG_LINES, eventLog.size)
        }
        when (level) {
            Log.ERROR -> if (error != null) Log.e(LOG_TAG, message, error) else Log.e(
                LOG_TAG,
                message
            )

            Log.WARN -> if (error != null) Log.w(LOG_TAG, message, error) else Log.w(
                LOG_TAG,
                message
            )

            Log.DEBUG -> if (error != null) Log.d(LOG_TAG, message, error) else Log.d(
                LOG_TAG,
                message
            )

            else -> if (error != null) Log.i(LOG_TAG, message, error) else Log.i(LOG_TAG, message)
        }
    }

    suspend fun disconnectAdbConnection(
        clearQuickOnlineForTarget: ConnectionTarget? = currentTarget,
        logMessage: String? = null,
        showSnackMessage: String? = null,
    ) {
        withContext(adbWorkerDispatcher) {
            runCatching { nativeCore.scrcpyStop() }
            runCatching { nativeCore.adbDisconnect() }
        }
        adbConnected = false
        currentTargetHost = ""
        currentTargetPort = AppDefaults.ADB_PORT
        audioForwardingSupported = true
        cameraMirroringSupported = true
        sessionInfo = null
        statusLine = "未连接"
        connectedDeviceLabel = "未连接"
        clearQuickOnlineForTarget?.let { target ->
            if (target.host.isNotBlank()) {
                upsertQuickDevice(context, quickDevices, target.host, target.port, false)
            }
        }
        logMessage?.let { logEvent(it) }
        if (!showSnackMessage.isNullOrBlank()) {
            scope.launch {
                snack.showSnackbar(showSnackMessage)
            }
        }
    }

    suspend fun disconnectCurrentTargetBeforeConnecting(newHost: String, newPort: Int) {
        val current = currentTarget
        if (!adbConnected || current == null) return
        if (current.host == newHost && current.port == newPort) return

        sessionReconnectBlacklistHosts += current.host
        logEvent("切换连接目标，先断开当前设备: ${current.host}:${current.port}")
        disconnectAdbConnection(clearQuickOnlineForTarget = current)
    }

    fun applyConnectedDeviceCapabilities(sdkInt: Int, release: String) {
        val audioSupported = sdkInt !in 0..<30
        audioForwardingSupported = audioSupported
        if (!audioSupported && audioEnabled) {
            onAudioEnabledChange(false)
            logEvent(
                "设备 Android ${release.ifBlank { "?" }} (SDK $sdkInt) 不支持音频转发，已自动关闭",
                Log.WARN
            )
        }
        val cameraSupported = sdkInt !in 0..<31
        cameraMirroringSupported = cameraSupported
        if (!cameraSupported && videoSourcePreset == "camera") {
            onVideoSourcePresetChange("display")
            logEvent(
                "设备 Android ${release.ifBlank { "?" }} (SDK $sdkInt) 不支持 camera mirroring，已切换为 display",
                Log.WARN
            )
        }
    }

    suspend fun connectWithTimeout(host: String, port: Int): Boolean {
        return withContext(adbWorkerDispatcher) {
            withTimeout(ADB_CONNECT_TIMEOUT_MS) {
                nativeCore.adbConnect(host, port)
            }
        }
    }

    suspend fun keepAliveCheck(host: String, port: Int): Boolean {
        return withContext(adbWorkerDispatcher) {
            withTimeout(ADB_KEEPALIVE_TIMEOUT_MS) {
                val connected = nativeCore.adbIsConnected()
                if (!connected) {
                    return@withTimeout false
                }
                runCatching {
                    nativeCore.adbShell("echo -n 1")
                    true
                }.getOrElse { false }
            }
        }
    }

    suspend fun probeTcpReachable(host: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), ADB_TCP_PROBE_TIMEOUT_MS)
                    true
                }
            }.getOrDefault(false)
        }
    }

    fun runBusy(label: String, onFinished: (() -> Unit)? = null, block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                block()
            } catch (_: TimeoutCancellationException) {
                logEvent("$label 超时", Log.WARN)
            } catch (e: IllegalArgumentException) {
                val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                logEvent("$label 参数错误: $detail", Log.WARN, e)
                scope.launch {
                    snack.showSnackbar("$label 参数错误: $detail")
                }
            } catch (e: Exception) {
                val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                logEvent("$label 失败: $detail", Log.ERROR, e)
            } finally {
                busy = false
                onFinished?.invoke()
            }
        }
    }

    fun runAdbConnect(label: String, onFinished: (() -> Unit)? = null, block: suspend () -> Unit) {
        if (adbConnecting) return
        scope.launch {
            adbConnecting = true
            try {
                block()
            } catch (_: TimeoutCancellationException) {
                logEvent("$label 超时", Log.WARN)
            } catch (e: IllegalArgumentException) {
                val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                logEvent("$label 参数错误: $detail", Log.WARN, e)
                scope.launch {
                    snack.showSnackbar("$label 参数错误: $detail")
                }
            } catch (e: Exception) {
                val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                logEvent("$label 失败: $detail", Log.ERROR, e)
            } finally {
                adbConnecting = false
                onFinished?.invoke()
            }
        }
    }

    suspend fun runAutoAdbConnect(host: String, port: Int): Boolean {
        return runCatching {
            connectWithTimeout(host, port)
        }.getOrElse { error ->
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            logEvent("自动重连失败: $host:$port ($detail)", Log.WARN)
            false
        }
    }

    fun refreshEncoderLists() {
        if (!adbConnected) return
        val remotePath = serverRemotePath.trim().ifBlank { AppDefaults.SERVER_REMOTE_PATH }
        runCatching {
            nativeCore.scrcpyListEncoders(
                customServerUri = customServerUri,
                remotePath = remotePath,
            )
        }.onSuccess { lists ->
            onVideoEncoderOptionsChange(lists.videoEncoders)
            onAudioEncoderOptionsChange(lists.audioEncoders)
            onVideoEncoderTypeMapChange(lists.videoEncoderTypes)
            onAudioEncoderTypeMapChange(lists.audioEncoderTypes)
            if (videoEncoder.isNotBlank() && videoEncoder !in videoEncoderOptions) {
                onVideoEncoderChange("")
            }
            if (audioEncoder.isNotBlank() && audioEncoder !in audioEncoderOptions) {
                onAudioEncoderChange("")
            }
            logEvent("编码器列表已刷新: video=${lists.videoEncoders.size} audio=${lists.audioEncoders.size}")
            if (lists.videoEncoders.isEmpty() && lists.audioEncoders.isEmpty()) {
                logEvent("提示: 编码器为空，请检查 server 路径/版本与设备系统日志", Log.WARN)
                val preview = lists.rawOutput.lineSequence().take(20).joinToString(" | ")
                if (preview.isNotBlank()) {
                    logEvent("编码器原始输出: $preview", Log.DEBUG)
                }
            }
        }.onFailure { e ->
            onVideoEncoderOptionsChange(emptyList())
            onAudioEncoderOptionsChange(emptyList())
            onVideoEncoderTypeMapChange(emptyMap())
            onAudioEncoderTypeMapChange(emptyMap())
            logEvent("读取编码器列表失败: ${e.message ?: e.javaClass.simpleName}", Log.ERROR, e)
        }
    }

    fun refreshCameraSizeLists() {
        if (!adbConnected) return
        val remotePath = serverRemotePath.trim().ifBlank { AppDefaults.SERVER_REMOTE_PATH }
        runCatching {
            nativeCore.scrcpyListCameraSizes(
                customServerUri = customServerUri,
                remotePath = remotePath,
            )
        }.onSuccess { lists ->
            onCameraSizeOptionsChange(lists.sizes)
            if (cameraSizePreset.isNotBlank() && cameraSizePreset != "custom" && cameraSizePreset !in lists.sizes) {
                onCameraSizePresetChange("")
            }
            logEvent("camera sizes 已刷新: count=${lists.sizes.size}")
            if (lists.sizes.isEmpty()) {
                val preview = lists.rawOutput.lineSequence().take(20).joinToString(" | ")
                if (preview.isNotBlank()) {
                    logEvent("camera sizes 原始输出: $preview", Log.DEBUG)
                }
            }
        }.onFailure { e ->
            onCameraSizeOptionsChange(emptyList())
            logEvent("读取 camera sizes 失败: ${e.message ?: e.javaClass.simpleName}", Log.ERROR, e)
        }
    }

    fun handleAdbConnected(host: String, port: Int) {
        currentTargetHost = host
        currentTargetPort = port

        val info = fetchConnectedDeviceInfo(nativeCore, host, port)
        val fullLabel = if (info.serial.isNotBlank()) {
            "${info.model} (${info.serial})"
        } else {
            info.model
        }

        connectedDeviceLabel = info.model
        applyConnectedDeviceCapabilities(info.sdkInt, info.androidRelease)
        updateQuickDeviceNameIfEmpty(context, quickDevices, host, port, fullLabel)
        connectHost = host
        connectPort = port.toString()
        statusLine = "$host:$port"

        logEvent("ADB 已连接: model=${info.model}, serial=${info.serial.ifBlank { "unknown" }}, manufacturer=${info.manufacturer.ifBlank { "unknown" }}, brand=${info.brand.ifBlank { "unknown" }}, device=${info.device.ifBlank { "unknown" }}, android=${info.androidRelease.ifBlank { "unknown" }}, sdk=${info.sdkInt}")
        scope.launch {
            snack.showSnackbar("ADB 已连接")
        }
        refreshEncoderLists()
        refreshCameraSizeLists()
    }

    LaunchedEffect(bitRateInput) {
        val parsed = bitRateInput.toFloatOrNull() ?: return@LaunchedEffect
        bitRateMbps = parsed.coerceAtLeast(0.1f)
    }

    LaunchedEffect(
        quickConnectInput,
        audioBitRateKbps,
        bitRateMbps,
        bitRateInput,
        turnScreenOff,
        noControl,
        noVideo,
        videoSourcePreset,
        displayIdInput,
        cameraIdInput,
        cameraFacingPreset,
        cameraSizePreset,
        cameraSizeCustom,
        cameraArInput,
        cameraFpsInput,
        cameraHighSpeed,
        audioSourcePreset,
        audioSourceCustom,
        audioDup,
        noAudioPlayback,
        requireAudio,
        maxSizeInput,
        maxFpsInput,
        videoEncoder,
        videoCodecOptions,
        audioEncoder,
        audioCodecOptions,
        newDisplayWidth,
        newDisplayHeight,
        newDisplayDpi,
        cropWidth,
        cropHeight,
        cropX,
        cropY,
    ) {
        saveDevicePageSettings(
            context,
            DevicePageSettings(
                quickConnectInput = quickConnectInput,
                audioBitRateKbps = audioBitRateKbps,
                audioBitRateInput = audioBitRateKbps.toString(),
                videoBitRateMbps = bitRateMbps,
                videoBitRateInput = bitRateInput,
                turnScreenOff = turnScreenOff,
                noControl = noControl,
                noVideo = noVideo,
                videoSourcePreset = videoSourcePreset,
                displayIdInput = displayIdInput,
                cameraIdInput = cameraIdInput,
                cameraFacingPreset = cameraFacingPreset,
                cameraSizePreset = cameraSizePreset,
                cameraSizeCustom = cameraSizeCustom,
                cameraAr = cameraArInput,
                cameraFps = cameraFpsInput,
                cameraHighSpeed = cameraHighSpeed,
                audioSourcePreset = audioSourcePreset,
                audioSourceCustom = audioSourceCustom,
                audioDup = audioDup,
                noAudioPlayback = noAudioPlayback,
                requireAudio = requireAudio,
                maxSizeInput = maxSizeInput,
                maxFpsInput = maxFpsInput,
                videoEncoder = videoEncoder,
                videoCodecOptions = videoCodecOptions,
                audioEncoder = audioEncoder,
                audioCodecOptions = audioCodecOptions,
                newDisplayWidth = newDisplayWidth,
                newDisplayHeight = newDisplayHeight,
                newDisplayDpi = newDisplayDpi,
                cropWidth = cropWidth,
                cropHeight = cropHeight,
                cropX = cropX,
                cropY = cropY,
            ),
        )
    }

    LaunchedEffect(Unit) {
        if (quickDevices.isEmpty()) {
            quickDevices.clear()
            quickDevices.addAll(loadQuickDevices(context))
        }
    }

    LaunchedEffect(adbConnected, currentTargetHost, currentTargetPort, quickDevices.size) {
        val activeId = if (adbConnected && currentTargetHost.isNotBlank()) {
            "$currentTargetHost:$currentTargetPort"
        } else {
            null
        }
        for (index in quickDevices.indices) {
            val item = quickDevices[index]
            val shouldOnline = activeId != null && item.id == activeId
            if (item.online != shouldOnline) {
                quickDevices[index] = item.copy(online = shouldOnline)
            }
        }
    }

    LaunchedEffect(adbConnected, currentTargetHost, currentTargetPort) {
        if (!adbConnected || currentTargetHost.isBlank()) return@LaunchedEffect

        val host = currentTargetHost
        val port = currentTargetPort
        while (adbConnected && currentTargetHost == host && currentTargetPort == port) {
            delay(ADB_KEEPALIVE_INTERVAL_MS)
            val alive = runCatching { keepAliveCheck(host, port) }.getOrElse { false }
            if (alive) continue

            logEvent("ADB 长连接中断，尝试自动重连: $host:$port", Log.WARN)
            val reconnected = runCatching { connectWithTimeout(host, port) }.getOrElse { false }
            adbConnected = reconnected
            if (reconnected) {
                statusLine = "$host:$port"
                logEvent("ADB 自动重连成功: $host:$port")
                scope.launch {
                    snack.showSnackbar("ADB 自动重连成功")
                }
            } else {
                disconnectAdbConnection()
                statusLine = "ADB 连接断开"
                logEvent("ADB 自动重连失败: $host:$port", Log.ERROR)
                scope.launch {
                    snack.showSnackbar("ADB 自动重连失败")
                }
                break
            }
        }
    }

    LaunchedEffect(adbConnected, adbAutoReconnectPairedDevice, adbMdnsLanDiscoveryEnabled) {
        if (adbConnected || !adbAutoReconnectPairedDevice) return@LaunchedEffect

        val quickConnectTriedOnce = mutableSetOf<String>()
        while (!adbConnected && adbAutoReconnectPairedDevice) {
            if (busy || adbConnecting || sessionInfo != null) {
                delay(ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS)
                continue
            }

            val quickCandidates = quickDevices.toList()
            if (quickCandidates.isNotEmpty()) {
                for (target in quickCandidates) {
                    if (adbConnected || adbConnecting) break
                    if (sessionReconnectBlacklistHosts.contains(target.host)) continue
                    val targetKey = "${target.host}:${target.port}"
                    if (quickConnectTriedOnce.contains(targetKey)) continue

                    val portReachable = probeTcpReachable(target.host, target.port)
                    if (!portReachable) continue

                    quickConnectTriedOnce += targetKey
                    val ok = runAutoAdbConnect(target.host, target.port)
                    adbConnected = ok
                    upsertQuickDevice(
                        context,
                        quickDevices,
                        target.host,
                        target.port,
                        ok
                    )
                    if (ok) {
                        handleAdbConnected(target.host, target.port)
                        logEvent("ADB 快速探测连接成功: ${target.host}:${target.port}")
                        break
                    }
                }
                if (adbConnected) break
            }

            val discovered = withContext(Dispatchers.IO) {
                nativeCore.adbDiscoverConnectService(
                    timeoutMs = ADB_AUTO_RECONNECT_DISCOVER_TIMEOUT_MS,
                    includeLanDevices = adbMdnsLanDiscoveryEnabled,
                )
            }

            if (discovered == null) {
                delay(ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS)
                continue
            }

            val (discoveredHost, discoveredPort) = discovered
            if (sessionReconnectBlacklistHosts.contains(discoveredHost)) {
                delay(ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS)
                continue
            }
            val knownDevice = quickDevices.firstOrNull { it.host == discoveredHost }
            if (knownDevice == null) {
                delay(ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS)
                continue
            }
            val portToReplace = quickDevices.firstOrNull {
                it.host == discoveredHost &&
                        it.port != AppDefaults.ADB_PORT &&
                        it.port != discoveredPort
            }?.port
            if (portToReplace != null) {
                replaceQuickDevicePort(
                    context = context,
                    quickDevices = quickDevices,
                    host = discoveredHost,
                    oldPort = portToReplace,
                    newPort = discoveredPort,
                    online = false,
                )
                logEvent(
                    "mDNS 发现新端口，已更新快速设备: $discoveredHost:$portToReplace -> $discoveredHost:$discoveredPort"
                )
            }

            if (adbConnected || adbConnecting) {
                delay(ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS)
                continue
            }

            val ok = runAutoAdbConnect(discoveredHost, discoveredPort)
            adbConnected = ok
            upsertQuickDevice(
                context,
                quickDevices,
                discoveredHost,
                discoveredPort,
                ok
            )
            if (ok) {
                handleAdbConnected(discoveredHost, discoveredPort)
                logEvent("ADB 自动重连成功: $discoveredHost:$discoveredPort")
            } else {
                logEvent("ADB 自动重连失败: $discoveredHost:$discoveredPort", Log.WARN)
            }

            delay(ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS)
        }
    }

    DisposableEffect(nativeCore) {
        val listener: (Int, Int) -> Unit = { width, height ->
            sessionInfo = sessionInfo?.copy(width = width, height = height)
        }
        nativeCore.addVideoSizeListener(listener)
        onDispose {
            nativeCore.removeVideoSizeListener(listener)
        }
    }

    LaunchedEffect(sessionInfo) {
        if (sessionInfo != null) {
            sessionInfoWidth = sessionInfo?.width ?: 0
            sessionInfoHeight = sessionInfo?.height ?: 0
            sessionInfoDeviceName = sessionInfo?.deviceName.orEmpty()
            sessionInfoCodec = sessionInfo?.codec.orEmpty()
            sessionInfoControlEnabled = sessionInfo?.controlEnabled == true
        } else {
            sessionInfoWidth = 0
            sessionInfoHeight = 0
            sessionInfoDeviceName = ""
            sessionInfoCodec = ""
            sessionInfoControlEnabled = false
        }
        onSessionStartedChange(sessionInfo != null)
    }

    DisposableEffect(Unit) {
        onRefreshEncodersActionChange {
            runBusy("刷新编码器") { refreshEncoderLists() }
        }
        onRefreshCameraSizesActionChange {
            runBusy("刷新 Camera Sizes") { refreshCameraSizeLists() }
        }
        onClearLogsActionChange {
            eventLog.clear()
        }
        onOpenReorderDevicesActionChange {
            showReorderSheet = true
        }
        onDispose {
            onRefreshEncodersActionChange(null)
            onRefreshCameraSizesActionChange(null)
            onClearLogsActionChange(null)
            onCanClearLogsChange(false)
            onOpenReorderDevicesActionChange(null)
        }
    }

    SuperBottomSheet(
        show = showReorderSheet,
        title = "调整设备排序",
        onDismissRequest = { showReorderSheet = false },
    ) {
        SortableCardList(
            title = "设备列表",
            items = quickDevices.map { device ->
                SortableCardItem(
                    id = device.id,
                    title = device.name.ifBlank { device.host },
                    subtitle = "${device.host}:${device.port}",
                )
            },
            modifier = Modifier.padding(horizontal = UiSpacing.CardContent),
            transferDirection = SortTransferDirection.NONE,
            onLongPressHaptic = { haptics.press() },
            onDrop = { payload: SortDropPayload ->
                val fromIndex = quickDevices.indexOfFirst { it.id == payload.itemId }
                if (fromIndex < 0) return@SortableCardList
                val steps = (payload.deltaY / 54f).roundToInt()
                if (steps == 0) return@SortableCardList
                val toIndex = (fromIndex + steps).coerceIn(0, quickDevices.lastIndex)
                if (toIndex == fromIndex) return@SortableCardList
                val moved = quickDevices.removeAt(fromIndex)
                quickDevices.add(toIndex, moved)
                saveQuickDevices(context, quickDevices)
            },
        )
        Spacer(Modifier.height(UiSpacing.Large))
    }

    fun sendVirtualButtonAction(action: VirtualButtonAction) {
        val keycode = action.keycode ?: return
        runBusy("发送 ${action.title}") {
            nativeCore.scrcpyInjectKeycode(0, keycode)
            nativeCore.scrcpyInjectKeycode(1, keycode)
        }
    }

    if (editingDeviceId != null) {
        val device = quickDevices.firstOrNull { it.id == editingDeviceId }
        if (device != null) {
            DeviceEditorScreen(
                contentPadding = contentPadding,
                device = device,
                onSave = { updated ->
                    val idx = quickDevices.indexOfFirst { it.id == device.id }
                    if (idx >= 0) {
                        quickDevices[idx] = updated.copy(online = quickDevices[idx].online)
                        saveQuickDevices(context, quickDevices)
                    }
                    editingDeviceId = null
                },
                onDelete = {
                    quickDevices.removeAll { it.id == device.id }
                    saveQuickDevices(context, quickDevices)
                    editingDeviceId = null
                },
                onBack = { editingDeviceId = null },
            )
            return
        }
        editingDeviceId = null
    }

    // 设备
    AppPageLazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
    ) {
        item {
            StatusCard(
                statusLine = statusLine,
                adbConnected = adbConnected,
                streaming = sessionInfo != null,
                sessionInfo = sessionInfo,
                busyLabel = null,
                connectedDeviceLabel = connectedDeviceLabel,
            )
        }

        itemsIndexed(quickDevices, key = { _, device -> device.id }) { _, device ->
            val host = device.host
            val port = device.port
            val isConnectedTarget =
                adbConnected && currentTarget?.host == host && currentTarget.port == port

            DeviceTile(
                device = device,
                actionText = if (isConnectedTarget) "断开" else "连接",
                actionEnabled = !busy && !adbConnecting,
                actionInProgress = adbConnecting && activeDeviceActionId == device.id,
                onLongPress = { editingDeviceId = device.id },
                onContentClick = {
                    scope.launch {
                        snack.showSnackbar("长按可编辑设备")
                    }
                },
                onAction = {
                    haptics.press()
                    if (isConnectedTarget) {
                        activeDeviceActionId = device.id
                        runAdbConnect("断开 ADB", onFinished = { activeDeviceActionId = null }) {
                            sessionReconnectBlacklistHosts += host
                            disconnectAdbConnection(
                                clearQuickOnlineForTarget = ConnectionTarget(host, port),
                                logMessage = "ADB 已断开: ${device.name}",
                                showSnackMessage = "ADB 已断开",
                            )
                        }
                    } else {
                        activeDeviceActionId = device.id
                        runAdbConnect("连接 ADB", onFinished = { activeDeviceActionId = null }) {
                            disconnectCurrentTargetBeforeConnecting(host, port)
                            val ok = connectWithTimeout(host, port)
                            adbConnected = ok
                            upsertQuickDevice(context, quickDevices, host, port, ok)
                            if (ok) {
                                handleAdbConnected(host, port)
                            } else {
                                statusLine = "ADB 连接失败"
                                logEvent("ADB 连接失败: $host:$port", Log.ERROR)
                                scope.launch {
                                    snack.showSnackbar("ADB 连接失败")
                                }
                            }
                        }
                    }
                },
            )
        }

        if (!adbConnected) item {
            // "快速连接"
            QuickConnectCard(
                input = quickConnectInput,
                onInputChange = { quickConnectInput = it },
                enabled = !adbConnecting,
                onAddDevice = {
                    val target = parseQuickTarget(quickConnectInput) ?: return@QuickConnectCard
                    upsertQuickDevice(
                        context,
                        quickDevices,
                        target.host,
                        target.port,
                        online = false
                    )
                    scope.launch {
                        snack.showSnackbar("已添加设备: ${target.host}:${target.port}")
                    }
                },
                onConnect = {
                    val target = parseQuickTarget(quickConnectInput) ?: return@QuickConnectCard
                    runAdbConnect("连接 ADB") {
                        disconnectCurrentTargetBeforeConnecting(target.host, target.port)
                        val ok = connectWithTimeout(target.host, target.port)
                        adbConnected = ok
                        upsertQuickDevice(context, quickDevices, target.host, target.port, ok)
                        if (ok) {
                            handleAdbConnected(target.host, target.port)
                        } else {
                            statusLine = "ADB 连接失败"
                            logEvent("ADB 连接失败: ${target.host}:${target.port}", Log.ERROR)
                            scope.launch {
                                snack.showSnackbar("ADB 连接失败")
                            }
                        }
                    }
                },
            )
            SectionSmallTitle(text = "无线配对")
            // "使用配对码配对设备"
            PairingCard(
                busy = busy,
                autoDiscoverOnDialogOpen = adbPairingAutoDiscoverOnDialogOpen,
                onDiscoverTarget = {
                    nativeCore.adbDiscoverPairingService(
                        includeLanDevices = adbMdnsLanDiscoveryEnabled,
                    )
                },
                onPair = { host, port, code ->
                    runBusy("执行配对") {
                        val resolvedHost = host.trim()
                        val resolvedPort = port.toIntOrNull() ?: return@runBusy
                        val resolvedCode = code.trim()
                        val ok = nativeCore.adbPair(
                            resolvedHost,
                            resolvedPort,
                            resolvedCode,
                        )
                        logEvent(
                            if (ok) "配对成功" else "配对失败",
                            if (ok) Log.INFO else Log.ERROR
                        )
                        scope.launch {
                            snack.showSnackbar(if (ok) "配对成功" else "配对失败")
                        }
                    }
                },
            )
        }

        if (adbConnected) {
            item {
                ConfigPanel(
                    busy = busy,
                    bitRateMbps = bitRateMbps,
                    onBitRateSliderChange = {
                        bitRateMbps = it
                        @SuppressLint("DefaultLocale")
                        bitRateInput = String.format("%.1f", it)
                    },
                    onBitRateInputChange = { bitRateInput = it },
                    audioBitRateKbps = audioBitRateKbps,
                    onAudioBitRateChange = { audioBitRateKbps = it },
                    videoCodec = videoCodec,
                    onVideoCodecChange = onVideoCodecChange,
                    audioEnabled = audioEnabled,
                    onAudioEnabledChange = onAudioEnabledChange,
                    audioForwardingSupported = audioForwardingSupported,
                    audioCodec = audioCodec,
                    onAudioCodecChange = onAudioCodecChange,
                    onOpenAdvanced = onOpenAdvancedPage,
                    onStartStopHaptic = { haptics.press() },
                    onStart = {
                        runBusy("启动 scrcpy") {
                            if (noVideo && !audioEnabled) {
                                throw IllegalArgumentException("--no-video 需要同时启用音频")
                            }
                            if (audioEnabled && audioSourcePreset == "custom" && audioSourceCustom.isBlank()) {
                                throw IllegalArgumentException("audio-source 选择自定义时不能为空")
                            }
                            val resolvedVideoSource = videoSourcePreset.trim().ifBlank { "display" }
                            if (resolvedVideoSource == "camera" && !cameraMirroringSupported) {
                                throw IllegalArgumentException("camera mirroring 需要 Android 12+ (SDK 31+)")
                            }
                            val resolvedCameraSize = when (cameraSizePreset) {
                                "custom" -> cameraSizeCustom.trim()
                                else -> cameraSizePreset.trim()
                            }
                            if (resolvedVideoSource == "camera" && cameraSizePreset == "custom" && resolvedCameraSize.isBlank()) {
                                throw IllegalArgumentException("camera-size 选择自定义时不能为空")
                            }
                            val resolvedCameraId = cameraIdInput.trim()
                            val resolvedCameraFacing = cameraFacingPreset.trim()
                            if (resolvedVideoSource == "camera" && resolvedCameraId.isNotBlank() && resolvedCameraFacing.isNotBlank()) {
                                throw IllegalArgumentException("camera-id 与 camera-facing 不能同时设置")
                            }
                            val resolvedCameraAr = cameraArInput.trim()
                            val resolvedCameraFps =
                                cameraFpsInput.filter(Char::isDigit).toIntOrNull() ?: 0
                            if (resolvedVideoSource == "camera" && cameraHighSpeed && resolvedCameraFps <= 0) {
                                throw IllegalArgumentException("启用 --camera-high-speed 时，--camera-fps 不能为 0")
                            }
                            val maxSize =
                                maxSizeInput.filter(Char::isDigit).toIntOrNull()?.takeIf { it > 0 }
                                    ?: 0
                            val maxFps =
                                maxFpsInput.filter(Char::isDigit).toIntOrNull()?.toFloat() ?: 0f
                            if (resolvedVideoSource == "camera" && resolvedCameraSize.isNotBlank() && (maxSize > 0 || resolvedCameraAr.isNotBlank())) {
                                throw IllegalArgumentException("显式 camera-size 时不能同时设置 --max-size 或 --camera-ar")
                            }
                            val bitRateBps = (bitRateMbps * 1_000_000).toInt()
                            val audioBitRateBps = (audioBitRateKbps.coerceAtLeast(1)) * 1_000
                            val resolvedAudioSource = when (audioSourcePreset) {
                                "custom" -> audioSourceCustom.trim()
                                else -> audioSourcePreset.trim()
                            }
                            val newDisplayArg = buildNewDisplayArg(
                                newDisplayWidth.filter(Char::isDigit),
                                newDisplayHeight.filter(Char::isDigit),
                                newDisplayDpi.filter(Char::isDigit),
                            )
                            val displayId = displayIdInput.filter(Char::isDigit).toIntOrNull()
                                ?.takeIf { it > 0 }
                            val crop = buildCropArg(
                                cropWidth.filter(Char::isDigit),
                                cropHeight.filter(Char::isDigit),
                                cropX.filter(Char::isDigit),
                                cropY.filter(Char::isDigit),
                            )
                            val effectiveTurnScreenOff = turnScreenOff && !noControl
                            val session = nativeCore.scrcpyStart(
                                NativeCoreFacade.defaultStartRequest(
                                    customServerUri = customServerUri,
                                    maxSize = maxSize,
                                    maxFps = maxFps,
                                    videoBitRate = bitRateBps,
                                    remotePath = serverRemotePath.trim(),
                                    videoCodec = videoCodec,
                                    audio = audioEnabled,
                                    audioCodec = audioCodec,
                                    audioBitRate = audioBitRateBps,
                                    noControl = noControl,
                                    videoEncoder = videoEncoder,
                                    videoCodecOptions = videoCodecOptions,
                                    audioEncoder = audioEncoder,
                                    audioCodecOptions = audioCodecOptions,
                                    audioDup = audioDup,
                                    audioSource = resolvedAudioSource,
                                    videoSource = resolvedVideoSource,
                                    cameraId = resolvedCameraId,
                                    cameraFacing = resolvedCameraFacing,
                                    cameraSize = resolvedCameraSize,
                                    cameraAr = resolvedCameraAr,
                                    cameraFps = resolvedCameraFps,
                                    cameraHighSpeed = cameraHighSpeed,
                                    noAudioPlayback = noAudioPlayback,
                                    noVideo = noVideo,
                                    requireAudio = requireAudio,
                                    turnScreenOff = effectiveTurnScreenOff,
                                    newDisplay = newDisplayArg,
                                    displayId = displayId,
                                    crop = crop,
                                ),
                            )
                            sessionInfo = session
                            statusLine = "scrcpy 运行中"
                            @SuppressLint("DefaultLocale")
                            val videoDetail = if (noVideo) {
                                "off"
                            } else {
                                "${session.codec} ${session.width}x${session.height} @${
                                    String.format(
                                        "%.1f",
                                        bitRateMbps
                                    )
                                }Mbps"
                            }
                            val audioDetail = if (!audioEnabled) {
                                "off"
                            } else {
                                val playback = if (noAudioPlayback) "(no-playback)" else ""
                                "$audioCodec ${audioBitRateKbps}kbps source=${resolvedAudioSource.ifBlank { "default" }}$playback"
                            }
                            logEvent("scrcpy 已启动: device=${session.deviceName}, video=$videoDetail, audio=$audioDetail, control=${!noControl}, turnScreenOff=$effectiveTurnScreenOff, maxSize=${if (maxSize > 0) maxSize else "auto"}, maxFps=${if (maxFps > 0f) maxFps else "auto"}")
                            scope.launch {
                                snack.showSnackbar("scrcpy 已启动")
                            }
                            nativeCore.getLastScrcpyServerCommand()?.let { command ->
                                logEvent("scrcpy-server args: $command")
                            }
                        }
                    },
                    onStop = {
                        runBusy("停止 scrcpy") {
                            nativeCore.scrcpyStop()
                            sessionInfo = null
                            statusLine =
                                currentTarget?.let { "${it.host}:${it.port}" } ?: "ADB 已连接"
                            logEvent("scrcpy 已停止")
                            scope.launch {
                                snack.showSnackbar("scrcpy 已停止")
                            }
                        }
                    },
                    sessionStarted = sessionInfo != null,
                )
            }

            if (
                sessionInfo != null &&
                sessionInfo!!.width > 0 &&
                sessionInfo!!.height > 0
            ) {
                item {
                    PreviewCard(
                        sessionInfo = sessionInfo,
                        nativeCore = nativeCore,
                        previewHeightDp = previewCardHeightDp.coerceAtLeast(120),
                        controlsVisible = previewControlsVisible,
                        onTapped = {
                            previewControlsVisible = true
                            scope.launch {
                                delay(2000)
                                previewControlsVisible = false
                            }
                        },
                        onOpenFullscreen = {
                            val info = sessionInfo ?: return@PreviewCard
                            onOpenFullscreenPage(info)
                        },
                        onOpenFullscreenHaptic = { haptics.press() },
                    )
                }
                item {
                    VirtualButtonCard(
                        busy = busy,
                        outsideActions = virtualButtonLayout.first,
                        moreActions = virtualButtonLayout.second,
                        onPressHaptic = { haptics.press() },
                        onConfirmHaptic = { haptics.confirm() },
                        onAction = ::sendVirtualButtonAction,
                    )
                }
            }
        }

        if (eventLog.isNotEmpty()) item {
            Spacer(Modifier.height(UiSpacing.PageItem))
            LogsPanel(lines = eventLog)
        }

        // TODO: 放进 [AppPageLazyColumn] 里
        item { Spacer(Modifier.height(UiSpacing.BottomContent)) }
    }
}

private fun buildNewDisplayArg(width: String, height: String, dpi: String): String {
    val w = width.toIntOrNull()?.takeIf { it > 0 }
    val h = height.toIntOrNull()?.takeIf { it > 0 }
    val d = dpi.toIntOrNull()?.takeIf { it > 0 }
    val sizePart = if (w != null && h != null) "${w}x${h}" else ""
    return when {
        sizePart.isNotEmpty() && d != null -> "$sizePart/$d"
        sizePart.isNotEmpty() -> sizePart
        d != null -> "/$d"
        else -> ""
    }
}

private fun buildCropArg(width: String, height: String, x: String, y: String): String {
    val w = width.toIntOrNull()?.takeIf { it > 0 } ?: return ""
    val h = height.toIntOrNull()?.takeIf { it > 0 } ?: return ""
    val ox = x.toIntOrNull()?.takeIf { it >= 0 } ?: return ""
    val oy = y.toIntOrNull()?.takeIf { it >= 0 } ?: return ""
    return "$w:$h:$ox:$oy"
}
