package io.github.miuzarte.scrcpyforandroid.pages

import android.content.Context
import android.graphics.Typeface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import io.github.miuzarte.scrcpyforandroid.nativecore.AdbSocketStream
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.services.LocalInputService
import io.github.miuzarte.scrcpyforandroid.storage.BundleSyncDelegate
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

private const val DEFAULT_TERMINAL_FONT_SIZE_SP = 14f
private const val LOG_TAG = "TerminalScreen"

internal class TerminalViewModel : ViewModel() {

    private val asBundleSync = BundleSyncDelegate(
        sharedFlow = appSettings.bundleState,
        save = { appSettings.saveBundle(it) },
        scope = viewModelScope,
    )
    val asBundle = asBundleSync.value

    private val _terminalFontSizeSp = MutableStateFlow(asBundle.value.terminalFontSizeSp)
    val terminalFontSizeSp: StateFlow<Float> = _terminalFontSizeSp.asStateFlow()

    fun updateTerminalFontSize(newValue: Float, onApplied: (Float) -> Unit) {
        val clamped = newValue.coerceIn(1f, 32f)
        if (clamped == _terminalFontSizeSp.value) return
        _terminalFontSizeSp.value = clamped
        asBundleSync.update { it.copy(terminalFontSizeSp = clamped) }
        onApplied(clamped)
    }

    fun resetFontSizeToDefault(onApplied: (Float) -> Unit) {
        updateTerminalFontSize(DEFAULT_TERMINAL_FONT_SIZE_SP, onApplied)
    }

    private val _shellReady = MutableStateFlow(false)
    val shellReady: StateFlow<Boolean> = _shellReady.asStateFlow()

    private val _shellConnecting = MutableStateFlow(false)
    val shellConnecting: StateFlow<Boolean> = _shellConnecting.asStateFlow()

    private val shellWriteMutex = Mutex()
    private var shellStream: AdbSocketStream? = null

    val sessionHolder = arrayOfNulls<TerminalSession>(1)

    fun writeBytesToShell(data: ByteArray, offset: Int, count: Int) {
        val stream = shellStream
        if (stream == null || !_shellReady.value || stream.closed) return
        val payload = data.copyOfRange(offset, offset + count)
        viewModelScope.launch {
            val result = runCatching {
                shellWriteMutex.withLock {
                    stream.outputStream.write(payload)
                    stream.outputStream.flush()
                }
            }
            withContext(Dispatchers.Main) {
                result.onFailure { error ->
                    snackbar("终端输入失败: ${error.message ?: error.javaClass.simpleName}")
                }
            }
        }
    }

    fun writeClipboardToShell(context: Context) {
        val text = LocalInputService.getClipboardText(context)
        if (!text.isNullOrBlank()) {
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            writeBytesToShell(bytes, 0, bytes.size)
        }
    }

    var ctrlLatched: Boolean = false
    var altLatched: Boolean = false
    var pendingLatchedConsume: Boolean = false

    fun consumeLatchedVisualState() {
        ctrlLatched = false; altLatched = false; pendingLatchedConsume = false
    }

    fun writeLiteralKey(text: String) {
        var payload = text
        if (ctrlLatched) {
            payload = payload.map { applyCtrlModifier(it) }.joinToString(separator = "")
        }
        if (altLatched) {
            payload = "$payload"
        }
        ctrlLatched = false; altLatched = false; pendingLatchedConsume = false
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        writeBytesToShell(bytes, 0, bytes.size)
    }

    fun writeSpecialKey(keyCode: Int) {
        val session = sessionHolder[0] ?: return
        var modifiers = 0
        if (ctrlLatched) modifiers = modifiers or KeyHandler.KEYMOD_CTRL
        if (altLatched) modifiers = modifiers or KeyHandler.KEYMOD_ALT
        ctrlLatched = false; altLatched = false; pendingLatchedConsume = false
        val sequence = KeyHandler.getCode(
            keyCode, modifiers,
            session.emulator.isCursorKeysApplicationMode(),
            session.emulator.isKeypadApplicationMode(),
        ) ?: return
        val bytes = sequence.toByteArray(StandardCharsets.UTF_8)
        writeBytesToShell(bytes, 0, bytes.size)
    }

    fun extractTranscript(session: TerminalSession): String {
        val screen = session.emulator.getScreen()
        return screen.getSelectedText(
            selX1 = 0, selY1 = -screen.activeTranscriptRows,
            selX2 = session.emulator.mColumns, selY2 = session.emulator.mRows - 1,
            joinBackLines = false, joinFullLines = false,
        ).trim('\n')
    }

    fun syncOutput(onOutputChange: (String) -> Unit) {
        val session = sessionHolder[0] ?: return
        onOutputChange(extractTranscript(session))
    }

    fun applyTerminalThemeColors(surfaceArgb: Int, onSurfaceArgb: Int, cursorArgb: Int) {
        val session = sessionHolder[0] ?: return
        val colors = session.emulator.mColors.mCurrentColors
        colors[TextStyle.COLOR_INDEX_BACKGROUND] = surfaceArgb
        colors[TextStyle.COLOR_INDEX_FOREGROUND] = onSurfaceArgb
        colors[TextStyle.COLOR_INDEX_CURSOR] = cursorArgb
    }

    fun openShellSession(showKeyboardAfterConnect: Boolean, requestFocus: () -> Unit) {
        if (shellStream != null || _shellConnecting.value) {
            if (_shellReady.value && showKeyboardAfterConnect) requestFocus()
            return
        }
        _shellConnecting.value = true
        viewModelScope.launch {
            val streamResult = runCatching {
                withContext(Dispatchers.IO) { NativeAdbService.openShellStream("") }
            }
            val stream = streamResult.getOrElse { error ->
                withContext(Dispatchers.Main) {
                    _shellConnecting.value = false
                    _shellReady.value = false
                    snackbar("终端会话创建失败: ${error.message ?: error.javaClass.simpleName}")
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                shellStream = stream
                _shellReady.value = true
                _shellConnecting.value = false
                if (showKeyboardAfterConnect) requestFocus()
            }

            val buffer = ByteArray(4096)
            try {
                while (!stream.closed) {
                    val count = withContext(Dispatchers.IO) { stream.inputStream.read(buffer) }
                    if (count <= 0) break
                    withContext(Dispatchers.Main) {
                        sessionHolder[0]?.append(buffer, count)
                    }
                }
            } catch (error: Throwable) {
                withContext(Dispatchers.Main) {
                    snackbar("终端输出失败: ${error.message ?: error.javaClass.simpleName}")
                }
            } finally {
                runCatching { stream.close() }
                withContext(Dispatchers.Main) {
                    if (shellStream === stream) shellStream = null
                    _shellReady.value = false
                    _shellConnecting.value = false
                }
            }
        }
    }

    fun closeShell() {
        runCatching { shellStream?.close() }
        shellStream = null
        _shellReady.value = false
        _shellConnecting.value = false
    }

    private val _snackbarEvents = Channel<String>(Channel.BUFFERED)
    val snackbarEvents: Flow<String> = _snackbarEvents.receiveAsFlow()

    private fun snackbar(message: String) {
        _snackbarEvents.trySend(message)
    }

    fun launchFontSizeSnackbar(
        fontSizeSp: Float,
        hostState: SnackbarHostState,
        onReset: (Float) -> Unit,
    ) {
        viewModelScope.launch {
            hostState.newestSnackbarData()?.dismiss()
            val result = hostState.showSnackbar(
                message = "终端字号 ${fontSizeSp.roundToInt()}sp",
                actionLabel = "恢复默认",
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                resetFontSizeToDefault(onReset)
            }
        }
    }

    init {
        asBundleSync.start()
    }

    override fun onCleared() {
        closeShell()
        runBlocking(Dispatchers.IO) { asBundleSync.flush() }
    }

    companion object {
        private fun applyCtrlModifier(ch: Char): Char = when (ch) {
            in 'a'..'z' -> (ch.code - 'a'.code + 1).toChar()
            in 'A'..'Z' -> (ch.code - 'A'.code + 1).toChar()
            ' ' -> 0.toChar()
            '[' -> 27.toChar()
            '\\' -> 28.toChar()
            ']' -> 29.toChar()
            '^' -> 30.toChar()
            '_', '/' -> 31.toChar()
            else -> ch
        }
    }
}
