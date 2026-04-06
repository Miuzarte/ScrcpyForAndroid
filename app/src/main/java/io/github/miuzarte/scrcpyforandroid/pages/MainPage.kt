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
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.github.miuzarte.scrcpyforandroid.constants.UiMotion
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.Storage
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
    Device(title = "设备", label = "设备", icon = Icons.Rounded.Devices),
    Settings(title = "设置", label = "设置", icon = Icons.Rounded.Settings);
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
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()

    val activity = remember(context) { context as? Activity }
    val initialOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = initialOrientation
        }
    }

    val nativeCore = remember(appContext) { NativeCoreFacade.get(appContext) }
    val adbService = remember(appContext) { NativeAdbService(appContext) }
    val scrcpy = remember(appContext, adbService) { Scrcpy(appContext, adbService) }

    val snackHostState = remember { SnackbarHostState() }
    val saveableStateHolder = rememberSaveableStateHolder()
    val tabs = remember { MainTabDestination.entries }
    val pagerState = rememberPagerState(
        initialPage = MainTabDestination.Device.ordinal,
        pageCount = { tabs.size })
    val currentTab = tabs[pagerState.currentPage]
    val rootBackStack = remember { mutableStateListOf<NavKey>(RootScreen.Home) }
    val currentRootScreen = rootBackStack.lastOrNull() as? RootScreen ?: RootScreen.Home
    val devicesPageScrollBehavior = MiuixScrollBehavior(
        canScroll = { currentTab == MainTabDestination.Device })
    val settingsPageScrollBehavior = MiuixScrollBehavior(
        canScroll = { currentTab == MainTabDestination.Settings })
    val advancedPageScrollBehavior = MiuixScrollBehavior(
        canScroll = {
            when (currentRootScreen) {
                is RootScreen.Advanced -> true
                is RootScreen.VirtualButtonOrder -> true
                else -> false
            }
        })

    fun popRoot() {
        if (rootBackStack.size > 1) {
            rootBackStack.removeAt(rootBackStack.lastIndex)
        }
    }

    // Unified back behavior:
    // 1) pop inner route
    // 2) switch tab back to Device
    // 3) double-back to exit and disconnect adb/scrcpy
    var lastExitBackPressAtMs by rememberSaveable { mutableLongStateOf(0L) }
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
                    runCatching { scrcpy.stop() }
                    runCatching { adbService.disconnect() }
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

    val appSettings = Storage.appSettings
    val scrcpyOptions = Storage.scrcpyOptions

    var sessionStarted by remember { mutableStateOf(false) }
    var clearLogsAction by remember { mutableStateOf({}) }
    var openReorderDevicesAction by remember { mutableStateOf({}) }
    var canClearLogs by remember { mutableStateOf(false) }
    var showDeviceMenu by rememberSaveable { mutableStateOf(false) }
    var fullscreenOrientation by rememberSaveable {
        mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    }

    var themeBaseIndex by appSettings.themeBaseIndex.asMutableState()
    var monet by appSettings.monet.asMutableState()
    val themeMode = resolveThemeMode(themeBaseIndex, monet)
    val themeController = remember(themeMode) { ThemeController(colorSchemeMode = themeMode) }

    val keepScreenOnWhenStreamingEnabled by appSettings.keepScreenOnWhenStreaming.asMutableState()
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

    val adbKeyName by appSettings.adbKeyName.asState()
    LaunchedEffect(adbKeyName) {
        adbService.keyName = adbKeyName.ifBlank { AppSettings.ADB_KEY_NAME.defaultValue }
    }

    var customServerUri by appSettings.customServerUri.asMutableState()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
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
                                                    Icons.Rounded.MoreVert,
                                                    contentDescription = "更多"
                                                )
                                            }
                                            DeviceMenuPopup(
                                                show = showDeviceMenu,
                                                canClearLogs = canClearLogs,
                                                onDismissRequest = { showDeviceMenu = false },
                                                onReorderDevices = {
                                                    openReorderDevicesAction()
                                                    showDeviceMenu = false
                                                },
                                                onOpenVirtualButtonOrder = {
                                                    rootBackStack.add(RootScreen.VirtualButtonOrder)
                                                    showDeviceMenu = false
                                                },
                                                onClearLogs = {
                                                    clearLogsAction()
                                                    showDeviceMenu = false
                                                },
                                            )
                                        },
                                        scrollBehavior = devicesPageScrollBehavior,
                                    )
                                },
                            ) { pagePadding ->
                                DeviceTabScreen(
                                    contentPadding = pagePadding,
                                    nativeCore = nativeCore,
                                    adbService = adbService,
                                    scrcpy = scrcpy,
                                    snack = snackHostState,
                                    scrollBehavior = devicesPageScrollBehavior,
                                    onSessionStartedChange = { sessionStarted = it },
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
                                                    codec = session.codecName,
                                                ),
                                            ),
                                        )
                                    },
                                )
                            }

                            MainTabDestination.Settings -> Scaffold(
                                topBar = {
                                    TopAppBar(
                                        title = tab.title,
                                        scrollBehavior = settingsPageScrollBehavior,
                                    )
                                },
                            ) { pagePadding ->
                                SettingsScreen(
                                    contentPadding = pagePadding,
                                    onOpenReorderDevices = {
                                        openReorderDevicesAction()
                                    },
                                    onOpenVirtualButtonOrder = {
                                        rootBackStack.add(RootScreen.VirtualButtonOrder)
                                    },
                                    onPickServer = {
                                        picker.launch(
                                            arrayOf(
                                                "application/java-archive",
                                                "application/octet-stream",
                                                "*/*"
                                            )
                                        )
                                    },
                                    scrollBehavior = settingsPageScrollBehavior,
                                )
                            }
                        }
                    }
                }
            }
        }

        entry(RootScreen.Advanced) {
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
                        scrollBehavior = advancedPageScrollBehavior,
                    )
                },
                snackbarHost = { SnackbarHost(snackHostState) },
            ) { pagePadding ->
                AdvancedConfigPage(
                    contentPadding = pagePadding,
                    scrollBehavior = advancedPageScrollBehavior,
                    snackbarHostState = snackHostState,
                    scrcpy = scrcpy,
                )
            }
        }

        entry(RootScreen.VirtualButtonOrder) {
            Scaffold(
                modifier = Modifier.nestedScroll(advancedPageScrollBehavior.nestedScrollConnection),
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
                        scrollBehavior = advancedPageScrollBehavior,
                    )
                },
            ) { pagePadding ->
                VirtualButtonOrderPage(
                    contentPadding = pagePadding,
                    scrollBehavior = advancedPageScrollBehavior,
                )
            }
        }

        entry<RootScreen.Fullscreen> { screen ->
            FullscreenControlPage(
                launch = screen.launch,
                nativeCore = nativeCore,
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
    onDismissRequest: () -> Unit,
    onReorderDevices: () -> Unit,
    onOpenVirtualButtonOrder: () -> Unit,
    onClearLogs: () -> Unit,
    canClearLogs: Boolean,
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
                text = "快速设备排序",
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
