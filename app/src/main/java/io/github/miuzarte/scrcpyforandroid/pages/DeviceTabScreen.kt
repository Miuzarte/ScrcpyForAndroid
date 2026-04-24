package io.github.miuzarte.scrcpyforandroid.pages

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.miuzarte.scrcpyforandroid.StreamActivity
import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcut
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcuts
import io.github.miuzarte.scrcpyforandroid.password.PasswordPickerPopupContent
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.SectionSmallTitle
import io.github.miuzarte.scrcpyforandroid.scrcpy.ClientOptions
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.services.AppScreenOn
import io.github.miuzarte.scrcpyforandroid.services.ConnectionController
import io.github.miuzarte.scrcpyforandroid.services.ConnectionStateStore
import io.github.miuzarte.scrcpyforandroid.services.DeviceAdbAutoReconnectManager
import io.github.miuzarte.scrcpyforandroid.services.DeviceAdbConnectionCoordinator
import io.github.miuzarte.scrcpyforandroid.services.DisconnectCause
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
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import io.github.miuzarte.scrcpyforandroid.widgets.AppListBottomSheet
import io.github.miuzarte.scrcpyforandroid.widgets.AppListEntry
import io.github.miuzarte.scrcpyforandroid.widgets.ConfigPanel
import io.github.miuzarte.scrcpyforandroid.widgets.DeviceTileList
import io.github.miuzarte.scrcpyforandroid.widgets.PairingCard
import io.github.miuzarte.scrcpyforandroid.widgets.PopupMenuItem
import io.github.miuzarte.scrcpyforandroid.widgets.PreviewCard
import io.github.miuzarte.scrcpyforandroid.widgets.QuickConnectCard
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

private const val ADB_CONNECT_TIMEOUT_MS = 12_000L
private const val ADB_KEEPALIVE_INTERVAL_MS = 3_000L
private const val ADB_KEEPALIVE_TIMEOUT_MS = 1_500L
private const val ADB_AUTO_RECONNECT_DISCOVER_TIMEOUT_MS = 2_000L
private const val ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS = 2_000L
private const val ADB_TCP_PROBE_TIMEOUT_MS = 500
private const val PREVIEW_CARD_ITEM_KEY = "preview_card"
private const val PREVIEW_CARD_ITEM_INDEX = 3

internal data class DeviceConnectionServices(
    val adbCoordinator: DeviceAdbConnectionCoordinator,
    val connectionStateStore: ConnectionStateStore,
    val connectionController: ConnectionController,
    val autoReconnectManager: DeviceAdbAutoReconnectManager,
)

@Composable
internal fun DeviceTabScreen(
    scrollBehavior: ScrollBehavior,
    scrcpy: Scrcpy,
    connectionServices: DeviceConnectionServices,
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
                        Box {
                            IconButton(
                                onClick = { showThreePointMenu = true },
                                holdDownState = showThreePointMenu,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.More,
                                    contentDescription = "更多"
                                )
                            }
                            OverlayListPopup(
                                show = showThreePointMenu,
                                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                alignment = PopupPositionProvider.Align.TopEnd,
                                onDismissRequest = { showThreePointMenu = false },
                            ) {
                                ListPopupColumn {
                                    PopupMenuItem(
                                        text = "快速设备排序",
                                        optionSize = 3,
                                        index = 0,
                                        onSelectedIndexChange = {
                                            onOpenReorderDevices()
                                            showThreePointMenu = false
                                        },
                                    )
                                    PopupMenuItem(
                                        text = "虚拟按钮排序",
                                        optionSize = 3,
                                        index = 1,
                                        onSelectedIndexChange = {
                                            navigator.push(RootScreen.VirtualButtonOrder)
                                            showThreePointMenu = false
                                        },
                                    )
                                    PopupMenuItem(
                                        text = "清空日志",
                                        optionSize = 3,
                                        index = 2,
                                        enabled = EventLogger.hasLogs(),
                                        onSelectedIndexChange = {
                                            EventLogger.clearLogs()
                                            showThreePointMenu = false
                                        },
                                    )
                                }
                            }
                        }
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
                connectionServices = connectionServices,
                bottomInnerPadding = bottomInnerPadding,
            )
        }
    }
}

@Composable
internal fun DeviceTabPage(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    scrcpy: Scrcpy,
    connectionServices: DeviceConnectionServices,
    bottomInnerPadding: Dp,
) {
    val activity = LocalActivity.current
    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    val taskScope = remember { CoroutineScope(Dispatchers.IO + SupervisorJob()) }
    val adbCoordinator = connectionServices.adbCoordinator
    val connectionStateStore = connectionServices.connectionStateStore
    val connectionController = connectionServices.connectionController
    val autoReconnectManager = connectionServices.autoReconnectManager
    val lifecycleOwner = LocalLifecycleOwner.current

    val haptic = LocalHapticFeedback.current
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
    val connectionState by connectionStateStore.state.collectAsState()
    val adbSession = connectionState.adbSession
    val sessionInfo by scrcpy.currentSessionState.collectAsState()
    val listingsRefreshBusy by scrcpy.listings.refreshBusyState.collectAsState()
    val listingsRefreshVersion by scrcpy.listings.refreshVersionState.collectAsState()
    var editingDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeDeviceActionId by rememberSaveable { mutableStateOf<String?>(null) }
    var adbConnecting by rememberSaveable { mutableStateOf(false) }
    var pendingScrollToPreview by rememberSaveable { mutableStateOf(false) }
    var showRecentTasksSheet by rememberSaveable { mutableStateOf(false) }
    var showAllAppsSheet by rememberSaveable { mutableStateOf(false) }
    var imeRequestToken by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }
    val isPreviewCardVisible by remember(listState) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == PREVIEW_CARD_ITEM_KEY }
        }
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

    fun resolveScrcpyBundle(profileId: String): ScrcpyOptions.Bundle {
        if (profileId == ScrcpyOptions.GLOBAL_PROFILE_ID) {
            return soBundleShared
        }
        return scrcpyProfilesState.profiles.firstOrNull { it.id == profileId }?.bundle
            ?: soBundleShared
    }

    val adbConnected = adbSession.isConnected
    val statusLine = adbSession.statusLine
    val isQuickConnected = adbSession.isQuickConnected
    val currentTarget = adbSession.currentTarget
    val connectedDeviceLabel = adbSession.connectedDeviceLabel
    val connectedScrcpyProfileId =
        if (adbConnected && currentTarget != null)
            savedShortcuts.get(currentTarget.host, currentTarget.port)
                ?.scrcpyProfileId
                ?: adbSession.connectedScrcpyProfileId
        else
            adbSession.connectedScrcpyProfileId

    val connectedScrcpyBundle = resolveScrcpyBundle(connectedScrcpyProfileId)
    val connectedVideoPlaybackEnabled =
        connectedScrcpyBundle.video && connectedScrcpyBundle.videoPlayback
    val connectedScrcpyProfileName = remember(connectedScrcpyProfileId, scrcpyProfilesState) {
        scrcpyProfilesState.profiles
            .firstOrNull { it.id == connectedScrcpyProfileId }
            ?.name
            ?: ScrcpyOptions.GLOBAL_PROFILE_NAME
    }
    val apps = remember(listingsRefreshVersion) { scrcpy.listings.apps }
    val recentTasks = remember(listingsRefreshVersion) { scrcpy.listings.recentTasks }

    val sessionReconnectBlacklistHosts = remember { mutableSetOf<String>() }

    val virtualButtonLayout = remember(asBundle.virtualButtonsLayout) {
        VirtualButtonActions.splitLayout(
            VirtualButtonActions.parseStoredLayout(asBundle.virtualButtonsLayout)
        )
    }

    suspend fun refreshApps() {
        runCatching {
            scrcpy.listings.getApps(forceRefresh = true)
        }.onFailure { error ->
            logEvent("获取应用列表失败: ${error.message}", Log.WARN, error)
            withContext(Dispatchers.Main) {
                snackbar.show("获取应用列表失败")
            }
        }
    }

    suspend fun refreshRecentTasks() {
        runCatching {
            scrcpy.listings.getRecentTasks(forceRefresh = true)
        }.onFailure { error ->
            logEvent("获取最近任务失败: ${error.message}", Log.WARN, error)
            withContext(Dispatchers.Main) {
                snackbar.show("获取最近任务失败")
            }
        }
    }

    suspend fun pasteLocalClipboard() {
        val text =
            io.github.miuzarte.scrcpyforandroid.services.LocalInputService.getClipboardText(context)
                ?.takeIf { it.isNotBlank() }
        if (text == null) {
            snackbar.show("本机剪贴板为空或不是文本")
            return
        }
        val useLegacyPaste = connectedScrcpyBundle.legacyPaste
        runCatching {
            withContext(Dispatchers.IO) {
                if (useLegacyPaste) {
                    scrcpy.injectText(text)
                } else {
                    scrcpy.setClipboard(text, paste = true)
                }
            }
            logEvent(
                if (useLegacyPaste) "已使用 legacy paste 注入本机剪贴板文本"
                else "已同步本机剪贴板到设备并触发粘贴"
            )
        }.onFailure { error ->
            logEvent("本机剪贴板粘贴失败: ${error.message}", Log.WARN, error)
            snackbar.show(
                if (useLegacyPaste) "legacy 粘贴失败"
                else "剪贴板同步粘贴失败，可尝试开启 --legacy-paste"
            )
        }
    }

    suspend fun commitImeText(text: String) {
        submitImeText(
            scrcpy = scrcpy,
            text = text,
            keyInjectMode = scrcpyOptions.toClientOptions(connectedScrcpyBundle).keyInjectMode,
        ) { error, useClipboardPaste ->
            logEvent("输入法文本提交失败: ${error.message}", Log.WARN, error)
            snackbar.show(
                if (useClipboardPaste) "非 ASCII 文本粘贴失败"
                else "文本输入失败"
            )
        }
    }

    var quickConnectInputTemp by rememberSaveable(qdBundle.quickConnectInput) {
        mutableStateOf(qdBundle.quickConnectInput)
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
        cause: DisconnectCause = DisconnectCause.User,
        statusLine: String = "未连接",
    ) {
        val result = connectionController.disconnectAdbConnection(
            clearQuickOnlineForTarget = clearQuickOnlineForTarget,
            cause = cause,
            statusLine = statusLine,
        )
        val targetToClear = result.clearedTarget
        targetToClear?.let { target ->
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
        val disconnected = connectionController.disconnectCurrentTargetBeforeConnecting(
            newHost = newHost,
            newPort = newPort,
        ) ?: return

        sessionReconnectBlacklistHosts += disconnected.host
        if (disconnected.host.isNotBlank()) {
            savedShortcuts = savedShortcuts.update(
                host = disconnected.host,
                port = disconnected.port,
            )
        }
    }

    fun applyConnectedDeviceCapabilities(sdkInt: Int, release: String) {
        val currentBundle = resolveScrcpyBundle(connectedScrcpyProfileId)
        val audioSupported = sdkInt !in 0..<30
        connectionController.applyConnectedDeviceCapabilities(sdkInt)
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
        connectionController.connectWithTimeout(host, port, ADB_CONNECT_TIMEOUT_MS)
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
        scope.launch {
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
        scope.launch {
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

    LaunchedEffect(adbConnected, currentTarget, isAppInForeground) {
        if (!adbConnected || currentTarget == null) return@LaunchedEffect
        autoReconnectManager.runKeepAliveLoop(
            isForeground = { isAppInForeground },
            intervalMs = ADB_KEEPALIVE_INTERVAL_MS,
            connectTimeoutMs = ADB_CONNECT_TIMEOUT_MS,
            keepAliveTimeoutMs = ADB_KEEPALIVE_TIMEOUT_MS,
            onReconnectSuccess = { host, port ->
                logEvent("ADB 自动重连成功: $host:$port")
                snackbar.show("ADB 自动重连成功")
            },
            onReconnectFailure = { error ->
                disconnectAdbConnection(
                    cause = DisconnectCause.KeepAliveFailed,
                    statusLine = "ADB 连接断开",
                )
                logEvent("ADB 自动重连失败: $error", Log.ERROR)
                snackbar.show("ADB 自动重连失败")
            },
        )
    }

    LaunchedEffect(adbConnected, currentTarget?.host, currentTarget?.port, savedShortcuts) {
        val target = currentTarget ?: return@LaunchedEffect
        if (!adbConnected) return@LaunchedEffect
        val boundProfileId = savedShortcuts
            .get(target.host, target.port)
            ?.scrcpyProfileId
            ?: ScrcpyOptions.GLOBAL_PROFILE_ID
        if (boundProfileId != adbSession.connectedScrcpyProfileId) {
            connectionController.syncConnectedScrcpyProfileId(boundProfileId)
            logEvent("当前连接设备已切换为配置: $boundProfileId")
        }
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
        pendingScrollToPreview = resolvedOptions.video && resolvedOptions.videoPlayback
        if (resolvedOptions.startApp.isNotBlank() && resolvedOptions.control) {
            runCatching {
                scrcpy.startApp(resolvedOptions.startApp)
            }.onSuccess {
                logEvent("已请求 scrcpy 启动应用: ${resolvedOptions.startApp}")
            }.onFailure { error ->
                logEvent(
                    "通过 scrcpy 控制通道启动应用失败: " +
                            "${error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName}",
                    Log.WARN,
                    error,
                )
            }
        }
        if ((resolvedOptions.fullscreen || openFullscreen) &&
            resolvedOptions.video &&
            resolvedOptions.videoPlayback
        ) withContext(Dispatchers.Main) {
            context.startActivity(StreamActivity.createIntent(context))
        }
        if (resolvedOptions.disableScreensaver)
            AppScreenOn.acquire()

        connectionController.markScrcpyStarted()
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
        snackbar.show(
            "scrcpy 已启动" +
                    if (resolvedOptions.recordFilename.isNotBlank()) "并开始录制"
                    else ""
        )
    }

    suspend fun stopScrcpySession() {
        val activeBundle = resolveScrcpyBundle(connectedScrcpyProfileId)
        val options = scrcpyOptions.toClientOptions(activeBundle).fix()
        if (options.killAdbOnClose) {
            currentTarget?.host?.let { sessionReconnectBlacklistHosts += it }
            val stopResult = connectionController.stopScrcpySession(killAdbOnClose = true)
            stopResult.clearedTarget?.let { target ->
                if (target.host.isNotBlank()) {
                    savedShortcuts = savedShortcuts.update(
                        host = target.host,
                        port = target.port,
                    )
                }
            }
            logEvent("scrcpy 已停止，ADB 已断开")
            snackbar.show("scrcpy 已停止，ADB 已断开")
        } else {
            connectionController.stopScrcpySession(killAdbOnClose = false)
            logEvent("scrcpy 已停止")
            snackbar.show("scrcpy 已停止")
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
        val connected = connectionController.handleAdbConnected(
            host = host,
            port = port,
            scrcpyProfileId = scrcpyProfileId,
        )
        val target = connected.target
        val info = connected.info
        val fullLabel = if (info.serial.isNotBlank()) {
            "${info.model} (${info.serial})"
        } else {
            info.model
        }

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
        autoReconnectManager.runAutoReconnectLoop(
            isForeground = { isAppInForeground },
            isAutoReconnectEnabled = { asBundle.adbAutoReconnectPairedDevice },
            isBusy = { busy },
            isAdbConnecting = { adbConnecting },
            hasActiveSession = { sessionInfo != null },
            savedShortcuts = { savedShortcuts.toList() },
            isBlacklisted = { host -> sessionReconnectBlacklistHosts.contains(host) },
            connectTimeoutMs = ADB_CONNECT_TIMEOUT_MS,
            probeTimeoutMs = ADB_TCP_PROBE_TIMEOUT_MS,
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
            onKnownDeviceReconnected = { target ->
                savedShortcuts = savedShortcuts.update(
                    host = target.host,
                    port = target.port,
                )
                logEvent("ADB 快速探测连接成功: ${target.host}:${target.port}")
            },
            onDiscoveredDeviceReconnected = { host, port, _ ->
                savedShortcuts = savedShortcuts.update(host = host, port = port)
                logEvent("ADB 自动重连成功: $host:$port")
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
            connectionController.markConnectionFailed(error)
            logEvent("ADB 连接失败: $error", Log.ERROR)
            snackbar.show("ADB 连接失败")
        },
        onQuickConnectedChanged = { quickConnected ->
            connectionController.updateQuickConnected(quickConnected)
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
                    haptic.contextClick()
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
                    activeProfileId = connectedScrcpyProfileId,
                    activeBundle = connectedScrcpyBundle,
                    hideSimpleConfigItems = asBundle.hideSimpleConfigItems,
                    audioForwardingSupported = adbSession.audioForwardingSupported,
                    cameraMirroringSupported = adbSession.cameraMirroringSupported,
                    adbConnecting = adbConnecting,
                    isQuickConnected = isQuickConnected,
                    advancedEndActionText = connectedScrcpyProfileName,
                    allAppsEndActionText = when {
                        listingsRefreshBusy -> "..."
                        apps.isNotEmpty() -> apps.size.toString()
                        else -> "空"
                    },
                    onOpenAllApps = {
                        showAllAppsSheet = true
                        if (apps.isEmpty() && !listingsRefreshBusy) {
                            scope.launch(Dispatchers.IO) { refreshApps() }
                        }
                    },
                    recentTasksEndActionText = when {
                        listingsRefreshBusy -> "..."
                        recentTasks.isNotEmpty() -> recentTasks.size.toString()
                        else -> "空"
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
                    onStartStopHaptic = haptic::contextClick,
                    onStart = {
                        runBusy("启动 scrcpy") {
                            startScrcpySession()
                        }
                    },
                    onStop = {
                        runBusy("停止 scrcpy") {
                            stopScrcpySession()
                        }
                    },
                    sessionInfo = sessionInfo,
                    onDisconnect = { adbCallbacks.onDisconnectCurrent(currentTarget) },
                )
            }

            if (
                connectedVideoPlaybackEnabled &&
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
                        imeRequestToken = imeRequestToken,
                        onImeCommitText = { text -> commitImeText(text) },
                        onImeDeleteSurroundingText = { beforeLength, _ ->
                            submitImeDeleteSurroundingText(
                                scrcpy = scrcpy,
                                beforeLength = beforeLength,
                                afterLength = 0,
                            )
                        },
                        onImeKeyEvent = { event ->
                            submitImeKeyEvent(
                                scrcpy = scrcpy,
                                event = event,
                                keyInjectMode = sessionInfo?.keyInjectMode
                                    ?: ClientOptions.KeyInjectMode.MIXED,
                                forwardKeyRepeat = sessionInfo?.forwardKeyRepeat ?: true,
                            )
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
                            when (action) {
                                VirtualButtonAction.RECENT_TASKS -> {
                                    showRecentTasksSheet = true
                                    if (recentTasks.isEmpty() && !listingsRefreshBusy) {
                                        scope.launch(Dispatchers.IO) { refreshApps() }
                                        scope.launch(Dispatchers.IO) { refreshRecentTasks() }
                                    }
                                }

                                VirtualButtonAction.ALL_APPS -> {
                                    showAllAppsSheet = true
                                    if (apps.isEmpty() && !listingsRefreshBusy) {
                                        scope.launch(Dispatchers.IO) { refreshApps() }
                                    }
                                }

                                VirtualButtonAction.TOGGLE_IME -> {
                                    imeRequestToken++
                                }

                                VirtualButtonAction.PASTE_LOCAL_CLIPBOARD -> {
                                    scope.launch { pasteLocalClipboard() }
                                }

                                else -> {
                                    val keycode = action.keycode ?: return@VirtualButtonCard
                                    runBusy("发送 ${action.title}") {
                                        scrcpy.injectKeycode(0, keycode)
                                        scrcpy.injectKeycode(1, keycode)
                                    }
                                }
                            }
                        },
                        passwordPopupContent = { onDismissRequest ->
                            PasswordPickerPopupContent(
                                onDismissRequest = onDismissRequest,
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

    AppListBottomSheet(
        show = showRecentTasksSheet,
        title = "最近任务",
        loadingText = "最近任务加载中",
        emptyText = "没有可用的最近任务",
        entries = recentTasks.map { task ->
            val app = scrcpy.listings.findCachedApp(task.packageName)
            AppListEntry(
                key = task.packageName,
                title = app?.label?.takeIf { it.isNotBlank() } ?: task.packageName,
                summary = if (app?.label != null) task.packageName else null,
                system = app?.system,
                onClick = {
                    showRecentTasksSheet = false
                    if (sessionInfo == null) runBusy("启动 scrcpy") {
                        startScrcpySession(startAppOverride = task.packageName)
                    }
                    else runBusy("启动应用") {
                        runCatching {
                            scrcpy.startApp(task.packageName)
                        }.onSuccess {
                            logEvent("已在当前显示启动应用: ${task.packageName}")
                        }.onFailure { error ->
                            snackbar.show("通过 scrcpy 控制通道启动应用失败，回退 ADB")
                            logEvent(
                                "通过 scrcpy 控制通道启动应用失败，回退 ADB" +
                                        ": ${error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName}",
                                Log.WARN,
                                error,
                            )
                            adbCoordinator.startApp(packageName = task.packageName)
                            logEvent("已通过 ADB 启动应用: ${task.packageName}")
                        }
                    }
                },
            )
        },
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
    )

    AppListBottomSheet(
        show = showAllAppsSheet,
        title = "所有应用",
        loadingText = "应用列表加载中",
        emptyText = "没有可用的应用列表",
        entries = apps.map { app ->
            AppListEntry(
                key = app.packageName,
                title = app.label?.takeIf { it.isNotBlank() } ?: app.packageName,
                summary = if (app.label != null) app.packageName else null,
                system = app.system,
                onClick = {
                    showAllAppsSheet = false
                    if (sessionInfo == null) {
                        runBusy("启动 scrcpy") {
                            startScrcpySession(startAppOverride = app.packageName)
                        }
                    } else {
                        runBusy("启动应用") {
                            runCatching {
                                scrcpy.startApp(app.packageName)
                            }.onSuccess {
                                logEvent("已在当前显示启动应用: ${app.packageName}")
                            }.onFailure { error ->
                                snackbar.show("通过 scrcpy 控制通道启动应用失败，回退 ADB")
                                logEvent(
                                    "通过 scrcpy 控制通道启动应用失败，回退 ADB" +
                                            ": ${error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName}",
                                    Log.WARN,
                                    error,
                                )
                                adbCoordinator.startApp(packageName = app.packageName)
                                logEvent("已通过 ADB 启动应用: ${app.packageName}")
                            }
                        }
                    }
                },
            )
        },
        refreshBusy = listingsRefreshBusy,
        onDismissRequest = { showAllAppsSheet = false },
        onRefresh = { scope.launch(Dispatchers.IO) { refreshApps() } },
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
            PopupMenuItem(
                text = "快速设备排序",
                optionSize = 3,
                index = 0,
                onSelectedIndexChange = { onReorderDevices() },
            )
            PopupMenuItem(
                text = "虚拟按钮排序",
                optionSize = 3,
                index = 1,
                onSelectedIndexChange = { onOpenVirtualButtonOrder() },
            )
            PopupMenuItem(
                text = "清空日志",
                optionSize = 3,
                index = 2,
                enabled = canClearLogs,
                onSelectedIndexChange = { onClearLogs() },
            )
        }
    }
}

