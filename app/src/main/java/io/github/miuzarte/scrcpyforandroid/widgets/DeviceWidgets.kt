package io.github.miuzarte.scrcpyforandroid.widgets

import android.annotation.SuppressLint
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.constants.Defaults
import io.github.miuzarte.scrcpyforandroid.constants.ScrcpyPresets
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.haptics.rememberAppHaptics
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcut
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperSlider
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperTextField
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.Codec
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyOptions
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import io.github.miuzarte.scrcpyforandroid.storage.Storage.scrcpyOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlin.math.roundToInt

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
    val appSettingsBundle by appSettings.bundleState.collectAsState()
    val themeBaseIndex = appSettingsBundle.themeBaseIndex

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
                    sessionInfo.codec?.displayName ?: "null",
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
        ArrowPreference(
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
    ) { host, port, code ->
        showPairDialog.value = false
        onPair(host, port, code)
    }
}

@Composable
internal fun PreviewCard(
    modifier: Modifier,
    sessionInfo: Scrcpy.Session.SessionInfo?,
    previewHeightDp: Int,
    onOpenFullscreen: () -> Unit,
    autoBringIntoView: Boolean = false,
    onAutoBringIntoViewConsumed: () -> Unit = {},
) {
    val haptics = rememberAppHaptics()

    var previewControlsVisible by rememberSaveable { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        if (previewControlsVisible) 1f else 0f,
        label = "preview-controls"
    )
    val lifecycleOwner = LocalLifecycleOwner.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(autoBringIntoView) {
        if (!autoBringIntoView) return@LaunchedEffect
        bringIntoViewRequester.bringIntoView()
        onAutoBringIntoViewConsumed()
    }

    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            VideoOutputTargetState.set(VideoOutputTarget.PREVIEW)
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> VideoOutputTargetState.set(VideoOutputTarget.PREVIEW)
                Lifecycle.Event.ON_STOP ->
                    if (VideoOutputTargetState.current.value == VideoOutputTarget.PREVIEW)
                        VideoOutputTargetState.set(VideoOutputTarget.NONE)

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (VideoOutputTargetState.current.value == VideoOutputTarget.PREVIEW) {
                VideoOutputTargetState.set(VideoOutputTarget.NONE)
            }
        }
    }

    Card(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .then(modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeightDp.coerceAtLeast(120).dp)
                .pointerInput(sessionInfo) {
                    detectTapGestures(onTap = {
                        previewControlsVisible = !previewControlsVisible
                    })
                },
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val sessionAspect =
                    if (sessionInfo == null || sessionInfo.height == 0)
                        16f / 9f
                    else sessionInfo.width.toFloat() / sessionInfo.height.toFloat()

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
                        session = sessionInfo,
                    )
                }
            }

            if (sessionInfo != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(UiSpacing.ContentVertical),
                ) {
                    Button(
                        onClick = {
                            if (alpha > 0.1f) {
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
    passwordPopupContent: (@Composable (onDismissRequest: () -> Unit) -> Unit)? = null,
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
            passwordPopupContent = passwordPopupContent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiSpacing.ContentVertical),
        )
    }
}

@Composable
internal fun ConfigPanel(
    busy: Boolean,
    audioForwardingSupported: Boolean,
    cameraMirroringSupported: Boolean,
    adbConnecting: Boolean,
    isQuickConnected: Boolean,
    recentTasksEndAction: @Composable (RowScope.() -> Unit)? = null,
    onOpenRecentTasks: (() -> Unit)? = null,
    onOpenAdvanced: () -> Unit,
    onStartStopHaptic: (() -> Unit)? = null,
    onStart: () -> Unit,
    onStop: () -> Unit,
    sessionInfo: Scrcpy.Session.SessionInfo?,
    onDisconnect: () -> Unit = {},
) {
    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    val snackbar = LocalSnackbarController.current

    val sessionStarted = sessionInfo != null

    val soBundleShared by scrcpyOptions.bundleState.collectAsState()
    val soBundleSharedLatest by rememberUpdatedState(soBundleShared)
    var soBundle by rememberSaveable(soBundleShared) { mutableStateOf(soBundleShared) }
    val soBundleLatest by rememberUpdatedState(soBundle)
    LaunchedEffect(soBundleShared) {
        if (soBundle != soBundleShared) {
            soBundle = soBundleShared
        }
    }
    LaunchedEffect(soBundle) {
        delay(Settings.BUNDLE_SAVE_DELAY)
        if (soBundle != soBundleSharedLatest) {
            scrcpyOptions.saveBundle(soBundle)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            taskScope.launch {
                scrcpyOptions.saveBundle(soBundleLatest)
            }
        }
    }

    val audioBitRateVisibility = rememberSaveable(soBundle) {
        soBundle.audio && (soBundle.audioCodec == "opus" || soBundle.audioCodec == "aac")
    }

    val audioCodecItems = rememberSaveable { Codec.AUDIO.map { it.displayName } }
    val audioCodecIndex = rememberSaveable(soBundle) {
        Codec.AUDIO
            .indexOfFirst { it.string == soBundle.audioCodec }
            .coerceAtLeast(0)
    }

    val videoCodecItems = rememberSaveable { Codec.VIDEO.map { it.displayName } }
    val videoCodecIndex = rememberSaveable(soBundle) {
        Codec.VIDEO
            .indexOfFirst { it.string == soBundle.videoCodec }
            .coerceAtLeast(0)
    }

    Card {
        SwitchPreference(
            title = "音频转发",
            summary = "转发设备音频到本机 (Android 11+)",
            checked = soBundle.audio,
            onCheckedChange = { soBundle = soBundle.copy(audio = it) },
            enabled = !sessionStarted
                    && audioForwardingSupported,
        )

        OverlayDropdownPreference(
            title = "音频编码",
            summary = "--audio-codec",
            items = audioCodecItems,
            selectedIndex = audioCodecIndex,
            onSelectedIndexChange = {
                val codec = Codec.AUDIO[it]
                soBundle = soBundle.copy(audioCodec = codec.string)
                if (codec == Codec.FLAC) {
                    snackbar.show("注意：FLAC编解码会引入较大的延迟")
                }
            },
            enabled = !sessionStarted && soBundle.audio,
        )
        AnimatedVisibility(audioBitRateVisibility) {
            SuperSlider(
                title = "音频码率",
                summary = "--audio-bit-rate",
                value = ScrcpyPresets.AudioBitRate
                    .indexOfOrNearest(soBundle.audioBitRate / 1000)
                    .toFloat(),
                onValueChange = { value ->
                    val idx = value.roundToInt()
                        .coerceIn(0, ScrcpyPresets.AudioBitRate.lastIndex)
                    soBundle = soBundle.copy(
                        audioBitRate = ScrcpyPresets.AudioBitRate[idx] * 1000
                    )
                },
                valueRange = 0f..ScrcpyPresets.AudioBitRate.lastIndex.toFloat(),
                steps = (ScrcpyPresets.AudioBitRate.size - 2).coerceAtLeast(0),
                unit = "Kbps",
                zeroStateText = "默认",
                displayText = (soBundle.audioBitRate / 1_000).toString(),
                inputInitialValue =
                    if (soBundle.audioBitRate <= 0) ""
                    else (soBundle.audioBitRate / 1_000).toString(),
                inputFilter = { it.filter(Char::isDigit) },
                inputValueRange = 0f..UShort.MAX_VALUE.toFloat(),
                onInputConfirm = { raw ->
                    raw.toIntOrNull()
                        ?.takeIf { it >= 0 }
                        ?.let { soBundle = soBundle.copy(audioBitRate = it * 1000) }
                },
                enabled = !sessionStarted,
            )
        }

        OverlayDropdownPreference(
            title = "视频编码",
            summary = "--video-codec",
            items = videoCodecItems,
            selectedIndex = videoCodecIndex,
            onSelectedIndexChange = {
                val codec = Codec.VIDEO[it]
                soBundle = soBundle.copy(videoCodec = codec.string)
                if (codec == Codec.AV1) {
                    snackbar.show("注意：绝大部分设备不支持AV1硬件编码")
                }
            },
            enabled = !sessionStarted,
        )
        @SuppressLint("DefaultLocale")
        SuperSlider(
            title = "视频码率",
            summary = "--video-bit-rate",
            value = soBundle.videoBitRate / 1_000_000f,
            onValueChange = { mbps ->
                soBundle = soBundle.copy(
                    videoBitRate = (mbps * 10).roundToInt() * (1_000_000 / 10)
                )
            },
            valueRange = 0f..40f,
            steps = 400 - 1,
            unit = "Mbps",
            zeroStateText = "默认",
            displayFormatter = { String.format("%.1f", it) },
            inputInitialValue =
                if (soBundle.videoBitRate <= 0) ""
                else String.format("%.1f", soBundle.videoBitRate / 1_000_000f),
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
            inputValueRange = 0f..UInt.MAX_VALUE.toFloat(),
            onInputConfirm = { raw ->
                raw.toFloatOrNull()?.let { parsed ->
                    if (parsed >= 0f) {
                        soBundle = soBundle.copy(
                            videoBitRate = (parsed * 1_000_000f).roundToInt()
                        )
                    }
                }
            },
            enabled = !sessionStarted,
        )

        ArrowPreference(
            title = "更多参数",
            summary = "所有 scrcpy 参数",
            onClick = onOpenAdvanced,
            enabled = !sessionStarted,
        )
        if (onOpenRecentTasks != null) {
            ArrowPreference(
                title = "最近任务",
                endActions = {
                    recentTasksEndAction?.invoke(this)
                },
                onClick = onOpenRecentTasks,
                enabled = !busy && !adbConnecting,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiSpacing.ContentVertical)
                .padding(bottom = UiSpacing.ContentVertical),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
        ) {
            if (isQuickConnected) TextButton(
                text = "断开",
                onClick = {
                    onStartStopHaptic?.invoke()
                    onDisconnect()
                },
                modifier = Modifier.weight(1f / 4f),
                enabled = !busy,
            )
            if (!sessionStarted) TextButton(
                text = "启动",
                onClick = {
                    onStartStopHaptic?.invoke()
                    onStart()
                },
                modifier = Modifier.weight(if (isQuickConnected) 3f / 4f else 1f),
                enabled = !busy,
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
            if (sessionStarted) TextButton(
                text = "停止",
                onClick = {
                    onStartStopHaptic?.invoke()
                    onStop()
                },
                modifier = Modifier.weight(if (isQuickConnected) 3f / 4f else 1f),
                enabled = !busy,
            )
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
    val focusManager = LocalFocusManager.current

    suspend fun doDiscover() {
        if (!(enabled && onDiscoverTarget != null && !discoveringPort)) return
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

    OverlayDialog(
        show = showDialog,
        title = "使用配对码配对设备",
        summary = "使用六位数的配对码配对新设备",
        onDismissRequest = {
            onDismissRequest()
        },
        onDismissFinished = {
            onDismissFinished()
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
        ) {
            TextField(
                value = host,
                onValueChange = { host = it },
                label = "IP 地址",
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Next) },
                ),
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                label = "端口",
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Next) },
                ),
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = code,
                onValueChange = { code = it },
                label = "WLAN 配对码",
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() },
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(UiSpacing.ContentVertical * 2))

        Column(
            verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
        ) {
            TextButton(
                text = if (!discoveringPort) "自动发现" else "发现中...",
                onClick = {
                    if (enabled && onDiscoverTarget != null && !discoveringPort)
                        scope.launch { doDiscover() }
                },
                enabled = enabled && onDiscoverTarget != null && !discoveringPort,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
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
        }
    }
}

/**
 * ScrcpyVideoSurface
 *
 * Purpose:
 * - Hosts a `SurfaceView` and bridges its `Surface` to `nativeCore` for video rendering.
 * - Ensures only a single `Surface` instance is registered at any time under the
 *   stable `surfaceTag` ("video-main"). This reduces surface recreation bugs seen
 *   when preview/fullscreen used separate tags.
 *
 * Concurrency / lifecycle:
 * - `currentSurface` is only mutated on the UI thread via SurfaceHolder callbacks.
 * - Registration to `nativeCore` is triggered from a [LaunchedEffect] when both
 *   `session` and `currentSurface` are available. Unregistration happens in
 *   `surfaceDestroyed` and `DisposableEffect.onDispose` to guarantee
 *   cleanup even if the composable leaves composition.
 *
 * Reliability notes:
 * - Always release old surfaces before assigning new ones to avoid native renderer
 *   referencing stale surfaces.
 */
@Composable
fun ScrcpyVideoSurface(
    modifier: Modifier,
    session: Scrcpy.Session.SessionInfo?,
) {

    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val scope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    var currentSurface by remember { mutableStateOf<Surface?>(null) }
    var currentSurfaceView by remember { mutableStateOf<SurfaceView?>(null) }

    val latestSession by rememberUpdatedState(session)

    LaunchedEffect(session, currentSurface) {
        val surface = currentSurface ?: return@LaunchedEffect
        if (session != null && surface.isValid) {
            NativeCoreFacade.attachVideoSurface(surface)
        }
    }

    LaunchedEffect(session?.width, session?.height, currentSurfaceView) {
        val surfaceView = currentSurfaceView ?: return@LaunchedEffect
        val currentSession = session ?: return@LaunchedEffect

        if (currentSession.width > 0 && currentSession.height > 0) {
            surfaceView.holder.setFixedSize(currentSession.width, currentSession.height)
        }
    }

    DisposableEffect(lifecycleOwner, session, currentSurface) {
        val surface = currentSurface
        if (
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
            session != null &&
            surface != null &&
            surface.isValid
        ) {
            scope.launch {
                NativeCoreFacade.attachVideoSurface(surface)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                val surface = currentSurface
                if (session != null && surface != null && surface.isValid) {
                    scope.launch {
                        NativeCoreFacade.attachVideoSurface(surface)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val surface = currentSurface
            if (surface != null) {
                taskScope.launch {
                    NativeCoreFacade.detachVideoSurface(surface)
                }
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                currentSurfaceView = this
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        val newSurface = holder.surface
                        if (!newSurface.isValid) return
                        currentSurface = newSurface
                        // Register immediately when surface becomes available
                        if (latestSession != null) {
                            scope.launch {
                                NativeCoreFacade.attachVideoSurface(newSurface)
                            }
                        }
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int,
                    ) {
                        if (width <= 0 || height <= 0) return
                        if (!holder.surface.isValid) return
                        val surface = holder.surface
                        currentSurface = surface
                        if (latestSession != null) {
                            scope.launch {
                                NativeCoreFacade.attachVideoSurface(surface)
                            }
                        }
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        val surface = currentSurface
                        if (surface != null) {
                            taskScope.launch {
                                NativeCoreFacade.detachVideoSurface(surface)
                            }
                            currentSurface = null
                        }
                    }
                })
            }
        },
        update = {},
    )
}

@Composable
internal fun DeviceTile(
    device: DeviceShortcut,
    isConnected: Boolean,
    actionEnabled: Boolean,
    actionInProgress: Boolean,
    editing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAction: () -> Unit,
    onEditorSave: (DeviceShortcut) -> Unit,
    onEditorDelete: () -> Unit,
    onEditorCancel: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    val snackbar = LocalSnackbarController.current
    val scrcpyProfilesState by Storage.scrcpyProfiles.state.collectAsState()

    var draft by remember(editing, device.id) {
        mutableStateOf(if (editing) device else null)
    }
    var originalDraft by remember(editing, device.id) {
        mutableStateOf(if (editing) device else null)
    }
    var draftPortText by remember(editing, device.id) {
        mutableStateOf(if (editing) device.port.toString() else null)
    }

    LaunchedEffect(editing, draft) {
        val currentDraft = draft ?: return@LaunchedEffect
        if (!editing) return@LaunchedEffect
        delay(Settings.BUNDLE_SAVE_DELAY)
        val trimmedHost = currentDraft.host.trim()
        if (trimmedHost.isBlank()) return@LaunchedEffect
        val updated = DeviceShortcut(
            id = currentDraft.id,
            name = currentDraft.name.trim(),
            host = trimmedHost,
            port = currentDraft.port,
            startScrcpyOnConnect = currentDraft.startScrcpyOnConnect,
            openFullscreenOnStart = currentDraft.startScrcpyOnConnect
                    && currentDraft.openFullscreenOnStart,
            scrcpyProfileId = currentDraft.scrcpyProfileId,
        )
        if (updated != device) {
            onEditorSave(updated)
        }
    }

    val currentDraft = draft ?: device
    val currentOriginalDraft = originalDraft ?: device
    val currentDraftPortText = draftPortText ?: device.port.toString()
    val profileNames = remember(scrcpyProfilesState.profiles) {
        scrcpyProfilesState.profiles.map { it.name }
    }
    val profileIds = remember(scrcpyProfilesState.profiles) {
        scrcpyProfilesState.profiles.map { it.id }
    }
    val profileDropdownIndex = remember(currentDraft.scrcpyProfileId, profileIds) {
        profileIds.indexOf(currentDraft.scrcpyProfileId).coerceAtLeast(0)
    }

    Card(
        colors = CardDefaults.defaultColors(
            color =
                if (isConnected) MiuixTheme.colorScheme.surfaceContainer
                else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
        ),
        pressFeedbackType = if (!editing) PressFeedbackType.Sink else PressFeedbackType.None,
        onClick = haptics.contextClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (!isConnected)
                        Modifier.combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                    else Modifier
                )
                .padding(UiSpacing.PageItem),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // status dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color =
                                if (isConnected) Color(0xFF44C74F)
                                else MiuixTheme.colorScheme.outline,
                            shape = CircleShape,
                        ),
                )
                Spacer(modifier = Modifier.width(UiSpacing.PageItem))
                // device name/address
                Column {
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (actionInProgress) {
                    CircularProgressIndicator(progress = null)
                    Spacer(Modifier.width(UiSpacing.Medium))
                }
                TextButton(
                    text = if (!isConnected) "连接" else "断开",
                    onClick = onAction,
                    enabled = actionEnabled && !actionInProgress,
                    colors = if (!isConnected && device.startScrcpyOnConnect) {
                        ButtonDefaults.textButtonColorsPrimary()
                    } else {
                        ButtonDefaults.textButtonColors()
                    },
                )
            }
        }

        AnimatedVisibility(editing) {
            Column(
                modifier = Modifier.padding(vertical = UiSpacing.Large),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = UiSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
                ) {
                    SuperTextField(
                        value = currentDraft.name,
                        onValueChange = { draft = currentDraft.copy(name = it) },
                        label = "设备名",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SuperTextField(
                        value = currentDraft.host,
                        onValueChange = { draft = currentDraft.copy(host = it) },
                        label = "IP 地址",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SuperTextField(
                        value = currentDraftPortText,
                        onValueChange = {
                            draftPortText = it.filter(Char::isDigit)
                            draft = currentDraft.copy(
                                port = draftPortText?.toIntOrNull() ?: Defaults.ADB_PORT
                            )
                        },
                        label = "端口",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CheckboxPreference(
                        title = "连接后立即启动 scrcpy",
                        checkboxLocation = CheckboxLocation.End,
                        checked = currentDraft.startScrcpyOnConnect,
                        onCheckedChange = {
                            draft = currentDraft.copy(startScrcpyOnConnect = it)
                        },
                    )
                    AnimatedVisibility(currentDraft.startScrcpyOnConnect) {
                        CheckboxPreference(
                            title = "直接进入全屏",
                            checkboxLocation = CheckboxLocation.End,
                            checked = currentDraft.startScrcpyOnConnect
                                    && currentDraft.openFullscreenOnStart,
                            enabled = currentDraft.startScrcpyOnConnect,
                            onCheckedChange = {
                                draft = currentDraft.copy(openFullscreenOnStart = it)
                            },
                        )
                    }
                    OverlayDropdownPreference(
                        title = "scrcpy 配置",
                        items = profileNames,
                        selectedIndex = profileDropdownIndex,
                        onSelectedIndexChange = {
                            val profileId = profileIds.getOrElse(it) {
                                ScrcpyOptions.GLOBAL_PROFILE_ID
                            }
                            val profileName = profileNames.getOrElse(it) { "全局" }
                            val deviceName = currentDraft.name.ifBlank { currentDraft.host }
                            draft = currentDraft.copy(scrcpyProfileId = profileId)
                            snackbar.show("$deviceName 已切换到配置 $profileName")
                        },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.Large),
                    horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
                ) {
                    TextButton(
                        text = "取消",
                        onClick = {
                            onEditorSave(currentOriginalDraft)
                            onEditorCancel()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "删除",
                        onClick = onEditorDelete,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "完成",
                        onClick = onEditorCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun DeviceTileList(
    devices: List<DeviceShortcut>,
    isConnected: (DeviceShortcut) -> Boolean,
    actionEnabled: Boolean,
    actionInProgress: (DeviceShortcut) -> Boolean,
    editingDeviceId: String?,
    onClick: (DeviceShortcut) -> Unit,
    onLongClick: (DeviceShortcut) -> Unit,
    onAction: (DeviceShortcut) -> Unit,
    onEditorSave: (DeviceShortcut, DeviceShortcut) -> Unit,
    onEditorDelete: (DeviceShortcut) -> Unit,
    onEditorCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
    ) {
        devices.forEach { device ->
            DeviceTile(
                device = device,
                isConnected = isConnected(device),
                actionEnabled = actionEnabled,
                actionInProgress = actionInProgress(device),
                editing = editingDeviceId == device.id,
                onClick = { onClick(device) },
                onLongClick = { onLongClick(device) },
                onAction = { onAction(device) },
                onEditorSave = { updated -> onEditorSave(device, updated) },
                onEditorDelete = { onEditorDelete(device) },
                onEditorCancel = onEditorCancel,
            )
        }
    }
}

@Composable
internal fun QuickConnectCard(
    input: String,
    onValueChange: (String) -> Unit,
    onFocusLost: (() -> Unit)? = null,
    onConnect: () -> Unit,
    onAddDevice: () -> Unit,
    enabled: Boolean = true,
) {
    val focusManager = LocalFocusManager.current

    Card(
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primaryContainer),
        pressFeedbackType =
            if (enabled) PressFeedbackType.Tilt
            else PressFeedbackType.None,
        insideMargin = PaddingValues(UiSpacing.Content),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical)) {
            Row(
                modifier = Modifier.padding(horizontal = UiSpacing.Small),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
            ) {
                Icon(
                    Icons.Rounded.AddLink,
                    contentDescription = "快速连接",
                    tint = MiuixTheme.colorScheme.onPrimaryContainer,
                )
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
                modifier = Modifier.fillMaxWidth(),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onFocusLost = onFocusLost,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
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
}
