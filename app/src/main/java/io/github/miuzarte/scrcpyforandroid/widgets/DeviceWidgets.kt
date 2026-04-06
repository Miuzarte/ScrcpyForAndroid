package io.github.miuzarte.scrcpyforandroid.widgets

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.constants.Defaults
import io.github.miuzarte.scrcpyforandroid.constants.ScrcpyPresets
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.haptics.rememberAppHaptics
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcut
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcuts
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperSlider
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperTextField
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.Codec
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlin.math.roundToInt

private object UiMotionActions {
    const val DOWN = 0
    const val UP = 1
    const val MOVE = 2
    const val CANCEL = 3
    const val POINTER_DOWN = 5
    const val POINTER_UP = 6
}

private const val FULLSCREEN_TOUCH_LOG_TAG = "FullscreenTouch"

@Composable
internal fun StatusCard(
    statusLine: String,
    adbConnected: Boolean,
    streaming: Boolean,
    sessionInfo: Scrcpy.Session.SessionInfo?,
    busyLabel: String?,
    connectedDeviceLabel: String,
) {
    val appSettings = Storage.appSettings

    val context = LocalContext.current

    val themeBaseIndex by appSettings.themeBaseIndex.asState()

    val cleanStatusLine = normalizeStatusLine(statusLine)

    // 根据应用主题设置决定是否使用深色模式
    val isDarkTheme = when (themeBaseIndex) {
        0 -> isSystemInDarkTheme() // 跟随系统
        1 -> false // 浅色
        2 -> true // 深色
        else -> isSystemInDarkTheme()
    }

    val spec = when {
        streaming && sessionInfo != null -> {
            val streamCardColor = when {
                isDynamicColor -> MiuixTheme.colorScheme.secondaryContainer
                isDarkTheme -> Color(0xFF1A3825)
                else -> Color(0xFFDFFAE4)
            }
            val streamTextColor = when {
                isDynamicColor -> MiuixTheme.colorScheme.onSecondaryContainer
                isDarkTheme -> Color.White
                else -> Color(0xFF111111)
            }
            val streamIconColor = if (isDynamicColor) {
                MiuixTheme.colorScheme.primary.copy(alpha = 0.8f)
            } else {
                Color(0xFF36D167)
            }
            StatusCardSpec(
                big = StatusBigCardSpec(
                    title = "投屏中 (视频流)",
                    subtitle = sessionInfo.deviceName,
                    containerColor = streamCardColor,
                    titleColor = streamTextColor,
                    subtitleColor = streamTextColor,
                    icon = Icons.Rounded.CheckCircleOutline,
                    iconTint = streamIconColor,
                ),
                firstSmall = StatusSmallCardSpec(
                    "分辨率",
                    "${sessionInfo.width}×${sessionInfo.height}",
                ),
                secondSmall = StatusSmallCardSpec(
                    "编解码器",
                    sessionInfo.codecName,
                ),
            )
        }

        adbConnected -> StatusCardSpec(
            big = StatusBigCardSpec(
                title = "ADB 已连接",
                subtitle = cleanStatusLine,
                containerColor = MiuixTheme.colorScheme.primaryContainer,
                titleColor = MiuixTheme.colorScheme.onPrimaryContainer,
                subtitleColor = MiuixTheme.colorScheme.onPrimaryContainer,
                icon = Icons.Rounded.Wifi,
                iconTint = MiuixTheme.colorScheme.primary.copy(alpha = 0.6f),
            ),
            firstSmall = StatusSmallCardSpec(
                "当前设备",
                connectedDeviceLabel,
            ),
            secondSmall = StatusSmallCardSpec(
                "状态",
                "空闲",
            ),
        )

        else -> StatusCardSpec(
            big = StatusBigCardSpec(
                title = "ADB 未连接",
                subtitle = "",
                containerColor = MiuixTheme.colorScheme.secondaryContainer,
                titleColor = MiuixTheme.colorScheme.onSecondaryContainer,
                subtitleColor = MiuixTheme.colorScheme.onSecondaryContainer,
                icon = Icons.Rounded.LinkOff,
                iconTint = MiuixTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
            ),
            firstSmall = StatusSmallCardSpec(
                "当前设备",
                "N/A",
            ),
            secondSmall = StatusSmallCardSpec(
                "状态",
                "N/A",
            ),
        )
    }

    StatusCardLayout(spec = spec, busyLabel = busyLabel)
}

@Composable
internal fun PairingCard(
    busy: Boolean,
    autoDiscoverOnDialogOpen: Boolean,
    onDiscoverTarget: (suspend () -> Pair<String, Int>?)? = null,
    onPair: (host: String, port: String, code: String) -> Unit,
) {
    val showPairDialog = remember { mutableStateOf(false) }
    val holdDownState = remember { mutableStateOf(false) }

    Card {
        SuperArrow(
            title = "使用配对码配对设备",
            onClick = {
                showPairDialog.value = true
                holdDownState.value = true
            },
            holdDownState = holdDownState.value,
            enabled = !busy,
        )
    }

    PairingDialog(
        showDialog = showPairDialog.value,
        enabled = !busy,
        autoDiscoverOnDialogOpen = autoDiscoverOnDialogOpen,
        onDiscoverTarget = onDiscoverTarget,
        onDismissRequest = { showPairDialog.value = false },
        onDismissFinished = { holdDownState.value = false },
        onConfirm = { host, port, code ->
            showPairDialog.value = false
            onPair(host, port, code)
        },
    )
}

@Composable
internal fun PreviewCard(
    sessionInfo: Scrcpy.Session.SessionInfo?,
    nativeCore: NativeCoreFacade,
    previewHeightDp: Int,
    controlsVisible: Boolean,
    onTapped: () -> Unit,
    onOpenFullscreen: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    val alpha by animateFloatAsState(if (controlsVisible) 1f else 0f, label = "preview-controls")

    Card {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeightDp.coerceAtLeast(120).dp)
                .pointerInput(sessionInfo) { detectTapGestures(onTap = { onTapped() }) },
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val sessionAspect = if (sessionInfo == null || sessionInfo.height == 0) {
                    16f / 9f
                } else {
                    sessionInfo.width.toFloat() / sessionInfo.height.toFloat()
                }
                val containerAspect = maxWidth.value / maxHeight.value
                val fittedModifier = if (sessionAspect > containerAspect) {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(sessionAspect)
                } else {
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(sessionAspect)
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .then(fittedModifier),
                ) {
                    ScrcpyVideoSurface(
                        modifier = Modifier.fillMaxSize(),
                        nativeCore = nativeCore,
                        session = sessionInfo,
                    )
                }
            }

            if (sessionInfo != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(UiSpacing.CardContent),
                ) {
                    Button(
                        onClick = {
                            if (alpha > 0.1) {
                                haptics.contextClick()
                                onOpenFullscreen()
                            }
                        },
                        modifier = Modifier.alpha(alpha),
                    ) {
                        Icon(
                            Icons.Rounded.Fullscreen,
                            contentDescription = "全屏",
                        )
                        Spacer(Modifier.width(UiSpacing.SectionTitleBottom))
                        Text("全屏")
                    }
                }
            }
        }
    }
}

@Composable
internal fun VirtualButtonCard(
    busy: Boolean,
    outsideActions: List<VirtualButtonAction>,
    moreActions: List<VirtualButtonAction>,
    showText: Boolean,
    onAction: (VirtualButtonAction) -> Unit,
) {
    val bar = remember(outsideActions, moreActions) {
        VirtualButtonBar(
            outsideActions = outsideActions,
            moreActions = moreActions,
        )
    }

    Card {
        bar.Preview(
            enabled = !busy,
            showText = showText,
            onAction = onAction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiSpacing.CardContent),
        )
    }
}

@Composable
internal fun ConfigPanel(
    busy: Boolean,
    audioForwardingSupported: Boolean,
    cameraMirroringSupported: Boolean,
    onOpenAdvanced: () -> Unit,
    onStartStopHaptic: (() -> Unit)? = null,
    onStart: () -> Unit,
    onStop: () -> Unit,
    sessionInfo: Scrcpy.Session.SessionInfo?,
    onDisconnect: () -> Unit = {},
) {
    val scrcpyOptions = Storage.scrcpyOptions
    val quickDevices = Storage.quickDevices

    val context = LocalContext.current

    val sessionStarted = sessionInfo != null
    
    // Check if device exists in shortcuts
    var quickDevicesList by quickDevices.quickDevicesList.asMutableState()
    val savedShortcuts = remember(quickDevicesList) {
        DeviceShortcuts.unmarshalFrom(quickDevicesList)
    }
    val isQuickConnected = remember(sessionInfo, savedShortcuts) {
        sessionInfo?.let { info ->
            savedShortcuts.get(info.host, info.port) == null
        } ?: false
    }

    var audio by scrcpyOptions.audio.asMutableState()

    var audioCodec by scrcpyOptions.audioCodec.asMutableState()
    val audioCodecItems = remember { Codec.AUDIO.map { it.displayName } }
    val audioCodecIndex = Codec.AUDIO.indexOfFirst {
        it.string == audioCodec
    }.coerceAtLeast(0)
    var audioBitRate by scrcpyOptions.audioBitRate.asMutableState()

    var videoCodec by scrcpyOptions.videoCodec.asMutableState()
    val videoCodecItems = remember { Codec.VIDEO.map { it.displayName } }
    val videoCodecIndex = Codec.VIDEO.indexOfFirst {
        it.string == videoCodec
    }.coerceAtLeast(0)
    var videoBitRate by scrcpyOptions.videoBitRate.asMutableState()
    val videoBitRateMbps = videoBitRate / 1_000_000f

    SectionSmallTitle("Scrcpy")
    Card {
        SuperSwitch(
            title = "音频转发",
            summary = "转发设备音频到本机 (Android 11+)",
            checked = audio,
            onCheckedChange = { audio = it },
            enabled = !sessionStarted && audioForwardingSupported,
        )

        SuperDropdown(
            title = "音频编码",
            summary = "--audio-codec",
            items = audioCodecItems,
            selectedIndex = audioCodecIndex,
            onSelectedIndexChange = { audioCodec = Codec.AUDIO[it].string },
            enabled = !sessionStarted && audio,
        )
        AnimatedVisibility(audio && (audioCodec == "opus" || audioCodec == "aac")) {
            SuperSlider(
                title = "音频码率",
                summary = "--audio-bit-rate",
                value = ScrcpyPresets.AudioBitRate.indexOfOrNearest(audioBitRate / 1000).toFloat(),
                onValueChange = { value ->
                    val idx = value.roundToInt().coerceIn(0, ScrcpyPresets.AudioBitRate.lastIndex)
                    audioBitRate = ScrcpyPresets.AudioBitRate[idx] * 1000
                },
                valueRange = 0f..ScrcpyPresets.AudioBitRate.lastIndex.toFloat(),
                steps = (ScrcpyPresets.AudioBitRate.size - 2).coerceAtLeast(0),
                enabled = !sessionStarted,
                unit = "Kbps",
                displayText = (audioBitRate / 1000).toString(),
                inputInitialValue = (audioBitRate / 1000).toString(),
                inputFilter = { it.filter(Char::isDigit) },
                inputValueRange = 1f..UShort.MAX_VALUE.toFloat(),
                onInputConfirm = { raw ->
                    raw.toIntOrNull()?.takeIf { it > 0 }?.let { audioBitRate = it * 1000 }
                },
            )
        }

        SuperDropdown(
            title = "视频编码",
            summary = "--video-codec",
            items = videoCodecItems,
            selectedIndex = videoCodecIndex,
            onSelectedIndexChange = { videoCodec = Codec.VIDEO[it].string },
            enabled = !sessionStarted,
        )
        SuperSlider(
            title = "视频码率",
            summary = "--video-bit-rate",
            value = videoBitRateMbps,
            onValueChange = { mbps ->
                videoBitRate = (mbps * 1_000_000).toInt()
            },
            valueRange = 0.1f..40f,
            steps = 399,
            enabled = !sessionStarted,
            unit = "Mbps",
            displayFormatter = { formatBitRate(it) },
            inputInitialValue = formatBitRate(videoBitRateMbps),
            inputFilter = { text ->
                var dotUsed = false
                text.filter { ch ->
                    when {
                        ch.isDigit() -> true
                        ch == '.' && !dotUsed -> {
                            dotUsed = true
                            true
                        }

                        else -> false
                    }
                }
            },
            inputValueRange = 0.1f..UInt.MAX_VALUE.toFloat(),
            onInputConfirm = { raw ->
                raw.toFloatOrNull()?.let { parsed ->
                    if (parsed >= 0.1f) {
                        videoBitRate = (parsed * 1_000_000).toInt()
                    }
                }
            },
        )

        SuperArrow(
            title = "高级参数",
            summary = "更多 scrcpy 启动参数",
            onClick = onOpenAdvanced,
            enabled = !sessionStarted,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiSpacing.CardContent)
                .padding(bottom = UiSpacing.CardContent),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
        ) {
            AnimatedVisibility(!isQuickConnected) {
                AnimatedVisibility(!sessionStarted) {
                    TextButton(
                        text = "启动",
                        onClick = {
                            onStartStopHaptic?.invoke()
                            onStart()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
                AnimatedVisibility(sessionStarted) {
                    TextButton(
                        text = "停止",
                        onClick = {
                            onStartStopHaptic?.invoke()
                            onStop()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                    )
                }
            }
            // display them at the same time in quick connection
            AnimatedVisibility(isQuickConnected) {
                TextButton(
                    text = "断开",
                    onClick = {
                        onStartStopHaptic?.invoke()
                        onDisconnect()
                    },
                    modifier = Modifier.weight(1f/4f),
                    enabled = !busy,
                )
                TextButton(
                    text = "启动",
                    onClick = {
                        onStartStopHaptic?.invoke()
                        onStart()
                    },
                    modifier = Modifier.weight(3f/4f),
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

/**
 * PairingDialog
 *
 * Purpose:
 * - A small helper dialog UI that optionally performs an asynchronous discovery
 *   step (`onDiscoverTarget`) to pre-fill host/port fields.
 *
 * Behavior:
 * - Discovery runs on [Dispatchers.IO] to avoid blocking the UI and updates
 *   local `host`/`port` state on success.
 * - Input validation is simple (non-empty checks) and the final `onConfirm` callback
 *   receives trimmed values.
 */
@Composable
private fun PairingDialog(
    showDialog: Boolean,
    enabled: Boolean,
    autoDiscoverOnDialogOpen: Boolean,
    onDiscoverTarget: (suspend () -> Pair<String, Int>?)?,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onConfirm: (host: String, port: String, code: String) -> Unit,
) {
    var host by rememberSaveable(showDialog) { mutableStateOf("") }
    var port by rememberSaveable(showDialog) { mutableStateOf("") }
    var code by rememberSaveable(showDialog) { mutableStateOf("") }
    var discoveringPort by rememberSaveable(showDialog) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun doDiscover() {
        if (onDiscoverTarget == null || discoveringPort || !enabled) return
        discoveringPort = true
        val found = withContext(Dispatchers.IO) { onDiscoverTarget.invoke() }
        if (found != null) {
            host = found.first
            port = found.second.toString()
        }
        discoveringPort = false
    }

    LaunchedEffect(showDialog, autoDiscoverOnDialogOpen, onDiscoverTarget, enabled) {
        if (showDialog && autoDiscoverOnDialogOpen && onDiscoverTarget != null && !discoveringPort) {
            doDiscover()
        }
    }

    fun clearInputs() {
        host = ""
        port = ""
        code = ""
        discoveringPort = false
    }

    SuperDialog(
        show = showDialog,
        title = "使用配对码配对设备",
        summary = "使用六位数的配对码配对新设备",
        onDismissRequest = {
            onDismissRequest()
        },
        onDismissFinished = {
            clearInputs()
            onDismissFinished()
        },
        content = {
            TextField(
                value = host,
                onValueChange = { host = it },
                label = "IP 地址",
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = UiSpacing.CardContent),
            )
            TextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                label = "端口",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = UiSpacing.CardContent),
            )
            TextField(
                value = code,
                onValueChange = { code = it },
                label = "WLAN 配对码",
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = UiSpacing.CardContent),
            )

            TextButton(
                text = if (discoveringPort) "发现中..." else "自动发现",
                onClick = {
                    if (onDiscoverTarget == null || discoveringPort || !enabled) return@TextButton
                    scope.launch {
                        doDiscover()
                    }
                },
                enabled = enabled && onDiscoverTarget != null && !discoveringPort,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = UiSpacing.Medium,
                        bottom = UiSpacing.CardContent,
                    ),
            )
            Row(
                modifier = Modifier
                    .padding(bottom = UiSpacing.PopupHorizontal),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.PopupHorizontal),
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "配对",
                    onClick = {
                        onConfirm(host.trim(), port.trim(), code.trim())
                        onDismissRequest()
                    },
                    enabled = enabled &&
                            host.trim().isNotBlank() &&
                            port.trim().isNotBlank() &&
                            code.trim().isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        },
    )
}

@SuppressLint("DefaultLocale")
private fun formatBitRate(value: Float): String = String.format("%.1f", value)

@Composable
internal fun LogsPanel(lines: List<String>) {
    Card {
        TextField(
            value = lines.joinToString(separator = "\n"),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * TouchEventHandler
 *
 * Purpose:
 * - Handles touch event processing for fullscreen control screen
 * - Manages pointer tracking, coordinate mapping, and touch injection
 */
class TouchEventHandler(
    private val coroutineScope: CoroutineScope,
    private val session: Scrcpy.Session.SessionInfo,
    private val touchAreaSize: IntSize,
    private val activePointerIds: LinkedHashSet<Int>,
    private val activePointerPositions: LinkedHashMap<Int, Offset>,
    private val activePointerDevicePositions: LinkedHashMap<Int, Pair<Int, Int>>,
    private val pointerLabels: LinkedHashMap<Int, Int>,
    private var nextPointerLabel: Int,
    private val onInjectTouch: suspend (action: Int, pointerId: Long, x: Int, y: Int, pressure: Float, buttons: Int) -> Unit,
    private val onActiveTouchCountChanged: (Int) -> Unit,
    private val onActiveTouchDebugChanged: (String) -> Unit,
    private val onNextPointerLabelChanged: (Int) -> Unit,
) {
    private val eventPointerIds = HashSet<Int>(10)
    private val eventPositions = HashMap<Int, Offset>(10)
    private val eventPressures = HashMap<Int, Float>(10)
    private val justPressedPointerIds = HashSet<Int>(10)

    fun handleMotionEvent(event: MotionEvent): Boolean {
        if (touchAreaSize.width == 0 || touchAreaSize.height == 0) {
            return true
        }

        val bounds = calculateContentBounds()

        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            return handleCancelAction(bounds)
        }

        extractEventData(event)
        handleDisappearedPointers(eventPointerIds, bounds)

        val endedPointerId = getEndedPointerId(event)
        handlePointerDown(event, endedPointerId, bounds)
        handlePointerMove(event, endedPointerId, bounds)
        handlePointerUp(endedPointerId, bounds)

        onActiveTouchCountChanged(activePointerIds.size)
        refreshTouchDebug()
        return true
    }

    private data class ContentBounds(
        val width: Float,
        val height: Float,
        val left: Float,
        val top: Float,
    )

    private fun calculateContentBounds(): ContentBounds {
        val sessionAspect = if (session.height == 0) {
            16f / 9f
        } else {
            session.width.toFloat() / session.height.toFloat()
        }
        val containerWidth = touchAreaSize.width.toFloat()
        val containerHeight = touchAreaSize.height.toFloat()
        val containerAspect = containerWidth / containerHeight

        val contentWidth: Float
        val contentHeight: Float
        if (sessionAspect > containerAspect) {
            contentWidth = containerWidth
            contentHeight = containerWidth / sessionAspect
        } else {
            contentHeight = containerHeight
            contentWidth = containerHeight * sessionAspect
        }
        val contentLeft = (containerWidth - contentWidth) / 2f
        val contentTop = (containerHeight - contentHeight) / 2f

        return ContentBounds(contentWidth, contentHeight, contentLeft, contentTop)
    }

    private fun isInsideContent(rawX: Float, rawY: Float, bounds: ContentBounds): Boolean {
        return rawX in bounds.left..(bounds.left + bounds.width) &&
                rawY in bounds.top..(bounds.top + bounds.height)
    }

    private fun mapToDevice(rawX: Float, rawY: Float, bounds: ContentBounds): Pair<Int, Int> {
        val normalizedX = ((rawX - bounds.left) / bounds.width).coerceIn(0f, 1f)
        val normalizedY = ((rawY - bounds.top) / bounds.height).coerceIn(0f, 1f)
        val x = (normalizedX * (session.width - 1).coerceAtLeast(0)).roundToInt()
            .coerceIn(0, (session.width - 1).coerceAtLeast(0))
        val y = (normalizedY * (session.height - 1).coerceAtLeast(0)).roundToInt()
            .coerceIn(0, (session.height - 1).coerceAtLeast(0))
        return x to y
    }

    private fun getPointerLabel(pointerId: Int): Int {
        val existing = pointerLabels[pointerId]
        if (existing != null) {
            return existing
        }
        val assigned = nextPointerLabel
        nextPointerLabel += 1
        onNextPointerLabelChanged(nextPointerLabel)
        pointerLabels[pointerId] = assigned
        return assigned
    }

    private fun refreshTouchDebug() {
        if (activePointerIds.isEmpty()) {
            onActiveTouchDebugChanged("")
            return
        }
        val debug = activePointerIds
            .sortedBy { getPointerLabel(it) }
            .joinToString(separator = "\n") { pointerId ->
                val label = getPointerLabel(pointerId)
                val pos = activePointerDevicePositions[pointerId]
                if (pos == null) {
                    "#$label(id=$pointerId):?"
                } else {
                    "#$label(id=$pointerId):${pos.first},${pos.second}"
                }
            }
        onActiveTouchDebugChanged(debug)
    }

    private fun releasePointer(pointerId: Int, bounds: ContentBounds) {
        if (!activePointerIds.contains(pointerId)) return
        val pos = activePointerPositions[pointerId] ?: Offset.Zero
        val (x, y) = mapToDevice(pos.x, pos.y, bounds)
        coroutineScope.launch {
            runCatching {
                onInjectTouch(UiMotionActions.UP, pointerId.toLong(), x, y, 0f, 0)
            }.onFailure { e ->
                Log.w(FULLSCREEN_TOUCH_LOG_TAG, "releasePointer failed for pointerId=$pointerId", e)
            }
        }
        activePointerIds -= pointerId
        activePointerPositions.remove(pointerId)
        activePointerDevicePositions.remove(pointerId)
        pointerLabels.remove(pointerId)
    }

    private fun handleCancelAction(bounds: ContentBounds): Boolean {
        val toCancel = activePointerIds.toList()
        for (pointerId in toCancel) {
            releasePointer(pointerId, bounds)
        }
        onActiveTouchCountChanged(activePointerIds.size)
        refreshTouchDebug()
        return true
    }

    private fun extractEventData(event: MotionEvent) {
        eventPointerIds.clear()
        eventPositions.clear()
        eventPressures.clear()
        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            eventPointerIds += pointerId
            eventPositions[pointerId] = Offset(event.getX(i), event.getY(i))
            eventPressures[pointerId] = event.getPressure(i).coerceIn(0f, 1f)
        }
    }

    private fun handleDisappearedPointers(eventPointerIds: Set<Int>, bounds: ContentBounds) {
        val disappearedPointers = activePointerIds.filter { it !in eventPointerIds }
        for (pointerId in disappearedPointers) {
            releasePointer(pointerId, bounds)
        }
    }

    private fun getEndedPointerId(event: MotionEvent): Int? {
        return when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> event.getPointerId(event.actionIndex)
            else -> null
        }
    }

    private fun handlePointerDown(
        event: MotionEvent,
        endedPointerId: Int?,
        bounds: ContentBounds,
    ) {
        justPressedPointerIds.clear()
        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            if (pointerId == endedPointerId) continue
            val raw = eventPositions[pointerId] ?: continue
            val pressure = eventPressures[pointerId] ?: 0f
            if (!activePointerIds.contains(pointerId)) {
                if (!isInsideContent(raw.x, raw.y, bounds)) continue
                val (x, y) = mapToDevice(raw.x, raw.y, bounds)
                activePointerIds += pointerId
                activePointerPositions[pointerId] = raw
                activePointerDevicePositions[pointerId] = x to y
                justPressedPointerIds += pointerId
                coroutineScope.launch {
                    runCatching {
                        onInjectTouch(UiMotionActions.DOWN, pointerId.toLong(), x, y, pressure, 0)
                    }.onFailure { e ->
                        Log.w(
                            FULLSCREEN_TOUCH_LOG_TAG,
                            "handlePointerDown failed for pointerId=$pointerId",
                            e
                        )
                    }
                }
            }
        }
    }

    private fun handlePointerMove(
        event: MotionEvent,
        endedPointerId: Int?,
        bounds: ContentBounds,
    ) {
        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            if (!activePointerIds.contains(pointerId)) continue
            if (pointerId == endedPointerId) continue
            if (pointerId in justPressedPointerIds) continue
            val raw = eventPositions[pointerId] ?: continue
            val pressure = eventPressures[pointerId] ?: 0f
            activePointerPositions[pointerId] = raw
            val (x, y) = mapToDevice(raw.x, raw.y, bounds)
            activePointerDevicePositions[pointerId] = x to y
            coroutineScope.launch {
                runCatching {
                    onInjectTouch(UiMotionActions.MOVE, pointerId.toLong(), x, y, pressure, 0)
                }.onFailure { e ->
                    Log.w(
                        FULLSCREEN_TOUCH_LOG_TAG,
                        "handlePointerMove failed for pointerId=$pointerId",
                        e
                    )
                }
            }
        }
    }

    private fun handlePointerUp(
        endedPointerId: Int?,
        bounds: ContentBounds,
    ) {
        if (endedPointerId != null) {
            val endPos = eventPositions[endedPointerId]
            if (endPos != null) {
                activePointerPositions[endedPointerId] = endPos
            }
            releasePointer(endedPointerId, bounds)
        }
    }

}

/**
 * FullscreenControlScreen
 *
 * Purpose:
 * - Presents a fullscreen interactive touch surface that maps Compose touch events
 *   to device coordinates and injects them via [onInjectTouch].
 * - Responsible for pointer tracking, multi-touch mapping, coordinate normalization,
 *   and lifetime of synthetic touch events sent to the device.
 *
 * Concurrency and side-effects:
 * - All heavy computations are local to the UI thread; injection itself is a quick
 *   callback (`onInjectTouch`) which delegates to native code elsewhere — keep that
 *   callback lightweight.
 * - Use `pointerInteropFilter` to receive raw MotionEvent instances for precise
 *   multi-touch handling and to map Android pointer IDs to device pointers.
 */
@Composable
fun FullscreenControlScreen(
    session: Scrcpy.Session.SessionInfo,
    nativeCore: NativeCoreFacade,
    onDismiss: () -> Unit,
    showDebugInfo: Boolean,
    currentFps: Float,
    enableBackHandler: Boolean = true,
    onInjectTouch: suspend (action: Int, pointerId: Long, x: Int, y: Int, pressure: Float, buttons: Int) -> Unit,
) {
    BackHandler(enabled = enableBackHandler, onBack = onDismiss)
    val coroutineScope = rememberCoroutineScope()
    var touchAreaSize by remember { mutableStateOf(IntSize.Zero) }
    val activePointerIds = remember { linkedSetOf<Int>() }
    val activePointerPositions = remember { linkedMapOf<Int, Offset>() }
    val activePointerDevicePositions = remember { linkedMapOf<Int, Pair<Int, Int>>() }
    val pointerLabels = remember { linkedMapOf<Int, Int>() }
    var nextPointerLabel by remember { mutableIntStateOf(1) }
    var activeTouchCount by remember { mutableIntStateOf(0) }
    var activeTouchDebug by remember { mutableStateOf("") }

    val touchEventHandler = remember(session, touchAreaSize) {
        TouchEventHandler(
            coroutineScope = coroutineScope,
            session = session,
            touchAreaSize = touchAreaSize,
            activePointerIds = activePointerIds,
            activePointerPositions = activePointerPositions,
            activePointerDevicePositions = activePointerDevicePositions,
            pointerLabels = pointerLabels,
            nextPointerLabel = nextPointerLabel,
            onInjectTouch = onInjectTouch,
            onActiveTouchCountChanged = { activeTouchCount = it },
            onActiveTouchDebugChanged = { activeTouchDebug = it },
            onNextPointerLabelChanged = { nextPointerLabel = it },
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInteropFilter { event ->
                touchEventHandler.handleMotionEvent(event)
            }
            .onSizeChanged { touchAreaSize = it },
    ) {
        val sessionAspect = if (session.height == 0) {
            16f / 9f
        } else {
            session.width.toFloat() / session.height.toFloat()
        }
        val containerAspect = maxWidth.value / maxHeight.value
        val fittedModifier = if (sessionAspect > containerAspect) {
            Modifier
                .fillMaxWidth()
                .aspectRatio(sessionAspect)
        } else {
            Modifier
                .fillMaxHeight()
                .aspectRatio(sessionAspect)
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .then(fittedModifier),
        ) {
            ScrcpyVideoSurface(
                modifier = Modifier.fillMaxSize(),
                nativeCore = nativeCore,
                session = session,
            )
        }

        if (showDebugInfo) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = UiSpacing.CardContent, top = UiSpacing.CardContent)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = UiSpacing.CardContent, vertical = UiSpacing.Medium),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.Tiny)) {
                    Text(
                        text = "分辨率: ${session.width}x${session.height}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    @SuppressLint("DefaultLocale")
                    Text(
                        text = "FPS: ${String.format("%.1f", currentFps.coerceAtLeast(0f))}",
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = "触点: $activeTouchCount",
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                    if (activeTouchDebug.isNotEmpty()) Text(
                        text = activeTouchDebug,
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

/**
 * ScrcpyVideoSurface
 *
 * Purpose:
 * - Hosts a `TextureView` and bridges its `Surface` to `nativeCore` for video rendering.
 * - Ensures only a single `Surface` instance is registered at any time under the
 *   stable `surfaceTag` ("video-main"). This reduces surface recreation bugs seen
 *   when preview/fullscreen used separate tags.
 *
 * Concurrency / lifecycle:
 * - `currentSurface` is only mutated on the UI thread via TextureView callbacks.
 * - Registration to `nativeCore` is triggered from a [LaunchedEffect] when both
 *   `session` and `currentSurface` are available. Unregistration happens in
 *   `onSurfaceTextureDestroyed` and `DisposableEffect.onDispose` to guarantee
 *   cleanup even if the composable leaves composition.
 *
 * Reliability notes:
 * - Always release old surfaces before assigning new ones to avoid native renderer
 *   referencing stale surfaces.
 */
@Composable
private fun ScrcpyVideoSurface(
    modifier: Modifier,
    nativeCore: NativeCoreFacade,
    session: Scrcpy.Session.SessionInfo?,
) {
    val surfaceTag = "video-main"
    var currentSurface by remember { mutableStateOf<Surface?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(session, currentSurface) {
        val surface = currentSurface
        if (session != null && surface != null && surface.isValid) {
            nativeCore.registerVideoSurface(surfaceTag, surface)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        @SuppressLint("Recycle")
                        val newSurface = Surface(surfaceTexture)
                        currentSurface = newSurface
                        // Register immediately when surface becomes available
                        if (session != null) {
                            scope.launch {
                                nativeCore.registerVideoSurface(surfaceTag, newSurface)
                            }
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) = Unit

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        // Return false to keep the SurfaceTexture alive
                        // This prevents the surface from being destroyed when the view is detached
                        return false
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                }
            }
        },
        update = {},
    )
}

@Composable
internal fun DeviceTile(
    device: DeviceShortcut,
    actionText: String,
    actionEnabled: Boolean,
    actionInProgress: Boolean,
    onLongPress: () -> Unit,
    onContentClick: () -> Unit,
    onAction: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    Card(
        colors = CardDefaults.defaultColors(
            color = if (device.online) MiuixTheme.colorScheme.surfaceContainer
            else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
        ),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = haptics.contextClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onContentClick,
                    onLongClick = onLongPress,
                )
                .padding(UiSpacing.PageItem),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = UiSpacing.CardContent),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (device.online) Color(0xFF44C74F) else MiuixTheme.colorScheme.outline,
                            shape = CircleShape,
                        ),
                )
                Spacer(modifier = Modifier.width(UiSpacing.PageItem))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        device.name.ifBlank { device.host },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${device.host}:${device.port}",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (actionInProgress) {
                    CircularProgressIndicator(progress = null)
                    Spacer(Modifier.width(UiSpacing.Medium))
                }
                TextButton(
                    text = actionText,
                    onClick = onAction,
                    enabled = actionEnabled && !actionInProgress,
                )
            }
        }
    }
}

@Composable
internal fun QuickConnectCard(
    input: String,
    onValueChange: (String) -> Unit,
    onFocusChange: (() -> Unit)? = null,
    onConnect: () -> Unit,
    onAddDevice: () -> Unit,
    enabled: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    Card(
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primaryContainer),
        pressFeedbackType = if (enabled) PressFeedbackType.Tilt else PressFeedbackType.None,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = UiSpacing.Large,
                    end = UiSpacing.Large,
                    top = UiSpacing.PageItem,
                    bottom = UiSpacing.Small,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.AddLink,
                contentDescription = "快速连接",
                tint = MiuixTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.width(UiSpacing.Medium))
            Text(
                "快速连接",
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onPrimaryContainer,
            )
        }
        SuperTextField(
            value = input,
            onValueChange = onValueChange,
            label = "IP:PORT",
            enabled = enabled,
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiSpacing.CardContent)
                .padding(bottom = UiSpacing.SectionTitleLeadingGap),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onFocusLost = onFocusChange,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiSpacing.CardContent)
                .padding(bottom = UiSpacing.CardContent),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
        ) {
            TextButton(
                text = "添加设备",
                onClick = onAddDevice,
                modifier = Modifier.weight(1f),
                enabled = enabled,
            )
            TextButton(
                text = "连接",
                onClick = onConnect,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
internal fun DeviceEditorScreen(
    contentPadding: PaddingValues,
    device: DeviceShortcut,
    onSave: (DeviceShortcut) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable(device.id) { mutableStateOf(device.name) }
    var host by rememberSaveable(device.id) { mutableStateOf(device.host) }
    var port by rememberSaveable(device.id) { mutableStateOf(device.port.toString()) }

    BackHandler(enabled = true, onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(UiSpacing.PageHorizontal),
    ) {
        SectionSmallTitle("编辑设备")
        Card {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = "设备名称",
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiSpacing.CardContent)
                    .padding(top = UiSpacing.CardContent),
            )
            TextField(
                value = host,
                onValueChange = { host = it },
                label = "IP 地址",
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiSpacing.CardContent)
                    .padding(top = UiSpacing.CardContent),
            )
            TextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                label = "端口",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiSpacing.CardContent)
                    .padding(top = UiSpacing.CardContent),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UiSpacing.CardContent),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
            ) {
                TextButton(
                    text = "返回",
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "删除",
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "保存",
                    onClick = {
                        val p = port.toIntOrNull() ?: Defaults.ADB_PORT
                        val h = host.trim()
                        if (h.isNotBlank()) {
                            onSave(
                                DeviceShortcut(
                                    name = name.trim(),
                                    host = h,
                                    port = p,
                                    online = device.online,
                                ),
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
