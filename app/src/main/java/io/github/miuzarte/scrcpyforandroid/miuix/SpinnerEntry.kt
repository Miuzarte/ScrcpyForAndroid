package io.github.miuzarte.scrcpyforandroid.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier

@Immutable
data class SpinnerEntry(
    val icon: @Composable ((Modifier) -> Unit)? = null,
    val title: String? = null,
    val summary: String? = null,
    val enabled: Boolean = true,
)
