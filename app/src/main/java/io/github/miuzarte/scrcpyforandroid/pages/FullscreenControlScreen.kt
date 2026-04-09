package io.github.miuzarte.scrcpyforandroid.pages

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.widgets.FullscreenControlScreen
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonActions
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Scaffold

@Composable
fun FullscreenControlScreen(
    onBack: () -> Unit,
    session: Scrcpy.Session.SessionInfo,
    nativeCore: NativeCoreFacade,
    onVideoSizeChanged: (width: Int, height: Int) -> Unit,
) {
    // Disable predictive back handler temporarily to avoid decoding issues.
    BackHandler(enabled = true, onBack = onBack)


    val context = LocalContext.current

    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    val activity = remember(context) { context as? Activity }

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
    val fullscreenDebugInfo = asBundle.fullscreenDebugInfo
    val showFullscreenVirtualButtons = asBundle.showFullscreenVirtualButtons

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

    DisposableEffect(nativeCore) {
        val listener: (Int, Int) -> Unit = { w, h ->
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
            withContext(Dispatchers.IO) {
                nativeCore.session?.injectKeycode(0, keycode)
                nativeCore.session?.injectKeycode(1, keycode)
            }
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
                onDismiss = onBack,
                showDebugInfo = fullscreenDebugInfo,
                currentFps = currentFps,
                enableBackHandler = false,
                onInjectTouch = { action, pointerId, x, y, pressure, buttons ->
                    withContext(Dispatchers.IO) {
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
                    }
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
