package io.github.miuzarte.scrcpyforandroid.storage

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Manages a local copy of a bundle from persistent storage with debounced save.
 *
 * Usage in a ViewModel:
 * ```
 * val asBundle = BundleSyncDelegate(
 *     sharedFlow = appSettings.bundleState,
 *     save = { appSettings.saveBundle(it) },
 *     scope = viewModelScope,
 * )
 *
 * init { asBundle.start() }
 *
 * override fun onCleared() {
 *     runBlocking(Dispatchers.IO) { asBundle.flush() }
 * }
 * ```
 */
@OptIn(FlowPreview::class)
class BundleSyncDelegate<T>(
    private val sharedFlow: StateFlow<T>,
    private val save: suspend (T) -> Unit,
    private val scope: CoroutineScope,
    private val delayMs: Long = 600L,
) {
    private val _value = MutableStateFlow(sharedFlow.value)
    val value: StateFlow<T> = _value.asStateFlow()

    fun update(transform: (T) -> T) {
        _value.update(transform)
    }

    fun start() {
        scope.launch {
            sharedFlow.collectLatest { shared ->
                if (_value.value != shared) _value.value = shared
            }
        }
        scope.launch {
            _value.debounce(delayMs).collectLatest { v ->
                if (v != sharedFlow.value) save(v)
            }
        }
    }

    suspend fun flush() {
        val latest = _value.value
        if (latest != sharedFlow.value) save(latest)
    }
}

/**
 * Composable equivalent of [BundleSyncDelegate] for files not yet migrated to
 * ViewModel. Returns a mutable state backed by [sharedFlow] with debounced save.
 *
 * Usage:
 * ```
 * var asBundle by rememberBundleState(
 *     sharedFlow = appSettings.bundleState,
 *     save = { appSettings.saveBundle(it) },
 * )
 * ```
 */
@Composable
fun <T> rememberBundleState(
    sharedFlow: StateFlow<T>,
    save: suspend (T) -> Unit,
): MutableState<T> {
    val shared by sharedFlow.collectAsState()
    val sharedLatest by rememberUpdatedState(shared)
    val state = rememberSaveable(shared) { mutableStateOf(shared) }
    var local by state
    val localLatest by rememberUpdatedState(local)
    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    LaunchedEffect(shared) {
        if (local != shared) local = shared
    }
    LaunchedEffect(local) {
        delay(Settings.BUNDLE_SAVE_DELAY)
        if (local != sharedLatest) save(local)
    }
    DisposableEffect(Unit) {
        onDispose {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                if (localLatest != sharedFlow.value) save(localLatest)
            }
            taskScope.cancel()
        }
    }

    return state
}
