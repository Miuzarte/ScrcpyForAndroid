package io.github.miuzarte.scrcpyforandroid.pages

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.KeyEvent
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.password.PasswordPickerPopupContent
import io.github.miuzarte.scrcpyforandroid.scrcpy.ClientOptions
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.scrcpy.TouchEventHandler
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.widgets.AppListBottomSheet
import io.github.miuzarte.scrcpyforandroid.widgets.AppListEntry
import io.github.miuzarte.scrcpyforandroid.widgets.ScrcpyVideoSurface
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonAction
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonActions
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.Text

@SuppressLint("NewApi")
@Composable
fun FullscreenControlScreen(
    scrcpy: Scrcpy,
    onBack: () -> Unit,
    isInPip: Boolean,
    onVideoSizeChanged: (width: Int, height: Int) -> Unit,
    onVideoBoundsInWindowChanged: (Rect?) -> Unit,
) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val fragmentActivity = remember(activity) { activity as? FragmentActivity }

    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    val snackbar = LocalSnackbarController.current
    val currentSession by scrcpy.currentSessionState.collectAsState()
    val listingsRefreshBusy by scrcpy.listings.refreshBusyState.collectAsState()
    val listingsRefreshVersion by scrcpy.listings.refreshVersionState.collectAsState()

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

    val buttonItems = remember(asBundle.virtualButtonsLayout) {
        VirtualButtonActions.splitLayout(
            VirtualButtonActions.parseStoredLayout(asBundle.virtualButtonsLayout)
        )
    }
    val floatingActions = remember(buttonItems) {
        (buttonItems.first + buttonItems.second).filter { it != VirtualButtonAction.MORE }
    }
    val fullscreenDebugInfo = asBundle.fullscreenDebugInfo
    val showFullscreenVirtualButtons = asBundle.showFullscreenVirtualButtons
    val fullscreenVirtualButtonHeight = asBundle.fullscreenVirtualButtonHeightDp.dp
    val fullscreenVirtualButtonDockSetting = remember(asBundle.fullscreenVirtualButtonDock) {
        AppSettings.FullscreenVirtualButtonDock.fromStoredValue(
            asBundle.fullscreenVirtualButtonDock
        )
    }
    var displayRotation by remember(activity) {
        mutableIntStateOf(activity?.display?.rotation ?: Surface.ROTATION_0)
    }
    DisposableEffect(activity, context) {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        if (displayManager == null || activity == null) {
            onDispose {}
        } else {
            val listener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = Unit

                override fun onDisplayRemoved(displayId: Int) = Unit

                override fun onDisplayChanged(displayId: Int) {
                    if (displayId == activity.display?.displayId) {
                        displayRotation = activity.display?.rotation ?: Surface.ROTATION_0
                    }
                }
            }
            displayRotation = activity.display?.rotation ?: Surface.ROTATION_0
            displayManager.registerDisplayListener(listener, null)
            onDispose {
                displayManager.unregisterDisplayListener(listener)
            }
        }
    }
    val fullscreenVirtualButtonPhysicalDock = remember(fullscreenVirtualButtonDockSetting) {
        when (fullscreenVirtualButtonDockSetting) {
            AppSettings.FullscreenVirtualButtonDock.FOLLOW_TOP,
            AppSettings.FullscreenVirtualButtonDock.FOLLOW_BOTTOM,
            AppSettings.FullscreenVirtualButtonDock.FOLLOW_LEFT,
            AppSettings.FullscreenVirtualButtonDock.FOLLOW_RIGHT -> null

            AppSettings.FullscreenVirtualButtonDock.FIXED_TOP -> VirtualButtonBar.FullscreenDock.TOP
            AppSettings.FullscreenVirtualButtonDock.FIXED_BOTTOM -> VirtualButtonBar.FullscreenDock.BOTTOM
            AppSettings.FullscreenVirtualButtonDock.FIXED_LEFT -> VirtualButtonBar.FullscreenDock.LEFT
            AppSettings.FullscreenVirtualButtonDock.FIXED_RIGHT -> VirtualButtonBar.FullscreenDock.RIGHT
        }
    }
    val fullscreenVirtualButtonDock = remember(
        fullscreenVirtualButtonDockSetting,
        displayRotation,
    ) {
        when (fullscreenVirtualButtonDockSetting) {
            AppSettings.FullscreenVirtualButtonDock.FOLLOW_TOP -> VirtualButtonBar.FullscreenDock.TOP
            AppSettings.FullscreenVirtualButtonDock.FOLLOW_BOTTOM -> VirtualButtonBar.FullscreenDock.BOTTOM
            AppSettings.FullscreenVirtualButtonDock.FOLLOW_LEFT -> VirtualButtonBar.FullscreenDock.LEFT
            AppSettings.FullscreenVirtualButtonDock.FOLLOW_RIGHT -> VirtualButtonBar.FullscreenDock.RIGHT
            AppSettings.FullscreenVirtualButtonDock.FIXED_TOP -> dockForFixedPhysicalEdge(
                physicalDock = VirtualButtonBar.FullscreenDock.TOP,
                displayRotation = displayRotation,
            )

            AppSettings.FullscreenVirtualButtonDock.FIXED_BOTTOM -> dockForFixedPhysicalEdge(
                physicalDock = VirtualButtonBar.FullscreenDock.BOTTOM,
                displayRotation = displayRotation,
            )

            AppSettings.FullscreenVirtualButtonDock.FIXED_LEFT -> dockForFixedPhysicalEdge(
                physicalDock = VirtualButtonBar.FullscreenDock.LEFT,
                displayRotation = displayRotation,
            )

            AppSettings.FullscreenVirtualButtonDock.FIXED_RIGHT -> dockForFixedPhysicalEdge(
                physicalDock = VirtualButtonBar.FullscreenDock.RIGHT,
                displayRotation = displayRotation,
            )
        }
    }
    val fullscreenVirtualButtonReverseOrder = remember(
        fullscreenVirtualButtonPhysicalDock,
        displayRotation,
    ) {
        fullscreenVirtualButtonPhysicalDock
            ?.let { physicalDock ->
                isFixedDockOrderReversed(
                    physicalDock = physicalDock,
                    displayRotation = displayRotation,
                )
            }
            ?: false
    }

    val bar = remember(buttonItems) {
        VirtualButtonBar(
            outsideActions = buttonItems.first,
            moreActions = buttonItems.second,
        )
    }
    val recentTasks = remember(listingsRefreshVersion) { scrcpy.listings.recentTasks }
    val apps = remember(listingsRefreshVersion) { scrcpy.listings.apps }

    var currentFps by remember { mutableFloatStateOf(0f) }
    var showRecentTasksSheet by rememberSaveable { mutableStateOf(false) }
    var showAllAppsSheet by rememberSaveable { mutableStateOf(false) }
    var imeRequestToken by rememberSaveable { mutableIntStateOf(0) }

    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            val restoreWindow = activity?.window
            if (restoreWindow != null) {
                WindowInsetsControllerCompat(restoreWindow, restoreWindow.decorView).show(
                    WindowInsetsCompat.Type.systemBars()
                )
                WindowCompat.setDecorFitsSystemWindows(restoreWindow, true)
            }
        }
    }

    LaunchedEffect(currentSession?.width, currentSession?.height) {
        val session = currentSession ?: return@LaunchedEffect
        onVideoSizeChanged(session.width, session.height)
    }

    DisposableEffect(Unit) {
        val listener: (Float) -> Unit = { fps ->
            currentFps = fps
        }
        NativeCoreFacade.addVideoFpsListener(listener)
        onDispose {
            NativeCoreFacade.removeVideoFpsListener(listener)
        }
    }

    suspend fun sendKeycode(keycode: Int) {
        runCatching {
            withContext(Dispatchers.IO) {
                scrcpy.injectKeycode(0, keycode)
                scrcpy.injectKeycode(1, keycode)
            }
        }.onFailure { e ->
            Log.w(
                "FullscreenControlPage",
                "sendKeycode failed for keycode=$keycode",
                e
            )
        }
    }

    suspend fun sendBackOrTurnScreenOn() {
        runCatching {
            withContext(Dispatchers.IO) {
                scrcpy.pressBackOrTurnScreenOn(KeyEvent.ACTION_DOWN)
                scrcpy.pressBackOrTurnScreenOn(KeyEvent.ACTION_UP)
            }
        }.onFailure { e ->
            Log.w("FullscreenControlPage", "send back failed", e)
        }
    }

    BackHandler(enabled = true) {
        if (asBundle.fullscreenControlBackToDevice && currentSession != null)
            taskScope.launch {
                sendBackOrTurnScreenOn()
            }
        else onBack()
    }

    suspend fun refreshApps() {
        runCatching {
            withContext(Dispatchers.IO) {
                scrcpy.listings.getApps(forceRefresh = true)
            }
        }.onFailure { error ->
            snackbar.show("获取应用列表失败")
            Log.w("FullscreenControlPage", "refreshApps failed", error)
        }
    }

    suspend fun refreshRecentTasks() {
        runCatching {
            withContext(Dispatchers.IO) {
                scrcpy.listings.getRecentTasks(forceRefresh = true)
            }
        }.onFailure { error ->
            snackbar.show("获取最近任务失败")
            Log.w("FullscreenControlPage", "refreshRecentTasks failed", error)
        }
    }

    suspend fun pasteLocalClipboard() {
        val session = currentSession ?: return
        val text = io.github.miuzarte.scrcpyforandroid.services.LocalInputService.getClipboardText(
            activity ?: return
        )
            ?.takeIf { it.isNotBlank() }
        if (text == null) {
            snackbar.show("本机剪贴板为空或不是文本")
            return
        }
        val useLegacyPaste = session.legacyPaste
        runCatching {
            withContext(Dispatchers.IO) {
                if (useLegacyPaste) {
                    scrcpy.injectText(text)
                } else {
                    scrcpy.setClipboard(text, paste = true)
                }
            }
        }.onFailure { error ->
            Log.w("FullscreenControlPage", "pasteLocalClipboard failed", error)
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
            keyInjectMode = currentSession?.keyInjectMode ?: ClientOptions.KeyInjectMode.MIXED,
        ) { error, useClipboardPaste ->
            Log.w("FullscreenControlPage", "commitImeText failed", error)
            snackbar.show(
                if (useClipboardPaste) "非 ASCII 文本粘贴失败"
                else "文本输入失败"
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar.hostState) },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            val session = currentSession ?: return@Box
            FullscreenControlPage(
                scrcpy = scrcpy,
                session = session,
                onDismiss = onBack,
                showDebugInfo = fullscreenDebugInfo && !isInPip,
                currentFps = currentFps,
                imeRequestToken = imeRequestToken,
                enableBackHandler = false,
                interactive = !isInPip,
                onVideoBoundsInWindowChanged = onVideoBoundsInWindowChanged,
                onImeCommitText = ::commitImeText,
                onInjectTouch = { action, pointerId, x, y, pressure, actionButton, buttons ->
                    withContext(Dispatchers.IO) {
                        scrcpy.injectTouch(
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
                },
                onBackOrScreenOn = { action ->
                    withContext(Dispatchers.IO) {
                        scrcpy.pressBackOrTurnScreenOn(action)
                    }
                },
            )

            if (showFullscreenVirtualButtons && !isInPip) {
                bar.Fullscreen(
                    modifier = Modifier.align(
                        when (fullscreenVirtualButtonDock) {
                            VirtualButtonBar.FullscreenDock.TOP -> Alignment.TopCenter
                            VirtualButtonBar.FullscreenDock.BOTTOM -> Alignment.BottomCenter
                            VirtualButtonBar.FullscreenDock.LEFT -> Alignment.CenterStart
                            VirtualButtonBar.FullscreenDock.RIGHT -> Alignment.CenterEnd
                        }
                    ),
                    dock = fullscreenVirtualButtonDock,
                    reverseOrder = fullscreenVirtualButtonReverseOrder,
                    thickness = fullscreenVirtualButtonHeight,
                    onAction = { action ->
                        when (action) {
                            VirtualButtonAction.RECENT_TASKS -> {
                                showRecentTasksSheet = true
                                if (recentTasks.isEmpty() && !listingsRefreshBusy) {
                                    refreshApps()
                                    refreshRecentTasks()
                                }
                            }

                            VirtualButtonAction.ALL_APPS -> {
                                showAllAppsSheet = true
                                if (apps.isEmpty() && !listingsRefreshBusy) {
                                    refreshApps()
                                }
                            }

                            VirtualButtonAction.TOGGLE_IME -> {
                                imeRequestToken++
                            }

                            VirtualButtonAction.PASTE_LOCAL_CLIPBOARD -> {
                                pasteLocalClipboard()
                            }

                            else -> {
                                action.keycode?.let { sendKeycode(it) }
                            }
                        }
                    },
                    passwordPopupContent = if (fragmentActivity == null) {
                        null
                    } else {
                        { onDismissRequest ->
                            PasswordPickerPopupContent(
                                onDismissRequest = onDismissRequest,
                            )
                        }
                    },
                )
            }

            if (asBundle.showFullscreenFloatingButton && !isInPip) {
                bar.FloatingBall(
                    actions = floatingActions,
                    modifier = Modifier.fillMaxSize(),
                    onAction = { action ->
                        when (action) {
                            VirtualButtonAction.RECENT_TASKS -> {
                                showRecentTasksSheet = true
                                if (recentTasks.isEmpty() && !listingsRefreshBusy) {
                                    refreshApps()
                                    refreshRecentTasks()
                                }
                            }

                            VirtualButtonAction.ALL_APPS -> {
                                showAllAppsSheet = true
                                if (apps.isEmpty() && !listingsRefreshBusy) {
                                    refreshApps()
                                }
                            }

                            VirtualButtonAction.TOGGLE_IME -> {
                                imeRequestToken++
                            }

                            VirtualButtonAction.PASTE_LOCAL_CLIPBOARD -> {
                                pasteLocalClipboard()
                            }

                            else -> {
                                action.keycode?.let { sendKeycode(it) }
                            }
                        }
                    },
                    passwordPopupContent =
                        if (fragmentActivity == null) null
                        else { onDismissRequest ->
                            PasswordPickerPopupContent(
                                onDismissRequest = onDismissRequest,
                            )
                        },
                )
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
                            taskScope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        scrcpy.startApp(task.packageName)
                                    }
                                }.onFailure { error ->
                                    snackbar.show("启动应用失败: ${error.message ?: error.javaClass.simpleName}")
                                }
                            }
                        },
                    )
                },
                refreshBusy = listingsRefreshBusy,
                onDismissRequest = { showRecentTasksSheet = false },
                onRefresh = {
                    taskScope.launch(Dispatchers.Main) {
                        refreshApps()
                        refreshRecentTasks()
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
                            taskScope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        scrcpy.startApp(app.packageName)
                                    }
                                }.onFailure { error ->
                                    snackbar.show("启动应用失败: ${error.message ?: error.javaClass.simpleName}")
                                }
                            }
                        },
                    )
                },
                refreshBusy = listingsRefreshBusy,
                onDismissRequest = { showAllAppsSheet = false },
                onRefresh = {
                    taskScope.launch(Dispatchers.Main) {
                        refreshApps()
                    }
                },
            )

        }
    }
}

private fun dockForFixedPhysicalEdge(
    physicalDock: VirtualButtonBar.FullscreenDock,
    displayRotation: Int,
) = when (displayRotation) {
    Surface.ROTATION_0 -> physicalDock
    Surface.ROTATION_90 -> when (physicalDock) {
        VirtualButtonBar.FullscreenDock.TOP -> VirtualButtonBar.FullscreenDock.LEFT
        VirtualButtonBar.FullscreenDock.BOTTOM -> VirtualButtonBar.FullscreenDock.RIGHT
        VirtualButtonBar.FullscreenDock.LEFT -> VirtualButtonBar.FullscreenDock.BOTTOM
        VirtualButtonBar.FullscreenDock.RIGHT -> VirtualButtonBar.FullscreenDock.TOP
    }

    Surface.ROTATION_180 -> when (physicalDock) {
        VirtualButtonBar.FullscreenDock.TOP -> VirtualButtonBar.FullscreenDock.BOTTOM
        VirtualButtonBar.FullscreenDock.BOTTOM -> VirtualButtonBar.FullscreenDock.TOP
        VirtualButtonBar.FullscreenDock.LEFT -> VirtualButtonBar.FullscreenDock.RIGHT
        VirtualButtonBar.FullscreenDock.RIGHT -> VirtualButtonBar.FullscreenDock.LEFT
    }

    Surface.ROTATION_270 -> when (physicalDock) {
        VirtualButtonBar.FullscreenDock.TOP -> VirtualButtonBar.FullscreenDock.RIGHT
        VirtualButtonBar.FullscreenDock.BOTTOM -> VirtualButtonBar.FullscreenDock.LEFT
        VirtualButtonBar.FullscreenDock.LEFT -> VirtualButtonBar.FullscreenDock.TOP
        VirtualButtonBar.FullscreenDock.RIGHT -> VirtualButtonBar.FullscreenDock.BOTTOM
    }

    else -> physicalDock
}

private enum class DockDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    TOP_TO_BOTTOM,
    BOTTOM_TO_TOP,
}

private fun isFixedDockOrderReversed(
    physicalDock: VirtualButtonBar.FullscreenDock,
    displayRotation: Int,
): Boolean {
    val visualDock = dockForFixedPhysicalEdge(
        physicalDock = physicalDock,
        displayRotation = displayRotation,
    )
    val visualDirection = rotateDockDirection(
        direction = physicalDockBaseDirection(physicalDock),
        displayRotation = displayRotation,
    )
    return when (visualDock) {
        VirtualButtonBar.FullscreenDock.TOP,
        VirtualButtonBar.FullscreenDock.BOTTOM -> visualDirection == DockDirection.RIGHT_TO_LEFT

        VirtualButtonBar.FullscreenDock.LEFT,
        VirtualButtonBar.FullscreenDock.RIGHT -> visualDirection == DockDirection.BOTTOM_TO_TOP
    }
}

private fun physicalDockBaseDirection(
    physicalDock: VirtualButtonBar.FullscreenDock,
) = when (physicalDock) {
    VirtualButtonBar.FullscreenDock.TOP -> DockDirection.RIGHT_TO_LEFT
    VirtualButtonBar.FullscreenDock.BOTTOM -> DockDirection.LEFT_TO_RIGHT
    VirtualButtonBar.FullscreenDock.LEFT -> DockDirection.TOP_TO_BOTTOM
    VirtualButtonBar.FullscreenDock.RIGHT -> DockDirection.BOTTOM_TO_TOP
}

private fun rotateDockDirection(
    direction: DockDirection,
    displayRotation: Int,
) = when (displayRotation) {
    Surface.ROTATION_0 -> direction
    Surface.ROTATION_90 -> when (direction) {
        DockDirection.LEFT_TO_RIGHT -> DockDirection.BOTTOM_TO_TOP
        DockDirection.RIGHT_TO_LEFT -> DockDirection.TOP_TO_BOTTOM
        DockDirection.TOP_TO_BOTTOM -> DockDirection.LEFT_TO_RIGHT
        DockDirection.BOTTOM_TO_TOP -> DockDirection.RIGHT_TO_LEFT
    }

    Surface.ROTATION_180 -> when (direction) {
        DockDirection.LEFT_TO_RIGHT -> DockDirection.RIGHT_TO_LEFT
        DockDirection.RIGHT_TO_LEFT -> DockDirection.LEFT_TO_RIGHT
        DockDirection.TOP_TO_BOTTOM -> DockDirection.BOTTOM_TO_TOP
        DockDirection.BOTTOM_TO_TOP -> DockDirection.TOP_TO_BOTTOM
    }

    Surface.ROTATION_270 -> when (direction) {
        DockDirection.LEFT_TO_RIGHT -> DockDirection.TOP_TO_BOTTOM
        DockDirection.RIGHT_TO_LEFT -> DockDirection.BOTTOM_TO_TOP
        DockDirection.TOP_TO_BOTTOM -> DockDirection.RIGHT_TO_LEFT
        DockDirection.BOTTOM_TO_TOP -> DockDirection.LEFT_TO_RIGHT
    }

    else -> direction
}

/**
 * FullscreenControlScreen
 *
 * Purpose:
 * - Presents a fullscreen interactive touch surface that maps Compose touch events
 *   to device coordinates and injects them via [onInjectTouch].
 * - Responsible for pointer tracking, multi-touch mapping, coordinate normalization,
 *   and lifetime of synthetic touch events sent to the device.
 *
 * Concurrency and side-effects:
 * - All heavy computations are local to the UI thread; injection itself is a quick
 *   callback (`onInjectTouch`) which delegates to native code elsewhere — keep that
 *   callback lightweight.
 * - Use `pointerInteropFilter` to receive raw MotionEvent instances for precise
 *   multi-touch handling and to map Android pointer IDs to device pointers.
 */
@Composable
fun FullscreenControlPage(
    scrcpy: Scrcpy,
    session: Scrcpy.Session.SessionInfo,
    onDismiss: () -> Unit,
    showDebugInfo: Boolean,
    currentFps: Float,
    imeRequestToken: Int = 0,
    enableBackHandler: Boolean = true,
    interactive: Boolean = true,
    onVideoBoundsInWindowChanged: (Rect?) -> Unit = {},
    onImeCommitText: suspend (String) -> Unit,
    onInjectTouch: suspend (
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        pressure: Float,
        actionButton: Int,
        buttons: Int,
    ) -> Unit,
    onBackOrScreenOn: suspend (action: Int) -> Unit,
) {
    BackHandler(enabled = enableBackHandler, onBack = onDismiss)

    val coroutineScope = rememberCoroutineScope()

    var touchAreaSize by remember { mutableStateOf(IntSize.Zero) }

    val activePointerIds = remember { linkedSetOf<Int>() }
    val activePointerPositions = remember { linkedMapOf<Int, Offset>() }
    val activePointerDevicePositions = remember { linkedMapOf<Int, Pair<Int, Int>>() }
    val pointerLabels = remember { linkedMapOf<Int, Int>() }

    var nextPointerLabel by remember { mutableIntStateOf(1) }
    var activeTouchCount by remember { mutableIntStateOf(0) }
    var activeTouchDebug by remember { mutableStateOf("") }

    val touchEventHandler = remember(session, touchAreaSize) {
        TouchEventHandler(
            coroutineScope = coroutineScope,
            session = session,
            touchAreaSize = touchAreaSize,
            activePointerIds = activePointerIds,
            activePointerPositions = activePointerPositions,
            activePointerDevicePositions = activePointerDevicePositions,
            pointerLabels = pointerLabels,
            nextPointerLabel = nextPointerLabel,
            mouseHoverEnabled = session.mouseHover,
            onInjectTouch = onInjectTouch,
            onBackOrScreenOn = onBackOrScreenOn,
            onActiveTouchCountChanged = { activeTouchCount = it },
            onActiveTouchDebugChanged = { activeTouchDebug = it },
            onNextPointerLabelChanged = { nextPointerLabel = it },
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (interactive)
                    Modifier.pointerInteropFilter { event ->
                        touchEventHandler.handleMotionEvent(event)
                    }
                else Modifier
            )
            .onSizeChanged { touchAreaSize = it },
    ) {
        val sessionAspect =
            if (session.height == 0) 16f / 9f
            else session.width.toFloat() / session.height.toFloat()

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .then(
                    if (sessionAspect > (maxWidth.value / maxHeight.value))
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(sessionAspect)
                    else
                        Modifier
                            .fillMaxHeight()
                            .aspectRatio(sessionAspect)
                ),
        ) {
            ScrcpyVideoSurface(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        onVideoBoundsInWindowChanged(
                            Rect(
                                bounds.left.toInt(),
                                bounds.top.toInt(),
                                bounds.right.toInt(),
                                bounds.bottom.toInt(),
                            )
                        )
                    },
                session = session,
                imeRequestToken = imeRequestToken,
                onImeCommitText = onImeCommitText,
                onImeDeleteSurroundingText = { beforeLength, afterLength ->
                    submitImeDeleteSurroundingText(
                        scrcpy = scrcpy,
                        beforeLength = beforeLength,
                        afterLength = afterLength,
                    )
                },
                onImeKeyEvent = { event ->
                    submitImeKeyEvent(
                        scrcpy = scrcpy,
                        event = event,
                        keyInjectMode = session.keyInjectMode,
                        forwardKeyRepeat = session.forwardKeyRepeat,
                    )
                },
            )
        }

        if (showDebugInfo) Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = UiSpacing.ContentVertical, top = UiSpacing.ContentVertical)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = UiSpacing.ContentVertical, vertical = UiSpacing.Medium),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.Tiny)) {
                Text(
                    text = "分辨率: ${session.width}x${session.height}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                @SuppressLint("DefaultLocale")
                Text(
                    text = "FPS: ${String.format("%.1f", currentFps.coerceAtLeast(0f))}",
                    color = Color.White,
                    fontSize = 13.sp,
                )
                Text(
                    text = "触点: $activeTouchCount",
                    color = Color.White,
                    fontSize = 13.sp,
                )
                if (activeTouchDebug.isNotEmpty()) Text(
                    text = activeTouchDebug,
                    color = Color.White,
                    fontSize = 13.sp,
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onVideoBoundsInWindowChanged(null)
        }
    }
}
