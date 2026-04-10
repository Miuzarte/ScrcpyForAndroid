package io.github.miuzarte.scrcpyforandroid.pages

import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf

class FloatingWindowPermissionRequester(
    val permissionGrantedState: State<Boolean>,
    val refresh: () -> Unit,
    val request: () -> Unit,
) {
    fun canDrawOverlays(): Boolean = permissionGrantedState.value
}

val LocalFloatingWindowPermissionRequester = staticCompositionLocalOf<FloatingWindowPermissionRequester> {
    error("No FloatingWindowPermissionRequester provided")
}
