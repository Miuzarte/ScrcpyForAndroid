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
import io.github.miuzarte.scrcpyforandroid.haptics.rememberAppHaptics
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.storage.Storage
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
    onVideoSizeChanged: (width: Int, height: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // Disable predictive back handler temporarily to avoid decoding issues.
    BackHandler(enabled = true, onBack = onDismiss)

    val appSettings = Storage.appSettings

    val context = LocalContext.current

    val haptics = rememberAppHaptics()

    val activity = remember(context) { context as? Activity }

    var virtualButtonsLayout by appSettings.virtualButtonsLayout.asMutableState()
    val buttonItems = remember(virtualButtonsLayout) {
        VirtualButtonActions.splitLayout(VirtualButtonActions.parseStoredLayout(virtualButtonsLayout))
    }
    val fullscreenDebugInfo by appSettings.fullscreenDebugInfo.asState()
    val showFullscreenVirtualButtons by appSettings.showFullscreenVirtualButtons.asState()

    val bar = remember(buttonItems) {
        VirtualButtonBar(
            outsideActions = buttonItems.first,
            moreActions = buttonItems.second,
        )
    }
    var session by remember(launch) {
        mutableStateOf(
            Scrcpy.Session.SessionInfo(
                width = launch.width,
                height = launch.height,
                deviceName = launch.deviceName.ifBlank { "设备" },
                codecId = 0,
                codecName = launch.codec.ifBlank { "unknown" },
                audioCodecId = 0,
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

    suspend fun sendKeycode(keycode: Int) {
        runCatching {
            nativeCore.session?.injectKeycode(0, keycode)
            nativeCore.session?.injectKeycode(1, keycode)
        }.onFailure { e ->
            android.util.Log.w("FullscreenControlPage", "sendKeycode failed for keycode=$keycode", e)
        }
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
                showDebugInfo = fullscreenDebugInfo,
                currentFps = currentFps,
                enableBackHandler = false,
                onInjectTouch = { action, pointerId, x, y, pressure, buttons ->
                    nativeCore.session?.injectTouch(
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

            if (showFullscreenVirtualButtons) {
                bar.Fullscreen(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onAction = { action ->
                        action.keycode?.let {
                            sendKeycode(it)
                        }
                    },
                )
            }
        }
    }
}
