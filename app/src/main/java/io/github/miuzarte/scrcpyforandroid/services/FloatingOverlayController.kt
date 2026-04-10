package io.github.miuzarte.scrcpyforandroid.services

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import com.petterp.floatingx.FloatingX
import com.petterp.floatingx.assist.FxGravity
import com.petterp.floatingx.assist.FxDisplayMode
import com.petterp.floatingx.assist.FxScopeType
import com.petterp.floatingx.compose.enableComposeSupport
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.pages.FloatingOverlayScreen
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings

class FloatingOverlayController(
    context: Context,
) {
    private val appContext = context.applicationContext as Application
    private var currentOverlayWidthPx: Int = 0
    private var currentOverlayHeightPx: Int = 0

    fun show(scrcpy: Scrcpy, nativeCore: NativeCoreFacade) {
        if (!FloatingWindowPermissions.canDrawOverlays(appContext)) {
            hide()
            return
        }
        val session = scrcpy.currentSessionState.value
        val (widthPx, heightPx) = resolveOverlaySizePx(session?.width ?: 0, session?.height ?: 0)
        ensureInstalled(scrcpy, nativeCore, widthPx, heightPx)
        applyOverlaySizeIfNeeded(widthPx, heightPx)
        FloatingX.control(OVERLAY_TAG).show()
    }

    fun syncSizeWithSession(sessionWidth: Int, sessionHeight: Int) {
        val (widthPx, heightPx) = resolveOverlaySizePx(sessionWidth, sessionHeight)
        applyOverlaySizeIfNeeded(widthPx, heightPx)
    }

    fun hide() {
        FloatingX.controlOrNull(OVERLAY_TAG)?.hide()
    }

    private fun ensureInstalled(
        scrcpy: Scrcpy,
        nativeCore: NativeCoreFacade,
        overlayWidthPx: Int,
        overlayHeightPx: Int,
    ) {
        if (FloatingX.isInstalled(OVERLAY_TAG)) return
        currentOverlayWidthPx = overlayWidthPx
        currentOverlayHeightPx = overlayHeightPx

        FloatingX.install {
            setContext(appContext)
            setTag(OVERLAY_TAG)
            setScopeType(FxScopeType.SYSTEM)
            enableComposeSupport()
            setDisplayMode(FxDisplayMode.Normal)
            setEnableSafeArea(true)
            setEnableEdgeAdsorption(false)
            setGravity(FxGravity.RIGHT_OR_BOTTOM)
            // setOffsetXY(24f, 120f)
            setManagerParams(
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            )
            setLayoutView(
                createOverlayView(
                    scrcpy = scrcpy,
                    nativeCore = nativeCore,
                    widthPx = overlayWidthPx,
                    heightPx = overlayHeightPx,
                )
            )
        }
    }

    private fun createOverlayView(
        scrcpy: Scrcpy,
        nativeCore: NativeCoreFacade,
        widthPx: Int,
        heightPx: Int,
    ): View {
        return ComposeView(appContext).apply {
            layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
            setContent {
                FloatingOverlayScreen(
                    scrcpy = scrcpy,
                    nativeCore = nativeCore,
                )
            }
        }
    }

    private fun applyOverlaySizeIfNeeded(widthPx: Int, heightPx: Int) {
        if (currentOverlayWidthPx == widthPx && currentOverlayHeightPx == heightPx) return
        currentOverlayWidthPx = widthPx
        currentOverlayHeightPx = heightPx

        val view = FloatingX.controlOrNull(OVERLAY_TAG)?.getView() ?: return
        view.layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
        view.requestLayout()
    }

    private fun resolveOverlaySizePx(sessionWidth: Int, sessionHeight: Int): Pair<Int, Int> {
        val minWidthDp = appSettings.bundleState.value.floatingOverlayMinWidthDp.coerceAtLeast(32)
        val minWidthPx = dpToPx(minWidthDp)

        if (sessionWidth <= 0 || sessionHeight <= 0) {
            return minWidthPx to (minWidthPx * 16 / 9)
        }

        val longSide = maxOf(sessionWidth, sessionHeight)
        val shortSide = minOf(sessionWidth, sessionHeight).coerceAtLeast(1)
        val longSidePx = (minWidthPx.toLong() * longSide / shortSide).toInt().coerceAtLeast(minWidthPx)
        val isLandscape = sessionWidth > sessionHeight
        return if (isLandscape) {
            longSidePx to minWidthPx
        } else {
            minWidthPx to longSidePx
        }
    }

    companion object {
        private const val OVERLAY_TAG = "scrcpy_overlay"
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * appContext.resources.displayMetrics.density).toInt()
    }
}
