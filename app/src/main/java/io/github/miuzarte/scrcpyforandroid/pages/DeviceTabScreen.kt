package io.github.miuzarte.scrcpyforandroid.pages

import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.models.DeviceConnectionType
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcut
import io.github.miuzarte.scrcpyforandroid.nativecore.UsbAdbDeviceWatcher
import io.github.miuzarte.scrcpyforandroid.nativecore.UsbDeviceEvent
import io.github.miuzarte.scrcpyforandroid.password.PasswordPickerPopupContent
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.SectionSmallTitle
import io.github.miuzarte.scrcpyforandroid.scrcpy.ClientOptions
import io.github.miuzarte.scrcpyforandroid.services.*
import io.github.miuzarte.scrcpyforandroid.storage.Storage.scrcpyProfiles
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import io.github.miuzarte.scrcpyforandroid.widgets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

private const val PREVIEW_CARD_ITEM_KEY = "preview_card"
private const val PREVIEW_CARD_ITEM_INDEX = 3
private val DEVICE_TWO_PANE_CONFIG_MAX_WIDTH = 640.dp

internal data class DeviceConnectionServices(
    val adbCoordinator: DeviceAdbConnectionCoordinator,
    val connectionStateStore: ConnectionStateStore,
    val connectionController: ConnectionController,
    val autoReconnectManager: DeviceAdbAutoReconnectManager,
)

@Composable
internal fun DeviceTabScreen(
    viewModelFactory: ViewModelProvider.Factory,
    scrollBehavior: ScrollBehavior,
    bottomInnerPadding: Dp,
    onOpenReorderDevices: () -> Unit,
    onPreviewGestureLockChanged: (Boolean) -> Unit = {},
    onOpenFullscreenCompat: () -> Unit = {},
) {
    val viewModel: DeviceTabViewModel = viewModel(factory = viewModelFactory)

    val navigator = LocalRootNavigator.current
    val haptic = LocalHapticFeedback.current
    var useCompactTopAppBar by remember { mutableStateOf(false) }
    var showTwoPaneSideAction by remember { mutableStateOf(false) }
    var configPanelOnLeft by remember { mutableStateOf(true) }
    var twoPaneSideToggleRequest by remember { mutableIntStateOf(0) }
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                val topAppBarColor =
                    if (blurActive) Color.Transparent
                    else colorScheme.surface
                val topAppBarActions: @Composable RowScope.() -> Unit = {
                    if (showTwoPaneSideAction) {
                        IconButton(
                            onClick = {
                                haptic.contextClick()
                                twoPaneSideToggleRequest++
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = stringResource(
                                    if (configPanelOnLeft) R.string.device_cd_config_right
                                    else R.string.device_cd_config_left,
                                ),
                            )
                        }
                    }
                    OverlayIconDropdownMenu(
                        entry = DropdownEntry(
                            items = listOf(
                                DropdownItem(
                                    text = stringResource(R.string.device_menu_quick_sort),
                                    onClick = {
                                        onOpenReorderDevices()
                                    },
                                ),
                                DropdownItem(
                                    text = stringResource(R.string.device_menu_virtual_button_sort),
                                    onClick = {
                                        navigator.push(RootScreen.VirtualButtonOrder)
                                    },
                                ),
                                DropdownItem(
                                    text = stringResource(R.string.device_menu_clear_logs),
                                    enabled = EventLogger.hasLogs(),
                                    onClick = {
                                        EventLogger.clearLogs()
                                    },
                                ),
                            ),
                        ),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.More,
                            contentDescription = stringResource(R.string.cd_more),
                        )
                    }
                }
                if (useCompactTopAppBar) SmallTopAppBar(
                    title = stringResource(R.string.device_title),
                    color = topAppBarColor,
                    actions = topAppBarActions,
                )
                else TopAppBar(
                    title = stringResource(R.string.device_title),
                    color = topAppBarColor,
                    actions = topAppBarActions,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { pagePadding ->
        Box(modifier = if (blurActive) Modifier.layerBackdrop(blurBackdrop) else Modifier) {
            DeviceTabPage(
                viewModel = viewModel,
                contentPadding = pagePadding,
                scrollBehavior = scrollBehavior,
                bottomInnerPadding = bottomInnerPadding,
                twoPaneSideToggleRequest = twoPaneSideToggleRequest,
                onPreviewGestureLockChanged = onPreviewGestureLockChanged,
                onOpenFullscreenCompat = onOpenFullscreenCompat,
                onCompactTopAppBarChanged = { useCompactTopAppBar = it },
                onTwoPaneSideActionChanged = { visible, onLeft ->
                    showTwoPaneSideAction = visible
                    configPanelOnLeft = onLeft
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
internal fun DeviceTabPage(
    viewModel: DeviceTabViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    bottomInnerPadding: Dp,
    twoPaneSideToggleRequest: Int = 0,
    onPreviewGestureLockChanged: (Boolean) -> Unit = {},
    onOpenFullscreenCompat: () -> Unit = {},
    onCompactTopAppBarChanged: (Boolean) -> Unit = {},
    onTwoPaneSideActionChanged: (Boolean, Boolean) -> Unit = { _, _ -> },
) {
    val asBundle by viewModel.asBundle.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val sessionInfo by viewModel.sessionInfo.collectAsState()
    val listingsRefreshBusy by viewModel.listingsRefreshBusy.collectAsState()
    val listingsRefreshVersion by viewModel.listingsRefreshVersion.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val adbConnecting by viewModel.adbConnecting.collectAsState()
    val editingDeviceId by viewModel.editingDeviceId.collectAsState()
    val activeDeviceActionId by viewModel.activeDeviceActionId.collectAsState()
    val showRecentTasksSheet by viewModel.showRecentTasksSheet.collectAsState()
    val showAllAppsSheet by viewModel.showAllAppsSheet.collectAsState()
    val imeRequestToken by viewModel.imeRequestToken.collectAsState()
    val pendingScrollToPreview by viewModel.pendingScrollToPreview.collectAsState()
    val savedShortcuts by viewModel.savedShortcuts.collectAsState()
    val quickConnectInputTemp by viewModel.quickConnectInput.collectAsState()

    val adbConnected by viewModel.adbConnected.collectAsState()
    val statusLine by viewModel.statusLine.collectAsState()
    val isQuickConnected by viewModel.isQuickConnected.collectAsState()
    val currentTarget by viewModel.currentTarget.collectAsState()
    val connectedDeviceLabel by viewModel.connectedDeviceLabel.collectAsState()
    val connectedScrcpyProfileId by viewModel.connectedScrcpyProfileId.collectAsState()
    val connectedScrcpyBundle by viewModel.connectedScrcpyBundle.collectAsState()
    val connectedScrcpyProfileName by viewModel.connectedScrcpyProfileName.collectAsState()
    val scrcpyProfilesState by scrcpyProfiles.state.collectAsState()
    val canShowPreviewControls by viewModel.canShowPreviewControls.collectAsState()
    val virtualButtonLayout by viewModel.virtualButtonLayout.collectAsState()

    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val navigator = LocalRootNavigator.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // USB 设备监听：页面级常驻。若放在 LazyColumn item 内，滚动出屏会 dispose 并
    // 反复注册/注销系统广播，导致插拔事件丢失与状态不一致
    val usbWatcher = remember { UsbAdbDeviceWatcher(context) }
    val usbDevices by usbWatcher.devicesFlow.collectAsState()
    val usbEvents by usbWatcher.eventsFlow.collectAsState()
    LaunchedEffect(Unit) { usbWatcher.startWatching() }
    DisposableEffect(Unit) { onDispose { usbWatcher.stopWatching() } }

    // 设备插拔 Snackbar 提示（OTG 两段枚举会触发多次广播，按事件+设备 500ms 去重）
    val lastUsbEventSnackbarKey = remember { mutableStateOf<Pair<String, Long>?>(null) }
    LaunchedEffect(usbEvents) {
        val evt = usbEvents ?: return@LaunchedEffect
        val dev = when (evt) {
            is UsbDeviceEvent.Attached -> evt.device
            is UsbDeviceEvent.Detached -> evt.device
            else -> return@LaunchedEffect
        }
        val key = "${evt::class.simpleName}:${dev.vendorId}:${dev.productId}:${dev.deviceId}"
        val now = SystemClock.elapsedRealtime()
        val last = lastUsbEventSnackbarKey.value
        if (last?.first != key || now - last.second > 500L) {
            lastUsbEventSnackbarKey.value = key to now
            if (evt is UsbDeviceEvent.Attached) AppRuntime.snackbar(R.string.usb_device_detected)
            else AppRuntime.snackbar(R.string.usb_device_disconnected)
        }
    }

    val apps = remember(listingsRefreshVersion) { viewModel.scrcpyListings.apps }
    val recentTasks = remember(listingsRefreshVersion) { viewModel.scrcpyListings.recentTasks }

    var handledTwoPaneSideToggleRequest by rememberSaveable {
        mutableIntStateOf(twoPaneSideToggleRequest)
    }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val isPreviewCardVisible by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == PREVIEW_CARD_ITEM_KEY } }
    }

    LaunchedEffect(Unit) { viewModel.startKeepAliveLoop() }
    LaunchedEffect(Unit) { viewModel.startAutoReconnectLoop() }
    LaunchedEffect(Unit) { viewModel.startProfileIdSync() }
    LaunchedEffect(Unit) { viewModel.startRecentTasksAutoRefresh() }
    LaunchedEffect(Unit) { viewModel.startConnectionHealthCheckLoop() }

    fun openFullscreenControl() {
        if (viewModel.shouldOpenFullscreenCompat())
            onOpenFullscreenCompat()
        else
            viewModel.openStreamActivity(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            onPreviewGestureLockChanged(false)
            onCompactTopAppBarChanged(false)
            onTwoPaneSideActionChanged(false, true)
        }
    }

    DisposableEffect(lifecycleOwner) {
        viewModel.setAppInForeground(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setAppInForeground(true)
                Lifecycle.Event.ON_STOP -> viewModel.setAppInForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.fullscreenRequests.collect { openFullscreenControl() }
    }

    LaunchedEffect(pendingScrollToPreview, isPreviewCardVisible) {
        if (!pendingScrollToPreview) return@LaunchedEffect
        if (isPreviewCardVisible) return@LaunchedEffect
        listState.animateScrollToItem(PREVIEW_CARD_ITEM_INDEX)
    }

    fun handleVirtualButtonAction(action: VirtualButtonAction) {
        when (action) {
            VirtualButtonAction.RECENT_TASKS -> {
                viewModel.showRecentTasks()
                if (recentTasks.isEmpty() && !listingsRefreshBusy) {
                    scope.launch(Dispatchers.IO) {
                        viewModel.refreshApps()
                    }
                    scope.launch(Dispatchers.IO) {
                        viewModel.refreshRecentTasks()
                    }
                }
            }

            VirtualButtonAction.ALL_APPS -> {
                viewModel.showAllApps()
                if (apps.isEmpty() && !listingsRefreshBusy) {
                    scope.launch(Dispatchers.IO) {
                        viewModel.refreshApps()
                    }
                }
            }

            VirtualButtonAction.TOGGLE_IME -> viewModel.toggleIme()
            VirtualButtonAction.PASTE_LOCAL_CLIPBOARD -> scope.launch {
                viewModel.pasteLocalClipboard(context)
            }

            else -> viewModel.handleVirtualButtonAction(action)
        }
    }

    @Composable
    fun StatusSection() {

        StatusCard(
            statusLine = statusLine,
            adbConnected = adbConnected,
            streaming = sessionInfo != null,
            sessionInfo = sessionInfo,
            busyLabel = null,
            connectedDeviceLabel = connectedDeviceLabel,
        )
    }

    @Composable
    fun UsbDevicesSection() {
        // watcher 与插拔 Snackbar 响应提升至 DeviceTabPage 页面级，此处只消费状态

        // 已断开的设备不显示条目：无设备时整个区块不渲染
        if (usbDevices.isEmpty()) return

        // 与无线 DeviceTileList 相同的容器样式，USB 条目置顶、视觉连续
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
        ) {
            usbDevices.forEach { deviceInfo ->
                val vidPid = String.format(
                    "0x%04X/0x%04X", deviceInfo.device.vendorId, deviceInfo.device.productId,
                )
                val connectedToThis = adbConnected &&
                    currentTarget?.connectionType == DeviceConnectionType.USB &&
                    currentTarget?.host == vidPid
                val actionInProgress = adbConnecting && activeDeviceActionId == vidPid

                // 卡片样式需与 DeviceTile 保持一致（灰白系底色 + Sink 反馈），改无线样式时同步此处
                Card(
                    colors = CardDefaults.defaultColors(
                        color =
                            if (connectedToThis) colorScheme.surfaceContainer
                            else colorScheme.surfaceContainer.copy(alpha = 0.6f),
                    ),
                    pressFeedbackType = PressFeedbackType.Sink,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    haptic.contextClick()
                                    when {
                                        connectedToThis -> Unit
                                        !deviceInfo.hasPermission -> {
                                            AppRuntime.snackbar(R.string.usb_permission_request_hint)
                                            usbWatcher.requestUsbPermission(deviceInfo.device)
                                        }
                                        else -> viewModel.connectUsbDevice(deviceInfo)
                                    }
                                },
                            )
                            .padding(UiSpacing.PageItem),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // status dot（与无线卡片同一套逻辑）
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color =
                                            if (connectedToThis) Color(0xFF44C74F)
                                            else colorScheme.outline,
                                        shape = CircleShape,
                                    ),
                            )
                            Spacer(modifier = Modifier.width(UiSpacing.PageItem))
                            Column {
                                Text(
                                    deviceInfo.getDisplayName(),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = colorScheme.onSurface,
                                )
                                Text(
                                    vidPid,
                                    fontSize = 13.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (actionInProgress) {
                                CircularProgressIndicator(progress = null)
                                Spacer(Modifier.width(UiSpacing.Medium))
                                TextButton(
                                    text = stringResource(R.string.button_cancel),
                                    onClick = {
                                        haptic.contextClick()
                                        viewModel.disconnectUsbDevice()
                                    },
                                    colors = ButtonDefaults.textButtonColors(),
                                )
                            } else {
                                TextButton(
                                    text = stringResource(
                                        when {
                                            connectedToThis -> R.string.button_disconnect
                                            !deviceInfo.hasPermission -> R.string.button_authorize
                                            else -> R.string.button_connect
                                        }
                                    ),
                                    onClick = {
                                        haptic.contextClick()
                                        when {
                                            connectedToThis -> viewModel.disconnectUsbDevice()
                                            !deviceInfo.hasPermission -> {
                                                AppRuntime.snackbar(R.string.usb_permission_request_hint)
                                                usbWatcher.requestUsbPermission(deviceInfo.device)
                                            }
                                            else -> viewModel.connectUsbDevice(deviceInfo)
                                        }
                                    },
                                    enabled = (!busy && !adbConnecting) || connectedToThis,
                                    colors = ButtonDefaults.textButtonColors(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun DeviceListSection() {
        // USB 设备条目置顶，与无线设备构成同一列表
        UsbDevicesSection()
        DeviceTileList(
            devices = savedShortcuts,
            currentTarget = currentTarget,
            isConnected = { device ->
                adbConnected
                        && currentTarget?.let { device.matchesAddress(it) } == true
            },
            actionEnabled = !busy && !adbConnecting,
            actionInProgress = { device -> adbConnecting && activeDeviceActionId == device.id },
            editingDeviceId = editingDeviceId,
            onClick = { device ->
                if (editingDeviceId != device.id)
                    AppRuntime.snackbar(R.string.device_hint_long_press_edit)
            },
            onLongClick = { device ->
                val connected = adbConnected
                        && currentTarget?.let { device.matchesAddress(it) } == true
                if (connected) {
                    AppRuntime.snackbar(R.string.device_cannot_modify_connected)
                } else {
                    viewModel.setEditingDeviceId(
                        if (editingDeviceId != device.id) device.id
                        else null,
                    )
                }
            },
            onAction = { device ->
                haptic.contextClick()
                if (editingDeviceId == device.id) viewModel.setEditingDeviceId(null)
                viewModel.onDeviceAction(device)
            },
            onCancelAction = { device ->
                viewModel.cancelAdbConnect()
            },
            onEditorSave = { device, updated ->
                viewModel.updateShortcut(
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
                viewModel.removeShortcut(device.id)
                viewModel.setEditingDeviceId(null)
            },
            onEditorCancel = { viewModel.setEditingDeviceId(null) },
        )
    }

    @Composable
    fun QuickConnectSection() {
        QuickConnectCard(
            input = quickConnectInputTemp,
            onValueChange = { viewModel.setQuickConnectInput(it) },
            onFocusLost = { viewModel.saveQuickConnectInput() },
            enabled = !adbConnecting,
            connecting = adbConnecting,
            onAddDevice = {
                val target = ConnectionTarget.unmarshalFrom(quickConnectInputTemp)
                    ?: return@QuickConnectCard
                viewModel.upsertShortcut(DeviceShortcut(addresses = listOf(target.toString())))
                AppRuntime.snackbar(R.string.device_added, target.host, target.port)
            },
            onConnect = {
                val target = ConnectionTarget.unmarshalFrom(quickConnectInputTemp)
                    ?: return@QuickConnectCard
                viewModel.onQuickConnect(target)
            },
            onCancelConnect = {
                viewModel.cancelAdbConnect()
            },
        )
    }

    @Composable
    fun PairingSection() {
        SectionSmallTitle(stringResource(R.string.device_section_wireless_pairing))
        PairingCard(
            busy = busy,
            autoDiscoverOnDialogOpen = asBundle.adbPairingAutoDiscoverOnDialogOpen,
            onDiscoverTarget = { viewModel.onDiscoverPairingTarget() },
            onPair = viewModel::onPair,
        )
    }


    @Composable
    fun ScrcpyConfigSection() {
        ConfigPanel(
            busy = busy,
            activeProfileId = connectedScrcpyProfileId,
            activeBundle = connectedScrcpyBundle,
            hideSimpleConfigItems = asBundle.hideSimpleConfigItems,
            audioForwardingSupported = connectionState.adbSession.audioForwardingSupported,
            cameraMirroringSupported = connectionState.adbSession.cameraMirroringSupported,
            adbConnecting = adbConnecting,
            isQuickConnected = isQuickConnected || (adbConnected && currentTarget?.connectionType == DeviceConnectionType.USB),
            advancedEndActionText = connectedScrcpyProfileName,
            allAppsEndActionText = when {
                listingsRefreshBusy -> "..."
                apps.isNotEmpty() -> apps.size.toString()
                else -> stringResource(R.string.text_none)
            },
            onOpenAllApps = {
                viewModel.showAllApps()
                if (apps.isEmpty() && !listingsRefreshBusy)
                    scope.launch(Dispatchers.IO) {
                        viewModel.refreshApps()
                    }
            },
            recentTasksEndActionText = when {
                listingsRefreshBusy -> "..."
                recentTasks.isNotEmpty() -> recentTasks.size.toString()
                else -> stringResource(R.string.text_none)
            },
            onOpenRecentTasks = {
                viewModel.showRecentTasks()
                if (recentTasks.isEmpty() && !listingsRefreshBusy)
                    scope.launch(Dispatchers.IO) {
                        viewModel.refreshRecentTasks()
                    }
            },
            onOpenAdvanced = { navigator.push(RootScreen.Advanced) },
            onStart = viewModel::startScrcpy,
            onStop = viewModel::stopScrcpy,
            sessionInfo = sessionInfo,
            // USB 连接时底部不显示"断开"（断开入口在设备列表条目上），对齐无线行为
            onDisconnect = if (currentTarget?.connectionType == DeviceConnectionType.USB) null
            else {
                { viewModel.onDisconnectCurrent(currentTarget) }
            },
            showFullscreenAction = false,
            onOpenFullscreen = ::openFullscreenControl,
        )
    }

    @Composable
    fun PreviewSection(
        modifier: Modifier = Modifier,
        directControlEnabled: Boolean = false,
    ) {
        PreviewCard(
            modifier = modifier,
            sessionInfo = sessionInfo,
            previewHeightDp = asBundle.devicePreviewCardHeightDp.coerceAtLeast(120),
            onOpenFullscreen = ::openFullscreenControl,
            directControlEnabled = directControlEnabled,
            onInjectTouch = { action, pointerId, x, y, pressure, actionButton, buttons ->
                val session = sessionInfo
                if (session != null) {
                    withContext(Dispatchers.IO) {
                        viewModel.injectTouch(
                            action = action,
                            pointerId = pointerId,
                            x = x,
                            y = y,
                            screenWidth = session.width,
                            screenHeight = session.height,
                            pressure = pressure,
                            actionButton = actionButton,
                            buttons = buttons,
                        )
                    }
                }
            },
            onBackOrScreenOn = { action ->
                withContext(Dispatchers.IO) {
                    viewModel.pressBackOrTurnScreenOn(action)
                }
            },
            imeRequestToken = imeRequestToken,
            onImeCommitText = { text ->
                scope.launch {
                    viewModel.commitImeText(text)
                }
            },
            onImeDeleteSurroundingText = { beforeLength, _ ->
                submitImeDeleteSurroundingText(
                    scrcpy = viewModel.scrcpy,
                    beforeLength = beforeLength,
                    afterLength = 0,
                )
            },
            onImeKeyEvent = { event ->
                submitImeKeyEvent(
                    scrcpy = viewModel.scrcpy,
                    event = event,
                    keyInjectMode = sessionInfo?.keyInjectMode
                        ?: ClientOptions.KeyInjectMode.MIXED,
                    forwardKeyRepeat = sessionInfo?.forwardKeyRepeat ?: true,
                )
            },
            autoBringIntoView = pendingScrollToPreview && !directControlEnabled && !asBundle.previewCardOnTop,
            onAutoBringIntoViewConsumed = { viewModel.clearPendingScrollToPreview() },
            onTouchActiveChanged = {
                if (directControlEnabled) onPreviewGestureLockChanged(it)
            },
        )
    }

    @Composable
    fun ScrcpyConfigSectionForTwoPane() {
        ConfigPanel(
            busy = busy,
            activeProfileId = connectedScrcpyProfileId,
            activeBundle = connectedScrcpyBundle,
            hideSimpleConfigItems = asBundle.hideSimpleConfigItems,
            audioForwardingSupported = connectionState.adbSession.audioForwardingSupported,
            cameraMirroringSupported = connectionState.adbSession.cameraMirroringSupported,
            adbConnecting = adbConnecting,
            isQuickConnected = isQuickConnected || (adbConnected && currentTarget?.connectionType == DeviceConnectionType.USB),
            advancedEndActionText = connectedScrcpyProfileName,
            allAppsEndActionText = when {
                listingsRefreshBusy -> "..."
                apps.isNotEmpty() -> apps.size.toString()
                else -> stringResource(R.string.text_none)
            },
            onOpenAllApps = {
                viewModel.showAllApps()
                if (apps.isEmpty() && !listingsRefreshBusy)
                    scope.launch(Dispatchers.IO) {
                        viewModel.refreshApps()
                    }
            },
            recentTasksEndActionText = when {
                listingsRefreshBusy -> "..."
                recentTasks.isNotEmpty() -> recentTasks.size.toString()
                else -> stringResource(R.string.text_none)
            },
            onOpenRecentTasks = {
                viewModel.showRecentTasks()
                if (recentTasks.isEmpty() && !listingsRefreshBusy)
                    scope.launch(Dispatchers.IO) {
                        viewModel.refreshRecentTasks()
                    }
            },
            onOpenAdvanced = { navigator.push(RootScreen.Advanced) },
            onStart = viewModel::startScrcpy,
            onStop = viewModel::stopScrcpy,
            sessionInfo = sessionInfo,
            // USB 连接时底部不显示"断开"（断开入口在设备列表条目上），对齐无线行为
            onDisconnect = if (currentTarget?.connectionType == DeviceConnectionType.USB) null
            else {
                { viewModel.onDisconnectCurrent(currentTarget) }
            },
            showFullscreenAction = canShowPreviewControls,
            onOpenFullscreen = ::openFullscreenControl,
            reverseSideActions = asBundle.deviceTwoPaneConfigOnRight,
        )
    }

    @Composable
    fun VirtualButtonsSection(modifier: Modifier = Modifier) {
        VirtualButtonCard(
            busy = busy,
            outsideActions = virtualButtonLayout.first,
            moreActions = virtualButtonLayout.second,
            showText = asBundle.previewVirtualButtonShowText,
            onAction = ::handleVirtualButtonAction,
            passwordPopupContent = { onDismissRequest ->
                PasswordPickerPopupContent(onDismissRequest = onDismissRequest)
            },
            popupBottomPadding = bottomInnerPadding,
            modifier = modifier,
        )
    }

    @Composable
    fun LogsSection() {
        if (!asBundle.hideDeviceLogs && EventLogger.hasLogs()) {
            val context = LocalContext.current
            SectionSmallTitle(stringResource(R.string.device_section_logs))
            Card {
                TextField(
                    value = EventLogger.eventLog.joinToString(separator = "\n") {
                        it.render(context)
                    },
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    @Composable
    fun ProfilesTabRow() {
        val profileTabs = remember(scrcpyProfilesState.profiles) {
            scrcpyProfilesState.profiles.map { it.name }
        }
        val profileIds = remember(scrcpyProfilesState.profiles) {
            scrcpyProfilesState.profiles.map { it.id }
        }
        val selectedProfileIndex = remember(connectedScrcpyProfileId, profileIds) {
            profileIds.indexOf(connectedScrcpyProfileId).coerceAtLeast(0)
        }
        val textGlobal = stringResource(R.string.text_global)

        TabRow(
            tabs = profileTabs,
            selectedTabIndex = selectedProfileIndex,
            onTabSelected = { index ->
                val newProfileId = profileIds.getOrNull(index) ?: return@TabRow
                if (newProfileId == connectedScrcpyProfileId) return@TabRow
                haptic.contextClick()
                // 脱离快捷设备：直接写入 session 级状态
                AppRuntime.currentConnectionProfileId.value = newProfileId
                val device = currentTarget?.let { ct ->
                    savedShortcuts.firstOrNull { it.matchesAddress(ct) }
                }
                if (device != null) {
                    viewModel.updateShortcut(id = device.id, scrcpyProfileId = newProfileId)
                }
                val profileName = profileTabs.getOrElse(index) { textGlobal }
                AppRuntime.snackbar(R.string.device_switched_profile, device?.name ?: "", profileName)
            },
            modifier = Modifier
                .padding(bottom = UiSpacing.Medium),
            minWidth = 96.dp,
            maxWidth = 192.dp,
            height = 48.dp,
            itemSpacing = UiSpacing.Medium,
        )
    }


    val pageContentPadding = contentPadding
    val pageBottomInnerPadding = bottomInnerPadding

    @Composable
    fun DeviceListContent(
        state: LazyListState,
        includeInlinePreviewControls: Boolean,
        useTwoPaneConfigPanel: Boolean = false,
        modifier: Modifier = Modifier,
        contentPadding: PaddingValues = pageContentPadding,
        bottomInnerPadding: Dp? = pageBottomInnerPadding,
    ) {
        LazyColumn(
            modifier = modifier,
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
            state = state,
            bottomInnerPadding = bottomInnerPadding,
        ) {
            item { StatusSection() }
            item { DeviceListSection() }

            if (!adbConnected) {
                item { QuickConnectSection() }
                item { PairingSection() }
            }

            if (adbConnected) {
                if (includeInlinePreviewControls && canShowPreviewControls && asBundle.previewCardOnTop) {
                    item(key = PREVIEW_CARD_ITEM_KEY) {
                        SectionSmallTitle(stringResource(R.string.device_section_scrcpy))
                        PreviewSection()
                    }
                    item { VirtualButtonsSection() }
                    item {
                        if (sessionInfo == null) ProfilesTabRow()
                        if (useTwoPaneConfigPanel) ScrcpyConfigSectionForTwoPane()
                        else ScrcpyConfigSection()
                    }
                } else {
                    item {
                        SectionSmallTitle(stringResource(R.string.device_section_scrcpy))
                        if (sessionInfo == null) ProfilesTabRow()
                        if (useTwoPaneConfigPanel) ScrcpyConfigSectionForTwoPane()
                        else ScrcpyConfigSection()
                    }
                    if (includeInlinePreviewControls && canShowPreviewControls) {
                        item(key = PREVIEW_CARD_ITEM_KEY) { PreviewSection() }
                        item { VirtualButtonsSection() }
                    }
                }
            }

            item { LogsSection() }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowSizeClass = activity?.let { calculateWindowSizeClass(it) }
        val useTwoPane = maxWidth > maxHeight
                || when (windowSizeClass?.widthSizeClass) {
            WindowWidthSizeClass.Compact -> false
            WindowWidthSizeClass.Medium -> false
            WindowWidthSizeClass.Expanded -> true
            else -> false
        }
        val availableMaxWidth = maxWidth
        val compactTopAppBar = useTwoPane && canShowPreviewControls
        val showTwoPaneSideAction = useTwoPane && canShowPreviewControls
        val configOnLeft = !asBundle.deviceTwoPaneConfigOnRight

        LaunchedEffect(compactTopAppBar) {
            onCompactTopAppBarChanged(compactTopAppBar)
        }
        LaunchedEffect(showTwoPaneSideAction, configOnLeft) {
            onTwoPaneSideActionChanged(
                showTwoPaneSideAction,
                configOnLeft,
            )
        }
        LaunchedEffect(twoPaneSideToggleRequest, showTwoPaneSideAction) {
            if (twoPaneSideToggleRequest == handledTwoPaneSideToggleRequest) return@LaunchedEffect
            handledTwoPaneSideToggleRequest = twoPaneSideToggleRequest
            if (!showTwoPaneSideAction) return@LaunchedEffect
            viewModel.updateAsBundle { it.copy(deviceTwoPaneConfigOnRight = !it.deviceTwoPaneConfigOnRight) }
        }

        if (!useTwoPane || !canShowPreviewControls) {
            DeviceListContent(state = listState, includeInlinePreviewControls = true)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(
                        horizontal = UiSpacing.PageHorizontal,
                        vertical = UiSpacing.PageVertical,
                    ),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.PageItem),
            ) {
                val currentSession = sessionInfo!!
                val twoPaneContentWidth =
                    (availableMaxWidth - UiSpacing.PageHorizontal * 2 - UiSpacing.PageItem)
                        .coerceAtLeast(0.dp)
                val desiredPreviewWidth =
                    (asBundle.devicePreviewCardHeightDp.coerceAtLeast(120)
                        .toFloat() * currentSession.width / currentSession.height).dp
                val previewWidth = desiredPreviewWidth
                    .coerceAtMost(twoPaneContentWidth * 2f / 3f)
                val configColumnWidth =
                    (twoPaneContentWidth - previewWidth)
                        .coerceIn(0.dp, DEVICE_TWO_PANE_CONFIG_MAX_WIDTH)
                val previewPaneWidth = (twoPaneContentWidth - configColumnWidth).coerceAtLeast(0.dp)

                @Composable
                fun ConfigColumn() {
                    DeviceListContent(
                        modifier = Modifier.width(configColumnWidth),
                        state = listState,
                        includeInlinePreviewControls = false,
                        useTwoPaneConfigPanel = true,
                        contentPadding = PaddingValues(0.dp),
                        bottomInnerPadding = bottomInnerPadding,
                    )
                }

                @Composable
                fun PreviewColumn() {
                    Box(
                        modifier = Modifier
                            .width(previewPaneWidth)
                            .fillMaxHeight()
                            .padding(bottom = bottomInnerPadding),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Column(
                            modifier = Modifier.width(previewWidth),
                            verticalArrangement = Arrangement.spacedBy(UiSpacing.PageItem),
                        ) {
                            PreviewSection(
                                modifier = Modifier.width(previewWidth),
                                directControlEnabled = true,
                            )
                            VirtualButtonsSection(modifier = Modifier.width(previewWidth))
                        }
                    }
                }

                if (configOnLeft) {
                    ConfigColumn()
                    PreviewColumn()
                } else {
                    PreviewColumn()
                    ConfigColumn()
                }
            }
        }
    }

    AppListBottomSheet(
        show = showRecentTasksSheet,
        title = stringResource(R.string.bottomsheet_recent_tasks),
        loadingText = stringResource(R.string.bottomsheet_loading_tasks),
        emptyText = stringResource(R.string.bottomsheet_no_tasks),
        entries = recentTasks.map { task ->
            val app = viewModel.findCachedApp(task.packageName)
            AppListEntry(
                key = task.packageName,
                title = app?.label?.takeIf { it.isNotBlank() } ?: task.packageName,
                summary = if (app?.label != null) task.packageName else null,
                system = app?.system,
                onClick = {
                    haptic.contextClick()
                    viewModel.hideRecentTasks()
                    if (sessionInfo == null) viewModel.startScrcpy(task.packageName)
                    else viewModel.launchAppWithFallback(task.packageName)
                },
            )
        },
        refreshBusy = listingsRefreshBusy,
        onDismissRequest = { viewModel.hideRecentTasks() },
        onRefresh = {
            scope.launch(Dispatchers.IO) {
                viewModel.refreshRecentTasks()
            }
        },
    )

    AppListBottomSheet(
        show = showAllAppsSheet,
        title = stringResource(R.string.bottomsheet_all_apps),
        loadingText = stringResource(R.string.bottomsheet_loading_apps),
        emptyText = stringResource(R.string.bottomsheet_no_apps),
        entries = apps.map { app ->
            AppListEntry(
                key = app.packageName,
                title = app.label?.takeIf { it.isNotBlank() } ?: app.packageName,
                summary = if (app.label != null) app.packageName else null,
                system = app.system,
                onClick = {
                    viewModel.hideAllApps()
                    if (sessionInfo == null) viewModel.startScrcpy(app.packageName)
                    else viewModel.launchAppWithFallback(app.packageName)
                },
            )
        },
        refreshBusy = listingsRefreshBusy,
        onDismissRequest = { viewModel.hideAllApps() },
        onRefresh = { scope.launch(Dispatchers.IO) { viewModel.refreshApps() } },
    )
}
