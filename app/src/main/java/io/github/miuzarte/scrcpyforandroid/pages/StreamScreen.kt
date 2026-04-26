package io.github.miuzarte.scrcpyforandroid.pages

import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.util.Rational
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.miuzarte.scrcpyforandroid.StreamActivity
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.services.SnackbarController
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import io.github.miuzarte.scrcpyforandroid.ui.createThemeController
import io.github.miuzarte.scrcpyforandroid.widgets.VideoOutputTarget
import io.github.miuzarte.scrcpyforandroid.widgets.VideoOutputTargetState
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun StreamScreen(activity: StreamActivity) {
    val scrcpy = remember { AppRuntime.scrcpy!! }
    val asBundle by Storage.appSettings.bundleState.collectAsState()

    val isInPip by activity.pipModeState.collectAsState()

    var pipSourceRectHint by remember { mutableStateOf<Rect?>(null) }
    var lastPipAspectRatio by remember { mutableStateOf<Rational?>(null) }
    var lastPipOrientationLandscape by remember { mutableStateOf<Boolean?>(null) }

    DisposableEffect(isInPip) {
        onDispose {
            VideoOutputTargetState.set(VideoOutputTarget.PREVIEW)
        }
    }

    val currentSession by scrcpy.currentSessionState.collectAsState()

    LaunchedEffect(
        activity, isInPip,
        currentSession?.width, currentSession?.height,
    ) {
        val session = currentSession ?: return@LaunchedEffect

        val isLandscape = session.width >= session.height
        if (lastPipAspectRatio != null && lastPipOrientationLandscape == isLandscape) {
            // 一定要只在视频比例变更时才更新,
            // .setAspectRatio() 多次传递相同的值时,
            // 内部会自行应用其倒数
            return@LaunchedEffect
        }
        lastPipOrientationLandscape = isLandscape

        val pipAspectRatio = Rational(
            session.width.coerceAtLeast(1),
            session.height.coerceAtLeast(1),
        ).also { ratio ->
            lastPipAspectRatio = ratio
        }

        activity.configurePip {
            setEnabled(true)
            setAspectRatio(pipAspectRatio)
            setSourceRectHint(pipSourceRectHint)
            setSeamlessResizeEnabled(true)
            setCloseAction(activity.pipStopAction)
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

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val snackbarController = remember(snackbarScope, snackbarHostState) {
        SnackbarController(scope = snackbarScope, hostState = snackbarHostState)
    }

    MiuixTheme(
        controller = themeController,
        smoothRounding = asBundle.smoothCorner,
    ) {
        CompositionLocalProvider(
            LocalSnackbarController provides snackbarController,
        ) {
            FullscreenControlRoute(
                scrcpy = scrcpy,
                onBack = activity::finish,
                isInPip = isInPip,
                onVideoBoundsInWindowChanged = {
                    // 记录下一次进入 PiP 时可用的 sourceRectHint
                    pipSourceRectHint = it
                },
            )
        }
    }
}

@Composable
fun FullscreenControlRoute(
    scrcpy: Scrcpy,
    onBack: () -> Unit,
    isInPip: Boolean = false,
    autoExitOnStop: Boolean = false,
    onVideoBoundsInWindowChanged: (Rect?) -> Unit = {},
) {
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val asBundle by Storage.appSettings.bundleState.collectAsState()
    val currentSession by scrcpy.currentSessionState.collectAsState()

    LaunchedEffect(currentSession) {
        if (currentSession == null) {
            onBack()
        }
    }

    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(lifecycleOwner, autoExitOnStop) {
        if (!autoExitOnStop) {
            onDispose {}
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    onBack()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    FullscreenControlScreen(
        scrcpy = scrcpy,
        onBack = onBack,
        isInPip = isInPip,
        onVideoSizeChanged = { width, height ->
            if (!isInPip) {
                activity?.requestedOrientation =
                    fullscreenRequestedOrientation(
                        width = width,
                        height = height,
                        ignoreSystemRotationLock = asBundle.fullscreenControlIgnoreSystemRotationLock,
                    )
            }
        },
        onVideoBoundsInWindowChanged = onVideoBoundsInWindowChanged,
    )
}

private fun fullscreenRequestedOrientation(
    width: Int,
    height: Int,
    ignoreSystemRotationLock: Boolean,
) = if (width >= height) {
    if (ignoreSystemRotationLock)
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    else
        ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
} else {
    if (ignoreSystemRotationLock)
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    else
        ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
}
