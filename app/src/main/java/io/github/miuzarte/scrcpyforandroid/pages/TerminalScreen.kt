package io.github.miuzarte.scrcpyforandroid.pages

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.nativecore.AdbSocketStream
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.services.LocalInputService
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import io.github.miuzarte.scrcpyforandroid.widgets.PopupMenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.roundToInt

private const val DEFAULT_TERMINAL_FONT_SIZE_SP = 14f
private const val MIN_TERMINAL_FONT_SIZE_SP = 1f
private const val MAX_TERMINAL_FONT_SIZE_SP = 32f
private const val FONT_SCALE_STEP_THRESHOLD = 1.08f
private const val LOG_TAG = "TerminalScreen"
private const val TERMINAL_FONT_RELATIVE_PATH = "terminal/font.ttf"

private fun terminalFontFile(context: android.content.Context): File {
    return File(context.filesDir, TERMINAL_FONT_RELATIVE_PATH)
}

private fun loadTerminalTypeface(context: android.content.Context): Typeface? {
    val file = terminalFontFile(context)
    if (!file.exists()) return null
    return runCatching { Typeface.createFromFile(file) }.getOrNull()
}

@Composable
fun TerminalScreen(
    bottomInnerPadding: Dp,
    isActive: Boolean,
    onTerminalGestureLockChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showOutputSheet by rememberSaveable { mutableStateOf(false) }
    var output by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                SmallTopAppBar(
                    title = "终端",
                    color = if (blurActive) Color.Transparent else colorScheme.surface,
                    actions = {
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                holdDownState = showMenu,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "更多",
                                )
                            }

                            OverlayListPopup(
                                show = showMenu,
                                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                onDismissRequest = { showMenu = false },
                            ) {
                                ListPopupColumn {
                                    PopupMenuItem(
                                        text = "自由复制",
                                        optionSize = 2,
                                        index = 0,
                                        enabled = output.isNotBlank(),
                                        onSelectedIndexChange = {
                                            showMenu = false
                                            showOutputSheet = true
                                        },
                                    )
                                    PopupMenuItem(
                                        text = "清屏",
                                        optionSize = 2,
                                        index = 1,
                                        onSelectedIndexChange = {
                                            showMenu = false
                                            output = ""
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
    ) { pagePadding ->
        Box(
            modifier =
                if (blurActive) Modifier.layerBackdrop(blurBackdrop)
                else Modifier,
        ) {
            TerminalPage(
                contentPadding = pagePadding,
                bottomInnerPadding = bottomInnerPadding,
                isActive = isActive,
                onTerminalGestureLockChanged = onTerminalGestureLockChanged,
                output = output,
                onOutputChange = { output = it },
            )

            TerminalOutputBottomSheet(
                show = showOutputSheet,
                output = output,
                onDismissRequest = { showOutputSheet = false },
                onCopyAll = {
                    showMenu = false
                    LocalInputService.setClipboardText(context, output)
                    snackbar.show("已复制所有终端输出")
                },
            )
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun TerminalPage(
    contentPadding: PaddingValues,
    bottomInnerPadding: Dp,
    isActive: Boolean,
    onTerminalGestureLockChanged: (Boolean) -> Unit,
    output: String,
    onOutputChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val snackbar = LocalSnackbarController.current
    val haptic = LocalHapticFeedback.current
    val asBundleShared by appSettings.bundleState.collectAsState()
    val uiScope = rememberCoroutineScope()
    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val shellWriteMutex = remember { Mutex() }
    var terminalFontSizeSp by rememberSaveable(asBundleShared.terminalFontSizeSp) {
        mutableFloatStateOf(asBundleShared.terminalFontSizeSp)
    }
    var shellReady by remember { mutableStateOf(false) }
    var shellConnecting by remember { mutableStateOf(false) }
    var shellStream by remember { mutableStateOf<AdbSocketStream?>(null) }
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    val imeBottomDp = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    var ctrlLatched by rememberSaveable { mutableStateOf(false) }
    var altLatched by rememberSaveable { mutableStateOf(false) }
    var pendingLatchedConsume by remember { mutableStateOf(false) }
    var pinchGestureLock by remember { mutableStateOf(false) }
    var customTypeface by remember { mutableStateOf(loadTerminalTypeface(context)) }
    val sessionHolder = remember { arrayOfNulls<TerminalSession>(1) }
    val terminalSurfaceColorArgb = colorScheme.surface.toArgb()
    val terminalOnSurfaceColorArgb = colorScheme.onSurface.toArgb()
    val terminalCursorColorArgb =
        if (colorScheme.surface.luminance() < 0.5f) 0xffffffff.toInt()
        else 0xff000000.toInt()

    LaunchedEffect(asBundleShared.terminalFontSizeSp) {
        if (terminalFontSizeSp != asBundleShared.terminalFontSizeSp) {
            terminalFontSizeSp = asBundleShared.terminalFontSizeSp
        }
    }

    fun extractTranscript(session: TerminalSession): String {
        val screen = session.emulator.getScreen()
        return screen.getSelectedText(
            selX1 = 0,
            selY1 = -screen.activeTranscriptRows,
            selX2 = session.emulator.mColumns,
            selY2 = session.emulator.mRows - 1,
            joinBackLines = false,
            joinFullLines = false,
        ).trim('\n')
    }

    fun syncOutput() {
        val session = sessionHolder[0] ?: return
        onOutputChange(extractTranscript(session))
        terminalView?.onScreenUpdated()
    }

    fun writeBytesToShell(
        data: ByteArray,
        offset: Int,
        count: Int,
    ) {
        val stream = shellStream
        if (stream == null || !shellReady || stream.closed) {
            return
        }
        val payload = data.copyOfRange(offset, offset + count)
        taskScope.launch {
            val result = runCatching {
                shellWriteMutex.withLock {
                    stream.outputStream.write(payload)
                    stream.outputStream.flush()
                }
            }
            withContext(Dispatchers.Main) {
                result.onFailure { error ->
                    snackbar.show("终端输入失败: ${error.message ?: error.javaClass.simpleName}")
                }
            }
        }
    }

    fun writeClipboardToShell() {
        val text = LocalInputService.getClipboardText(context)
        if (!text.isNullOrBlank()) {
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            writeBytesToShell(bytes, 0, bytes.size)
        }
    }

    val terminalSession = remember {
        TerminalSession(
            shellWriter = ::writeBytesToShell,
            onScreenUpdated = ::syncOutput,
            onCopyTextToClipboardRequested = { text ->
                LocalInputService.setClipboardText(context, text)
                uiScope.launch {
                    snackbar.show("已复制到剪贴板")
                }
            },
            onPasteTextFromClipboardRequested = ::writeClipboardToShell,
            onBellRequested = {},
        )
    }
    sessionHolder[0] = terminalSession

    fun applyTerminalThemeColors() {
        val colors = terminalSession.emulator.mColors.mCurrentColors
        colors[TextStyle.COLOR_INDEX_BACKGROUND] = terminalSurfaceColorArgb
        colors[TextStyle.COLOR_INDEX_FOREGROUND] = terminalOnSurfaceColorArgb
        colors[TextStyle.COLOR_INDEX_CURSOR] = terminalCursorColorArgb
    }

    fun updateTerminalFontSize(newValue: Float) {
        val clamped = newValue.coerceIn(MIN_TERMINAL_FONT_SIZE_SP, MAX_TERMINAL_FONT_SIZE_SP)
        if (clamped == terminalFontSizeSp) {
            return
        }
        terminalFontSizeSp = clamped
        terminalView?.setTextSize(with(density) { clamped.sp.roundToPx() })
        uiScope.launch {
            val latest = appSettings.bundleState.value
            if (latest.terminalFontSizeSp != clamped) {
                appSettings.updateBundle {
                    it.copy(terminalFontSizeSp = clamped)
                }
            }
        }
    }

    fun showFontSizeSnackbar() {
        uiScope.launch {
            snackbar.hostState.newestSnackbarData()?.dismiss()
            val result = snackbar.hostState.showSnackbar(
                message = "终端字号 ${terminalFontSizeSp.roundToInt()}sp",
                actionLabel = "恢复默认",
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                updateTerminalFontSize(DEFAULT_TERMINAL_FONT_SIZE_SP)
            }
        }
    }

    fun handleTerminalTouchInterception(
        view: TerminalView,
        event: MotionEvent,
    ): Boolean {
        val shouldLock =
            event.pointerCount > 1 || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN
        if (shouldLock) {
            view.parent?.requestDisallowInterceptTouchEvent(true)
            if (!pinchGestureLock) {
                pinchGestureLock = true
                onTerminalGestureLockChanged(true)
            }
        }
        val shouldUnlock = event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL ||
                (event.actionMasked == MotionEvent.ACTION_POINTER_UP && event.pointerCount <= 2)
        if (shouldUnlock) {
            view.parent?.requestDisallowInterceptTouchEvent(false)
            if (pinchGestureLock) {
                pinchGestureLock = false
                onTerminalGestureLockChanged(false)
            }
        }
        return false
    }

    fun openShellSession(showKeyboardAfterConnect: Boolean) {
        if (shellStream != null || shellConnecting) {
            if (shellReady && showKeyboardAfterConnect) {
                terminalView?.requestFocusFromTouch()
                terminalView?.requestFocus()
                terminalView?.post {
                    terminalView?.requestFocusFromTouch()
                    terminalView?.requestFocus()
                    terminalView?.let(LocalInputService::showSoftKeyboard)
                }
            }
            return
        }
        shellConnecting = true
        taskScope.launch {
            val streamResult = runCatching {
                NativeAdbService.openShellStream("")
            }
            val stream = streamResult.getOrElse { error ->
                withContext(Dispatchers.Main) {
                    shellConnecting = false
                    shellReady = false
                    snackbar.show("终端会话创建失败: ${error.message ?: error.javaClass.simpleName}")
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                shellStream = stream
                shellReady = true
                shellConnecting = false
                if (showKeyboardAfterConnect) {
                    terminalView?.requestFocusFromTouch()
                    terminalView?.requestFocus()
                    terminalView?.post {
                        terminalView?.requestFocusFromTouch()
                        terminalView?.requestFocus()
                        terminalView?.let(LocalInputService::showSoftKeyboard)
                    }
                }
            }

            val buffer = ByteArray(4096)
            try {
                while (!stream.closed) {
                    val count = stream.inputStream.read(buffer)
                    if (count <= 0) {
                        break
                    }
                    withContext(Dispatchers.Main) {
                        terminalSession.append(buffer, count)
                    }
                }
            } catch (error: Throwable) {
                withContext(Dispatchers.Main) {
                    snackbar.show("终端输出失败: ${error.message ?: error.javaClass.simpleName}")
                }
            } finally {
                runCatching { stream.close() }
                withContext(Dispatchers.Main) {
                    if (shellStream === stream) {
                        shellStream = null
                    }
                    shellReady = false
                    shellConnecting = false
                }
            }
        }
    }

    fun consumeLatchedModifiers(): Int {
        var modifiers = 0
        if (ctrlLatched) modifiers = modifiers or KeyHandler.KEYMOD_CTRL
        if (altLatched) modifiers = modifiers or KeyHandler.KEYMOD_ALT
        ctrlLatched = false
        altLatched = false
        pendingLatchedConsume = false
        return modifiers
    }

    fun consumeLatchedVisualState() {
        ctrlLatched = false
        altLatched = false
        pendingLatchedConsume = false
    }

    fun applyCtrlModifier(ch: Char): Char {
        return when (ch) {
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

    fun writeLiteralKey(text: String) {
        var payload = text
        val modifiers = consumeLatchedModifiers()
        if ((modifiers and KeyHandler.KEYMOD_CTRL) != 0) {
            payload = payload.map(::applyCtrlModifier).joinToString(separator = "")
        }
        if ((modifiers and KeyHandler.KEYMOD_ALT) != 0) {
            payload = "\u001B$payload"
        }
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        writeBytesToShell(bytes, 0, bytes.size)
    }

    fun writeSpecialKey(keyCode: Int) {
        val modifiers = consumeLatchedModifiers()
        val sequence = KeyHandler.getCode(
            keyCode,
            modifiers,
            terminalSession.emulator.isCursorKeysApplicationMode(),
            terminalSession.emulator.isKeypadApplicationMode(),
        ) ?: return
        val bytes = sequence.toByteArray(StandardCharsets.UTF_8)
        writeBytesToShell(bytes, 0, bytes.size)
    }

    val terminalViewClient = remember {
        object : TerminalViewClient {
            override fun onScale(scale: Float): Float {
                when {
                    scale >= FONT_SCALE_STEP_THRESHOLD -> {
                        updateTerminalFontSize(terminalFontSizeSp + 1f)
                        showFontSizeSnackbar()
                        return 1f
                    }

                    scale <= 1f / FONT_SCALE_STEP_THRESHOLD -> {
                        updateTerminalFontSize(terminalFontSizeSp - 1f)
                        showFontSizeSnackbar()
                        return 1f
                    }
                }
                return scale
            }

            override fun onSingleTapUp(e: MotionEvent) {
                openShellSession(showKeyboardAfterConnect = true)
            }

            override fun shouldBackButtonBeMappedToEscape(): Boolean = false

            override fun shouldEnforceCharBasedInput(): Boolean = false

            override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

            override fun isTerminalViewSelected(): Boolean = true

            override fun copyModeChanged(copyMode: Boolean) = Unit

            override fun onKeyDown(
                keyCode: Int,
                e: KeyEvent,
                session: TerminalSession,
            ): Boolean {
                if (e.action == KeyEvent.ACTION_DOWN && (ctrlLatched || altLatched)) {
                    pendingLatchedConsume = true
                }
                return false
            }

            override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
                if (e.action == KeyEvent.ACTION_UP && pendingLatchedConsume) {
                    consumeLatchedVisualState()
                }
                return false
            }

            override fun onLongPress(event: MotionEvent): Boolean = false

            override fun readControlKey(): Boolean = ctrlLatched

            override fun readAltKey(): Boolean = altLatched

            override fun readShiftKey(): Boolean = false

            override fun readFnKey(): Boolean = false

            override fun onCodePoint(
                codePoint: Int,
                ctrlDown: Boolean,
                session: TerminalSession,
            ): Boolean {
                if (ctrlLatched || altLatched || pendingLatchedConsume) {
                    consumeLatchedVisualState()
                }
                return false
            }

            override fun onEmulatorSet() {
                terminalView?.setTerminalCursorBlinkerRate(500)
                terminalView?.setTerminalCursorBlinkerState(
                    start = true,
                    startOnlyIfCursorEnabled = true
                )
                syncOutput()
            }

            override fun logError(tag: String?, message: String?) {
                Log.e(tag ?: LOG_TAG, message.orEmpty())
            }

            override fun logWarn(tag: String?, message: String?) {
                Log.w(tag ?: LOG_TAG, message.orEmpty())
            }

            override fun logInfo(tag: String?, message: String?) {
                Log.i(tag ?: LOG_TAG, message.orEmpty())
            }

            override fun logDebug(tag: String?, message: String?) {
                Log.d(tag ?: LOG_TAG, message.orEmpty())
            }

            override fun logVerbose(tag: String?, message: String?) {
                Log.v(tag ?: LOG_TAG, message.orEmpty())
            }

            override fun logStackTraceWithMessage(
                tag: String?,
                message: String?,
                e: Exception?,
            ) {
                Log.e(tag ?: LOG_TAG, message, e)
            }

            override fun logStackTrace(tag: String?, e: Exception?) {
                Log.e(tag ?: LOG_TAG, e?.message, e)
            }
        }
    }

    LaunchedEffect(output) {
        if (output.isEmpty() && extractTranscript(terminalSession).isNotEmpty()) {
            terminalSession.reset()
            applyTerminalThemeColors()
            terminalView?.mEmulator = terminalSession.emulator
            terminalView?.onScreenUpdated()
            syncOutput()
        }
    }

    LaunchedEffect(isActive) {
        if (!isActive) {
            onTerminalGestureLockChanged(false)
            return@LaunchedEffect
        }
        customTypeface = loadTerminalTypeface(context)
        if (shellStream == null && !shellConnecting) {
            val connected = runCatching { NativeAdbService.isConnected() }.getOrDefault(false)
            if (connected) {
                openShellSession(showKeyboardAfterConnect = false)
            }
        }
    }

    LaunchedEffect(colorScheme.surface, colorScheme.onSurface) {
        applyTerminalThemeColors()
        terminalView?.onScreenUpdated()
        syncOutput()
    }

    DisposableEffect(Unit) {
        onDispose {
            val latestFontSize = terminalFontSizeSp
            onTerminalGestureLockChanged(false)
            taskScope.launch {
                val latest = appSettings.bundleState.value
                if (latest.terminalFontSizeSp != latestFontSize) {
                    appSettings.updateBundle {
                        it.copy(terminalFontSizeSp = latestFontSize)
                    }
                }
            }
            runCatching { shellStream?.close() }
            taskScope.cancel()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(
                start = UiSpacing.PageHorizontal,
                top = UiSpacing.PageHorizontal,
                end = UiSpacing.PageVertical,
                bottom = UiSpacing.PageVertical +
                        max(bottomInnerPadding.value, imeBottomDp.value).dp,
            ),
    ) {
        AndroidView(
            factory = { viewContext ->
                TerminalView(viewContext, null).apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                    setTerminalViewClient(terminalViewClient)
                    setIsTerminalViewKeyLoggingEnabled(false)
                    setTextSize(with(density) { terminalFontSizeSp.sp.roundToPx() })
                    setTypeface(customTypeface ?: Typeface.MONOSPACE)
                    attachSession(terminalSession)
                    applyTerminalThemeColors()
                    setTerminalCursorBlinkerRate(500)
                    setTerminalCursorBlinkerState(
                        start = true,
                        startOnlyIfCursorEnabled = true,
                    )
                    setOnTouchListener { _, event ->
                        handleTerminalTouchInterception(this, event)
                    }
                    terminalView = this
                }
            },
            update = {
                terminalView = it
                it.setTextSize(with(density) { terminalFontSizeSp.sp.roundToPx() })
                it.setTypeface(customTypeface ?: Typeface.MONOSPACE)
                if (it.currentSession !== terminalSession) {
                    it.attachSession(terminalSession)
                }
                it.setOnTouchListener { _, event ->
                    handleTerminalTouchInterception(it, event)
                }
                applyTerminalThemeColors()
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            TerminalExtraKeyButton(
                label = "ESC",
                modifier = Modifier.weight(1f),
            ) {
                haptic.contextClick()
                writeLiteralKey("\u001B")
            }
            TerminalExtraKeyButton(
                label = "/",
                modifier = Modifier.weight(1f),
            ) {
                haptic.contextClick()
                writeLiteralKey("/")
            }
            TerminalExtraKeyButton(
                label = "-",
                modifier = Modifier.weight(1f),
            ) {
                haptic.contextClick()
                writeLiteralKey("-")
            }
            TerminalExtraKeyButton(
                label = "HOME",
                modifier = Modifier.weight(1f),
            ) {
                haptic.contextClick()
                writeSpecialKey(KeyEvent.KEYCODE_MOVE_HOME)
            }
            TerminalExtraKeyButton(
                label = "↑",
                modifier = Modifier.weight(1f),
            ) {
                haptic.contextClick()
                writeSpecialKey(KeyEvent.KEYCODE_DPAD_UP)
            }
            TerminalExtraKeyButton(
                label = "END",
                modifier = Modifier.weight(1f),
            ) {
                haptic.contextClick()
                writeSpecialKey(KeyEvent.KEYCODE_MOVE_END)
            }
            TerminalExtraKeyButton(
                label = "PGUP",
                modifier = Modifier.weight(1f),
            ) {
                haptic.contextClick()
                writeSpecialKey(KeyEvent.KEYCODE_PAGE_UP)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            TerminalExtraKeyButton(
                label = "TAB",
                modifier = Modifier.weight(1f),
            ) {
                haptic.contextClick()
                writeSpecialKey(KeyEvent.KEYCODE_TAB)
            }
            TerminalExtraKeyButton(
                label = "CTRL",
                active = ctrlLatched,
                modifier = Modifier.weight(1f),
            ) {
                haptic.contextClick()
                ctrlLatched = !ctrlLatched
            }
            TerminalExtraKeyButton(
                label = "ALT",
                active = altLatched,
                modifier = Modifier.weight(1f),
            ) {
                haptic.contextClick()
                altLatched = !altLatched
            }
            TerminalExtraKeyButton(
                label = "←",
                modifier = Modifier.weight(1f),
            ) {
                haptic.contextClick()
                writeSpecialKey(KeyEvent.KEYCODE_DPAD_LEFT)
            }
            TerminalExtraKeyButton(
                label = "↓",
                modifier = Modifier.weight(1f),
            ) {
                haptic.contextClick()
                writeSpecialKey(KeyEvent.KEYCODE_DPAD_DOWN)
            }
            TerminalExtraKeyButton(
                label = "→",
                modifier = Modifier.weight(1f),
            ) {
                haptic.contextClick()
                writeSpecialKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            }
            TerminalExtraKeyButton(
                label = "PGDN",
                modifier = Modifier.weight(1f),
            ) {
                haptic.contextClick()
                writeSpecialKey(KeyEvent.KEYCODE_PAGE_DOWN)
            }
        }
    }
}

@Composable
private fun TerminalExtraKeyButton(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val content =
        if (active) colorScheme.primary
        else colorScheme.onSurface

    Box(
        modifier = modifier
            .height(32.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = content,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun TerminalOutputBottomSheet(
    show: Boolean,
    output: String,
    onDismissRequest: () -> Unit,
    onCopyAll: () -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = "自由复制",
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
        endAction = {
            IconButton(onClick = onCopyAll) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "复制全部",
                )
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(2f / 3f),
        ) {
            item {
                TextField(
                    value = output.ifBlank { "当前没有输出" },
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = UiSpacing.PageVertical),
                    readOnly = true,
                    label = "终端输出",
                    useLabelAsPlaceholder = true,
                )
            }
        }
    }
}
