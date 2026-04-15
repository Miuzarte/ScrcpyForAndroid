package io.github.miuzarte.scrcpyforandroid.pages

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlendModeColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.miuzarte.scrcpyforandroid.StreamActivity
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.haptics.LocalAppHaptics
import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcut
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcuts
import io.github.miuzarte.scrcpyforandroid.password.PasswordPickerPopupContent
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.scrcpy.ClientOptions
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.VideoSource
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.AppWakeLocks
import io.github.miuzarte.scrcpyforandroid.services.DeviceAdbBackgroundRunner
import io.github.miuzarte.scrcpyforandroid.services.DeviceAdbConnectionCoordinator
import io.github.miuzarte.scrcpyforandroid.services.DeviceAdbSessionState
import io.github.miuzarte.scrcpyforandroid.services.EventLogger
import io.github.miuzarte.scrcpyforandroid.services.EventLogger.logEvent
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyOptions
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.quickDevices
import io.github.miuzarte.scrcpyforandroid.storage.Storage.scrcpyOptions
import io.github.miuzarte.scrcpyforandroid.storage.Storage.scrcpyProfiles
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SpinnerColors
import top.yukonga.miuix.kmp.basic.SpinnerDefaults
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles

private const val ADB_CONNECT_TIMEOUT_MS = 12_000L
private const val ADB_KEEPALIVE_INTERVAL_MS = 3_000L
private const val ADB_KEEPALIVE_TIMEOUT_MS = 1_500L
private const val ADB_AUTO_RECONNECT_DISCOVER_TIMEOUT_MS = 2_000L
private const val ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS = 2_000L
private const val ADB_TCP_PROBE_TIMEOUT_MS = 500
private const val PREVIEW_CARD_ITEM_KEY = "preview_card"
private const val PREVIEW_CARD_ITEM_INDEX = 3

private data class StartAppRequest(
    val packageName: String,
    val displayId: Int?,
    val forceStop: Boolean,
    val matchedAppLabel: String? = null,
)

@Composable
fun DeviceTabScreen(
    scrollBehavior: ScrollBehavior,
    scrcpy: Scrcpy,
    bottomInnerPadding: Dp,
    onOpenReorderDevices: () -> Unit,
) {
    val navigator = LocalRootNavigator.current
    var showThreePointMenu by rememberSaveable { mutableStateOf(false) }
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                TopAppBar(
                    title = "设备",
                    color =
                        if (blurActive) Color.Transparent
                        else colorScheme.surface,
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
            }
        },
    ) { pagePadding ->
        Box(modifier = if (blurActive) Modifier.layerBackdrop(blurBackdrop) else Modifier) {
            DeviceTabPage(
                contentPadding = pagePadding,
                scrollBehavior = scrollBehavior,
                scrcpy = scrcpy,
                bottomInnerPadding = bottomInnerPadding,
            )
        }
    }
}

@Composable
fun DeviceTabPage(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    scrcpy: Scrcpy,
    bottomInnerPadding: Dp,
) {
    val activity = LocalActivity.current
    val fragmentActivity = remember(activity) { activity as? FragmentActivity }
    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    val taskScope = remember { CoroutineScope(Dispatchers.IO + SupervisorJob()) }
    val adbCoordinator = remember { DeviceAdbConnectionCoordinator() }
    val adbBackgroundRunner = remember { DeviceAdbBackgroundRunner() }
    val lifecycleOwner = LocalLifecycleOwner.current

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
    val scrcpyProfilesState by scrcpyProfiles.state.collectAsState()

    var isAppInForeground by rememberSaveable { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose {
            AppWakeLocks.release()
        }
    }
    DisposableEffect(adbBackgroundRunner) {
        onDispose {
            adbBackgroundRunner.close()
        }
    }
    DisposableEffect(lifecycleOwner) {
        isAppInForeground =
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> isAppInForeground = true
                Lifecycle.Event.ON_STOP -> isAppInForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Run adb operations on a dedicated single thread.
    // Try to avoid blocking UI/recomposition and keeps adb call ordering deterministic.

    var busy by rememberSaveable { mutableStateOf(false) }
    var adbSession by rememberSaveable { mutableStateOf(DeviceAdbSessionState()) }
    val sessionInfo by scrcpy.currentSessionState.collectAsState()
    val listingsRefreshBusy by scrcpy.listings.refreshBusyState.collectAsState()
    var editingDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeDeviceActionId by rememberSaveable { mutableStateOf<String?>(null) }
    var adbConnecting by rememberSaveable { mutableStateOf(false) }
    var pendingScrollToPreview by rememberSaveable { mutableStateOf(false) }
    var showRecentTasksSheet by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isPreviewCardVisible by remember(listState) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == PREVIEW_CARD_ITEM_KEY }
        }
    }

    val adbConnected = adbSession.isConnected
    val statusLine = adbSession.statusLine
    val isQuickConnected = adbSession.isQuickConnected
    val currentTarget = adbSession.currentTarget
    val connectedDeviceLabel = adbSession.connectedDeviceLabel
    val connectedScrcpyProfileId = adbSession.connectedScrcpyProfileId
    val audioForwardingSupported = adbSession.audioForwardingSupported
    val cameraMirroringSupported = adbSession.cameraMirroringSupported
    val recentTasks = scrcpy.listings.recentTasks

    fun resolveScrcpyBundle(profileId: String): ScrcpyOptions.Bundle {
        if (profileId == ScrcpyOptions.GLOBAL_PROFILE_ID) {
            return soBundleShared
        }
        return scrcpyProfilesState.profiles.firstOrNull { it.id == profileId }?.bundle
            ?: soBundleShared
    }

    val sessionReconnectBlacklistHosts = remember { mutableSetOf<String>() }

    val virtualButtonLayout = remember(asBundle.virtualButtonsLayout) {
        VirtualButtonActions.splitLayout(
            VirtualButtonActions.parseStoredLayout(asBundle.virtualButtonsLayout)
        )
    }

    var quickConnectInputTemp by rememberSaveable(qdBundle.quickConnectInput) {
        mutableStateOf(qdBundle.quickConnectInput)
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
        runCatching { adbCoordinator.disconnect() }
        AppWakeLocks.release()
        adbSession = DeviceAdbSessionState()
        AppRuntime.currentConnectionTarget = null
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
        val currentBundle = resolveScrcpyBundle(connectedScrcpyProfileId)
        val audioSupported = sdkInt !in 0..<30
        adbSession = adbSession.copy(audioForwardingSupported = audioSupported)
        if (!audioSupported && currentBundle.audio) {
            scope.launch {
                if (connectedScrcpyProfileId == ScrcpyOptions.GLOBAL_PROFILE_ID) {
                    scrcpyOptions.updateBundle { it.copy(audio = false) }
                } else {
                    scrcpyProfiles.updateBundle(
                        connectedScrcpyProfileId,
                        currentBundle.copy(audio = false),
                    )
                }
            }
            logEvent(
                "设备 Android ${release.ifBlank { "?" }} (SDK $sdkInt) 不支持音频转发，已自动关闭",
                Log.WARN
            )
        }
        val cameraSupported = sdkInt !in 0..<31
        adbSession = adbSession.copy(cameraMirroringSupported = cameraSupported)
        if (!cameraSupported && currentBundle.videoSource == "camera") {
            scope.launch {
                if (connectedScrcpyProfileId == ScrcpyOptions.GLOBAL_PROFILE_ID) {
                    scrcpyOptions.updateBundle { it.copy(videoSource = "display") }
                } else {
                    scrcpyProfiles.updateBundle(
                        connectedScrcpyProfileId,
                        currentBundle.copy(videoSource = "display"),
                    )
                }
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
        adbCoordinator.connectWithTimeout(host, port, ADB_CONNECT_TIMEOUT_MS)
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
        return adbCoordinator.isConnected(ADB_KEEPALIVE_TIMEOUT_MS)
    }

    /**
     * Quickly test TCP reachability to an endpoint.
     *
     * - Uses a plain Socket connect on [Dispatchers.IO] with a very short timeout.
     * - This is useful before attempting an adb connect to avoid long native timeouts.
     * - Returns true when TCP handshake succeeds within [ADB_TCP_PROBE_TIMEOUT_MS].
     */
    suspend fun probeTcpReachable(host: String, port: Int): Boolean {
        return adbCoordinator.probeTcpReachable(host, port, ADB_TCP_PROBE_TIMEOUT_MS)
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

    suspend fun runAutoAdbConnect(host: String, port: Int): Boolean {
        return runCatching {
            connectWithTimeout(host, port)
            true
        }.getOrElse { error ->
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            logEvent("自动重连失败: $host:$port ($detail)", Log.WARN)
            false
        }
    }

    LaunchedEffect(adbConnected, currentTarget, isAppInForeground) {
        if (!adbConnected || currentTarget == null) return@LaunchedEffect
        adbBackgroundRunner.runKeepAliveLoop(
            sessionState = { adbSession },
            isForeground = { isAppInForeground },
            intervalMs = ADB_KEEPALIVE_INTERVAL_MS,
            keepAliveCheck = ::keepAliveCheck,
            reconnect = ::connectWithTimeout,
            onReconnectSuccess = { host, port ->
                logEvent("ADB 自动重连成功: $host:$port")
                adbSession = adbSession.copy(
                    isConnected = true,
                    statusLine = "$host:$port",
                )
                snackbar.show("ADB 自动重连成功")
            },
            onReconnectFailure = { error ->
                disconnectAdbConnection()
                adbSession = adbSession.copy(statusLine = "ADB 连接断开")
                logEvent("ADB 自动重连失败: $error", Log.ERROR)
                snackbar.show("ADB 自动重连失败")
            },
        )
    }

    suspend fun resolveStartAppRequest(
        scrcpy: Scrcpy,
        options: ClientOptions,
    ): StartAppRequest? {
        val raw = options.startApp.trim()
        if (raw.isBlank()) {
            return null
        }

        var query = raw
        val forceStop = query.startsWith("+")
        if (forceStop) {
            query = query.drop(1).trimStart()
        }
        require(query.isNotBlank()) { "应用名或包名不能为空" }

        val displayId = when {
            options.videoSource != VideoSource.DISPLAY -> null
            options.displayId >= 0 -> options.displayId
            else -> null
        }

        if (!query.startsWith("?")) {
            return StartAppRequest(
                packageName = query,
                displayId = displayId,
                forceStop = forceStop,
            )
        }

        val searchName = query.drop(1).trim()
        require(searchName.isNotBlank()) {
            "应用名不能为空"
        }

        val apps = scrcpy.listings.getApps(forceRefresh = false)
        val matches = apps.filter {
            it.label?.startsWith(searchName, ignoreCase = true) == true
        }

        require(matches.isNotEmpty()) {
            "未找到应用名以 \"$searchName\" 开头的应用"
        }
        require(matches.size == 1) {
            "按应用名匹配到多个应用: " +
                    matches.take(5).joinToString {
                        "${it.label ?: it.packageName} (${it.packageName})"
                    }
        }

        return StartAppRequest(
            packageName = matches[0].packageName,
            displayId = displayId,
            forceStop = forceStop,
            matchedAppLabel = matches[0].label,
        )
    }

    suspend fun startScrcpySession(
        openFullscreen: Boolean = false,
        startAppOverride: String? = null,
    ) {
        val activeBundle = resolveScrcpyBundle(connectedScrcpyProfileId)
        val options = scrcpyOptions.toClientOptions(activeBundle).fix()
        val resolvedOptions = startAppOverride
            ?.takeIf { it.isNotBlank() }
            ?.let { options.copy(startApp = it) }
            ?: options
        val session = scrcpy.start(resolvedOptions)
        pendingScrollToPreview = true
        val startAppRequest = runCatching {
            resolveStartAppRequest(scrcpy, resolvedOptions)
        }.getOrElse { error ->
            logEvent(
                "启动应用请求无效: " +
                        "${error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName}",
                Log.WARN,
                error,
            )
            null
        }
        startAppRequest?.let { request ->
            if (resolvedOptions.newDisplay.isNotBlank() && request.displayId == null) {
                logEvent(
                    "当前实现无法获取 new display 的真实 displayId，应用会启动到默认显示",
                    Log.WARN,
                )
            }
            runCatching {
                adbCoordinator.startApp(
                    packageName = request.packageName,
                    displayId = request.displayId,
                    forceStop = request.forceStop,
                )
            }.onSuccess {
                val appLabelPart = request.matchedAppLabel?.let { " ($it)" }.orEmpty()
                logEvent(
                    "已启动应用: " +
                            "${request.packageName}$appLabelPart"
                            + request.displayId?.let { " @display=$it" }.orEmpty()
                )
            }.onFailure { error ->
                logEvent(
                    "启动应用失败: " +
                            "${request.packageName} " +
                            "(${error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName})",
                    Log.WARN,
                    error,
                )
            }
        }
        if (resolvedOptions.fullscreen || openFullscreen) withContext(Dispatchers.Main) {
            context.startActivity(StreamActivity.createIntent(context))
        }
        if (resolvedOptions.disableScreensaver)
            AppWakeLocks.acquire()

        adbSession = adbSession.copy(statusLine = "scrcpy 运行中")
        @SuppressLint("DefaultLocale")
        val videoDetail =
            if (!resolvedOptions.video) "off"
            else if (activeBundle.videoBitRate <= 0) "${session.codec?.string ?: "null"} ${session.width}x${session.height} @default"
            else "${session.codec?.string ?: "null"} ${session.width}x${session.height} " +
                    "@${String.format("%.1f", activeBundle.videoBitRate / 1_000_000f)}Mbps"

        val audioDetail =
            if (!activeBundle.audio) "off"
            else if (activeBundle.audioBitRate <= 0) "${resolvedOptions.audioCodec} default source=${resolvedOptions.audioSource}"
            else "${resolvedOptions.audioCodec} ${activeBundle.audioBitRate / 1_000f}Kbps source=${resolvedOptions.audioSource}${if (!resolvedOptions.audioPlayback) "(no-playback)" else ""}"

        logEvent(
            "scrcpy 已启动: device=${session.deviceName}" +
                    ", video=$videoDetail, audio=$audioDetail" +
                    ", control=${resolvedOptions.control}, turnScreenOff=${resolvedOptions.turnScreenOff}" +
                    ", maxSize=${resolvedOptions.maxSize}, maxFps=${resolvedOptions.maxFps}"
        )
        snackbar.show("scrcpy 已启动")
    }

    suspend fun stopScrcpySession() {
        val activeBundle = resolveScrcpyBundle(connectedScrcpyProfileId)
        val options = scrcpyOptions.toClientOptions(activeBundle).fix()
        scrcpy.stop()
        if (options.killAdbOnClose) {
            // TODO
            disconnectAdbConnection()
        }
    }

    LaunchedEffect(pendingScrollToPreview, isPreviewCardVisible) {
        if (!pendingScrollToPreview) return@LaunchedEffect
        if (isPreviewCardVisible) return@LaunchedEffect
        listState.animateScrollToItem(PREVIEW_CARD_ITEM_INDEX)
    }

    suspend fun handleAdbConnected(
        host: String, port: Int,
        autoStartScrcpy: Boolean = false,
        autoEnterFullScreen: Boolean = false,
        scrcpyProfileId: String = ScrcpyOptions.GLOBAL_PROFILE_ID,
    ) {
        val target = ConnectionTarget(host, port)
        adbSession = adbSession.copy(
            isConnected = true,
            currentTarget = target,
            connectedScrcpyProfileId = scrcpyProfileId,
            statusLine = "$host:$port",
        )
        AppRuntime.currentConnectionTarget = target

        val info = adbCoordinator.fetchConnectedDeviceInfo(host, port)
        val fullLabel = if (info.serial.isNotBlank()) {
            "${info.model} (${info.serial})"
        } else {
            info.model
        }

        adbSession = adbSession.copy(connectedDeviceLabel = info.model)
        applyConnectedDeviceCapabilities(info.sdkInt, info.androidRelease)
        savedShortcuts = savedShortcuts.update(
            host = host, port = port,
            name = fullLabel,
            updateNameOnlyWhenEmpty = true
        )

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
        if (asBundle.adbAutoLoadAppListOnConnect) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    scrcpy.listings.getApps(forceRefresh = true)
                }.onFailure { error ->
                    logEvent("获取应用列表失败: ${error.message}", Log.WARN, error)
                }
            }
        }

        if (autoStartScrcpy && sessionInfo == null) runBusy("启动 scrcpy") {
            startScrcpySession(openFullscreen = autoStartScrcpy && autoEnterFullScreen)
        }
    }

    LaunchedEffect(
        adbConnected,
        currentTarget?.host,
        currentTarget?.port,
        isAppInForeground,
    ) {
        if (!adbConnected || !isAppInForeground) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                scrcpy.listings.getRecentTasks(forceRefresh = true)
            }.onFailure { error ->
                logEvent("获取最近任务失败: ${error.message}", Log.WARN, error)
            }
        }
    }

    LaunchedEffect(
        adbConnected,
        asBundle.adbAutoReconnectPairedDevice,
        asBundle.adbMdnsLanDiscovery,
        isAppInForeground,
    ) {
        if (adbConnected || !asBundle.adbAutoReconnectPairedDevice) return@LaunchedEffect
        adbBackgroundRunner.runAutoReconnectLoop(
            isConnected = { adbConnected },
            isForeground = { isAppInForeground },
            isAutoReconnectEnabled = { asBundle.adbAutoReconnectPairedDevice },
            isBusy = { busy },
            isAdbConnecting = { adbConnecting },
            hasActiveSession = { sessionInfo != null },
            savedShortcuts = { savedShortcuts.toList() },
            isBlacklisted = { host -> sessionReconnectBlacklistHosts.contains(host) },
            probeTcpReachable = ::probeTcpReachable,
            discoverConnectService = {
                adbCoordinator.discoverConnectService(
                    timeoutMs = ADB_AUTO_RECONNECT_DISCOVER_TIMEOUT_MS,
                    includeLanDevices = asBundle.adbMdnsLanDiscovery,
                )
            },
            onMdnsPortChanged = { host, oldPort, newPort ->
                savedShortcuts = savedShortcuts.update(
                    host = host,
                    port = oldPort,
                    newPort = newPort,
                )
                logEvent("mDNS 发现新端口，已更新快速设备: $host:$oldPort -> $host:$newPort")
            },
            connectKnownShortcut = { target ->
                if (!runAutoAdbConnect(target.host, target.port)) {
                    false
                } else {
                    savedShortcuts = savedShortcuts.update(
                        host = target.host,
                        port = target.port,
                    )
                    handleAdbConnected(
                        target.host,
                        target.port,
                        scrcpyProfileId = target.scrcpyProfileId,
                    )
                    logEvent("ADB 快速探测连接成功: ${target.host}:${target.port}")
                    true
                }
            },
            connectDiscoveredShortcut = { host, port, knownDevice ->
                if (!runAutoAdbConnect(host, port)) {
                    false
                } else {
                    savedShortcuts = savedShortcuts.update(host = host, port = port)
                    handleAdbConnected(
                        host,
                        port,
                        scrcpyProfileId = knownDevice.scrcpyProfileId,
                    )
                    logEvent("ADB 自动重连成功: $host:$port")
                    true
                }
            },
            retryIntervalMs = ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS,
        )
    }

    val adbCallbacks = DeviceTabAdbCallbacks(
        runAdbConnect = { label, onStarted, onFinished, block ->
            runAdbConnect(label, onStarted, onFinished, block)
        },
        runBusy = { label, onFinished, block ->
            runBusy(label, onFinished, block)
        },
        disconnectCurrentTargetBeforeConnecting = { host, port ->
            disconnectCurrentTargetBeforeConnecting(host, port)
        },
        connectWithTimeout = { host, port ->
            connectWithTimeout(host, port)
        },
        handleAdbConnected = { host, port, autoStartScrcpy, autoEnterFullScreen, scrcpyProfileId ->
            handleAdbConnected(
                host = host,
                port = port,
                autoStartScrcpy = autoStartScrcpy,
                autoEnterFullScreen = autoEnterFullScreen,
                scrcpyProfileId = scrcpyProfileId,
            )
        },
        disconnectAdbConnection = { clearQuickOnlineForTarget, logMessage, showSnackMessage ->
            disconnectAdbConnection(
                clearQuickOnlineForTarget = clearQuickOnlineForTarget,
                logMessage = logMessage,
                showSnackMessage = showSnackMessage,
            )
        },
        discoverPairingTarget = {
            adbCoordinator.discoverPairingService(
                includeLanDevices = asBundle.adbMdnsLanDiscovery,
            )
        },
        pairTarget = { host, port, code ->
            val ok = adbCoordinator.pair(host, port, code)
            logEvent(
                if (ok) "配对成功" else "配对失败",
                if (ok) Log.INFO else Log.ERROR
            )
            snackbar.show(if (ok) "配对成功" else "配对失败")
            ok
        },
        isConnectedToTarget = { host, port ->
            adbConnected && currentTarget?.host == host && currentTarget.port == port
        },
        onConnectionFailed = { error ->
            adbSession = adbSession.copy(statusLine = "ADB 连接失败")
            logEvent("ADB 连接失败: $error", Log.ERROR)
            snackbar.show("ADB 连接失败")
        },
        onQuickConnectedChanged = { quickConnected ->
            adbSession = adbSession.copy(isQuickConnected = quickConnected)
        },
        onBlackListHost = { host -> sessionReconnectBlacklistHosts += host },
        setActiveDeviceActionId = { activeDeviceActionId = it },
    )

    // 设备
    LazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
        state = listState,
        bottomInnerPadding = bottomInnerPadding,
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
                onClick = { device ->
                    if (editingDeviceId != device.id)
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
                    if (editingDeviceId == device.id) editingDeviceId = null
                    adbCallbacks.onDeviceAction(device)
                },
                onEditorSave = { device, updated ->
                    savedShortcuts = savedShortcuts.update(
                        id = device.id,
                        name = updated.name,
                        host = updated.host,
                        port = updated.port,
                        startScrcpyOnConnect = updated.startScrcpyOnConnect,
                        openFullscreenOnStart = updated.openFullscreenOnStart,
                        scrcpyProfileId = updated.scrcpyProfileId,
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
                    input = quickConnectInputTemp,
                    onValueChange = {
                        quickConnectInputTemp = it
                    },
                    onFocusLost = {
                        qdBundle = qdBundle.copy(
                            quickConnectInput = quickConnectInputTemp
                        )
                    },
                    enabled = !adbConnecting,
                    onAddDevice = {
                        val target = ConnectionTarget.unmarshalFrom(quickConnectInputTemp)
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
                        val target = ConnectionTarget.unmarshalFrom(quickConnectInputTemp)
                            ?: return@QuickConnectCard

                        adbCallbacks.onQuickConnect(target)
                    },
                )
            }

            item {
                SectionSmallTitle("无线配对")
                // "使用配对码配对设备"
                PairingCard(
                    busy = busy,
                    autoDiscoverOnDialogOpen = asBundle.adbPairingAutoDiscoverOnDialogOpen,
                    onDiscoverTarget = adbCallbacks::onDiscoverPairingTarget,
                    onPair = adbCallbacks::onPair,
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
                    recentTasksEndAction = {
                        Text(
                            text = when {
                                listingsRefreshBusy -> "..."
                                recentTasks.isNotEmpty() -> recentTasks.size.toString()
                                else -> "空"
                            },
                            color = colorScheme.onSurfaceVariantActions,
                            fontSize = textStyles.body2.fontSize,
                            modifier = Modifier.padding(end = UiSpacing.ContentVertical),
                        )
                    },
                    onOpenRecentTasks = {
                        showRecentTasksSheet = true
                        if (recentTasks.isEmpty() && !listingsRefreshBusy) {
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    scrcpy.listings.getRecentTasks(forceRefresh = true)
                                }.onFailure { error ->
                                    logEvent("获取最近任务失败: ${error.message}", Log.WARN, error)
                                    withContext(Dispatchers.Main) {
                                        snackbar.show("获取最近任务失败")
                                    }
                                }
                            }
                        }
                    },
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
                            AppWakeLocks.release()
                            adbSession = adbSession.copy(
                                statusLine = "${currentTarget!!.host}:${currentTarget.port}"
                            )
                            logEvent("scrcpy 已停止")
                            snackbar.show("scrcpy 已停止")
                        }
                    },
                    sessionInfo = sessionInfo,
                    onDisconnect = { adbCallbacks.onDisconnectCurrent(currentTarget) },
                )
            }

            if (
                sessionInfo != null &&
                sessionInfo!!.width > 0 &&
                sessionInfo!!.height > 0
            ) {
                item(key = PREVIEW_CARD_ITEM_KEY) {
                    PreviewCard(
                        modifier = Modifier,
                        sessionInfo = sessionInfo,
                        previewHeightDp = asBundle.devicePreviewCardHeightDp.coerceAtLeast(120),
                        onOpenFullscreen = {
                            context.startActivity(StreamActivity.createIntent(context))
                        },
                        autoBringIntoView = pendingScrollToPreview,
                        onAutoBringIntoViewConsumed = { pendingScrollToPreview = false },
                    )
                }

                item {
                    VirtualButtonCard(
                        busy = busy,
                        outsideActions = virtualButtonLayout.first,
                        moreActions = virtualButtonLayout.second,
                        showText = asBundle.previewVirtualButtonShowText,
                        onAction = { action ->
                            if (action == VirtualButtonAction.PASSWORD_INPUT) {
                                snackbar.show("当前页面无法拉起验证")
                                return@VirtualButtonCard
                            }
                            val keycode = action.keycode ?: return@VirtualButtonCard
                            runBusy("发送 ${action.title}") {
                                scrcpy.injectKeycode(0, keycode)
                                scrcpy.injectKeycode(1, keycode)
                            }
                        },
                        passwordPopupContent =
                            if (fragmentActivity == null) null
                            else { onDismissRequest ->
                                PasswordPickerPopupContent(
                                    onDismissRequest = onDismissRequest,
                                    onMessage = { message ->
                                        scope.launch { snackbar.show(message) }
                                    },
                                )
                            },
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

    }

    RecentTasksBottomSheet(
        show = showRecentTasksSheet,
        tasks = recentTasks,
        scrcpy = scrcpy,
        refreshBusy = listingsRefreshBusy,
        onDismissRequest = { showRecentTasksSheet = false },
        onRefresh = {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    scrcpy.listings.getRecentTasks(forceRefresh = true)
                }.onFailure { error ->
                    logEvent("获取最近任务失败: ${error.message}", Log.WARN, error)
                    withContext(Dispatchers.Main) {
                        snackbar.show("获取最近任务失败")
                    }
                }
            }
        },
        onLaunchTask = { task ->
            showRecentTasksSheet = false
            if (sessionInfo == null) runBusy("启动 scrcpy") {
                startScrcpySession(startAppOverride = task.packageName)
            }
            else runBusy("启动应用") {
                adbCoordinator.startApp(packageName = task.packageName)
                logEvent("已启动应用: ${task.packageName}")
            }
        },
    )
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
private fun RecentTasksBottomSheet(
    show: Boolean,
    tasks: List<Scrcpy.RecentTaskInfo>,
    scrcpy: Scrcpy,
    refreshBusy: Boolean,
    onDismissRequest: () -> Unit,
    onRefresh: () -> Unit,
    onLaunchTask: (Scrcpy.RecentTaskInfo) -> Unit,
) {
    val spinnerColors = SpinnerDefaults.spinnerColors()
    OverlayBottomSheet(
        show = show,
        title = "最近任务",
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
        endAction = {
            IconButton(
                onClick = {
                    if (!refreshBusy) {
                        onRefresh()
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "刷新最近任务",
                )
            }
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
        ) {
            when {
                tasks.isEmpty() && refreshBusy -> {
                    Text(
                        text = "最近任务加载中",
                        modifier = Modifier.padding(horizontal = UiSpacing.Large),
                    )
                }

                tasks.isEmpty() -> {
                    Text(
                        text = "没有可用的最近任务",
                        modifier = Modifier.padding(horizontal = UiSpacing.Large),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                    ) {
                        items(tasks.size) { index ->
                            val task = tasks[index]
                            val app = scrcpy.listings.findCachedApp(task.packageName)
                            val entry = SpinnerEntry(
                                icon = app?.system?.let { system ->
                                    { modifier ->
                                        Icon(
                                            imageVector =
                                                if (system) Icons.Rounded.Android // MiuixIcons.Tune
                                                else MiuixIcons.Store,
                                            contentDescription = task.packageName,
                                            modifier = modifier,
                                        )
                                    }
                                },
                                title = app?.label?.takeIf { it.isNotBlank() } ?: task.packageName,
                                summary = app?.let { task.packageName },
                            )
                            RecentTaskSheetItem(
                                entry = entry,
                                entryCount = tasks.size,
                                index = index,
                                spinnerColors = spinnerColors,
                                onClick = { onLaunchTask(task) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(UiSpacing.SheetBottom))
        }
    }
}

@Composable
private fun RecentTaskSheetItem(
    entry: SpinnerEntry,
    entryCount: Int,
    index: Int,
    spinnerColors: SpinnerColors,
    onClick: () -> Unit,
) {
    val additionalTopPadding = if (index == 0) 20.dp else 12.dp
    val additionalBottomPadding = if (index == entryCount - 1) 20.dp else 12.dp
    val checkColorFilter = remember {
        BlendModeColorFilter(Color.Transparent, BlendMode.SrcIn)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(spinnerColors.containerColor) }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp)
            .padding(top = additionalTopPadding, bottom = additionalBottomPadding),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            entry.icon?.invoke(
                Modifier
                    .sizeIn(minWidth = 26.dp, minHeight = 26.dp)
                    .padding(end = 12.dp)
            )
            Column {
                entry.title?.let {
                    Text(
                        text = it,
                        fontSize = textStyles.body1.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = spinnerColors.contentColor,
                    )
                }
                entry.summary?.let {
                    Text(
                        text = it,
                        fontSize = textStyles.body2.fontSize,
                        color = spinnerColors.summaryColor,
                    )
                }
            }
        }
        Image(
            imageVector = MiuixIcons.Basic.Check,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 12.dp)
                .sizeIn(minWidth = 20.dp, minHeight = 20.dp),
            colorFilter = checkColorFilter,
        )
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
        fontSize = textStyles.body1.fontSize,
        fontWeight = FontWeight.Medium,
        color = colorScheme.disabledOnSecondaryVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiSpacing.PopupHorizontal)
            .padding(top = additionalTopPadding, bottom = additionalBottomPadding),
    )
}
