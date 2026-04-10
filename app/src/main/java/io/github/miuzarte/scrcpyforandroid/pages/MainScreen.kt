package io.github.miuzarte.scrcpyforandroid.pages

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import io.github.miuzarte.scrcpyforandroid.BuildConfig
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.constants.ThemeModes
import io.github.miuzarte.scrcpyforandroid.constants.UiMotion
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcuts
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.services.AppUpdateChecker
import io.github.miuzarte.scrcpyforandroid.services.SnackbarController
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.quickDevices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

private enum class MainBottomTabDestination(
    val label: String,
    val icon: ImageVector,
) {
    Device(label = "设备", icon = Icons.Rounded.Devices),
    Settings(label = "设置", icon = Icons.Rounded.Settings);
}

sealed interface RootScreen : NavKey {
    data object Home : RootScreen
    data object Advanced : RootScreen
    data object About : RootScreen
    data object VirtualButtonOrder : RootScreen
    data object Fullscreen : RootScreen
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    val activity = remember(context) { context as? Activity }
    val initialOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = initialOrientation
        }
    }

    val nativeCore = remember(appContext) {
        NativeCoreFacade.get(appContext)
    }
    val adbService = remember(appContext) {
        NativeAdbService(appContext)
    }

    val snackHostState = remember { SnackbarHostState() }
    val snackbarController = remember(scope, snackHostState) {
        SnackbarController(
            scope = scope,
            hostState = snackHostState,
        )
    }
    val saveableStateHolder = rememberSaveableStateHolder()
    val tabs = remember { MainBottomTabDestination.entries }
    val pagerState = rememberPagerState(
        initialPage = MainBottomTabDestination.Device.ordinal,
        pageCount = { tabs.size })
    val currentTab = tabs[pagerState.currentPage]
    val rootBackStack = remember { mutableStateListOf<NavKey>(RootScreen.Home) }
    val currentRootScreen = rootBackStack.lastOrNull() as? RootScreen ?: RootScreen.Home
    val devicesPageScrollBehavior = MiuixScrollBehavior(
        canScroll = { currentTab == MainBottomTabDestination.Device })
    val settingsPageScrollBehavior = MiuixScrollBehavior(
        canScroll = { currentTab == MainBottomTabDestination.Settings })
    val advancedPageScrollBehavior = MiuixScrollBehavior(
        canScroll = {
            when (currentRootScreen) {
                is RootScreen.Advanced -> true
                is RootScreen.VirtualButtonOrder -> true
                else -> false
            }
        })

    fun popRoot() {
        if (rootBackStack.size > 1)
            rootBackStack.removeAt(rootBackStack.lastIndex)
    }

    // Unified back behavior:
    // 1) pop inner route
    // 2) switch tab back to Device
    // 3) double-back to exit and disconnect adb/scrcpy
    var lastExitBackPressAtMs by rememberSaveable { mutableLongStateOf(0L) }

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

    val customServerUri = asBundle.customServerUri
        .ifBlank { null }
    val customServerVersion = asBundle.customServerVersion
        .ifBlank { Scrcpy.DEFAULT_SERVER_VERSION }
    val serverRemotePath = asBundle.serverRemotePath
        .ifBlank { AppSettings.SERVER_REMOTE_PATH.defaultValue }
    val scrcpy = remember(
        appContext,
        adbService,
        customServerUri,
        customServerVersion,
        serverRemotePath,
    ) {
        Scrcpy(
            appContext = appContext,
            adbService = adbService,
            customServerUri = customServerUri,
            serverVersion = customServerVersion,
            serverRemotePath = serverRemotePath,
        )
    }
    val currentSession by scrcpy.currentSessionState.collectAsState()

    LaunchedEffect(asBundle.lastUpdateCheckAt) {
        val now = System.currentTimeMillis()
        if (now - asBundle.lastUpdateCheckAt < AppUpdateChecker.CHECK_INTERVAL_MS) return@LaunchedEffect
        taskScope.launch {
            appSettings.updateBundle { it.copy(lastUpdateCheckAt = now) }
            AppUpdateChecker.ensureChecked(BuildConfig.VERSION_NAME)
        }
    }

    fun handleBackNavigation() {
        if (rootBackStack.size > 1) {
            popRoot()
        } else if (pagerState.currentPage != MainBottomTabDestination.Device.ordinal) {
            scope.launch {
                pagerState.animateScrollToPage(
                    page = MainBottomTabDestination.Device.ordinal,
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

    var showReorderDevices by rememberSaveable { mutableStateOf(false) }
    var fullscreenOrientation by rememberSaveable {
        mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    }

    val canNavigateBack = rootBackStack.size > 1
            || pagerState.currentPage != MainBottomTabDestination.Device.ordinal

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

    // Fullscreen route can force orientation based on stream ratio; all other routes are portrait.
    LaunchedEffect(activity, currentRootScreen, fullscreenOrientation) {
        val targetOrientation = when (currentRootScreen) {
            is RootScreen.Fullscreen -> fullscreenOrientation
            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        activity?.requestedOrientation = targetOrientation
    }

    DisposableEffect(nativeCore, scrcpy) {
        val listener: (Int, Int) -> Unit = { width, height ->
            scrcpy.updateCurrentSessionSize(width, height)
        }
        nativeCore.addVideoSizeListener(listener)
        onDispose {
            nativeCore.removeVideoSizeListener(listener)
        }
    }

    LaunchedEffect(currentRootScreen, currentSession?.width, currentSession?.height) {
        if (currentRootScreen is RootScreen.Fullscreen) {
            val session = currentSession ?: return@LaunchedEffect
            fullscreenOrientation =
                if (session.width >= session.height) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
        }
    }

    LaunchedEffect(asBundle.adbKeyName) {
        adbService.keyName = asBundle.adbKeyName.ifBlank { AppSettings.ADB_KEY_NAME.defaultValue }
    }
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
                            MainBottomTabDestination.Device -> DeviceTabScreen(
                                nativeCore = nativeCore,
                                adbService = adbService,
                                scrcpy = scrcpy,
                                snackbar = snackbarController,
                                scrollBehavior = devicesPageScrollBehavior,
                                onOpenVirtualButtonOrder = { rootBackStack.add(RootScreen.VirtualButtonOrder) },
                                onOpenReorderDevices = { showReorderDevices = true },
                                onOpenAdvancedPage = { rootBackStack.add(RootScreen.Advanced) },
                                onOpenFullscreenPage = {
                                    currentSession?.let { session ->
                                        fullscreenOrientation =
                                            if (session.width >= session.height) {
                                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                            } else {
                                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                            }
                                        rootBackStack.add(RootScreen.Fullscreen)
                                    }
                                },
                            )

                            MainBottomTabDestination.Settings -> SettingsScreen(
                                scrollBehavior = settingsPageScrollBehavior,
                                snackbar = snackbarController,
                                onOpenReorderDevices = { showReorderDevices = true },
                                onOpenVirtualButtonOrder = { rootBackStack.add(RootScreen.VirtualButtonOrder) },
                                onOpenAbout = { rootBackStack.add(RootScreen.About) },
                                onPickServer = {
                                    picker.launch(
                                        arrayOf(
                                            "application/java-archive",
                                            "application/octet-stream",
                                            "*/*"
                                        )
                                    )
                                },
                            )
                        }
                    }
                }

                ReorderDevicesScreen(
                    show = showReorderDevices,
                    onDismissRequest = { showReorderDevices = false },
                )
            }
        }

        entry(RootScreen.Advanced) {
            ScrcpyAllOptionsScreen(
                onBack = ::popRoot,
                scrollBehavior = advancedPageScrollBehavior,
                snackbar = snackbarController,
                scrcpy = scrcpy,
            )
        }

        entry(RootScreen.About) {
            AboutScreen(
                onBack = ::popRoot,
            )
        }

        entry(RootScreen.VirtualButtonOrder) {
            VirtualButtonOrderScreen(
                onBack = ::popRoot,
                scrollBehavior = advancedPageScrollBehavior,
            )
        }

        entry(RootScreen.Fullscreen) {
            FullscreenControlScreen(
                onBack = ::popRoot,
                scrcpy = scrcpy,
                nativeCore = nativeCore,
                onVideoSizeChanged = { width, height ->
                    fullscreenOrientation =
                        if (width >= height) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                },
            )
        }
    }

    val rootEntries = rememberDecoratedNavEntries(
        backStack = rootBackStack,
        entryProvider = rootEntryProvider,
    )

    val themeMode = when (asBundle.themeBaseIndex.coerceIn(0, ThemeModes.baseOptions.lastIndex)) {
        1 -> if (!asBundle.monet) ColorSchemeMode.Light else ColorSchemeMode.MonetLight
        2 -> if (!asBundle.monet) ColorSchemeMode.Dark else ColorSchemeMode.MonetDark
        else -> if (!asBundle.monet) ColorSchemeMode.System else ColorSchemeMode.MonetSystem
    }
    val themeController = remember(themeMode) { ThemeController(colorSchemeMode = themeMode) }

    MiuixTheme(controller = themeController) {
        NavDisplay(
            entries = rootEntries,
            onBack = ::popRoot,
        )
    }
}
