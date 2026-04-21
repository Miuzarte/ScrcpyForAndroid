package io.github.miuzarte.scrcpyforandroid.pages

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.nativecore.AdbSocketStream
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.services.LocalInputService
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import io.github.miuzarte.scrcpyforandroid.widgets.PopupMenuItem
import io.github.miuzarte.scrcpyforandroid.widgets.TerminalInputView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
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
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.roundToInt
import android.view.KeyEvent as AndroidKeyEvent

private const val DEFAULT_TERMINAL_FONT_SIZE_SP = 14f
private const val MIN_TERMINAL_FONT_SIZE_SP = 1f
private const val MAX_TERMINAL_FONT_SIZE_SP = 32f
private const val FONT_SCALE_STEP_THRESHOLD = 1.08f

@Composable
fun TerminalScreen(
    bottomInnerPadding: Dp,
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
                                        text = "清空输出",
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
    output: String,
    onOutputChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val density = LocalDensity.current
    val uiScope = rememberCoroutineScope()
    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val touchSlop = remember(context) { ViewConfiguration.get(context).scaledTouchSlop }
    val outputScrollState = rememberScrollState()
    var outputBuffer by rememberSaveable { mutableStateOf(output) }
    var terminalFontSizeSp by rememberSaveable { mutableFloatStateOf(DEFAULT_TERMINAL_FONT_SIZE_SP) }
    var shellReady by remember { mutableStateOf(false) }
    var shellConnecting by remember { mutableStateOf(false) }
    var shellStream by remember { mutableStateOf<AdbSocketStream?>(null) }
    var terminalInputView by remember { mutableStateOf<TerminalInputView?>(null) }
    var showKeyboardWhenReady by remember { mutableStateOf(false) }
    var pinchInProgress by remember { mutableStateOf(false) }
    var touchDownX by remember { mutableFloatStateOf(0f) }
    var touchDownY by remember { mutableFloatStateOf(0f) }
    var tapPending by remember { mutableStateOf(false) }
    val imeBottomDp = with(density) { WindowInsets.ime.getBottom(this).toDp() }

    fun commitOutput(text: String) {
        outputBuffer = text
        onOutputChange(text)
    }

    fun appendRawOutput(text: String) {
        if (text.isEmpty()) {
            return
        }
        val builder = StringBuilder(outputBuffer)
        text.forEach { ch ->
            when (ch) {
                '\b',
                '\u007F' -> if (builder.isNotEmpty()) builder.deleteCharAt(builder.lastIndex)

                else -> builder.append(ch)
            }
        }
        commitOutput(builder.toString())
    }

    fun appendOutputLine(text: String) {
        commitOutput(
            buildString {
                append(outputBuffer)
                if (isNotEmpty() && !endsWith("\n")) {
                    append("\n")
                }
                append(text)
                if (!endsWith("\n")) {
                    append("\n")
                }
            }
        )
    }

    fun writeToShell(text: String) {
        val stream = shellStream
        if (stream == null || !shellReady || stream.closed) {
            snackbar.show("终端会话尚未就绪")
            return
        }
        taskScope.launch {
            val result = runCatching {
                stream.outputStream.write(text.toByteArray(StandardCharsets.UTF_8))
                stream.outputStream.flush()
            }
            withContext(Dispatchers.Main) {
                result.onFailure { error ->
                    appendOutputLine("错误: ${error.message ?: error.javaClass.simpleName}")
                    snackbar.show("终端输入失败")
                }
            }
        }
    }

    fun mapKeyEventToShellText(event: AndroidKeyEvent): String? {
        if (event.action != AndroidKeyEvent.ACTION_DOWN) {
            return ""
        }
        return when (event.keyCode) {
            AndroidKeyEvent.KEYCODE_ENTER -> "\n"
            AndroidKeyEvent.KEYCODE_DEL -> "\b"
            AndroidKeyEvent.KEYCODE_TAB -> "\t"
            AndroidKeyEvent.KEYCODE_DPAD_UP -> "\u001B[A"
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> "\u001B[B"
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> "\u001B[C"
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> "\u001B[D"
            else -> null
        }
    }

    fun showKeyboard() {
        val inputView = terminalInputView ?: return
        LocalInputService.showSoftKeyboard(inputView)
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
                terminalFontSizeSp = DEFAULT_TERMINAL_FONT_SIZE_SP
            }
        }
    }

    val scaleGestureDetector = remember {
        var scaleAccumulator = 1f
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    pinchInProgress = true
                    scaleAccumulator = 1f
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    scaleAccumulator *= detector.scaleFactor
                    var updated = false
                    while (scaleAccumulator >= FONT_SCALE_STEP_THRESHOLD) {
                        val nextSize = (terminalFontSizeSp + 1f)
                            .coerceAtMost(MAX_TERMINAL_FONT_SIZE_SP)
                        if (nextSize == terminalFontSizeSp) {
                            scaleAccumulator = 1f
                            break
                        }
                        terminalFontSizeSp = nextSize
                        scaleAccumulator /= FONT_SCALE_STEP_THRESHOLD
                        updated = true
                    }
                    while (scaleAccumulator <= 1f / FONT_SCALE_STEP_THRESHOLD) {
                        val nextSize = (terminalFontSizeSp - 1f)
                            .coerceAtLeast(MIN_TERMINAL_FONT_SIZE_SP)
                        if (nextSize == terminalFontSizeSp) {
                            scaleAccumulator = 1f
                            break
                        }
                        terminalFontSizeSp = nextSize
                        scaleAccumulator *= FONT_SCALE_STEP_THRESHOLD
                        updated = true
                    }
                    if (updated) {
                        showFontSizeSnackbar()
                    }
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    pinchInProgress = false
                }
            },
        )
    }

    fun openShellSession(showKeyboardAfterConnect: Boolean) {
        if (showKeyboardAfterConnect) {
            showKeyboardWhenReady = true
        }
        if (shellStream != null || shellConnecting) {
            if (shellReady && showKeyboardAfterConnect) {
                showKeyboard()
                showKeyboardWhenReady = false
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
                    showKeyboardWhenReady = false
                    appendOutputLine("错误: ${error.message ?: error.javaClass.simpleName}")
                    snackbar.show("终端会话创建失败")
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                shellStream = stream
                shellReady = true
                shellConnecting = false
                if (showKeyboardWhenReady) {
                    showKeyboard()
                    showKeyboardWhenReady = false
                }
            }

            val buffer = ByteArray(4096)
            try {
                while (!stream.closed) {
                    val count = stream.inputStream.read(buffer)
                    if (count <= 0) {
                        break
                    }
                    val chunk = String(buffer, 0, count, StandardCharsets.UTF_8)
                    withContext(Dispatchers.Main) {
                        appendRawOutput(chunk)
                    }
                }
            } catch (error: Throwable) {
                withContext(Dispatchers.Main) {
                    appendOutputLine("错误: ${error.message ?: error.javaClass.simpleName}")
                }
            } finally {
                runCatching { stream.close() }
                withContext(Dispatchers.Main) {
                    if (shellStream === stream) {
                        shellStream = null
                    }
                    shellReady = false
                    shellConnecting = false
                    showKeyboardWhenReady = false
                }
            }
        }
    }

    LaunchedEffect(output) {
        if (output != outputBuffer) {
            outputBuffer = output
        }
    }

    LaunchedEffect(outputBuffer) {
        outputScrollState.scrollTo(outputScrollState.maxValue)
    }

    LaunchedEffect(imeBottomDp.value) {
        if (imeBottomDp > 0.dp) {
            outputScrollState.scrollTo(outputScrollState.maxValue)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
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
                bottom = UiSpacing.PageVertical
                        + max(bottomInnerPadding.value, imeBottomDp.value).dp,
            ),
    ) {
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(outputScrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = outputBuffer.ifBlank {
                            """
                                可用缩放手势调整字体大小
                                轻触以连接终端
                            """.trimIndent()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = terminalFontSizeSp.sp,
                        color =
                            if (outputBuffer.isBlank()) colorScheme.onSurfaceVariantSummary
                            else colorScheme.onSurface,
                    )
                }

                AndroidView(
                    factory = {
                        TerminalInputView(it).apply {
                            alpha = 0f
                            setInputEnabled(true)
                            setOnTouchListener { _, event ->
                                scaleGestureDetector.onTouchEvent(event)
                                when {
                                    event.actionMasked == MotionEvent.ACTION_DOWN -> {
                                        pinchInProgress = false
                                        tapPending = true
                                        touchDownX = event.x
                                        touchDownY = event.y
                                        parent?.requestDisallowInterceptTouchEvent(false)
                                        true
                                    }

                                    event.pointerCount >= 2 -> {
                                        pinchInProgress = true
                                        tapPending = false
                                        parent?.requestDisallowInterceptTouchEvent(true)
                                        true
                                    }

                                    event.actionMasked == MotionEvent.ACTION_MOVE -> {
                                        val movedFar =
                                            kotlin.math.abs(event.x - touchDownX) > touchSlop ||
                                                    kotlin.math.abs(event.y - touchDownY) > touchSlop
                                        if (movedFar) {
                                            tapPending = false
                                        }
                                        if (pinchInProgress) {
                                            parent?.requestDisallowInterceptTouchEvent(true)
                                        } else {
                                            parent?.requestDisallowInterceptTouchEvent(false)
                                        }
                                        true
                                    }

                                    event.actionMasked == MotionEvent.ACTION_UP -> {
                                        if (!pinchInProgress && tapPending) {
                                            performClick()
                                            openShellSession(showKeyboardAfterConnect = true)
                                        }
                                        pinchInProgress = false
                                        tapPending = false
                                        parent?.requestDisallowInterceptTouchEvent(false)
                                        true
                                    }

                                    event.actionMasked == MotionEvent.ACTION_UP ||
                                            event.actionMasked == MotionEvent.ACTION_CANCEL -> {
                                        pinchInProgress = false
                                        tapPending = false
                                        parent?.requestDisallowInterceptTouchEvent(false)
                                        true
                                    }

                                    else -> true
                                }
                            }
                            inputCallbacks = object : TerminalInputView.InputCallbacks {
                                override fun handleCommitText(text: CharSequence): Boolean {
                                    if (text.isEmpty()) {
                                        return true
                                    }
                                    writeToShell(text.toString())
                                    return true
                                }

                                override fun handleDeleteSurroundingText(
                                    beforeLength: Int,
                                    afterLength: Int,
                                ): Boolean {
                                    val deleteCount = beforeLength.coerceAtLeast(1)
                                    writeToShell("\b".repeat(deleteCount))
                                    return true
                                }

                                override fun handleKeyEvent(event: AndroidKeyEvent): Boolean {
                                    val shellText = mapKeyEventToShellText(event) ?: return false
                                    if (shellText.isNotEmpty()) {
                                        writeToShell(shellText)
                                    }
                                    return true
                                }
                            }
                            terminalInputView = this
                        }
                    },
                    update = {
                        terminalInputView = it
                        if (showKeyboardWhenReady && shellReady) {
                            LocalInputService.showSoftKeyboard(it)
                            showKeyboardWhenReady = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
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
