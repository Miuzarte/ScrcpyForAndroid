package io.github.miuzarte.scrcpyforandroid.pages

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
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
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.miuzarte.scrcpyforandroid.BuildConfig
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.constants.ThemeModes
import io.github.miuzarte.scrcpyforandroid.constants.UiMotion
import io.github.miuzarte.scrcpyforandroid.haptics.LocalAppHaptics
import io.github.miuzarte.scrcpyforandroid.haptics.rememberAppHaptics
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.AppUpdateChecker
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
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.blur.layerBackdrop as miuixLayerBackdrop

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
    val haptics = rememberAppHaptics()

    // Root navigation and UI chrome state
    val saveableStateHolder = rememberSaveableStateHolder()
    val tabs = remember { MainBottomTabDestination.entries }
    val pagerState = rememberPagerState(
        initialPage = MainBottomTabDestination.Device.ordinal,
        pageCount = { tabs.size })
    val currentTab = tabs[pagerState.currentPage]
    val rootBackStack = remember { mutableStateListOf<NavKey>(RootScreen.Home) }
    val currentRootScreen = rootBackStack.lastOrNull() as? RootScreen ?: RootScreen.Home
    var showReorderDevices by rememberSaveable { mutableStateOf(false) }
    var lastExitBackPressAtMs by rememberSaveable { mutableLongStateOf(0L) }

    // Scroll behaviors
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
    val scrcpy = remember(
        appContext,
        customServerUri,
        customServerVersion,
        serverRemotePath,
    ) {
        Scrcpy(
            appContext = appContext,
            customServerUri = customServerUri,
            serverVersion = customServerVersion,
            serverRemotePath = serverRemotePath,
        ).also {
            AppRuntime.scrcpy = it
        }
    }

    val currentSession by scrcpy.currentSessionState.collectAsState()

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

    // Derived flags
    val canNavigateBack = rootBackStack.size > 1
            || pagerState.currentPage != MainBottomTabDestination.Device.ordinal

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
            rootNavigator.pop()
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
                    runCatching { NativeAdbService.disconnect() }
                }
                activity?.finish()
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

    val rootEntryProvider = entryProvider<NavKey> {
        entry(RootScreen.Home) {
            val blurBackdrop = rememberBlurBackdrop(enableBlur = asBundle.blur)
            val floatingBarBlurActive = asBundle.blur && asBundle.floatingBottomBarBlur
            val surfaceColor = colorScheme.surface
            val glassBackdrop = rememberLayerBackdrop {
                drawRect(surfaceColor)
                drawContent()
            }

            fun navigateToTab(tab: MainBottomTabDestination) {
                scope.launch {
                    pagerState.animateScrollToPage(
                        page = tab.ordinal,
                        animationSpec = spring(
                            dampingRatio = UiMotion.PAGE_SWITCH_DAMPING_RATIO,
                            stiffness = UiMotion.PAGE_SWITCH_STIFFNESS,
                        ),
                    )
                }
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
                        ) { page ->
                            val tab = tabs[page]
                            saveableStateHolder.SaveableStateProvider(tab.name) {
                                when (tab) {
                                    MainBottomTabDestination.Device -> DeviceTabScreen(
                                        scrollBehavior = devicesPageScrollBehavior,
                                        scrcpy = scrcpy,
                                        bottomInnerPadding = bottomInnerPadding,
                                        onOpenReorderDevices = { showReorderDevices = true },
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
                            selectedIndex = { pagerState.currentPage },
                            onSelected = { index ->
                                scope.launch {
                                    pagerState.animateScrollToPage(
                                        page = index,
                                        animationSpec = spring(
                                            dampingRatio = UiMotion.PAGE_SWITCH_DAMPING_RATIO,
                                            stiffness = UiMotion.PAGE_SWITCH_STIFFNESS,
                                        ),
                                    )
                                }
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
            LocalAppHaptics provides haptics,
            LocalServerPicker provides serverPicker,
        ) {
            NavDisplay(
                entries = rootEntries,
                onBack = rootNavigator.pop,
            )
        }
    }
}
