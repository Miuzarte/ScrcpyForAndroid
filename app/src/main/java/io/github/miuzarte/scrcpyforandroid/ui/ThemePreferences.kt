package io.github.miuzarte.scrcpyforandroid.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

val LocalEnableBlur = staticCompositionLocalOf { false }
val LocalBlurMode = staticCompositionLocalOf { AppSettings.BlurMode.NONE }

@Composable
fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
    val surfaceColor = colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    // 传入后渐进模糊改为随滚动淡入 (contentOffset 为 0 时不可见), 只适合静止时该透明的页面 (如 About);
    // 列表可能不滚动的短页面不要传, 否则 contentOffset 一直是 0, 顶栏会一直没有模糊
    scrollBehavior: ScrollBehavior? = null,
    // 置 false 时回退到高斯或无模糊, 用于有 bottomContent 的顶栏
    allowProgressive: Boolean = true,
    content: @Composable () -> Unit,
) {
    val blurActive = backdrop != null
    // 渐进模糊默认常驻; 传了 scrollBehavior 才改为随滚动淡入 (见上面的参数说明)
    val progressive = blurActive && LocalBlurMode.current == AppSettings.BlurMode.PROGRESSIVE && allowProgressive
    Box(
        modifier =
            if (blurActive && !progressive) {
                Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = RectangleShape,
                    blurRadius = 25f,
                    colors = barBlurColors(progressive = false),
                )
            } else {
                Modifier
            },
    ) {
        if (progressive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = scrollBehavior
                            ?.state
                            ?.let { (-it.contentOffset / 48.dp.toPx()).coerceIn(0f, 1f) }
                            ?: 1f
                    }
                    .progressiveTextureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        gradient = ProgressiveBlur.Top.copy(curve = 2.2f),
                        blurRadius = 10f,
                        colors = barBlurColors(progressive = true),
                    ),
            )
        }
        content()
    }
}

@Composable
private fun barBlurColors(progressive: Boolean): BlurColors = BlurDefaults.blurColors(
    blendColors = listOf(
        BlendColorEntry(color = colorScheme.surface.copy(if (progressive) 0.3f else 0.8f)),
    ),
)
