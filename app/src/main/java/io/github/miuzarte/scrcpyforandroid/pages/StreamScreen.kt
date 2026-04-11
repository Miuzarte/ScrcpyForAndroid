package io.github.miuzarte.scrcpyforandroid.pages

import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.util.Rational
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.miuzarte.scrcpyforandroid.StreamActivity
import io.github.miuzarte.scrcpyforandroid.constants.ThemeModes
import io.github.miuzarte.scrcpyforandroid.haptics.LocalAppHaptics
import io.github.miuzarte.scrcpyforandroid.haptics.rememberAppHaptics
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import io.github.miuzarte.scrcpyforandroid.widgets.VideoOutputTarget
import io.github.miuzarte.scrcpyforandroid.widgets.VideoOutputTargetState
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun StreamScreen(activity: StreamActivity) {
    val scrcpy = remember { AppRuntime.scrcpy!! }
    val asBundle by Storage.appSettings.bundleState.collectAsState()

    val isInPip by activity.pipModeState.collectAsState()

    val currentSession by scrcpy.currentSessionState.collectAsState()

    var pipSourceRectHint by remember { mutableStateOf<Rect?>(null) }
    var lastPipAspectRatio by remember { mutableStateOf<Rational?>(null) }
    var lastPipOrientationLandscape by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(currentSession) {
        if (currentSession == null) {
            activity.finish()
        }
    }

    DisposableEffect(isInPip) {
        onDispose {
            VideoOutputTargetState.set(VideoOutputTarget.PREVIEW)
        }
    }

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

    val themeMode =
        when (asBundle.themeBaseIndex.coerceIn(0, ThemeModes.baseOptions.lastIndex)) {
            1 -> if (!asBundle.monet) ColorSchemeMode.Light else ColorSchemeMode.MonetLight
            2 -> if (!asBundle.monet) ColorSchemeMode.Dark else ColorSchemeMode.MonetDark
            else -> if (!asBundle.monet) ColorSchemeMode.System else ColorSchemeMode.MonetSystem
        }
    val themeController = remember(themeMode) { ThemeController(colorSchemeMode = themeMode) }

    val haptics = rememberAppHaptics()

    MiuixTheme(controller = themeController) {
        CompositionLocalProvider(
            LocalAppHaptics provides haptics,
        ) {
            FullscreenControlScreen(
                scrcpy = scrcpy,
                onBack = activity::finish,
                isInPip = isInPip,
                onVideoSizeChanged = { width, height ->
                    // 只在全屏时跟随视频方向
                    if (!isInPip) {
                        activity.requestedOrientation =
                            if (width >= height) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                },
                onVideoBoundsInWindowChanged = {
                    // 记录下一次进入 PiP 时可用的 sourceRectHint
                    pipSourceRectHint = it
                },
            )
        }
    }
}
