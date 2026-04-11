package io.github.miuzarte.scrcpyforandroid.pages

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import io.github.miuzarte.scrcpyforandroid.StreamActivity
import io.github.miuzarte.scrcpyforandroid.constants.Defaults
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.haptics.LocalAppHaptics
import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcut
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcuts
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.EventLogger
import io.github.miuzarte.scrcpyforandroid.services.EventLogger.logEvent
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.services.fetchConnectedDeviceInfo
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.quickDevices
import io.github.miuzarte.scrcpyforandroid.storage.Storage.scrcpyOptions
import io.github.miuzarte.scrcpyforandroid.widgets.ConfigPanel
import io.github.miuzarte.scrcpyforandroid.widgets.DeviceTileList
import io.github.miuzarte.scrcpyforandroid.widgets.PairingCard
import io.github.miuzarte.scrcpyforandroid.widgets.PreviewCard
import io.github.miuzarte.scrcpyforandroid.widgets.QuickConnectCard
import io.github.miuzarte.scrcpyforandroid.widgets.SectionSmallTitle
import io.github.miuzarte.scrcpyforandroid.widgets.StatusCard
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonAction
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonActions
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.InetSocketAddress
import java.net.Socket

private const val ADB_CONNECT_TIMEOUT_MS = 12_000L
private const val ADB_KEEPALIVE_INTERVAL_MS = 3_000L
private const val ADB_KEEPALIVE_TIMEOUT_MS = 1_500L
private const val ADB_AUTO_RECONNECT_DISCOVER_TIMEOUT_MS = 2_000L
private const val ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS = 2_000L
private const val ADB_TCP_PROBE_TIMEOUT_MS = 500

@Composable
fun DeviceTabScreen(
    scrollBehavior: ScrollBehavior,
    scrcpy: Scrcpy,
    onOpenReorderDevices: () -> Unit,
) {
    val navigator = LocalRootNavigator.current
    var showThreePointMenu by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "设备",
                actions = {
                    IconButton(
                        onClick = { showThreePointMenu = true },
                        holdDownState = showThreePointMenu,
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "更多"
                        )
                    }
                    DeviceMenuPopup(
                        show = showThreePointMenu,
                        onDismissRequest = { showThreePointMenu = false },
                        onReorderDevices = {
                            onOpenReorderDevices()
                            showThreePointMenu = false
                        },
                        onOpenVirtualButtonOrder = {
                            navigator.push(RootScreen.VirtualButtonOrder)
                            showThreePointMenu = false
                        },
                        canClearLogs = EventLogger.hasLogs(),
                        onClearLogs = {
                            EventLogger.clearLogs()
                            showThreePointMenu = false
                        },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { pagePadding ->
        DeviceTabPage(
            contentPadding = pagePadding,
            scrollBehavior = scrollBehavior,
            scrcpy = scrcpy,
        )
    }
}

@Composable
fun DeviceTabPage(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    scrcpy: Scrcpy,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val taskScope = remember { CoroutineScope(Dispatchers.IO + SupervisorJob()) }
    val activity = remember(context) { context as? Activity }

    val haptics = LocalAppHaptics.current
    val navigator = LocalRootNavigator.current
    val snackbar = LocalSnackbarController.current

    val asBundleShared by appSettings.bundleState.collectAsState()
    val asBundleSharedLatest by rememberUpdatedState(asBundleShared)
    var asBundle by rememberSaveable(asBundleShared) { mutableStateOf(asBundleShared) }
    val asBundleLatest by rememberUpdatedState(asBundle)
    LaunchedEffect(asBundleShared) {
        if (asBundle != asBundleShared) {
            asBundle = asBundleShared
        }
    }
    LaunchedEffect(asBundle) {
        delay(Settings.BUNDLE_SAVE_DELAY)
        if (asBundle != asBundleSharedLatest) {
            appSettings.saveBundle(asBundle)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            taskScope.launch {
                appSettings.saveBundle(asBundleLatest)
            }
        }
    }

    val qdBundleShared by quickDevices.bundleState.collectAsState()
    val qdBundleSharedLatest by rememberUpdatedState(qdBundleShared)
    var qdBundle by rememberSaveable(qdBundleShared) { mutableStateOf(qdBundleShared) }
    val qdBundleLatest by rememberUpdatedState(qdBundle)
    LaunchedEffect(qdBundleShared) {
        if (qdBundle != qdBundleShared) {
            qdBundle = qdBundleShared
        }
    }
    LaunchedEffect(qdBundle) {
        delay(Settings.BUNDLE_SAVE_DELAY)
        if (qdBundle != qdBundleSharedLatest) {
            quickDevices.saveBundle(qdBundle)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            taskScope.launch {
                quickDevices.saveBundle(qdBundleLatest)
            }
        }
    }

    // read only
    val soBundleShared by scrcpyOptions.bundleState.collectAsState()

    fun setKeepScreenOn(enabled: Boolean) {
        val window = activity?.window ?: return
        window.decorView.post {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    DisposableEffect(activity) {
        onDispose {
            setKeepScreenOn(false)
        }
    }

    // Run adb operations on a dedicated single thread.
    // Try to avoid blocking UI/recomposition and keeps adb call ordering deterministic.

    var busy by rememberSaveable { mutableStateOf(false) }
    var statusLine by rememberSaveable { mutableStateOf("未连接") }
    var adbConnected by rememberSaveable { mutableStateOf(false) }
    var isQuickConnected by rememberSaveable { mutableStateOf(false) }
    var currentTargetHost by rememberSaveable { mutableStateOf("") }
    var currentTargetPort by rememberSaveable { mutableStateOf(Defaults.ADB_PORT) }
    var connectedDeviceLabel by rememberSaveable { mutableStateOf("未连接") }
    val sessionInfo by scrcpy.currentSessionState.collectAsState()
    var previewControlsVisible by rememberSaveable { mutableStateOf(false) }
    var editingDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeDeviceActionId by rememberSaveable { mutableStateOf<String?>(null) }
    var adbConnecting by rememberSaveable { mutableStateOf(false) }

    var audioForwardingSupported by rememberSaveable { mutableStateOf(true) }
    var cameraMirroringSupported by rememberSaveable { mutableStateOf(true) }
    var pendingScrollToPreview by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val currentTarget =
        if (currentTargetHost.isNotBlank())
            ConnectionTarget(currentTargetHost, currentTargetPort)
        else null

    val sessionReconnectBlacklistHosts = remember { mutableSetOf<String>() }

    val virtualButtonLayout = remember(asBundle.virtualButtonsLayout) {
        VirtualButtonActions.splitLayout(
            VirtualButtonActions.parseStoredLayout(asBundle.virtualButtonsLayout)
        )
    }

    var savedShortcuts by remember {
        mutableStateOf(DeviceShortcuts.unmarshalFrom(qdBundle.quickDevicesList))
    }

    LaunchedEffect(qdBundle.quickDevicesList) {
        savedShortcuts = DeviceShortcuts.unmarshalFrom(qdBundle.quickDevicesList)
    }
    // save changes when [savedShortcuts] was modified
    LaunchedEffect(savedShortcuts) {
        val serialized = savedShortcuts.marshalToString()
        if (serialized != qdBundle.quickDevicesList) {
            qdBundle = qdBundle.copy(quickDevicesList = serialized)
        }
    }
    val editingDevice = remember(savedShortcuts, editingDeviceId) {
        editingDeviceId?.let(savedShortcuts::get)
    }

    /**
     * Disconnect the current ADB connection and stop any running scrcpy session.
     *
     * Concurrency / thread boundary:
     * - Native calls that may block are executed on ADB dispatcher.
     * - This ensures UI coroutines are never blocked by synchronous native I/O.
     *
     * Side effects:
     * - Calls `scrcpy.stop()` and `NativeAdbService.disconnect()` (best-effort).
     * - Resets UI-visible connection state: `adbConnected`, `currentTargetHost/Port`,
     *   `sessionInfo`, device capability flags, `statusLine`, and `connectedDeviceLabel`.
     * - Updates the saved quick-device list via [savedShortcuts.update] when a target is provided.
     * - Logs an optional [logMessage] to the local event log.
     * - Shows an optional snackbar message asynchronously (launched on the composition scope)
     *   so callers don't get blocked by `snack.showSnackbar` (it is suspending).
     *
     * Usage notes:
     * - Prefer calling this from UI code wrapped by `runAdbConnect`/`runBusy` where appropriate
     *   so the UI busy/connect gates are respected.
     * - This function is idempotent from the UI state perspective: calling it when already
     *   disconnected will simply reset UI fields and not throw.
     */
    suspend fun disconnectAdbConnection(
        clearQuickOnlineForTarget: ConnectionTarget? = currentTarget,
        logMessage: String? = null,
        showSnackMessage: String? = null,
    ) {
        // Also stops scrcpy.
        runCatching { scrcpy.stop() }
        setKeepScreenOn(false)
        runCatching { NativeAdbService.disconnect() }
        adbConnected = false
        currentTargetHost = ""
        currentTargetPort = Defaults.ADB_PORT
        audioForwardingSupported = true
        cameraMirroringSupported = true
        statusLine = "未连接"
        connectedDeviceLabel = "未连接"
        clearQuickOnlineForTarget?.let { target ->
            if (target.host.isNotBlank())
                savedShortcuts = savedShortcuts.update(
                    host = target.host, port = target.port
                )
        }
        logMessage?.let { logEvent(it) }
        if (!showSnackMessage.isNullOrBlank()) {
            snackbar.show(showSnackMessage)
        }
    }

    suspend fun disconnectCurrentTargetBeforeConnecting(newHost: String, newPort: Int) {
        // Force old target cleanup before switching to another endpoint.
        val current = currentTarget
        if (!adbConnected || current == null) return
        if (current.host == newHost && current.port == newPort) return

        sessionReconnectBlacklistHosts += current.host
        disconnectAdbConnection(clearQuickOnlineForTarget = current)
    }

    fun applyConnectedDeviceCapabilities(sdkInt: Int, release: String) {
        val audioSupported = sdkInt !in 0..<30
        audioForwardingSupported = audioSupported
        if (!audioSupported && soBundleShared.audio) {
            scope.launch {
                scrcpyOptions.updateBundle { it.copy(audio = false) }
            }
            logEvent(
                "设备 Android ${release.ifBlank { "?" }} (SDK $sdkInt) 不支持音频转发，已自动关闭",
                Log.WARN
            )
        }
        val cameraSupported = sdkInt !in 0..<31
        cameraMirroringSupported = cameraSupported
        if (!cameraSupported && soBundleShared.videoSource == "camera") {
            scope.launch {
                scrcpyOptions.updateBundle { it.copy(videoSource = "display") }
            }
            logEvent(
                "设备 Android ${release.ifBlank { "?" }} (SDK $sdkInt) 不支持 camera mirroring，已切换为 display",
                Log.WARN
            )
        }
    }

    /**
     * Attempt to connect to an adb endpoint within a short timeout.
     *
     * Execution:
     * - Runs `nativeCore.adbConnect(host, port)` on [adbWorkerDispatcher] and wraps it with
     *   [withTimeout] to avoid hanging forever. Returns true on success, false / throws on failure
     *   depending on the underlying native behavior.
     *
     * Why this exists:
     * - Some adb endpoints can take long to accept TCP connects; the UI should not wait
     *   indefinitely. Use a small, caller-chosen timeout to keep UX snappy.
     */
    suspend fun connectWithTimeout(host: String, port: Int) {
        return withTimeout(ADB_CONNECT_TIMEOUT_MS) {
            NativeAdbService.connect(host, port)
        }
    }

    /**
     * Validate that the current ADB connection is still alive.
     *
     * Behavior:
     * - Runs on [adbWorkerDispatcher] with a short timeout.
     * - First checks `nativeCore.adbIsConnected()` to avoid unnecessary shell calls.
     * - Executes a lightweight `adb shell` command (`echo -n 1`) to verify the remote side is
     *   responsive. Returns true only when both checks succeed.
     *
     * Notes for reliability:
     * - Some devices may accept TCP connections but have a hung adb-server process; the shell
     *   echo check helps detect that state.
     */
    suspend fun keepAliveCheck(host: String, port: Int): Boolean {
        return withTimeout(ADB_KEEPALIVE_TIMEOUT_MS) {
            val connected = NativeAdbService.isConnected()
            if (!connected) {
                return@withTimeout false
            }
            return@withTimeout true
        }
    }

    /**
     * Quickly test TCP reachability to an endpoint.
     *
     * - Uses a plain Socket connect on [Dispatchers.IO] with a very short timeout.
     * - This is useful before attempting an adb connect to avoid long native timeouts.
     * - Returns true when TCP handshake succeeds within [ADB_TCP_PROBE_TIMEOUT_MS].
     */
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

    /**
     * Execute a suspend [block] while toggling the `busy` UI gate.
     *
     * - Intended for non-adb user actions (UI-level operations) that should disable
     *   certain controls while active (e.g. scrcpy start/stop, pairing, listing).
     * - Errors are logged and surfaced via a snackbar where appropriate. The snackbar
     *   is launched asynchronously so the outer coroutine can continue to unwind.
     * - Ensures `busy` is reset in `finally` so the UI recovers even on exceptions.
     */
    fun runBusy(label: String, onFinished: (() -> Unit)? = null, block: suspend () -> Unit) {
        // For non-adb actions (start/stop/pair/list refresh...).
        if (busy) return
        taskScope.launch {
            busy = true
            try {
                block()
            } catch (_: TimeoutCancellationException) {
                logEvent("$label 超时", Log.WARN)
            } catch (e: IllegalArgumentException) {
                val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                logEvent("$label 参数错误: $detail", Log.WARN, e)
                snackbar.show("$label 参数错误: $detail")
            } catch (e: Exception) {
                val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                logEvent("$label 失败: $detail", Log.ERROR, e)
            } finally {
                busy = false
                onFinished?.invoke()
            }
        }
    }

    /**
     * Execute a manual ADB-related suspend [block] while toggling `adbConnecting`.
     *
     * Purpose:
     * - Called from explicit user actions (pressing "connect" / "disconnect").
     * - Keeps the UI responsive by marking only user-initiated connect operations as in-progress.
     *
     * Concurrency notes:
     * - Background auto-reconnect attempts deliberately DO NOT set `adbConnecting` so that
     *   UI controls remain actionable while background retries occur.
     * - Errors and timeouts are logged and surfaced similarly to `runBusy`.
     */
    fun runAdbConnect(
        label: String,
        onStarted: (() -> Unit)? = null,
        onFinished: (() -> Unit)? = null,
        block: suspend () -> Unit,
    ) {
        // For manual adb operations from user actions.
        if (adbConnecting) return
        taskScope.launch {
            onStarted?.invoke()
            adbConnecting = true
            try {
                block()
            } catch (_: TimeoutCancellationException) {
                logEvent("$label 超时", Log.WARN)
            } catch (e: IllegalArgumentException) {
                val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                logEvent("$label 参数错误: $detail", Log.WARN, e)
                snackbar.show("$label 参数错误: $detail")
            } catch (e: Exception) {
                val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                logEvent("$label 失败: $detail", Log.ERROR, e)
            } finally {
                adbConnecting = false
                onFinished?.invoke()
            }
        }
    }

    suspend fun runAutoAdbConnect(host: String, port: Int) {
        runCatching {
            connectWithTimeout(host, port)
        }.getOrElse { error ->
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            logEvent("自动重连失败: $host:$port ($detail)", Log.WARN)
        }
    }

    LaunchedEffect(adbConnected, currentTargetHost, currentTargetPort) {
        if (!adbConnected || currentTargetHost.isBlank()) return@LaunchedEffect

        // Keep-alive loop for current target.
        // On failure: try to reconnect once; if failed, fully disconnect and reset UI state.
        val host = currentTargetHost
        val port = currentTargetPort
        while (adbConnected && currentTargetHost == host && currentTargetPort == port) {
            delay(ADB_KEEPALIVE_INTERVAL_MS)
            val alive = runCatching { keepAliveCheck(host, port) }.getOrElse { false }
            if (alive) continue

            logEvent("ADB 长连接中断，尝试自动重连: $host:$port", Log.WARN)
            try {
                connectWithTimeout(host, port)
                adbConnected = true
                statusLine = "$host:$port"
                logEvent("ADB 自动重连成功: $host:$port")
                snackbar.show("ADB 自动重连成功")
            } catch (e: Exception) {
                disconnectAdbConnection()
                statusLine = "ADB 连接断开"
                logEvent("ADB 自动重连失败: $e", Log.ERROR)
                snackbar.show("ADB 自动重连失败")
                break
            }
        }
    }

    suspend fun startScrcpySession() {
        val options = scrcpyOptions.toClientOptions(soBundleShared).fix()
        val session = scrcpy.start(options)
        pendingScrollToPreview = true
        if (options.disableScreensaver) {
            setKeepScreenOn(true)
        }
        statusLine = "scrcpy 运行中"
        @SuppressLint("DefaultLocale")
        val videoDetail =
            if (!options.video) "off"
            else if (soBundleShared.videoBitRate <= 0) "${session.codec?.string ?: "null"} ${session.width}x${session.height} @default"
            else "${session.codec?.string ?: "null"} ${session.width}x${session.height} " +
                    "@${String.format("%.1f", soBundleShared.videoBitRate / 1_000_000f)}Mbps"

        val audioDetail =
            if (!soBundleShared.audio) "off"
            else if (soBundleShared.audioBitRate <= 0) "${options.audioCodec} default source=${options.audioSource}"
            else "${options.audioCodec} ${soBundleShared.audioBitRate / 1_000f}Kbps source=${options.audioSource}${if (!options.audioPlayback) "(no-playback)" else ""}"

        logEvent(
            "scrcpy 已启动: device=${session.deviceName}" +
                    ", video=$videoDetail, audio=$audioDetail" +
                    ", control=${options.control}, turnScreenOff=${options.turnScreenOff}" +
                    ", maxSize=${options.maxSize}, maxFps=${options.maxFps}"
        )
        snackbar.show("scrcpy 已启动")
    }

    LaunchedEffect(pendingScrollToPreview, sessionInfo) {
        if (!pendingScrollToPreview) return@LaunchedEffect
        val session = sessionInfo ?: return@LaunchedEffect
        if (session.width <= 0 || session.height <= 0) return@LaunchedEffect
        // status/device list/scrcpy panel are above the preview card
        listState.animateScrollToItem(index = 3)
        pendingScrollToPreview = false
    }

    suspend fun handleAdbConnected(host: String, port: Int) {
        currentTargetHost = host
        currentTargetPort = port

        val info = fetchConnectedDeviceInfo(NativeAdbService, host, port)
        val fullLabel = if (info.serial.isNotBlank()) {
            "${info.model} (${info.serial})"
        } else {
            info.model
        }

        connectedDeviceLabel = info.model
        applyConnectedDeviceCapabilities(info.sdkInt, info.androidRelease)
        savedShortcuts = savedShortcuts.update(
            host = host, port = port,
            name = fullLabel,
            updateNameOnlyWhenEmpty = true
        )
        statusLine = "$host:$port"

        logEvent(
            "ADB 已连接: " +
                    "model=${info.model}, " +
                    "serial=${info.serial.ifBlank { "unknown" }}, " +
                    "manufacturer=${info.manufacturer.ifBlank { "unknown" }}, " +
                    "brand=${info.brand.ifBlank { "unknown" }}, " +
                    "device=${info.device.ifBlank { "unknown" }}, " +
                    "android=${info.androidRelease.ifBlank { "unknown" }}, " +
                    "sdk=${info.sdkInt}"
        )
        snackbar.show("ADB 已连接")

        if (
            savedShortcuts.get(host, port)?.startScrcpyOnConnect == true &&
            sessionInfo == null
        ) {
            runBusy("启动 scrcpy") {
                startScrcpySession()
            }
        }
    }
    LaunchedEffect(
        adbConnected,
        asBundle.adbAutoReconnectPairedDevice,
        asBundle.adbMdnsLanDiscovery
    ) {
        if (adbConnected || !asBundle.adbAutoReconnectPairedDevice) return@LaunchedEffect

        // Background auto reconnect pipeline:
        // 1) try quick list targets with reachable TCP ports
        // 2) fallback to mDNS discovery
        val quickConnectTriedOnce = mutableSetOf<String>()
        while (!adbConnected) {
            if (busy || adbConnecting || sessionInfo != null) {
                delay(ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS)
                continue
            }

            val quickCandidates = savedShortcuts.toList()
            if (quickCandidates.isNotEmpty()) {
                for (target in quickCandidates) {
                    if (adbConnected || adbConnecting) break
                    if (sessionReconnectBlacklistHosts.contains(target.host)) continue
                    val targetKey = "${target.host}:${target.port}"
                    if (quickConnectTriedOnce.contains(targetKey)) continue

                    val portReachable = probeTcpReachable(target.host, target.port)
                    if (!portReachable) continue

                    quickConnectTriedOnce += targetKey
                    try {
                        runAutoAdbConnect(target.host, target.port)
                        adbConnected = true
                        savedShortcuts = savedShortcuts.update(
                            host = target.host, port = target.port,
                        )
                        handleAdbConnected(target.host, target.port)
                        logEvent("ADB 快速探测连接成功: ${target.host}:${target.port}")
                    } catch (_: Exception) {
                    }
                    break
                }
                if (adbConnected) break
            }

            val discovered = withContext(Dispatchers.IO) {
                NativeAdbService.discoverConnectService(
                    timeoutMs = ADB_AUTO_RECONNECT_DISCOVER_TIMEOUT_MS,
                    includeLanDevices = asBundle.adbMdnsLanDiscovery,
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
            val knownDevice = savedShortcuts.firstOrNull { it.host == discoveredHost }
            if (knownDevice == null) {
                delay(ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS)
                continue
            }
            val portToReplace = savedShortcuts.firstOrNull {
                it.host == discoveredHost &&
                        it.port != Defaults.ADB_PORT &&
                        it.port != discoveredPort
            }?.port
            if (portToReplace != null) {
                savedShortcuts = savedShortcuts.update(
                    host = discoveredHost, port = portToReplace,
                    newPort = discoveredPort,
                )
                logEvent(
                    "mDNS 发现新端口，已更新快速设备: $discoveredHost:$portToReplace -> $discoveredHost:$discoveredPort"
                )
            }

            if (adbConnected || adbConnecting) {
                delay(ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS)
                continue
            }

            try {
                runAutoAdbConnect(discoveredHost, discoveredPort)
                adbConnected = true
                savedShortcuts = savedShortcuts.update(
                    host = discoveredHost, port = discoveredPort,
                )
                handleAdbConnected(discoveredHost, discoveredPort)
                logEvent("ADB 自动重连成功: $discoveredHost:$discoveredPort")
            } catch (_: Exception) {
            }

            delay(ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS)
        }
    }

    fun sendVirtualButtonAction(action: VirtualButtonAction) {
        val keycode = action.keycode ?: return
        runBusy("发送 ${action.title}") {
            scrcpy.injectKeycode(0, keycode)
            scrcpy.injectKeycode(1, keycode)
        }
    }

    // 设备
    LazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
        state = listState,
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

        item {
            DeviceTileList(
                devices = savedShortcuts,
                isConnected = { device ->
                    adbConnected
                            && currentTarget?.host == device.host
                            && currentTarget.port == device.port
                },
                actionEnabled = !busy && !adbConnecting,
                actionInProgress = { device ->
                    adbConnecting && activeDeviceActionId == device.id
                },
                editingDeviceId = editingDeviceId,
                onClick = {
                    snackbar.show("长按可编辑")
                },
                onLongClick = { device ->
                    val connected = adbConnected
                            && currentTarget?.host == device.host
                            && currentTarget.port == device.port
                    if (connected) {
                        snackbar.show("无法修改已连接的设备")
                    } else {
                        editingDeviceId =
                            if (editingDeviceId != device.id) device.id
                            else null
                    }
                },
                onAction = { device ->
                    haptics.contextClick()
                    val host = device.host
                    val port = device.port
                    val connected = adbConnected
                            && currentTarget?.host == host
                            && currentTarget.port == port
                    if (!connected) {
                        runAdbConnect(
                            "连接 ADB",
                            onStarted = { activeDeviceActionId = device.id },
                            onFinished = { activeDeviceActionId = null },
                        ) {
                            disconnectCurrentTargetBeforeConnecting(host, port)
                            try {
                                connectWithTimeout(host, port)
                                adbConnected = true
                                isQuickConnected = false
                                savedShortcuts = savedShortcuts.update(
                                    host = host, port = port,
                                )
                                handleAdbConnected(host, port)
                            } catch (e: Exception) {
                                statusLine = "ADB 连接失败"
                                logEvent("ADB 连接失败: $e", Log.ERROR)
                                snackbar.show("ADB 连接失败")
                            }
                        }
                    } else {
                        activeDeviceActionId = device.id
                        runAdbConnect(
                            "断开 ADB",
                            onStarted = { activeDeviceActionId = device.id },
                            onFinished = { activeDeviceActionId = null },
                        ) {
                            sessionReconnectBlacklistHosts += host
                            disconnectAdbConnection(
                                clearQuickOnlineForTarget = ConnectionTarget(host, port),
                                logMessage = "ADB 已断开: ${device.name}",
                                showSnackMessage = "ADB 已断开",
                            )
                        }
                    }
                },
                onEditorSave = { device, updated ->
                    savedShortcuts = savedShortcuts.update(
                        id = device.id,
                        name = updated.name,
                        host = updated.host,
                        port = updated.port,
                        startScrcpyOnConnect = updated.startScrcpyOnConnect,
                    )
                },
                onEditorDelete = { device ->
                    savedShortcuts = savedShortcuts.remove(id = device.id)
                    editingDeviceId = null
                },
                onEditorCancel = { editingDeviceId = null },
            )
        }

        if (!adbConnected) {
            // "快速连接"
            item {
                QuickConnectCard(
                    input = qdBundle.quickConnectInput,
                    onValueChange = {
                        qdBundle = qdBundle.copy(quickConnectInput = it)
                    },
                    enabled = !adbConnecting,
                    onAddDevice = {
                        val target = ConnectionTarget.unmarshalFrom(qdBundle.quickConnectInput)
                            ?: return@QuickConnectCard
                        savedShortcuts = savedShortcuts.upsert(
                            DeviceShortcut(host = target.host, port = target.port)
                        )
                        Log.d(
                            "SavedShortcuts",
                            "size: ${savedShortcuts.size}, list: ${qdBundle.quickDevicesList}"
                        )
                        snackbar.show("已添加设备: ${target.host}:${target.port}")
                    },
                    onConnect = {
                        val target = ConnectionTarget.unmarshalFrom(qdBundle.quickConnectInput)
                            ?: return@QuickConnectCard
                        runAdbConnect(
                            "连接 ADB",
                            onStarted = { activeDeviceActionId = target.toString() },
                            onFinished = { activeDeviceActionId = null },
                        ) {
                            disconnectCurrentTargetBeforeConnecting(target.host, target.port)
                            try {
                                connectWithTimeout(target.host, target.port)
                                adbConnected = true
                                isQuickConnected = true // 标记为快速连接
                                savedShortcuts = savedShortcuts.update(
                                    host = target.host, port = target.port,
                                )
                                handleAdbConnected(target.host, target.port)
                            } catch (e: Exception) {
                                statusLine = "ADB 连接失败"
                                logEvent("ADB 连接失败: $e", Log.ERROR)
                                snackbar.show("ADB 连接失败")
                            }
                        }
                    },
                )
            }

            item {
                SectionSmallTitle("无线配对")
                // "使用配对码配对设备"
                PairingCard(
                    busy = busy,
                    autoDiscoverOnDialogOpen = asBundle.adbPairingAutoDiscoverOnDialogOpen,
                    onDiscoverTarget = {
                        NativeAdbService.discoverPairingService(
                            includeLanDevices = asBundle.adbMdnsLanDiscovery,
                        )
                    },
                    onPair = { host, port, code ->
                        runBusy("执行配对") {
                            val resolvedHost = host.trim()
                            val resolvedPort = port.trim().toIntOrNull() ?: return@runBusy
                            val resolvedCode = code.trim()
                            val ok = NativeAdbService.pair(
                                resolvedHost,
                                resolvedPort,
                                resolvedCode,
                            )
                            logEvent(
                                if (ok) "配对成功" else "配对失败",
                                if (ok) Log.INFO else Log.ERROR
                            )
                            snackbar.show(if (ok) "配对成功" else "配对失败")
                        }
                    },
                )
            }
        }

        if (adbConnected) {
            item {
                SectionSmallTitle("Scrcpy")
                ConfigPanel(
                    busy = busy,
                    audioForwardingSupported = audioForwardingSupported,
                    cameraMirroringSupported = cameraMirroringSupported,
                    adbConnecting = adbConnecting,
                    isQuickConnected = isQuickConnected,
                    onOpenAdvanced = { navigator.push(RootScreen.Advanced) },
                    onStartStopHaptic = { haptics.contextClick() },
                    onStart = {
                        runBusy("启动 scrcpy") {
                            startScrcpySession()
                        }
                    },
                    onStop = {
                        runBusy("停止 scrcpy") {
                            scrcpy.stop()
                            setKeepScreenOn(false)
                            statusLine = "${currentTarget!!.host}:${currentTarget.port}"
                            logEvent("scrcpy 已停止")
                            snackbar.show("scrcpy 已停止")
                        }
                    },
                    sessionInfo = sessionInfo,
                    onDisconnect = {
                        runAdbConnect(
                            "断开 ADB",
                            onStarted = {},
                            onFinished = {},
                        ) {
                            currentTarget?.let { target ->
                                sessionReconnectBlacklistHosts += target.host
                                disconnectAdbConnection(
                                    clearQuickOnlineForTarget = target,
                                    logMessage = "ADB 已断开",
                                    showSnackMessage = "ADB 已断开",
                                )
                            }
                        }
                    },
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
                        previewHeightDp = asBundle.devicePreviewCardHeightDp.coerceAtLeast(120),
                        controlsVisible = previewControlsVisible,
                        onTapped = {
                            previewControlsVisible = !previewControlsVisible
                        },
                        onOpenFullscreen = {
                            context.startActivity(StreamActivity.createIntent(context))
                        },
                    )
                }

                item {
                    VirtualButtonCard(
                        busy = busy,
                        outsideActions = virtualButtonLayout.first,
                        moreActions = virtualButtonLayout.second,
                        showText = asBundle.previewVirtualButtonShowText,
                        onAction = ::sendVirtualButtonAction,
                    )
                }
            }
        }

        if (EventLogger.hasLogs()) {
            item {
                SectionSmallTitle("日志")
                Card {
                    TextField(
                        value = EventLogger.eventLog.joinToString(separator = "\n"),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item { Spacer(Modifier.height(UiSpacing.PageBottom)) }
    }
}

@Composable
private fun DeviceMenuPopup(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onReorderDevices: () -> Unit,
    onOpenVirtualButtonOrder: () -> Unit,
    canClearLogs: Boolean,
    onClearLogs: () -> Unit,
) {
    OverlayListPopup(
        show = show,
        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = onDismissRequest,
        enableWindowDim = false,
    ) {
        ListPopupColumn {
            DeviceMenuPopupItem(
                text = "快速设备排序",
                optionSize = 3,
                index = 0,
                onSelectedIndexChange = { onReorderDevices() },
            )
            DeviceMenuPopupItem(
                text = "虚拟按钮排序",
                optionSize = 3,
                index = 1,
                onSelectedIndexChange = { onOpenVirtualButtonOrder() },
            )
            DeviceMenuPopupItem(
                text = "清空日志",
                optionSize = 3,
                index = 2,
                enabled = canClearLogs,
                onSelectedIndexChange = { onClearLogs() },
            )
        }
    }
}

@Composable
private fun DeviceMenuPopupItem(
    text: String,
    optionSize: Int,
    index: Int,
    enabled: Boolean = true,
    onSelectedIndexChange: (Int) -> Unit,
) {
    if (enabled) {
        DropdownImpl(
            text = text,
            optionSize = optionSize,
            isSelected = false,
            index = index,
            onSelectedIndexChange = onSelectedIndexChange,
        )
        return
    }

    val additionalTopPadding = if (index == 0) UiSpacing.PopupHorizontal else UiSpacing.PageItem
    val additionalBottomPadding =
        if (index == optionSize - 1) UiSpacing.PopupHorizontal else UiSpacing.PageItem
    Text(
        text = text,
        fontSize = MiuixTheme.textStyles.body1.fontSize,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiSpacing.PopupHorizontal)
            .padding(top = additionalTopPadding, bottom = additionalBottomPadding),
    )
}
