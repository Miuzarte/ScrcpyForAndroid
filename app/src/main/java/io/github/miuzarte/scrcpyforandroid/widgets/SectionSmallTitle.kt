package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
fun SectionSmallTitle(
    text: String,
    insideMargin: PaddingValues = PaddingValues(16.dp, 8.dp),
) {
    SmallTitle(
        text = text,
        insideMargin = insideMargin,
    )
}
