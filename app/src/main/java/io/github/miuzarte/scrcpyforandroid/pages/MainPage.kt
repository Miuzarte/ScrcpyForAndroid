package io.github.miuzarte.scrcpyforandroid.pages

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.constants.AppDefaults
import io.github.miuzarte.scrcpyforandroid.constants.UiMotion
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.services.MainSettings
import io.github.miuzarte.scrcpyforandroid.services.loadDevicePageSettings
import io.github.miuzarte.scrcpyforandroid.services.loadMainSettings
import io.github.miuzarte.scrcpyforandroid.services.saveMainSettings
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

private enum class MainTabDestination(
    val title: String,
    val label: String,
    val icon: ImageVector,
) {
    Device(title = "设备", label = "设备", icon = Icons.Default.Devices),
    Settings(title = "设置", label = "设置", icon = Icons.Default.Settings),
}

private sealed interface RootScreen : NavKey {
    data object Home : RootScreen
    data object Advanced : RootScreen
    data object VirtualButtonOrder : RootScreen
    data class Fullscreen(val launch: FullscreenControlLaunch) : RootScreen
}

@Composable
fun MainPage() {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }
    val initialOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val nativeCore = remember(context) { NativeCoreFacade.get(context.applicationContext) }
    val initialSettings = remember(context) { loadMainSettings(context) }
    val initialDeviceSettings = remember(context) { loadDevicePageSettings(context) }
    val snackHostState = remember { SnackbarHostState() }
    val tabs = remember { MainTabDestination.entries }
    val pagerState = rememberPagerState(
        initialPage = MainTabDestination.Device.ordinal,
        pageCount = { tabs.size })
    val currentTab = tabs[pagerState.currentPage]
    val saveableStateHolder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val rootBackStack = remember { mutableStateListOf<NavKey>(RootScreen.Home) }
    val currentRootScreen = rootBackStack.lastOrNull() as? RootScreen ?: RootScreen.Home
    val deviceScrollBehavior =
        MiuixScrollBehavior(canScroll = { currentTab == MainTabDestination.Device })
    val settingsScrollBehavior =
        MiuixScrollBehavior(canScroll = { currentTab == MainTabDestination.Settings })
    val advancedScrollBehavior = MiuixScrollBehavior(
        canScroll = {
            currentRootScreen is RootScreen.Advanced || currentRootScreen is RootScreen.VirtualButtonOrder
        },
    )
    val stringListSaver = listSaver<List<String>, String>(
        save = { value -> ArrayList(value) },
        restore = { restored -> restored.toList() },
    )

    var audioEnabled by rememberSaveable { mutableStateOf(initialSettings.audioEnabled) }
    var audioCodec by rememberSaveable { mutableStateOf(initialSettings.audioCodec) }
    var videoCodec by rememberSaveable { mutableStateOf(initialSettings.videoCodec) }
    var themeBaseIndex by rememberSaveable { mutableIntStateOf(initialSettings.themeBaseIndex) }
    var monetEnabled by rememberSaveable { mutableStateOf(initialSettings.monetEnabled) }
    var fullscreenDebugInfoEnabled by rememberSaveable { mutableStateOf(initialSettings.fullscreenDebugInfoEnabled) }
    var showFullscreenVirtualButtons by rememberSaveable { mutableStateOf(initialSettings.showFullscreenVirtualButtons) }
    var keepScreenOnWhenStreamingEnabled by rememberSaveable { mutableStateOf(initialSettings.keepScreenOnWhenStreamingEnabled) }
    var devicePreviewCardHeightDp by rememberSaveable { mutableIntStateOf(initialSettings.devicePreviewCardHeightDp) }
    var virtualButtonsOutside by rememberSaveable(stateSaver = stringListSaver) {
        mutableStateOf(VirtualButtonActions.parseStoredIds(initialSettings.virtualButtonsOutside))
    }
    var virtualButtonsInMore by rememberSaveable(stateSaver = stringListSaver) {
        mutableStateOf(VirtualButtonActions.parseStoredIds(initialSettings.virtualButtonsInMore))
    }
    var customServerUri by rememberSaveable { mutableStateOf(initialSettings.customServerUri) }
    var serverRemotePath by rememberSaveable { mutableStateOf(initialSettings.serverRemotePath) }
    var adbKeyName by rememberSaveable { mutableStateOf(initialSettings.adbKeyName) }
    var adbPairingAutoDiscoverOnDialogOpen by rememberSaveable {
        mutableStateOf(initialSettings.adbPairingAutoDiscoverOnDialogOpen)
    }
    var adbAutoReconnectPairedDevice by rememberSaveable {
        mutableStateOf(initialSettings.adbAutoReconnectPairedDevice)
    }
    var adbMdnsLanDiscoveryEnabled by rememberSaveable {
        mutableStateOf(initialSettings.adbMdnsLanDiscoveryEnabled)
    }
    var noControl by rememberSaveable { mutableStateOf(initialDeviceSettings.noControl) }
    var videoEncoder by rememberSaveable { mutableStateOf(initialDeviceSettings.videoEncoder) }
    var videoCodecOptions by rememberSaveable { mutableStateOf(initialDeviceSettings.videoCodecOptions) }
    var audioEncoder by rememberSaveable { mutableStateOf(initialDeviceSettings.audioEncoder) }
    var audioCodecOptions by rememberSaveable { mutableStateOf(initialDeviceSettings.audioCodecOptions) }
    var audioDup by rememberSaveable { mutableStateOf(initialDeviceSettings.audioDup) }
    var audioSourcePreset by rememberSaveable { mutableStateOf(initialDeviceSettings.audioSourcePreset) }
    var audioSourceCustom by rememberSaveable { mutableStateOf(initialDeviceSettings.audioSourceCustom) }
    var videoSourcePreset by rememberSaveable { mutableStateOf(initialDeviceSettings.videoSourcePreset) }
    var cameraIdInput by rememberSaveable { mutableStateOf(initialDeviceSettings.cameraIdInput) }
    var cameraFacingPreset by rememberSaveable { mutableStateOf(initialDeviceSettings.cameraFacingPreset) }
    var cameraSizePreset by rememberSaveable { mutableStateOf(initialDeviceSettings.cameraSizePreset) }
    var cameraSizeCustom by rememberSaveable { mutableStateOf(initialDeviceSettings.cameraSizeCustom) }
    var cameraArInput by rememberSaveable { mutableStateOf(initialDeviceSettings.cameraAr) }
    var cameraFpsInput by rememberSaveable { mutableStateOf(initialDeviceSettings.cameraFps) }
    var cameraHighSpeed by rememberSaveable { mutableStateOf(initialDeviceSettings.cameraHighSpeed) }
    var noAudioPlayback by rememberSaveable { mutableStateOf(initialDeviceSettings.noAudioPlayback) }
    var noVideo by rememberSaveable { mutableStateOf(initialDeviceSettings.noVideo) }
    var requireAudio by rememberSaveable { mutableStateOf(initialDeviceSettings.requireAudio) }
    var turnScreenOff by rememberSaveable { mutableStateOf(initialDeviceSettings.turnScreenOff) }
    var maxSizeInput by rememberSaveable { mutableStateOf(initialDeviceSettings.maxSizeInput) }
    var maxFpsInput by rememberSaveable { mutableStateOf(initialDeviceSettings.maxFpsInput) }
    var newDisplayWidth by rememberSaveable { mutableStateOf(initialDeviceSettings.newDisplayWidth) }
    var newDisplayHeight by rememberSaveable { mutableStateOf(initialDeviceSettings.newDisplayHeight) }
    var newDisplayDpi by rememberSaveable { mutableStateOf(initialDeviceSettings.newDisplayDpi) }
    var displayIdInput by rememberSaveable { mutableStateOf(initialDeviceSettings.displayIdInput) }
    var cropWidth by rememberSaveable { mutableStateOf(initialDeviceSettings.cropWidth) }
    var cropHeight by rememberSaveable { mutableStateOf(initialDeviceSettings.cropHeight) }
    var cropX by rememberSaveable { mutableStateOf(initialDeviceSettings.cropX) }
    var cropY by rememberSaveable { mutableStateOf(initialDeviceSettings.cropY) }
    val videoEncoderOptions = remember { mutableStateListOf<String>() }
    val audioEncoderOptions = remember { mutableStateListOf<String>() }
    val videoEncoderTypeMap = remember { mutableStateMapOf<String, String>() }
    val audioEncoderTypeMap = remember { mutableStateMapOf<String, String>() }
    val cameraSizeOptions = remember { mutableStateListOf<String>() }
    var sessionStarted by remember { mutableStateOf(false) }
    var refreshEncodersAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var refreshCameraSizesAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var clearLogsAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var openReorderDevicesAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var canClearLogs by remember { mutableStateOf(false) }
    var showDeviceMenu by rememberSaveable { mutableStateOf(false) }
    var lastExitBackPressAtMs by rememberSaveable { mutableLongStateOf(0L) }
    var fullscreenOrientation by rememberSaveable {
        mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    }
    val themeMode = resolveThemeMode(themeBaseIndex, monetEnabled)
    val themeController = remember(themeMode) { ThemeController(colorSchemeMode = themeMode) }

    // Restore system orientation when MainPage leaves composition.
    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = initialOrientation
        }
    }

    // Keep-screen-on is controlled globally, so fullscreen and preview share the same behavior.
    DisposableEffect(activity, keepScreenOnWhenStreamingEnabled, sessionStarted) {
        val window = activity?.window
        val shouldKeepScreenOn = keepScreenOnWhenStreamingEnabled && sessionStarted
        if (window != null && shouldKeepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (window != null && shouldKeepScreenOn) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Fullscreen route can force orientation based on stream ratio; all other routes are portrait.
    LaunchedEffect(activity, currentRootScreen, fullscreenOrientation) {
        val targetOrientation = when (currentRootScreen) {
            is RootScreen.Fullscreen -> fullscreenOrientation
            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        activity?.requestedOrientation = targetOrientation
    }

    LaunchedEffect(
        audioEnabled,
        audioCodec,
        videoCodec,
        themeBaseIndex,
        monetEnabled,
        fullscreenDebugInfoEnabled,
        showFullscreenVirtualButtons,
        keepScreenOnWhenStreamingEnabled,
        devicePreviewCardHeightDp,
        virtualButtonsOutside,
        virtualButtonsInMore,
        customServerUri,
        serverRemotePath,
        adbKeyName,
        adbPairingAutoDiscoverOnDialogOpen,
        adbAutoReconnectPairedDevice,
        adbMdnsLanDiscoveryEnabled,
    ) {
        saveMainSettings(
            context,
            MainSettings(
                audioEnabled = audioEnabled,
                audioCodec = audioCodec,
                videoCodec = videoCodec,
                themeBaseIndex = themeBaseIndex,
                monetEnabled = monetEnabled,
                fullscreenDebugInfoEnabled = fullscreenDebugInfoEnabled,
                showFullscreenVirtualButtons = showFullscreenVirtualButtons,
                keepScreenOnWhenStreamingEnabled = keepScreenOnWhenStreamingEnabled,
                devicePreviewCardHeightDp = devicePreviewCardHeightDp,
                virtualButtonsOutside = VirtualButtonActions.encodeStoredIds(virtualButtonsOutside),
                virtualButtonsInMore = VirtualButtonActions.encodeStoredIds(virtualButtonsInMore),
                customServerUri = customServerUri,
                serverRemotePath = serverRemotePath,
                adbKeyName = adbKeyName,
                adbPairingAutoDiscoverOnDialogOpen = adbPairingAutoDiscoverOnDialogOpen,
                adbAutoReconnectPairedDevice = adbAutoReconnectPairedDevice,
                adbMdnsLanDiscoveryEnabled = adbMdnsLanDiscoveryEnabled,
            ),
        )
    }

    LaunchedEffect(adbKeyName) {
        nativeCore.setAdbKeyName(adbKeyName.ifBlank { AppDefaults.ADB_KEY_NAME })
    }

    fun popRoot() {
        if (rootBackStack.size > 1) {
            rootBackStack.removeAt(rootBackStack.lastIndex)
        }
    }

    // Unified back behavior:
    // 1) pop inner route
    // 2) switch tab back to Device
    // 3) double-back to exit and disconnect adb/scrcpy
    fun handleBackNavigation() {
        if (rootBackStack.size > 1) {
            popRoot()
        } else if (pagerState.currentPage != MainTabDestination.Device.ordinal) {
            scope.launch {
                pagerState.animateScrollToPage(
                    page = MainTabDestination.Device.ordinal,
                    animationSpec = spring(
                        dampingRatio = UiMotion.PAGE_SWITCH_DAMPING_RATIO,
                        stiffness = UiMotion.PAGE_SWITCH_STIFFNESS,
                    ),
                )
            }
        } else {
            val now = SystemClock.elapsedRealtime()
            if (now - lastExitBackPressAtMs > 2_000L) {
                lastExitBackPressAtMs = now
                Toast.makeText(context, "再按一次返回键退出", Toast.LENGTH_SHORT).show()
                return
            }
            lastExitBackPressAtMs = 0L
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching { nativeCore.scrcpyStop() }
                    runCatching { nativeCore.adbDisconnect() }
                }
                activity?.finish()
            }
        }
    }

    val canNavigateBack = rootBackStack.size > 1 ||
            pagerState.currentPage != MainTabDestination.Device.ordinal

    BackHandler(enabled = currentRootScreen !is RootScreen.Fullscreen) {
        handleBackNavigation()
    }

    PredictiveBackHandler(
        enabled = canNavigateBack && currentRootScreen !is RootScreen.Fullscreen
    ) { progress ->
        try {
            progress.collect { }
            handleBackNavigation()
        } catch (_: CancellationException) {
            // Gesture was cancelled by the system/user.
        }
    }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            customServerUri = uri.toString()
        }

    val rootEntryProvider = entryProvider<NavKey> {
        entry(RootScreen.Home) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            page = tab.ordinal,
                                            animationSpec = spring(
                                                dampingRatio = UiMotion.PAGE_SWITCH_DAMPING_RATIO,
                                                stiffness = UiMotion.PAGE_SWITCH_STIFFNESS,
                                            ),
                                        )
                                    }
                                },
                                icon = tab.icon,
                                label = tab.label,
                            )
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackHostState) },
            ) { contentPadding ->
                HorizontalPager(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = contentPadding.calculateBottomPadding()),
                    state = pagerState,
                    beyondViewportPageCount = 1,
                ) { page ->
                    val tab = tabs[page]
                    saveableStateHolder.SaveableStateProvider(tab.name) {
                        when (tab) {
                            MainTabDestination.Device -> Scaffold(
                                topBar = {
                                    TopAppBar(
                                        title = tab.title,
                                        actions = {
                                            IconButton(
                                                onClick = { showDeviceMenu = true },
                                                holdDownState = showDeviceMenu,
                                            ) {
                                                Icon(
                                                    Icons.Default.MoreVert,
                                                    contentDescription = "更多"
                                                )
                                            }
                                            DeviceMenuPopup(
                                                show = showDeviceMenu,
                                                canClearLogs = canClearLogs,
                                                onDismissRequest = { showDeviceMenu = false },
                                                onReorderDevices = {
                                                    openReorderDevicesAction?.invoke()
                                                    showDeviceMenu = false
                                                },
                                                onOpenVirtualButtonOrder = {
                                                    rootBackStack.add(RootScreen.VirtualButtonOrder)
                                                    showDeviceMenu = false
                                                },
                                                onClearLogs = {
                                                    clearLogsAction?.invoke()
                                                    showDeviceMenu = false
                                                },
                                            )
                                        },
                                        scrollBehavior = deviceScrollBehavior,
                                    )
                                },
                            ) { pagePadding ->
                                DeviceTabScreen(
                                    contentPadding = pagePadding,
                                    nativeCore = nativeCore,
                                    snack = snackHostState,
                                    scrollBehavior = deviceScrollBehavior,
                                    virtualButtonsOutside = virtualButtonsOutside,
                                    virtualButtonsInMore = virtualButtonsInMore,
                                    customServerUri = customServerUri,
                                    serverRemotePath = serverRemotePath,
                                    onServerRemotePathChange = { serverRemotePath = it },
                                    videoCodec = videoCodec,
                                    onVideoCodecChange = { videoCodec = it },
                                    audioEnabled = audioEnabled,
                                    onAudioEnabledChange = { audioEnabled = it },
                                    audioCodec = audioCodec,
                                    onAudioCodecChange = { audioCodec = it },
                                    noControl = noControl,
                                    onNoControlChange = {
                                        noControl = it
                                        if (it) {
                                            turnScreenOff = false
                                        }
                                    },
                                    videoEncoder = videoEncoder,
                                    onVideoEncoderChange = { videoEncoder = it },
                                    videoCodecOptions = videoCodecOptions,
                                    onVideoCodecOptionsChange = { videoCodecOptions = it },
                                    audioEncoder = audioEncoder,
                                    onAudioEncoderChange = { audioEncoder = it },
                                    audioCodecOptions = audioCodecOptions,
                                    onAudioCodecOptionsChange = { audioCodecOptions = it },
                                    audioDup = audioDup,
                                    onAudioDupChange = { audioDup = it },
                                    audioSourcePreset = audioSourcePreset,
                                    onAudioSourcePresetChange = { audioSourcePreset = it },
                                    audioSourceCustom = audioSourceCustom,
                                    onAudioSourceCustomChange = { audioSourceCustom = it },
                                    videoSourcePreset = videoSourcePreset,
                                    onVideoSourcePresetChange = { videoSourcePreset = it },
                                    cameraIdInput = cameraIdInput,
                                    onCameraIdInputChange = { cameraIdInput = it },
                                    cameraFacingPreset = cameraFacingPreset,
                                    onCameraFacingPresetChange = { cameraFacingPreset = it },
                                    cameraSizePreset = cameraSizePreset,
                                    onCameraSizePresetChange = { cameraSizePreset = it },
                                    cameraSizeCustom = cameraSizeCustom,
                                    onCameraSizeCustomChange = { cameraSizeCustom = it },
                                    cameraArInput = cameraArInput,
                                    onCameraArInputChange = { cameraArInput = it },
                                    cameraFpsInput = cameraFpsInput,
                                    onCameraFpsInputChange = { cameraFpsInput = it },
                                    cameraHighSpeed = cameraHighSpeed,
                                    onCameraHighSpeedChange = { cameraHighSpeed = it },
                                    noAudioPlayback = noAudioPlayback,
                                    onNoAudioPlaybackChange = { noAudioPlayback = it },
                                    noVideo = noVideo,
                                    requireAudio = requireAudio,
                                    onRequireAudioChange = { requireAudio = it },
                                    turnScreenOff = turnScreenOff,
                                    onTurnScreenOffChange = { turnScreenOff = it },
                                    maxSizeInput = maxSizeInput,
                                    onMaxSizeInputChange = { maxSizeInput = it },
                                    maxFpsInput = maxFpsInput,
                                    onMaxFpsInputChange = { maxFpsInput = it },
                                    newDisplayWidth = newDisplayWidth,
                                    onNewDisplayWidthChange = { newDisplayWidth = it },
                                    newDisplayHeight = newDisplayHeight,
                                    onNewDisplayHeightChange = { newDisplayHeight = it },
                                    newDisplayDpi = newDisplayDpi,
                                    onNewDisplayDpiChange = { newDisplayDpi = it },
                                    displayIdInput = displayIdInput,
                                    onDisplayIdInputChange = { displayIdInput = it },
                                    cropWidth = cropWidth,
                                    onCropWidthChange = { cropWidth = it },
                                    cropHeight = cropHeight,
                                    onCropHeightChange = { cropHeight = it },
                                    cropX = cropX,
                                    onCropXChange = { cropX = it },
                                    cropY = cropY,
                                    onCropYChange = { cropY = it },
                                    videoEncoderOptions = videoEncoderOptions,
                                    onVideoEncoderOptionsChange = {
                                        videoEncoderOptions.clear()
                                        videoEncoderOptions.addAll(it)
                                    },
                                    onVideoEncoderTypeMapChange = {
                                        videoEncoderTypeMap.clear()
                                        videoEncoderTypeMap.putAll(it)
                                    },
                                    audioEncoderOptions = audioEncoderOptions,
                                    onAudioEncoderOptionsChange = {
                                        audioEncoderOptions.clear()
                                        audioEncoderOptions.addAll(it)
                                    },
                                    onAudioEncoderTypeMapChange = {
                                        audioEncoderTypeMap.clear()
                                        audioEncoderTypeMap.putAll(it)
                                    },
                                    cameraSizeOptions = cameraSizeOptions,
                                    onCameraSizeOptionsChange = {
                                        cameraSizeOptions.clear()
                                        cameraSizeOptions.addAll(it)
                                    },
                                    onSessionStartedChange = { sessionStarted = it },
                                    onRefreshEncodersActionChange = { refreshEncodersAction = it },
                                    onRefreshCameraSizesActionChange = {
                                        refreshCameraSizesAction = it
                                    },
                                    onClearLogsActionChange = { clearLogsAction = it },
                                    onCanClearLogsChange = { canClearLogs = it },
                                    onOpenReorderDevicesActionChange = {
                                        openReorderDevicesAction = it
                                    },
                                    onOpenAdvancedPage = { rootBackStack.add(RootScreen.Advanced) },
                                    onOpenFullscreenPage = { session ->
                                        fullscreenOrientation =
                                            if (session.width >= session.height) {
                                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                            } else {
                                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                            }
                                        rootBackStack.add(
                                            RootScreen.Fullscreen(
                                                launch = FullscreenControlLaunch(
                                                    deviceName = session.deviceName,
                                                    width = session.width,
                                                    height = session.height,
                                                    codec = session.codec,
                                                ),
                                            ),
                                        )
                                    },
                                    previewCardHeightDp = devicePreviewCardHeightDp,
                                    adbPairingAutoDiscoverOnDialogOpen = adbPairingAutoDiscoverOnDialogOpen,
                                    adbAutoReconnectPairedDevice = adbAutoReconnectPairedDevice,
                                    adbMdnsLanDiscoveryEnabled = adbMdnsLanDiscoveryEnabled,
                                )
                            }

                            MainTabDestination.Settings -> Scaffold(
                                topBar = {
                                    TopAppBar(
                                        title = tab.title,
                                        scrollBehavior = settingsScrollBehavior,
                                    )
                                },
                            ) { pagePadding ->
                                SettingsScreen(
                                    contentPadding = pagePadding,
                                    themeBaseIndex = themeBaseIndex,
                                    onThemeBaseIndexChange = { themeBaseIndex = it },
                                    monetEnabled = monetEnabled,
                                    onMonetEnabledChange = { monetEnabled = it },
                                    fullscreenDebugInfoEnabled = fullscreenDebugInfoEnabled,
                                    onFullscreenDebugInfoEnabledChange = {
                                        fullscreenDebugInfoEnabled = it
                                    },
                                    keepScreenOnWhenStreamingEnabled = keepScreenOnWhenStreamingEnabled,
                                    onKeepScreenOnWhenStreamingEnabledChange = {
                                        keepScreenOnWhenStreamingEnabled = it
                                    },
                                    devicePreviewCardHeightDp = devicePreviewCardHeightDp,
                                    onDevicePreviewCardHeightDpChange = {
                                        devicePreviewCardHeightDp = it.coerceAtLeast(120)
                                    },
                                    showFullscreenVirtualButtons = showFullscreenVirtualButtons,
                                    onShowFullscreenVirtualButtonsChange = {
                                        showFullscreenVirtualButtons = it
                                    },
                                    customServerUri = customServerUri,
                                    onPickServer = {
                                        picker.launch(
                                            arrayOf(
                                                "application/java-archive",
                                                "application/octet-stream",
                                                "*/*"
                                            )
                                        )
                                    },
                                    onClearServer = { customServerUri = null },
                                    serverRemotePath = serverRemotePath,
                                    onServerRemotePathChange = { serverRemotePath = it },
                                    adbKeyName = adbKeyName,
                                    onAdbKeyNameChange = { adbKeyName = it },
                                    adbPairingAutoDiscoverOnDialogOpen = adbPairingAutoDiscoverOnDialogOpen,
                                    onAdbPairingAutoDiscoverOnDialogOpenChange = {
                                        adbPairingAutoDiscoverOnDialogOpen = it
                                    },
                                    adbAutoReconnectPairedDevice = adbAutoReconnectPairedDevice,
                                    onAdbAutoReconnectPairedDeviceChange = {
                                        adbAutoReconnectPairedDevice = it
                                    },
                                    scrollBehavior = settingsScrollBehavior,
                                )
                            }
                        }
                    }
                }
            }
        }

        entry(RootScreen.Advanced) {
            val videoEncoderDropdownItems = listOf("默认") + videoEncoderOptions
            val audioEncoderDropdownItems = listOf("默认") + audioEncoderOptions
            val videoEncoderIndex = (videoEncoderOptions.indexOf(videoEncoder) + 1).coerceAtLeast(0)
            val audioEncoderIndex = (audioEncoderOptions.indexOf(audioEncoder) + 1).coerceAtLeast(0)

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = "高级参数",
                        navigationIcon = {
                            IconButton(onClick = { popRoot() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回"
                                )
                            }
                        },
                        scrollBehavior = advancedScrollBehavior,
                    )
                },
                snackbarHost = { SnackbarHost(snackHostState) },
            ) { pagePadding ->
                AdvancedConfigPage(
                    contentPadding = pagePadding,
                    scrollBehavior = advancedScrollBehavior,
                    sessionStarted = sessionStarted,
                    snackbarHostState = snackHostState,
                    audioEnabled = audioEnabled,
                    noControl = noControl,
                    onNoControlChange = {
                        noControl = it
                        if (it) {
                            turnScreenOff = false
                        }
                    },
                    audioDup = audioDup,
                    onAudioDupChange = { audioDup = it },
                    audioSourcePreset = audioSourcePreset,
                    onAudioSourcePresetChange = { audioSourcePreset = it },
                    audioSourceCustom = audioSourceCustom,
                    onAudioSourceCustomChange = { audioSourceCustom = it },
                    videoSourcePreset = videoSourcePreset,
                    onVideoSourcePresetChange = { videoSourcePreset = it },
                    cameraIdInput = cameraIdInput,
                    onCameraIdInputChange = { cameraIdInput = it },
                    cameraFacingPreset = cameraFacingPreset,
                    onCameraFacingPresetChange = { cameraFacingPreset = it },
                    cameraSizePreset = cameraSizePreset,
                    onCameraSizePresetChange = { cameraSizePreset = it },
                    cameraSizeCustom = cameraSizeCustom,
                    onCameraSizeCustomChange = { cameraSizeCustom = it },
                    cameraSizeDropdownItems = listOf("默认") + cameraSizeOptions + listOf("自定义"),
                    cameraSizeIndex = when (cameraSizePreset) {
                        "custom" -> cameraSizeOptions.size + 1
                        in cameraSizeOptions -> cameraSizeOptions.indexOf(cameraSizePreset) + 1
                        else -> 0
                    },
                    cameraArInput = cameraArInput,
                    onCameraArInputChange = { cameraArInput = it },
                    cameraFpsInput = cameraFpsInput,
                    onCameraFpsInputChange = { cameraFpsInput = it },
                    cameraHighSpeed = cameraHighSpeed,
                    onCameraHighSpeedChange = { cameraHighSpeed = it },
                    noAudioPlayback = noAudioPlayback,
                    onNoAudioPlaybackChange = { noAudioPlayback = it },
                    noVideo = noVideo,
                    onNoVideoChange = { noVideo = it },
                    requireAudio = requireAudio,
                    onRequireAudioChange = { requireAudio = it },
                    turnScreenOff = turnScreenOff,
                    onTurnScreenOffChange = { turnScreenOff = it },
                    maxSizeInput = maxSizeInput,
                    onMaxSizeInputChange = { maxSizeInput = it },
                    maxFpsInput = maxFpsInput,
                    onMaxFpsInputChange = { maxFpsInput = it },
                    videoEncoderDropdownItems = videoEncoderDropdownItems,
                    videoEncoderTypeMap = videoEncoderTypeMap,
                    videoEncoderIndex = videoEncoderIndex,
                    onVideoEncoderChange = { videoEncoder = it },
                    videoCodecOptions = videoCodecOptions,
                    onVideoCodecOptionsChange = { videoCodecOptions = it },
                    audioEncoderDropdownItems = audioEncoderDropdownItems,
                    audioEncoderTypeMap = audioEncoderTypeMap,
                    audioEncoderIndex = audioEncoderIndex,
                    onAudioEncoderChange = { audioEncoder = it },
                    audioCodecOptions = audioCodecOptions,
                    onAudioCodecOptionsChange = { audioCodecOptions = it },
                    onRefreshEncoders = { refreshEncodersAction?.invoke() },
                    onRefreshCameraSizes = { refreshCameraSizesAction?.invoke() },
                    newDisplayWidth = newDisplayWidth,
                    onNewDisplayWidthChange = { newDisplayWidth = it },
                    newDisplayHeight = newDisplayHeight,
                    onNewDisplayHeightChange = { newDisplayHeight = it },
                    newDisplayDpi = newDisplayDpi,
                    onNewDisplayDpiChange = { newDisplayDpi = it },
                    displayIdInput = displayIdInput,
                    onDisplayIdInputChange = { displayIdInput = it },
                    cropWidth = cropWidth,
                    onCropWidthChange = { cropWidth = it },
                    cropHeight = cropHeight,
                    onCropHeightChange = { cropHeight = it },
                    cropX = cropX,
                    onCropXChange = { cropX = it },
                    cropY = cropY,
                    onCropYChange = { cropY = it },
                )
            }
        }

        entry(RootScreen.VirtualButtonOrder) {
            Scaffold(
                modifier = Modifier.nestedScroll(advancedScrollBehavior.nestedScrollConnection),
                topBar = {
                    TopAppBar(
                        title = "虚拟按钮排序",
                        navigationIcon = {
                            IconButton(onClick = { popRoot() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回"
                                )
                            }
                        },
                        scrollBehavior = advancedScrollBehavior,
                    )
                },
            ) { pagePadding ->
                VirtualButtonOrderPage(
                    contentPadding = pagePadding,
                    scrollBehavior = advancedScrollBehavior,
                    outsideIds = virtualButtonsOutside,
                    moreIds = virtualButtonsInMore,
                    onLayoutChange = { outside, more ->
                        virtualButtonsOutside = outside
                        virtualButtonsInMore = more
                    },
                )
            }
        }

        entry<RootScreen.Fullscreen> { screen ->
            FullscreenControlPage(
                launch = screen.launch,
                nativeCore = nativeCore,
                virtualButtonsOutside = virtualButtonsOutside,
                virtualButtonsInMore = virtualButtonsInMore,
                showDebugInfo = fullscreenDebugInfoEnabled,
                showVirtualButtons = showFullscreenVirtualButtons,
                onVideoSizeChanged = { width, height ->
                    fullscreenOrientation = if (width >= height) {
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                },
                onDismiss = { popRoot() },
            )
        }
    }

    val rootEntries = rememberDecoratedNavEntries(
        backStack = rootBackStack,
        entryProvider = rootEntryProvider,
    )

    MiuixTheme(controller = themeController) {
        NavDisplay(
            entries = rootEntries,
            onBack = { popRoot() },
        )
    }
}

@Composable
private fun DeviceMenuPopup(
    show: Boolean,
    canClearLogs: Boolean,
    onDismissRequest: () -> Unit,
    onReorderDevices: () -> Unit,
    onOpenVirtualButtonOrder: () -> Unit,
    onClearLogs: () -> Unit,
) {
    SuperListPopup(
        show = show,
        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = onDismissRequest,
        enableWindowDim = false,
    ) {
        ListPopupColumn {
            DeviceMenuPopupItem(
                text = "调整设备排序",
                optionSize = 3,
                index = 0,
                onClick = onReorderDevices,
            )
            DeviceMenuPopupItem(
                text = "虚拟按钮排序",
                optionSize = 3,
                index = 1,
                onClick = onOpenVirtualButtonOrder,
            )
            DeviceMenuPopupItem(
                text = "清空日志",
                optionSize = 3,
                index = 2,
                enabled = canClearLogs,
                onClick = onClearLogs,
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
    // TODO: (Int) -> Unit
    onClick: () -> Unit,
) {
    if (enabled) {
        DropdownImpl(
            text = text,
            optionSize = optionSize,
            isSelected = false,
            index = index,
            onSelectedIndexChange = { onClick() },
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
