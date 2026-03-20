package io.github.miuzarte.scrcpyforandroid.pages

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.ScrcpySessionInfo
import io.github.miuzarte.scrcpyforandroid.haptics.rememberAppHaptics
import io.github.miuzarte.scrcpyforandroid.widgets.FullscreenControlScreen
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonActions
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonBar
import top.yukonga.miuix.kmp.basic.Scaffold

data class FullscreenControlLaunch(
    val deviceName: String,
    val width: Int,
    val height: Int,
    val codec: String,
)

@Composable
fun FullscreenControlPage(
    launch: FullscreenControlLaunch,
    nativeCore: NativeCoreFacade,
    virtualButtonsOutside: List<String>,
    virtualButtonsInMore: List<String>,
    showDebugInfo: Boolean,
    showVirtualButtons: Boolean,
    onVideoSizeChanged: (width: Int, height: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // 全屏控制页不使用预测性返回手势,
    // 省得处理解码的问题
    BackHandler(enabled = true, onBack = onDismiss)

    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val activity = remember(context) { context as? Activity }
    val virtualButtonLayout = remember(virtualButtonsOutside, virtualButtonsInMore) {
        VirtualButtonActions.resolveLayout(virtualButtonsOutside, virtualButtonsInMore)
    }
    val bar = remember(virtualButtonLayout) {
        VirtualButtonBar(
            outsideActions = virtualButtonLayout.first,
            moreActions = virtualButtonLayout.second,
        )
    }
    var session by remember(launch) {
        mutableStateOf(
            ScrcpySessionInfo(
                width = launch.width,
                height = launch.height,
                deviceName = launch.deviceName.ifBlank { "设备" },
                codec = launch.codec.ifBlank { "unknown" },
                controlEnabled = true,
            ),
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

    DisposableEffect(nativeCore) {
        val listener: (Int, Int) -> Unit = { w, h ->
            session = session.copy(width = w, height = h)
            onVideoSizeChanged(w, h)
        }
        nativeCore.addVideoSizeListener(listener)
        onDispose {
            nativeCore.removeVideoSizeListener(listener)
        }
    }

    DisposableEffect(nativeCore) {
        val listener: (Float) -> Unit = { fps ->
            currentFps = fps
        }
        nativeCore.addVideoFpsListener(listener)
        onDispose {
            nativeCore.removeVideoFpsListener(listener)
        }
    }

    fun sendKeycode(keycode: Int) {
        nativeCore.scrcpyInjectKeycode(0, keycode)
        nativeCore.scrcpyInjectKeycode(1, keycode)
    }

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            FullscreenControlScreen(
                session = session,
                nativeCore = nativeCore,
                onDismiss = onDismiss,
                showDebugInfo = showDebugInfo,
                currentFps = currentFps,
                enableBackHandler = false,
                onInjectTouch = { action, pointerId, x, y, pressure, buttons ->
                    nativeCore.scrcpyInjectTouch(
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
                },
            )

            if (showVirtualButtons) {
                bar.Fullscreen(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onPressHaptic = { haptics.press() },
                    onConfirmHaptic = { haptics.confirm() },
                    onAction = { action ->
                        action.keycode?.let(::sendKeycode)
                    },
                )
            }
        }
    }
}
