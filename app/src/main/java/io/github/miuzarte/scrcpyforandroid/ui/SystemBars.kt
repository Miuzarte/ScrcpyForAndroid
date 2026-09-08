package io.github.miuzarte.scrcpyforandroid.ui

import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * Keeps the system bars' icon appearance in sync with the app's **effective** theme (rather than the
 * system theme). The app can be forced to a dark/light mode independently of the system; the status
 * bar icons must follow the actual rendered background so they stay readable. Wrap in [MiuixTheme] so
 * [colorScheme] reflects the active (possibly Monet / forced) mode, and call after edge-to-edge.
 *
 * @param window The activity window, or null to no-op.
 */
@Composable
fun ApplySystemBarsAppearance(window: Window?) {
    val isLight = colorScheme.background.luminance() >= 0.5f
    SideEffect {
        val w = window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(w, w.decorView)
        controller.isAppearanceLightStatusBars = isLight
        controller.isAppearanceLightNavigationBars = isLight
    }
}
