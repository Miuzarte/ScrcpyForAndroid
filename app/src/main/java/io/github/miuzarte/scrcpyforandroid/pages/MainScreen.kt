package io.github.miuzarte.scrcpyforandroid.pages

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.scrcpyforandroid.BuildConfig
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiMotion
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.services.*
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.quickDevices
import io.github.miuzarte.scrcpyforandroid.ui.*
import io.github.miuzarte.scrcpyforandroid.ui.component.FloatingBottomBar
import io.github.miuzarte.scrcpyforandroid.ui.component.FloatingBottomBarItem
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import java.io.File
import top.yukonga.miuix.kmp.blur.layerBackdrop as miuixLayerBackdrop

private const val TERMINAL_FONT_RELATIVE_PATH = "terminal/font.ttf"

private fun terminalFontFile(context: Context): File {
    return File(context.filesDir, TERMINAL_FONT_RELATIVE_PATH)
}

private fun copyTerminalFontToPrivate(context: Context, uri: Uri) {
    val target = terminalFontFile(context)
    target.parentFile?.mkdirs()
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { context.getString(R.string.main_font_read_error) }
        target.outputStream().use { output ->
            input.copyTo(output)
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(columnIndex)
            } else {
                null
            }
        }
}

private enum class MainBottomTabDestination(
    @field:StringRes val labelResId: Int,
    val icon: ImageVector,
) {
    Devices(labelResId = R.string.main_tab_devices, icon = Icons.Rounded.Devices),
    Terminal(labelResId = R.string.main_tab_terminal, icon = Icons.Rounded.Terminal),
    Files(labelResId = R.string.main_tab_files, icon = Icons.Rounded.Folder),
    Settings(labelResId = R.string.main_tab_settings, icon = Icons.Rounded.Settings);
}

@Serializable
sealed interface RootScreen: NavKey {
    @Serializable
    data object Home: RootScreen
    @Serializable
    data object Advanced: RootScreen
    @Serializable
    data object About: RootScreen
    @Serializable
    data object ThemeSettings: RootScreen
    @Serializable
    data object VirtualButtonOrder: RootScreen
    @Serializable
    data object FullscreenControl: RootScreen // compatibility mode
    @Serializable
    data class ScrcpyOptionRecord(val profileId: String): RootScreen
}

@Composable
fun MainScreen() {
    // Environment
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val appContext = context.applicationContext
    val activity = remember(context) { context as? Activity }

    // Scopes
    val scope = rememberCoroutineScope()
    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    // Global controllers provided to the compose tree
    val snackHostState = remember { SnackbarHostState() }
    val snackbarController = remember(scope, snackHostState) {
        SnackbarController(
            scope = scope,
            hostState = snackHostState,
        )
    }

    DisposableEffect(snackHostState) {
        val unregister = AppRuntime.registerSnackbarHostState(snackHostState)
        onDispose(unregister)
    }

    // Root navigation and UI chrome state
    val saveableStateHolder = rememberSaveableStateHolder()
    val tabs = remember { MainBottomTabDestination.entries }
    val pagerState = rememberPagerState(
        initialPage = MainBottomTabDestination.Devices.ordinal,
        pageCount = { tabs.size },
    )
    var selectedTabIndex by rememberSaveable {
        mutableIntStateOf(MainBottomTabDestination.Devices.ordinal)
    }
    var pagerNavigationJob by remember { mutableStateOf<Job?>(null) }
    var isPagerNavigating by remember { mutableStateOf(false) }
    val currentTab = tabs[selectedTabIndex]
    val rootBackStack = rememberNavBackStack<RootScreen>(RootScreen.Home)
    val currentRootScreen = rootBackStack.lastOrNull() as? RootScreen ?: RootScreen.Home
    var showReorderDevices by rememberSaveable { mutableStateOf(false) }
    var lastExitBackPressAtMs by rememberSaveable { mutableLongStateOf(0L) }
    var fileTabCanNavigateUp by remember { mutableStateOf(false) }
    var fileTabNavigateUp by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var terminalGestureLock by remember { mutableStateOf(false) }
    var devicePreviewGestureLock by remember { mutableStateOf(false) }

    // Scroll behaviors
    val devicesPageScrollBehavior = MiuixScrollBehavior(
        canScroll = { currentTab == MainBottomTabDestination.Devices },
    )
    val terminalPageScrollBehavior = MiuixScrollBehavior(
        canScroll = { currentTab == MainBottomTabDestination.Terminal },
    )
    val settingsPageScrollBehavior = MiuixScrollBehavior(
        canScroll = { currentTab == MainBottomTabDestination.Settings },
    )
    val advancedPageScrollBehavior = MiuixScrollBehavior(
        canScroll = {
            when (currentRootScreen) {
                is RootScreen.Advanced -> true
                is RootScreen.VirtualButtonOrder -> true
                is RootScreen.ScrcpyOptionRecord -> true
                else -> false
            }
        },
    )

    // Navigation helpers
    val rootNavigator = remember {
        RootNavigator(
            push = { rootBackStack.add(it) },
            pop = {
                if (rootBackStack.size > 1)
                    rootBackStack.removeAt(rootBackStack.lastIndex)
            },
        )
    }

    // Shared settings bundles
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

    // Scrcpy instance and session state
    val customServerUri = asBundle.customServerUri
        .ifBlank { null }
    val customServerVersion = asBundle.customServerVersion
        .ifBlank { Scrcpy.DEFAULT_SERVER_VERSION }
    val serverRemotePath = asBundle.serverRemotePath
        .ifBlank { AppSettings.SERVER_REMOTE_PATH.defaultValue }
    val lowLatency = asBundle.lowLatency
    val scrcpy = remember(
        appContext,
        customServerUri,
        customServerVersion,
        serverRemotePath,
        lowLatency,
    ) {
        Scrcpy(
            appContext = appContext,
            customServerUri = customServerUri,
            serverVersion = customServerVersion,
            serverRemotePath = serverRemotePath,
            lowLatency = lowLatency,
        ).also {
            AppRuntime.scrcpy = it
        }
    }

    val deviceConnectionServices = remember(scrcpy) {
        val adbCoordinator = DeviceAdbConnectionCoordinator()
        val connectionStateStore = ConnectionStateStore()
        val connectionController = ConnectionController(
            scrcpy = scrcpy,
            stateStore = connectionStateStore,
            adbCoordinator = adbCoordinator,
        )
        val autoReconnectManager = DeviceAdbAutoReconnectManager(
            controller = connectionController,
            stateStore = connectionStateStore,
        )
        DeviceConnectionServices(
            adbCoordinator = adbCoordinator,
            connectionStateStore = connectionStateStore,
            connectionController = connectionController,
            autoReconnectManager = autoReconnectManager,
        )
    }

    val deviceTabViewModelFactory = remember(scrcpy, deviceConnectionServices) {
        DeviceTabViewModel.Factory(scrcpy, deviceConnectionServices)
    }

    DisposableEffect(deviceConnectionServices) {
        onDispose {
            deviceConnectionServices.autoReconnectManager.close()
            AppScreenOn.release()
        }
    }

    // Side-effect launchers and composition locals
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        asBundle = asBundle.copy(customServerUri = uri.toString())
    }
    val serverPicker = remember(picker) {
        ServerPicker {
            picker.launch(
                arrayOf(
                    "application/java-archive",
                    "application/octet-stream",
                    "*/*",
                ),
            )
        }
    }
    val terminalFontDocumentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        taskScope.launch {
            val result = runCatching {
                val displayName = queryDisplayName(context, uri)
                    ?.takeIf { it.isNotBlank() }
                    ?: "font.ttf"
                copyTerminalFontToPrivate(context, uri)
                displayName
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { displayName ->
                    asBundle = asBundle.copy(terminalFontDisplayName = displayName)
                    AppRuntime.snackbar(R.string.main_terminal_font_imported)
                }.onFailure { error ->
                    AppRuntime.snackbar(
                        R.string.main_terminal_font_import_failed,
                        error.message ?: error.javaClass.simpleName,
                    )
                }
            }
        }
    }
    val terminalFontPicker = remember(terminalFontDocumentPicker) {
        TerminalFontPicker {
            terminalFontDocumentPicker.launch(
                arrayOf(
                    "font/ttf",
                    "font/otf",
                    "application/x-font-ttf",
                    "application/x-font-otf",
                    "*/*",
                ),
            )
        }
    }

    // Derived flags
    val isFullscreenControlRoute = currentRootScreen is RootScreen.FullscreenControl
    val canNavigateBack = !isFullscreenControlRoute &&
            (rootBackStack.size > 1
                    || selectedTabIndex != MainBottomTabDestination.Devices.ordinal)

    fun navigateToTab(tab: MainBottomTabDestination) {
        val targetIndex = tab.ordinal
        if (targetIndex == selectedTabIndex) {
            return
        }
        pagerNavigationJob?.cancel()
        selectedTabIndex = targetIndex
        isPagerNavigating = true
        scope.launch {
            val job = coroutineContext[Job]
            pagerNavigationJob = job
            try {
                pagerState.animateScrollToPage(
                    page = targetIndex,
                    animationSpec = spring(
                        dampingRatio = UiMotion.PAGE_SWITCH_DAMPING_RATIO,
                        stiffness = UiMotion.PAGE_SWITCH_STIFFNESS,
                    ),
                )
            } finally {
                if (pagerNavigationJob == job) {
                    isPagerNavigating = false
                    pagerNavigationJob = null
                    if (pagerState.currentPage != targetIndex) {
                        selectedTabIndex = pagerState.currentPage
                    }
                }
            }
        }
    }

    LaunchedEffect(asBundle.lastUpdateCheckAt) {
        val now = System.currentTimeMillis()
        if (now - asBundle.lastUpdateCheckAt < AppUpdateChecker.CHECK_INTERVAL_MS) return@LaunchedEffect
        taskScope.launch {
            appSettings.updateBundle { it.copy(lastUpdateCheckAt = now) }
            AppUpdateChecker.ensureChecked(BuildConfig.VERSION_NAME)
        }
    }

    val textMainPressBackAgain = stringResource(R.string.main_press_back_again)
    fun handleBackNavigation() {
        when {
            rootBackStack.size > 1 -> rootNavigator.pop()

            selectedTabIndex == MainBottomTabDestination.Files.ordinal
                    && fileTabCanNavigateUp
                    && fileTabNavigateUp?.invoke() == true
                -> return

            selectedTabIndex != MainBottomTabDestination.Devices.ordinal
                -> navigateToTab(MainBottomTabDestination.Devices)

            else -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastExitBackPressAtMs > 2_000L) {
                    lastExitBackPressAtMs = now
                    Toast.makeText(
                        context,
                        textMainPressBackAgain,
                        Toast.LENGTH_SHORT,
                    ).show()
                    return
                }
                lastExitBackPressAtMs = 0L
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { scrcpy.stop() }
                        runCatching { NativeAdbService.disconnect() }
                        // 释放 USB 隧道, 避免进程存活期间 USB 设备被持续占用
                        runCatching { io.github.miuzarte.scrcpyforandroid.nativecore.UsbAdbSession.disconnect() }
                        // 清理连接状态 (ConnectionStateStore/AppRuntime)
                        runCatching {
                            deviceConnectionServices.connectionController.disconnectAdbConnection(
                                cause = DisconnectCause.User,
                                statusLine = "Disconnected",
                            )
                            EventLogger.logEvent("App exit: connections released")
                        }
                    }
                    if (asBundle.clearLogsOnExit) {
                        EventLogger.clearLogs()
                    }
                    activity?.finish()
                }
            }
        }
    }

    BackHandler {
        handleBackNavigation()
    }

    PredictiveBackHandler(enabled = canNavigateBack) { progress ->
        try {
            progress.collect { }
            handleBackNavigation()
        } catch (_: CancellationException) {
            // Gesture was cancelled by the system/user.
        }
    }

    DisposableEffect(scrcpy) {
        val listener: (Int, Int) -> Unit = { width, height ->
            scrcpy.updateCurrentSessionSize(width, height)
        }
        NativeCoreFacade.addVideoSizeListener(listener)
        onDispose {
            NativeCoreFacade.removeVideoSizeListener(listener)
        }
    }

    LaunchedEffect(asBundle.adbKeyName) {
        NativeAdbService.keyName =
            asBundle.adbKeyName.ifBlank { AppSettings.ADB_KEY_NAME.defaultValue }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (!isPagerNavigating && selectedTabIndex != pagerState.currentPage) {
            selectedTabIndex = pagerState.currentPage
        }
    }

    val isCrossActivityStyle = asBundle.navTransitionStyle == 1
    val swipeBackDirection = when {
        !asBundle.swipeBack -> NavSwipeDirection.None
        LocalLayoutDirection.current == LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft
        else -> NavSwipeDirection.LeftToRight
    }
    val navTransition: NavTransition =
        if (isCrossActivityStyle) CrossActivityTransition else NavTransitions.MiuixDefault
    val navCornerRadius = rememberNavSystemCornerRadius()
    val navBackdropColor = colorScheme.surface
    val navEffects = remember(
        isCrossActivityStyle,
        asBundle.swipeBack,
        navCornerRadius,
        navBackdropColor,
    ) {
        NavDisplayEffects(
            enableCornerClip = true,
            cornerClipRadius = navCornerRadius,
            cornerClipMode =
                if (isCrossActivityStyle) NavCornerClipMode.All
                else NavCornerClipMode.Leading,
            dimAmount = 0.5f,
            blockInputDuringTransition = true,
            backdropColor = navBackdropColor,
        )
    }

    val navRootContent: NavEntryBuilder.() -> Unit = {
        entry<RootScreen.Home>(swipeDismiss = swipeBackDirection) {
            val blurBackdrop = rememberBlurBackdrop(enableBlur = asBundle.blur != AppSettings.BlurMode.NONE)
            // 悬浮底栏是否需要采样背后的内容 (液态玻璃和高斯模糊都要)
            val floatingBarBlurActive = asBundle.blur != AppSettings.BlurMode.NONE
            // 液态玻璃
            val floatingBarGlassActive = floatingBarBlurActive && asBundle.floatingBottomBarBlur
            // 液态玻璃关闭时悬浮底栏回退到高斯模糊
            val floatingBarGaussianBlur = floatingBarBlurActive && !asBundle.floatingBottomBarBlur
            val surfaceColor = colorScheme.surface
            val glassBackdrop = rememberLayerBackdrop {
                drawRect(surfaceColor)
                drawContent()
            }

            Scaffold(
                bottomBar = {
                    if (!asBundle.floatingBottomBar) {
                        // 底栏不跟随渐进模糊, 渐进模糊回退到高斯模糊, 没启用模糊则无模糊
                        BlurredBar(backdrop = blurBackdrop, allowProgressive = false) {
                            NavigationBar(
                                color =
                                    if (blurBackdrop != null) Color.Transparent
                                    else colorScheme.surface,
                            ) {
                                tabs.forEach { tab ->
                                    NavigationBarItem(
                                        selected = currentTab == tab,
                                        onClick = {
                                            haptic.contextClick()
                                            navigateToTab(tab)
                                        },
                                        icon = tab.icon,
                                        label = stringResource(tab.labelResId),
                                    )
                                }
                            }
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackHostState) },
            ) { contentPadding ->
                val bottomInnerPadding =
                    if (asBundle.floatingBottomBar)
                        12.dp + 64.dp + contentPadding.calculateBottomPadding()
                    else
                        contentPadding.calculateBottomPadding()

                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (blurBackdrop != null) Modifier.miuixLayerBackdrop(blurBackdrop) else Modifier),
                    ) {
                        val pagerGestureLocked =
                            selectedTabIndex == MainBottomTabDestination.Terminal.ordinal
                                    && terminalGestureLock
                                    || selectedTabIndex == MainBottomTabDestination.Devices.ordinal
                                    && devicePreviewGestureLock

                        HorizontalPager(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (asBundle.floatingBottomBar && floatingBarBlurActive) {
                                        Modifier.miuixLayerBackdrop(glassBackdrop)
                                    } else {
                                        Modifier
                                    },
                                ),
                            state = pagerState,
                            beyondViewportPageCount = 1,
                            userScrollEnabled = !pagerGestureLocked,
                        ) { page ->
                            val tab = tabs[page]
                            saveableStateHolder.SaveableStateProvider(tab.name) {
                                when (tab) {
                                    MainBottomTabDestination.Devices -> DeviceTabScreen(
                                        viewModelFactory = deviceTabViewModelFactory,
                                        scrollBehavior = devicesPageScrollBehavior,
                                        bottomInnerPadding = bottomInnerPadding,
                                        onOpenReorderDevices = { showReorderDevices = true },
                                        onPreviewGestureLockChanged = { locked ->
                                            devicePreviewGestureLock = locked
                                        },
                                        onOpenFullscreenCompat = {
                                            rootNavigator.push(RootScreen.FullscreenControl)
                                        },
                                    )

                                    MainBottomTabDestination.Terminal -> TerminalScreen(
                                        bottomInnerPadding = bottomInnerPadding,
                                        isActive = selectedTabIndex == MainBottomTabDestination.Terminal.ordinal,
                                        onTerminalGestureLockChanged = { locked ->
                                            terminalGestureLock = locked
                                        },
                                    )

                                    MainBottomTabDestination.Files -> FileManagerScreen(
                                        bottomInnerPadding = bottomInnerPadding,
                                        onCanNavigateUpChange = { fileTabCanNavigateUp = it },
                                        onNavigateUpActionChange = { fileTabNavigateUp = it },
                                    )

                                    MainBottomTabDestination.Settings -> SettingsScreen(
                                        scrollBehavior = settingsPageScrollBehavior,
                                        bottomInnerPadding = bottomInnerPadding,
                                        onOpenReorderDevices = { showReorderDevices = true },
                                    )
                                }
                            }
                        }
                    }

                    // 悬浮底栏依赖 InteractiveHighlight, 其内部构造 android.graphics.RuntimeShader (API 33+),
                    // 低版本组合即崩, 故在此拦截, 不依赖设置页的清理
                    if (
                        asBundle.floatingBottomBar &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ) {
                        FloatingBottomBar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp + contentPadding.calculateBottomPadding())
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {},
                                ),
                            selectedIndex = { selectedTabIndex },
                            onSelected = { index ->
                                navigateToTab(tabs[index])
                            },
                            backdrop = glassBackdrop,
                            tabsCount = tabs.size,
                            isBlurEnabled = floatingBarGlassActive,
                            isGaussianBlurEnabled = floatingBarGaussianBlur,
                        ) {
                            tabs.forEach { tab ->
                                FloatingBottomBarItem(
                                    onClick = { navigateToTab(tab) },
                                    modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = stringResource(tab.labelResId),
                                        tint = colorScheme.onSurface,
                                    )
                                    Text(
                                        text = stringResource(tab.labelResId),
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                        color = colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Visible,
                                    )
                                }
                            }
                        }
                    }

                    ReorderDevicesScreen(
                        show = showReorderDevices,
                        onDismissRequest = { showReorderDevices = false },
                    )
                }
            }
        }

        entry<RootScreen.Advanced>(swipeDismiss = swipeBackDirection) {
            ScrcpyAllOptionsScreen(
                scrollBehavior = advancedPageScrollBehavior,
                scrcpy = scrcpy,
            )
        }

        entry<RootScreen.About>(swipeDismiss = swipeBackDirection) {
            AboutScreen()
        }

        entry<RootScreen.ThemeSettings>(swipeDismiss = swipeBackDirection) {
            ThemeSettingsScreen()
        }

        entry<RootScreen.VirtualButtonOrder>(swipeDismiss = swipeBackDirection) {
            VirtualButtonOrderScreen(
                scrollBehavior = advancedPageScrollBehavior,
            )
        }

        entry<RootScreen.FullscreenControl>(swipeDismiss = swipeBackDirection) {
            FullscreenControlRoute(
                scrcpy = scrcpy,
                onBack = rootNavigator.pop,
                isInPip = false,
                autoExitOnStop = true,
            )
        }

        entry<RootScreen.ScrcpyOptionRecord>(swipeDismiss = swipeBackDirection) { route ->
            RecordPreferencesScreen(
                scrollBehavior = advancedPageScrollBehavior,
                profileId = route.profileId,
                scrcpy = scrcpy,
            )
        }
    }

    val themeController = remember(
        asBundle.themeBaseIndex,
        asBundle.monet,
        asBundle.monetSeedIndex,
        asBundle.monetPaletteStyle,
        asBundle.monetColorSpec,
    ) {
        asBundle.createThemeController()
    }

    MiuixTheme(
        controller = themeController,
    ) {
        ApplySystemBarsAppearance(activity?.window)
        CompositionLocalProvider(
            LocalEnableBlur provides (asBundle.blur != AppSettings.BlurMode.NONE),
            LocalBlurMode provides asBundle.blur,
            LocalSquircleEnabled provides asBundle.squircle,
            LocalRootNavigator provides rootNavigator,
            LocalSnackbarController provides snackbarController,
            LocalServerPicker provides serverPicker,
            LocalTerminalFontPicker provides terminalFontPicker,
        ) {
            NavDisplay(
                backStack = rootBackStack,
                onBack = rootNavigator.pop,
                transition = navTransition,
                effects = navEffects,
                content = navRootContent,
            )
        }
    }
}

class ServerPicker(
    val pick: () -> Unit,
)

class TerminalFontPicker(
    val pick: () -> Unit,
)

val LocalServerPicker = staticCompositionLocalOf<ServerPicker> {
    error("No ServerPicker provided")
}

val LocalTerminalFontPicker = staticCompositionLocalOf<TerminalFontPicker> {
    error("No TerminalFontPicker provided")
}
