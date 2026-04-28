package io.github.miuzarte.scrcpyforandroid.pages

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.services.LocalInputService
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import java.io.File
import kotlin.math.abs
import kotlin.math.max

private const val FONT_SCALE_STEP_THRESHOLD = 1.08f
private const val LOG_TAG = "TerminalScreen"

private fun terminalFontFile(context: android.content.Context) =
    File(context.filesDir, "terminal/font.ttf")

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
    val viewModel: TerminalViewModel = viewModel()
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showOutputSheet by rememberSaveable { mutableStateOf(false) }
    var output by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { snackbar.show(it) }
    }

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
                                    imageVector = MiuixIcons.More,
                                    contentDescription = "更多",
                                )
                            }
                            OverlayListPopup(
                                show = showMenu,
                                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                onDismissRequest = { showMenu = false },
                            ) {
                                ListPopupColumn {
                                    DropdownImpl(
                                        text = "自由复制",
                                        optionSize = 2,
                                        isSelected = false,
                                        index = 0,
                                        enabled = output.isNotBlank(),
                                        onSelectedIndexChange = {
                                            showMenu = false
                                            showOutputSheet = true
                                        },
                                    )
                                    DropdownImpl(
                                        text = "清屏",
                                        optionSize = 2,
                                        isSelected = false,
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
                else Modifier
        ) {
            TerminalPage(
                viewModel = viewModel,
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
    viewModel: TerminalViewModel,
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

    val asBundle by viewModel.asBundle.collectAsState()
    val terminalFontSizeSp by viewModel.terminalFontSizeSp.collectAsState()

    val imeBottomDp = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    var pinchGestureLock by remember { mutableStateOf(false) }
    var terminalTouchStartX by remember { mutableFloatStateOf(0f) }
    var terminalTouchStartY by remember { mutableFloatStateOf(0f) }
    val terminalTouchSlop = remember(context) { ViewConfiguration.get(context).scaledTouchSlop }
    var customTypeface by remember { mutableStateOf(loadTerminalTypeface(context)) }
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }

    val terminalSurfaceColorArgb = colorScheme.surface.toArgb()
    val terminalOnSurfaceColorArgb = colorScheme.onSurface.toArgb()
    val terminalCursorColorArgb =
        if (colorScheme.surface.luminance() < 0.5f) 0xffffffff.toInt()
        else 0xff000000.toInt()

    val terminalSession = remember {
        TerminalSession(
            shellWriter = { data, offset, count ->
                viewModel.writeBytesToShell(data, offset, count)
            },
            onScreenUpdated = {
                viewModel.syncOutput { onOutputChange(it) }
                terminalView?.onScreenUpdated()
            },
            onCopyTextToClipboardRequested = { text ->
                LocalInputService.setClipboardText(context, text)
                snackbar.show("已复制到剪贴板")
            },
            onPasteTextFromClipboardRequested = { viewModel.writeClipboardToShell(context) },
            onBellRequested = {},
        ).also { viewModel.sessionHolder[0] = it }
    }

    fun requestTerminalFocus() {
        terminalView?.requestFocusFromTouch()
        terminalView?.requestFocus()
        terminalView?.post {
            terminalView?.requestFocusFromTouch()
            terminalView?.requestFocus()
            terminalView?.let(LocalInputService::showSoftKeyboard)
        }
    }

    fun openShellSession(showKeyboardAfterConnect: Boolean) {
        viewModel.openShellSession(showKeyboardAfterConnect, ::requestTerminalFocus)
    }

    fun updateFontSize(newValue: Float) {
        viewModel.updateTerminalFontSize(newValue) { clamped ->
            terminalView?.setTextSize(with(density) { clamped.sp.roundToPx() })
        }
    }

    fun showFontSizeSnackbar() {
        viewModel.launchFontSizeSnackbar(
            fontSizeSp = terminalFontSizeSp,
            hostState = snackbar.hostState,
            onReset = { clamped ->
                terminalView?.setTextSize(with(density) { clamped.sp.roundToPx() })
            },
        )
    }

    fun applyTheme() {
        viewModel.applyTerminalThemeColors(
            terminalSurfaceColorArgb,
            terminalOnSurfaceColorArgb,
            terminalCursorColorArgb,
        )
    }

    fun handleTerminalTouchInterception(view: TerminalView, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                terminalTouchStartX = event.x
                terminalTouchStartY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                if (!pinchGestureLock && event.pointerCount == 1) {
                    val dx = abs(event.x - terminalTouchStartX)
                    val dy = abs(event.y - terminalTouchStartY)
                    if (dy > terminalTouchSlop && dy > dx) {
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        pinchGestureLock = true
                        onTerminalGestureLockChanged(true)
                    }
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                view.parent?.requestDisallowInterceptTouchEvent(true)
                if (!pinchGestureLock) {
                    pinchGestureLock = true
                    onTerminalGestureLockChanged(true)
                }
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

    val terminalViewClient = remember {
        object : TerminalViewClient {
            override fun onScale(scale: Float): Float {
                when {
                    scale >= FONT_SCALE_STEP_THRESHOLD -> {
                        updateFontSize(terminalFontSizeSp + 1f)
                        showFontSizeSnackbar()
                        return 1f
                    }

                    scale <= 1f / FONT_SCALE_STEP_THRESHOLD -> {
                        updateFontSize(terminalFontSizeSp - 1f)
                        showFontSizeSnackbar()
                        return 1f
                    }
                }
                return scale
            }

            override fun onSingleTapUp(e: MotionEvent) {
                openShellSession(true)
            }

            override fun shouldBackButtonBeMappedToEscape() = false
            override fun shouldEnforceCharBasedInput() = false
            override fun shouldUseCtrlSpaceWorkaround() = false
            override fun isTerminalViewSelected() = true
            override fun copyModeChanged(copyMode: Boolean) = Unit
            override fun onKeyDown(
                keyCode: Int,
                e: KeyEvent,
                session: TerminalSession,
            ): Boolean {
                if (e.action == KeyEvent.ACTION_DOWN && (viewModel.ctrlLatched || viewModel.altLatched))
                    viewModel.pendingLatchedConsume = true
                return false
            }

            override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
                if (e.action == KeyEvent.ACTION_UP && viewModel.pendingLatchedConsume)
                    viewModel.consumeLatchedVisualState()
                return false
            }

            override fun onLongPress(event: MotionEvent) = false
            override fun readControlKey() = viewModel.ctrlLatched
            override fun readAltKey() = viewModel.altLatched
            override fun readShiftKey() = false
            override fun readFnKey() = false
            override fun onCodePoint(
                codePoint: Int,
                ctrlDown: Boolean,
                session: TerminalSession,
            ): Boolean {
                if (viewModel.ctrlLatched || viewModel.altLatched || viewModel.pendingLatchedConsume)
                    viewModel.consumeLatchedVisualState()
                return false
            }

            override fun onEmulatorSet() {
                terminalView?.setTerminalCursorBlinkerRate(500)
                terminalView?.setTerminalCursorBlinkerState(
                    start = true,
                    startOnlyIfCursorEnabled = true
                )
                viewModel.syncOutput { onOutputChange(it) }
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

            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
                Log.e(tag ?: LOG_TAG, message, e)
            }

            override fun logStackTrace(tag: String?, e: Exception?) {
                Log.e(tag ?: LOG_TAG, e?.message, e)
            }
        }
    }

    // reset on clear output
    LaunchedEffect(output) {
        if (output.isEmpty() && viewModel.extractTranscript(terminalSession).isNotEmpty()) {
            terminalSession.reset()
            applyTheme()
            terminalView?.mEmulator = terminalSession.emulator
            terminalView?.onScreenUpdated()
            viewModel.syncOutput { onOutputChange(it) }
        }
    }

    // auto-connect when tab becomes active
    LaunchedEffect(isActive) {
        if (!isActive) {
            onTerminalGestureLockChanged(false)
            return@LaunchedEffect
        }
        customTypeface = loadTerminalTypeface(context)
        viewModel.autoConnectIfNeeded(::requestTerminalFocus)
    }

    // theme changes
    LaunchedEffect(
        colorScheme.surface,
        colorScheme.onSurface
    ) {
        applyTheme()
        terminalView?.onScreenUpdated()
        viewModel.syncOutput { onOutputChange(it) }
    }

    // font size sync from storage
    LaunchedEffect(asBundle.terminalFontSizeSp) {
        if (terminalFontSizeSp != asBundle.terminalFontSizeSp) {
            updateFontSize(asBundle.terminalFontSizeSp)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onTerminalGestureLockChanged(false)
            viewModel.closeShell()
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
                    applyTheme()
                    setTerminalCursorBlinkerRate(500)
                    setTerminalCursorBlinkerState(start = true, startOnlyIfCursorEnabled = true)
                    setOnTouchListener { _, event -> handleTerminalTouchInterception(this, event) }
                    terminalView = this
                }
            },
            update = {
                terminalView = it
                it.setTextSize(with(density) { terminalFontSizeSp.sp.roundToPx() })
                it.setTypeface(customTypeface ?: Typeface.MONOSPACE)
                if (it.currentSession !== terminalSession) it.attachSession(terminalSession)
                it.setOnTouchListener { _, event -> handleTerminalTouchInterception(it, event) }
                applyTheme()
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            TerminalExtraKeyButton(
                "ESC",
                Modifier.weight(1f),
            ) {
                haptic.contextClick()
                viewModel.writeLiteralKey("")
            }
            TerminalExtraKeyButton(
                "/",
                Modifier.weight(1f),
            ) {
                haptic.contextClick()
                viewModel.writeLiteralKey("/")
            }
            TerminalExtraKeyButton(
                "-",
                Modifier.weight(1f),
            ) {
                haptic.contextClick()
                viewModel.writeLiteralKey("-")
            }
            TerminalExtraKeyButton(
                "HOME",
                Modifier.weight(1f),
            ) {
                haptic.contextClick()
                viewModel.writeSpecialKey(KeyEvent.KEYCODE_MOVE_HOME)
            }
            TerminalExtraKeyButton(
                "↑",
                Modifier.weight(1f),
            ) {
                haptic.contextClick()
                viewModel.writeSpecialKey(KeyEvent.KEYCODE_DPAD_UP)
            }
            TerminalExtraKeyButton(
                "END",
                Modifier.weight(1f),
            ) {
                haptic.contextClick()
                viewModel.writeSpecialKey(KeyEvent.KEYCODE_MOVE_END)
            }
            TerminalExtraKeyButton(
                "PGUP",
                Modifier.weight(1f),
            ) {
                haptic.contextClick()
                viewModel.writeSpecialKey(KeyEvent.KEYCODE_PAGE_UP)
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            TerminalExtraKeyButton(
                "TAB",
                Modifier.weight(1f),
            ) {
                haptic.contextClick()
                viewModel.writeSpecialKey(KeyEvent.KEYCODE_TAB)
            }
            TerminalExtraKeyButton(
                "CTRL",
                Modifier.weight(1f),
                active = viewModel.ctrlLatched
            ) {
                haptic.contextClick()
                viewModel.ctrlLatched = !viewModel.ctrlLatched
            }
            TerminalExtraKeyButton(
                "ALT",
                Modifier.weight(1f),
                active = viewModel.altLatched
            ) {
                haptic.contextClick()
                viewModel.altLatched = !viewModel.altLatched
            }
            TerminalExtraKeyButton(
                "←",
                Modifier.weight(1f),
            ) {
                haptic.contextClick()
                viewModel.writeSpecialKey(KeyEvent.KEYCODE_DPAD_LEFT)
            }
            TerminalExtraKeyButton(
                "↓",
                Modifier.weight(1f),
            ) {
                haptic.contextClick()
                viewModel.writeSpecialKey(KeyEvent.KEYCODE_DPAD_DOWN)
            }
            TerminalExtraKeyButton(
                "→",
                Modifier.weight(1f),
            ) {
                haptic.contextClick()
                viewModel.writeSpecialKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            }
            TerminalExtraKeyButton(
                "PGDN",
                Modifier.weight(1f),
            ) {
                haptic.contextClick()
                viewModel.writeSpecialKey(KeyEvent.KEYCODE_PAGE_DOWN)
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
                .fillMaxHeight(2f / 3f)
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
