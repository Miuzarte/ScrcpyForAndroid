package io.github.miuzarte.scrcpyforandroid.pages

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcut
import io.github.miuzarte.scrcpyforandroid.password.PasswordPickerPopupContent
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.SectionSmallTitle
import io.github.miuzarte.scrcpyforandroid.scrcpy.ClientOptions
import io.github.miuzarte.scrcpyforandroid.services.ConnectionController
import io.github.miuzarte.scrcpyforandroid.services.ConnectionStateStore
import io.github.miuzarte.scrcpyforandroid.services.DeviceAdbAutoReconnectManager
import io.github.miuzarte.scrcpyforandroid.services.DeviceAdbConnectionCoordinator
import io.github.miuzarte.scrcpyforandroid.services.EventLogger
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import io.github.miuzarte.scrcpyforandroid.widgets.AppListBottomSheet
import io.github.miuzarte.scrcpyforandroid.widgets.AppListEntry
import io.github.miuzarte.scrcpyforandroid.widgets.ConfigPanel
import io.github.miuzarte.scrcpyforandroid.widgets.DeviceTileList
import io.github.miuzarte.scrcpyforandroid.widgets.PairingCard
import io.github.miuzarte.scrcpyforandroid.widgets.PreviewCard
import io.github.miuzarte.scrcpyforandroid.widgets.QuickConnectCard
import io.github.miuzarte.scrcpyforandroid.widgets.StatusCard
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonAction
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonCard
import kotlinx.coroutines.Dispatchers
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
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
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
    var showThreePointMenu by rememberSaveable { mutableStateOf(false) }
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
                            onClick = { twoPaneSideToggleRequest++ },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription =
                                    if (configPanelOnLeft) "配置显示在右侧"
                                    else "配置显示在左侧"
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { showThreePointMenu = true },
                            holdDownState = showThreePointMenu,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.More,
                                contentDescription = "更多",
                            )
                        }
                        OverlayListPopup(
                            show = showThreePointMenu,
                            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                            alignment = PopupPositionProvider.Align.TopEnd,
                            onDismissRequest = { showThreePointMenu = false },
                        ) {
                            ListPopupColumn {
                                DropdownImpl(
                                    text = "快速设备排序",
                                    optionSize = 3,
                                    isSelected = false,
                                    index = 0,
                                    onSelectedIndexChange = {
                                        onOpenReorderDevices()
                                        showThreePointMenu = false
                                    },
                                )
                                DropdownImpl(
                                    text = "虚拟按钮排序",
                                    optionSize = 3,
                                    isSelected = false,
                                    index = 1,
                                    onSelectedIndexChange = {
                                        navigator.push(RootScreen.VirtualButtonOrder)
                                        showThreePointMenu = false
                                    },
                                )
                                DropdownImpl(
                                    text = "清空日志",
                                    optionSize = 3,
                                    isSelected = false,
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
                }
                if (useCompactTopAppBar) SmallTopAppBar(
                    title = "设备",
                    color = topAppBarColor,
                    actions = topAppBarActions
                )
                else TopAppBar(
                    title = "设备",
                    color = topAppBarColor,
                    actions = topAppBarActions,
                    scrollBehavior = scrollBehavior
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
    val canShowPreviewControls by viewModel.canShowPreviewControls.collectAsState()
    val virtualButtonLayout by viewModel.virtualButtonLayout.collectAsState()

    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val navigator = LocalRootNavigator.current
    val snackbar = LocalSnackbarController.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
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
        viewModel.snackbarEvents.collect { message -> snackbar.show(message) }
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
    fun DeviceListSection() {
        DeviceTileList(
            devices = savedShortcuts,
            isConnected = { device ->
                adbConnected
                        && currentTarget?.host == device.host
                        && currentTarget?.port == device.port
            },
            actionEnabled = !busy && !adbConnecting,
            actionInProgress = { device -> adbConnecting && activeDeviceActionId == device.id },
            editingDeviceId = editingDeviceId,
            onClick = { device ->
                if (editingDeviceId != device.id)
                    snackbar.show("长按可编辑")
            },
            onLongClick = { device ->
                val connected = adbConnected
                        && currentTarget?.host == device.host
                        && currentTarget?.port == device.port
                if (connected) {
                    snackbar.show("无法修改已连接的设备")
                } else {
                    viewModel.setEditingDeviceId(
                        if (editingDeviceId != device.id) device.id
                        else null
                    )
                }
            },
            onAction = { device ->
                haptic.contextClick()
                if (editingDeviceId == device.id) viewModel.setEditingDeviceId(null)
                viewModel.onDeviceAction(device)
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
            onAddDevice = {
                val target = ConnectionTarget.unmarshalFrom(quickConnectInputTemp)
                    ?: return@QuickConnectCard
                viewModel.upsertShortcut(DeviceShortcut(host = target.host, port = target.port))
                snackbar.show("已添加设备: ${target.host}:${target.port}")
            },
            onConnect = {
                val target = ConnectionTarget.unmarshalFrom(quickConnectInputTemp)
                    ?: return@QuickConnectCard
                viewModel.onQuickConnect(target)
            },
        )
    }

    @Composable
    fun PairingSection() {
        SectionSmallTitle("无线配对")
        PairingCard(
            busy = busy,
            autoDiscoverOnDialogOpen = asBundle.adbPairingAutoDiscoverOnDialogOpen,
            onDiscoverTarget = { viewModel.onDiscoverPairingTarget() },
            onPair = viewModel::onPair,
        )
    }

    @Composable
    fun ScrcpyConfigSection() {
        SectionSmallTitle("Scrcpy")
        ConfigPanel(
            busy = busy,
            activeProfileId = connectedScrcpyProfileId,
            activeBundle = connectedScrcpyBundle,
            hideSimpleConfigItems = asBundle.hideSimpleConfigItems,
            audioForwardingSupported = connectionState.adbSession.audioForwardingSupported,
            cameraMirroringSupported = connectionState.adbSession.cameraMirroringSupported,
            adbConnecting = adbConnecting,
            isQuickConnected = isQuickConnected,
            advancedEndActionText = connectedScrcpyProfileName,
            allAppsEndActionText = when {
                listingsRefreshBusy -> "..."
                apps.isNotEmpty() -> apps.size.toString()
                else -> "空"
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
                else -> "空"
            },
            onOpenRecentTasks = {
                viewModel.showRecentTasks()
                if (recentTasks.isEmpty() && !listingsRefreshBusy)
                    scope.launch(Dispatchers.IO) {
                        viewModel.refreshRecentTasks()
                    }
            },
            onOpenAdvanced = { navigator.push(RootScreen.Advanced) },
            onStartStopHaptic = haptic::contextClick,
            onStart = viewModel::startScrcpy,
            onStop = viewModel::stopScrcpy,
            sessionInfo = sessionInfo,
            onDisconnect = { viewModel.onDisconnectCurrent(currentTarget) },
            showFullscreenAction = false,
            onOpenFullscreen = ::openFullscreenControl,
        )
    }

    @Composable
    fun PreviewSection(modifier: Modifier = Modifier, directControlEnabled: Boolean = false) {
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
            autoBringIntoView = pendingScrollToPreview && !directControlEnabled,
            onAutoBringIntoViewConsumed = { viewModel.clearPendingScrollToPreview() },
            onTouchActiveChanged = {
                if (directControlEnabled) onPreviewGestureLockChanged(it)
            },
        )
    }

    @Composable
    fun ScrcpyConfigSectionForTwoPane() {
        SectionSmallTitle("Scrcpy")
        ConfigPanel(
            busy = busy,
            activeProfileId = connectedScrcpyProfileId,
            activeBundle = connectedScrcpyBundle,
            hideSimpleConfigItems = asBundle.hideSimpleConfigItems,
            audioForwardingSupported = connectionState.adbSession.audioForwardingSupported,
            cameraMirroringSupported = connectionState.adbSession.cameraMirroringSupported,
            adbConnecting = adbConnecting,
            isQuickConnected = isQuickConnected,
            advancedEndActionText = connectedScrcpyProfileName,
            allAppsEndActionText = when {
                listingsRefreshBusy -> "..."
                apps.isNotEmpty() -> apps.size.toString()
                else -> "空"
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
                else -> "空"
            },
            onOpenRecentTasks = {
                viewModel.showRecentTasks()
                if (recentTasks.isEmpty() && !listingsRefreshBusy)
                    scope.launch(Dispatchers.IO) {
                        viewModel.refreshRecentTasks()
                    }
            },
            onOpenAdvanced = { navigator.push(RootScreen.Advanced) },
            onStartStopHaptic = haptic::contextClick,
            onStart = viewModel::startScrcpy,
            onStop = viewModel::stopScrcpy,
            sessionInfo = sessionInfo,
            onDisconnect = { viewModel.onDisconnectCurrent(currentTarget) },
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
                item {
                    if (useTwoPaneConfigPanel) ScrcpyConfigSectionForTwoPane()
                    else ScrcpyConfigSection()
                }

                if (includeInlinePreviewControls && canShowPreviewControls) {
                    item(key = PREVIEW_CARD_ITEM_KEY) { PreviewSection() }
                    item { VirtualButtonsSection() }
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
                        vertical = UiSpacing.PageVertical
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
        title = "最近任务",
        loadingText = "最近任务加载中",
        emptyText = "没有可用的最近任务",
        entries = recentTasks.map { task ->
            val app = viewModel.findCachedApp(task.packageName)
            AppListEntry(
                key = task.packageName,
                title = app?.label?.takeIf { it.isNotBlank() } ?: task.packageName,
                summary = if (app?.label != null) task.packageName else null,
                system = app?.system,
                onClick = {
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
