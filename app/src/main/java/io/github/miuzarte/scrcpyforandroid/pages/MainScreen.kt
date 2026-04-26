package io.github.miuzarte.scrcpyforandroid.pages

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.miuzarte.scrcpyforandroid.BuildConfig
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.constants.UiMotion
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.AppScreenOn
import io.github.miuzarte.scrcpyforandroid.services.AppUpdateChecker
import io.github.miuzarte.scrcpyforandroid.services.ConnectionController
import io.github.miuzarte.scrcpyforandroid.services.ConnectionStateStore
import io.github.miuzarte.scrcpyforandroid.services.DeviceAdbAutoReconnectManager
import io.github.miuzarte.scrcpyforandroid.services.DeviceAdbConnectionCoordinator
import io.github.miuzarte.scrcpyforandroid.services.EventLogger
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.services.SnackbarController
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.quickDevices
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableFloatingBottomBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableFloatingBottomBarBlur
import io.github.miuzarte.scrcpyforandroid.ui.component.FloatingBottomBar
import io.github.miuzarte.scrcpyforandroid.ui.component.FloatingBottomBarItem
import io.github.miuzarte.scrcpyforandroid.ui.createThemeController
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import java.io.File
import top.yukonga.miuix.kmp.blur.layerBackdrop as miuixLayerBackdrop

private const val TERMINAL_FONT_RELATIVE_PATH = "terminal/font.ttf"

private fun terminalFontFile(context: android.content.Context): File {
    return File(context.filesDir, TERMINAL_FONT_RELATIVE_PATH)
}

private fun copyTerminalFontToPrivate(context: android.content.Context, uri: Uri) {
    val target = terminalFontFile(context)
    target.parentFile?.mkdirs()
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "无法读取字体文件" }
        target.outputStream().use { output ->
            input.copyTo(output)
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
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
    val label: String,
    val icon: ImageVector,
) {
    Devices(label = "设备", icon = Icons.Rounded.Devices),
    Terminal(label = "终端", icon = Icons.Rounded.Terminal),
    Files(label = "文件", icon = Icons.Rounded.Folder),
    Settings(label = "设置", icon = Icons.Rounded.Settings);
}

sealed interface RootScreen : NavKey {
    data object Home : RootScreen
    data object Advanced : RootScreen
    data object About : RootScreen
    data object VirtualButtonOrder : RootScreen
    data object FullscreenControl : RootScreen // compatibility mode
    data class ScrcpyOptionRecord(val profileId: String) : RootScreen
}

@Composable
fun MainScreen() {
    // Environment
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

    // Root navigation and UI chrome state
    val saveableStateHolder = rememberSaveableStateHolder()
    val tabs = remember { MainBottomTabDestination.entries }
    val pagerState = rememberPagerState(
        initialPage = MainBottomTabDestination.Devices.ordinal,
        pageCount = { tabs.size })
    var selectedTabIndex by rememberSaveable {
        mutableIntStateOf(MainBottomTabDestination.Devices.ordinal)
    }
    var pagerNavigationJob by remember { mutableStateOf<Job?>(null) }
    var isPagerNavigating by remember { mutableStateOf(false) }
    val currentTab = tabs[selectedTabIndex]
    val rootBackStack = remember { mutableStateListOf<NavKey>(RootScreen.Home) }
    val currentRootScreen = rootBackStack.lastOrNull() as? RootScreen ?: RootScreen.Home
    var showReorderDevices by rememberSaveable { mutableStateOf(false) }
    var lastExitBackPressAtMs by rememberSaveable { mutableLongStateOf(0L) }
    var fileTabCanNavigateUp by remember { mutableStateOf(false) }
    var fileTabNavigateUp by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var terminalGestureLock by remember { mutableStateOf(false) }
    var devicePreviewGestureLock by remember { mutableStateOf(false) }

    // Scroll behaviors
    val devicesPageScrollBehavior = MiuixScrollBehavior(
        canScroll = { currentTab == MainBottomTabDestination.Devices })
    val terminalPageScrollBehavior = MiuixScrollBehavior(
        canScroll = { currentTab == MainBottomTabDestination.Terminal })
    val settingsPageScrollBehavior = MiuixScrollBehavior(
        canScroll = { currentTab == MainBottomTabDestination.Settings })
    val advancedPageScrollBehavior = MiuixScrollBehavior(
        canScroll = {
            when (currentRootScreen) {
                is RootScreen.Advanced -> true
                is RootScreen.VirtualButtonOrder -> true
                is RootScreen.ScrcpyOptionRecord -> true
                else -> false
            }
        })

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

    val currentSession by scrcpy.currentSessionState.collectAsState()
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
    DisposableEffect(deviceConnectionServices) {
        onDispose {
            deviceConnectionServices.autoReconnectManager.close()
            AppScreenOn.release()
        }
    }

    // Side-effect launchers and composition locals
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
        asBundle = asBundle.copy(customServerUri = uri.toString())
    }
    val serverPicker = remember(picker) {
        ServerPicker {
            picker.launch(
                arrayOf(
                    "application/java-archive",
                    "application/octet-stream",
                    "*/*"
                )
            )
        }
    }
    val terminalFontDocumentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
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
                    snackbarController.show("终端字体导入成功")
                }.onFailure { error ->
                    snackbarController.show(
                        "终端字体导入失败: ${error.message ?: error.javaClass.simpleName}"
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
                )
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
                    Toast.makeText(context, "再按一次返回键退出", Toast.LENGTH_SHORT).show()
                    return
                }
                lastExitBackPressAtMs = 0L
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { scrcpy.stop() }
                        runCatching { NativeAdbService.disconnect() }
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

    val rootEntryProvider = entryProvider<NavKey> {
        entry(RootScreen.Home) {
            val blurBackdrop = rememberBlurBackdrop(enableBlur = asBundle.blur)
            val floatingBarBlurActive = asBundle.blur && asBundle.floatingBottomBarBlur
            val surfaceColor = colorScheme.surface
            val glassBackdrop = rememberLayerBackdrop {
                drawRect(surfaceColor)
                drawContent()
            }

            Scaffold(
                bottomBar = {
                    if (!asBundle.floatingBottomBar) {
                        BlurredBar(backdrop = blurBackdrop) {
                            NavigationBar(
                                color =
                                    if (blurBackdrop != null) Color.Transparent
                                    else colorScheme.surface,
                            ) {
                                tabs.forEach { tab ->
                                    NavigationBarItem(
                                        selected = currentTab == tab,
                                        onClick = { navigateToTab(tab) },
                                        icon = tab.icon,
                                        label = tab.label,
                                    )
                                }
                            }
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackHostState) },
            ) { contentPadding ->
                val bottomInnerPadding: Dp = if (asBundle.floatingBottomBar) {
                    12.dp + 64.dp + contentPadding.calculateBottomPadding()
                } else {
                    contentPadding.calculateBottomPadding()
                }

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
                                        Modifier.layerBackdrop(glassBackdrop)
                                    } else {
                                        Modifier
                                    }
                                ),
                            state = pagerState,
                            beyondViewportPageCount = 1,
                            userScrollEnabled = !pagerGestureLocked,
                        ) { page ->
                            val tab = tabs[page]
                            saveableStateHolder.SaveableStateProvider(tab.name) {
                                when (tab) {
                                    MainBottomTabDestination.Devices -> DeviceTabScreen(
                                        scrollBehavior = devicesPageScrollBehavior,
                                        scrcpy = scrcpy,
                                        connectionServices = deviceConnectionServices,
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

                    if (asBundle.floatingBottomBar) {
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
                            isBlurEnabled = floatingBarBlurActive,
                        ) {
                            tabs.forEach { tab ->
                                FloatingBottomBarItem(
                                    onClick = { navigateToTab(tab) },
                                    modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        tint = colorScheme.onSurface,
                                    )
                                    Text(
                                        text = tab.label,
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

        entry(RootScreen.Advanced) {
            ScrcpyAllOptionsScreen(
                scrollBehavior = advancedPageScrollBehavior,
                scrcpy = scrcpy,
            )
        }

        entry(RootScreen.About) {
            AboutScreen()
        }

        entry(RootScreen.VirtualButtonOrder) {
            VirtualButtonOrderScreen(
                scrollBehavior = advancedPageScrollBehavior,
            )
        }

        entry(RootScreen.FullscreenControl) {
            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(currentSession) {
                if (currentSession == null) rootNavigator.pop()
            }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        rootNavigator.pop()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }
            DisposableEffect(activity) {
                onDispose {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
            FullscreenControlScreen(
                scrcpy = scrcpy,
                onBack = rootNavigator.pop,
                isInPip = false,
                onVideoSizeChanged = { width, height ->
                    activity?.requestedOrientation =
                        if (width >= height) {
                            if (asBundle.fullscreenControlIgnoreSystemRotationLock)
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            else
                                ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                        } else {
                            if (asBundle.fullscreenControlIgnoreSystemRotationLock)
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            else
                                ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                        }
                },
                onVideoBoundsInWindowChanged = {},
            )
        }

        entry<RootScreen.ScrcpyOptionRecord> { route ->
            RecordPreferencesScreen(
                scrollBehavior = advancedPageScrollBehavior,
                profileId = route.profileId,
                scrcpy = scrcpy,
            )
        }
    }

    val rootEntries = rememberDecoratedNavEntries(
        backStack = rootBackStack,
        entryProvider = rootEntryProvider,
    )

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
        smoothRounding = asBundle.smoothCorner,
    ) {
        CompositionLocalProvider(
            LocalEnableBlur provides asBundle.blur,
            LocalEnableFloatingBottomBar provides asBundle.floatingBottomBar,
            LocalEnableFloatingBottomBarBlur provides asBundle.floatingBottomBarBlur,
            LocalRootNavigator provides rootNavigator,
            LocalSnackbarController provides snackbarController,
            LocalServerPicker provides serverPicker,
            LocalTerminalFontPicker provides terminalFontPicker,
        ) {
            NavDisplay(
                entries = rootEntries,
                onBack = rootNavigator.pop,
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
