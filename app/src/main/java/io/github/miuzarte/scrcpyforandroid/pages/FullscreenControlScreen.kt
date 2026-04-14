package io.github.miuzarte.scrcpyforandroid.pages

import android.annotation.SuppressLint
import android.graphics.Rect
import android.util.Log
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.password.PasswordPickerPopupContent
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.scrcpy.TouchEventHandler
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
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

@Composable
fun FullscreenControlScreen(
    scrcpy: Scrcpy,
    onBack: () -> Unit,
    isInPip: Boolean,
    onVideoSizeChanged: (width: Int, height: Int) -> Unit,
    onVideoBoundsInWindowChanged: (Rect?) -> Unit,
) {
    BackHandler(enabled = true, onBack = onBack)

    val activity = LocalActivity.current
    val fragmentActivity = remember(activity) { activity as? FragmentActivity }

    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    val snackbar = LocalSnackbarController.current
    val currentSession by scrcpy.currentSessionState.collectAsState()

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
    val showFullscreenFloatingButton = asBundle.showFullscreenFloatingButton

    val bar = remember(buttonItems) {
        VirtualButtonBar(
            outsideActions = buttonItems.first,
            moreActions = buttonItems.second,
        )
    }

    var currentFps by remember { mutableFloatStateOf(0f) }

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
                session = session,
                onDismiss = onBack,
                showDebugInfo = fullscreenDebugInfo && !isInPip,
                currentFps = currentFps,
                enableBackHandler = false,
                interactive = !isInPip,
                onVideoBoundsInWindowChanged = onVideoBoundsInWindowChanged,
                onInjectTouch = { action, pointerId, x, y, pressure, buttons ->
                    withContext(Dispatchers.IO) {
                        scrcpy.injectTouch(
                            action = action,
                            pointerId = pointerId,
                            x = x,
                            y = y,
                            screenWidth = session.width,
                            screenHeight = session.height,
                            pressure = pressure,
                            actionButton = 0,
                            buttons = buttons,
                        )
                    }
                },
            )

            if (showFullscreenVirtualButtons && !isInPip) {
                bar.Fullscreen(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onAction = { action ->
                        if (action == VirtualButtonAction.PASSWORD_INPUT) {
                            snackbar.show("当前页面无法拉起验证")
                        } else {
                            action.keycode?.let { sendKeycode(it) }
                        }
                    },
                    passwordPopupContent = if (fragmentActivity == null) {
                        null
                    } else {
                        { onDismissRequest ->
                            PasswordPickerPopupContent(
                                onDismissRequest = onDismissRequest,
                                onMessage = { message ->
                                    taskScope.launch(Dispatchers.Main) { snackbar.show(message) }
                                },
                            )
                        }
                    },
                )
            }

            if (showFullscreenFloatingButton && !isInPip) {
                bar.FloatingBall(
                    actions = floatingActions,
                    modifier = Modifier.fillMaxSize(),
                    onAction = { action ->
                        if (action == VirtualButtonAction.PASSWORD_INPUT) {
                            snackbar.show("当前页面无法拉起验证")
                        } else {
                            action.keycode?.let { sendKeycode(it) }
                        }
                    },
                    passwordPopupContent = if (fragmentActivity == null) {
                        null
                    } else {
                        { onDismissRequest ->
                            PasswordPickerPopupContent(
                                onDismissRequest = onDismissRequest,
                                onMessage = { message ->
                                    taskScope.launch(Dispatchers.Main) { snackbar.show(message) }
                                },
                            )
                        }
                    },
                )
            }
        }
    }
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
    session: Scrcpy.Session.SessionInfo,
    onDismiss: () -> Unit,
    showDebugInfo: Boolean,
    currentFps: Float,
    enableBackHandler: Boolean = true,
    interactive: Boolean = true,
    onVideoBoundsInWindowChanged: (Rect?) -> Unit = {},
    onInjectTouch: suspend (action: Int, pointerId: Long, x: Int, y: Int, pressure: Float, buttons: Int) -> Unit,
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
            onInjectTouch = onInjectTouch,
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
