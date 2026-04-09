package io.github.miuzarte.scrcpyforandroid.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState

class SnackbarController(
    private val scope: CoroutineScope,
    val hostState: SnackbarHostState,
    val defaultActionLabel: String? = null,
    val defaultWithDismissAction: Boolean? = null,
    val defaultDuration: SnackbarDuration? = null,
) {
    fun show(
        message: String,
        actionLabel: String? = defaultActionLabel,
        withDismissAction: Boolean = defaultWithDismissAction ?: true,
        duration: SnackbarDuration = defaultDuration ?: SnackbarDuration.Short,
        scope: CoroutineScope = this.scope,
    ) {
        scope.launch {
            hostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = withDismissAction,
                duration = duration,
            )
        }
    }
}
