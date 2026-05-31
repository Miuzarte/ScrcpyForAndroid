package io.github.miuzarte.scrcpyforandroid.pages.effect

import androidx.compose.runtime.*

@Composable
internal fun rememberFrameTimeSeconds(
    playing: Boolean = true,
): () -> Float {
    var time by remember { mutableFloatStateOf(0f) }
    var startOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playing) {
        if (!playing) {
            startOffset = time
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (playing) {
            val now = withFrameNanos { it }
            time = startOffset + (now - start) / 1_000_000_000f
        }
    }

    return { time }
}
