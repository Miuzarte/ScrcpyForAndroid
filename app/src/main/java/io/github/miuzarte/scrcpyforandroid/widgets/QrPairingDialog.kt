package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.pages.QrPairingUiState
import io.github.miuzarte.scrcpyforandroid.util.QrCodeEncoder
import io.github.miuzarte.scrcpyforandroid.util.QrMatrix
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * Shows the pairing QR code for the ADB "pair with QR code" flow.
 *
 * There are no buttons on purpose: the dialog closes on success, and on failure it stays
 * open with the reason in the summary so the user knows what to fix. Reopening it always
 * generates fresh credentials.
 */
@Composable
internal fun QrPairingDialog(
    state: QrPairingUiState,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    val matrix = remember(state.payload) {
        state.payload.takeIf { it.isNotBlank() }
            ?.let { payload -> runCatching { QrCodeEncoder.encode(payload) }.getOrNull() }
    }
    val statusText = state.statusArg
        ?.let { stringResource(state.statusRes, it) }
        ?: stringResource(state.statusRes)

    // 扫码期间保持屏幕常亮, 否则平板息屏后二维码不可见
    DisposableEffect(state.visible) {
        if (state.visible) view.keepScreenOn = true
        onDispose {
            if (state.visible) view.keepScreenOn = false
        }
    }

    OverlayDialog(
        show = state.visible,
        title = stringResource(R.string.device_pairing_qr_title),
        summary = statusText,
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
        ) {
            if (matrix != null) {
                // 二维码取可用宽度; 短窗口下对话框不限高, 因此再按窗口高度收一次,
                // 配合纵向滚动保证标题/二维码/提示都不会被裁掉
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val windowHeight = LocalWindowInfo.current.containerDpSize.height
                    val side = minOf(maxWidth, maxHeight, windowHeight * QR_MAX_HEIGHT_FRACTION)
                    QrCodeCanvas(
                        matrix = matrix,
                        modifier = Modifier.size(side),
                    )
                }
            }
            Text(
                text = stringResource(R.string.device_pairing_qr_hint),
                style = MiuixTheme.textStyles.footnote1,
                color = DialogDefaults.summaryColor(),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Draws [matrix] with the mandatory quiet zone. Colors are hardcoded black on white
 * (never theme colors): scanners expect dark modules on a light background and are not
 * reliable on inverted symbols, so dark mode keeps the same rendering.
 */
@Composable
private fun QrCodeCanvas(
    matrix: QrMatrix,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val quiet = QrCodeEncoder.RENDER_QUIET_ZONE
        val total = matrix.size + quiet * 2
        val modulePx = size.minDimension / total
        drawRoundRect(
            color = QR_LIGHT,
            size = Size(total * modulePx, total * modulePx),
            cornerRadius = CornerRadius(modulePx * QUIET_CORNER_MODULES),
        )
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (!matrix[x, y]) continue
                // Snapping edges to whole pixels keeps neighbouring modules seamless.
                val left = ((x + quiet) * modulePx).roundToInt().toFloat()
                val top = ((y + quiet) * modulePx).roundToInt().toFloat()
                val right = ((x + quiet + 1) * modulePx).roundToInt().toFloat()
                val bottom = ((y + quiet + 1) * modulePx).roundToInt().toFloat()
                drawRect(
                    color = QR_DARK,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                )
            }
        }
    }
}

private val QR_LIGHT = Color.White
private val QR_DARK = Color.Black

/** Share of the window height the QR card may take, so short windows still fit the text. */
private const val QR_MAX_HEIGHT_FRACTION = 0.6f

/** Corner rounding of the white background, in modules; stays inside the quiet zone. */
private const val QUIET_CORNER_MODULES = 1f
