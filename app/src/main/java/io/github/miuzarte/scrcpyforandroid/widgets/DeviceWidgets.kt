package io.github.miuzarte.scrcpyforandroid.widgets

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.Defaults
import io.github.miuzarte.scrcpyforandroid.constants.ScrcpyPresets
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcut
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperSlider
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperTextField
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.Codec
import io.github.miuzarte.scrcpyforandroid.scrcpy.TouchEventHandler
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyOptions
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import io.github.miuzarte.scrcpyforandroid.storage.Storage.scrcpyOptions
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlin.math.roundToInt

@Composable
internal fun StatusCard(
    // TODO: unused
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
                isDynamicColor -> colorScheme.secondaryContainer
                isDarkTheme -> Color(0xFF1A3825)
                else -> Color(0xFFDFFAE4)
            }
            val streamTextColor = when {
                isDynamicColor -> colorScheme.onSecondaryContainer
                isDarkTheme -> Color.White
                else -> Color(0xFF111111)
            }
            val streamIconColor = if (isDynamicColor) {
                colorScheme.primary.copy(alpha = 0.8f)
            } else {
                Color(0xFF36D167)
            }
            StatusCardSpec(
                big = StatusBigCardSpec(
                    title = stringResource(R.string.device_status_mirroring),
                    subtitle = sessionInfo.deviceName,
                    containerColor = streamCardColor,
                    titleColor = streamTextColor,
                    subtitleColor = streamTextColor,
                    icon = Icons.Rounded.CheckCircleOutline,
                    iconTint = streamIconColor,
                ),
                firstSmall = StatusSmallCardSpec(
                    stringResource(R.string.device_status_resolution),
                    "${sessionInfo.width}×${sessionInfo.height}",
                ),
                secondSmall = StatusSmallCardSpec(
                    stringResource(R.string.device_status_codec),
                    sessionInfo.codec?.displayName ?: "null",
                ),
            )
        }

        adbConnected -> StatusCardSpec(
            big = StatusBigCardSpec(
                title = stringResource(R.string.device_status_adb_connected),
                subtitle = connectedDeviceLabel,
                containerColor = colorScheme.primaryContainer,
                titleColor = colorScheme.onPrimaryContainer,
                subtitleColor = colorScheme.onPrimaryContainer,
                icon = Icons.Rounded.Wifi,
                iconTint = colorScheme.primary.copy(alpha = 0.6f),
            ),
            firstSmall = StatusSmallCardSpec(
                stringResource(R.string.device_status_current),
                connectedDeviceLabel,
            ),
            secondSmall = StatusSmallCardSpec(
                stringResource(R.string.label_status),
                stringResource(R.string.device_status_idle),
            ),
        )

        else -> StatusCardSpec(
            big = StatusBigCardSpec(
                title = stringResource(R.string.device_status_adb_disconnected),
                subtitle = "",
                containerColor = colorScheme.secondaryContainer,
                titleColor = colorScheme.onSecondaryContainer,
                subtitleColor = colorScheme.onSecondaryContainer,
                icon = Icons.Rounded.LinkOff,
                iconTint = colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
            ),
            firstSmall = StatusSmallCardSpec(
                stringResource(R.string.device_status_current),
                "N/A",
            ),
            secondSmall = StatusSmallCardSpec(
                stringResource(R.string.label_status),
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
            title = stringResource(R.string.device_pairing_title),
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
    directControlEnabled: Boolean = false,
    onInjectTouch: (suspend (
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        pressure: Float,
        actionButton: Int,
        buttons: Int,
    ) -> Unit)? = null,
    onBackOrScreenOn: (suspend (action: Int) -> Unit)? = null,
    imeRequestToken: Int = 0,
    onImeCommitText: (suspend (String) -> Unit)? = null,
    onImeDeleteSurroundingText: (suspend (beforeLength: Int, afterLength: Int) -> Unit)? = null,
    onImeKeyEvent: (suspend (KeyEvent) -> Boolean)? = null,
    autoBringIntoView: Boolean = false,
    onAutoBringIntoViewConsumed: () -> Unit = {},
    onTouchActiveChanged: (Boolean) -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var previewControlsVisible by rememberSaveable { mutableStateOf(false) }
    var touchAreaSize by remember { mutableStateOf(IntSize.Zero) }
    val activePointerIds = remember { linkedSetOf<Int>() }
    val activePointerPositions = remember { linkedMapOf<Int, Offset>() }
    val activePointerDevicePositions = remember { linkedMapOf<Int, Pair<Int, Int>>() }
    val pointerLabels = remember { linkedMapOf<Int, Int>() }
    var nextPointerLabel by rememberSaveable { mutableIntStateOf(1) }
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

    val touchEventHandler = remember(
        directControlEnabled,
        sessionInfo,
        touchAreaSize,
        onInjectTouch,
        onBackOrScreenOn,
    ) {
        if (!directControlEnabled || sessionInfo == null || onInjectTouch == null)
            null
        else TouchEventHandler(
            coroutineScope = coroutineScope,
            session = sessionInfo,
            touchAreaSize = touchAreaSize,
            activePointerIds = activePointerIds,
            activePointerPositions = activePointerPositions,
            activePointerDevicePositions = activePointerDevicePositions,
            pointerLabels = pointerLabels,
            nextPointerLabel = nextPointerLabel,
            mouseHoverEnabled = sessionInfo.mouseHover,
            onInjectTouch = onInjectTouch,
            onBackOrScreenOn = onBackOrScreenOn ?: {},
            onActiveTouchCountChanged = {},
            onActiveTouchDebugChanged = {},
            onNextPointerLabelChanged = { nextPointerLabel = it },
        )

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
            onTouchActiveChanged(false)
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
                .then(
                    if (directControlEnabled && touchEventHandler != null) {
                        Modifier.pointerInteropFilter { event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> onTouchActiveChanged(true)
                                MotionEvent.ACTION_UP,
                                MotionEvent.ACTION_CANCEL -> onTouchActiveChanged(false)
                            }
                            touchEventHandler.handleMotionEvent(event)
                        }
                    } else {
                        Modifier.pointerInput(sessionInfo) {
                            detectTapGestures(onTap = {
                                previewControlsVisible = !previewControlsVisible
                            })
                        }
                    }
                )
                .onSizeChanged { touchAreaSize = it },
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
                        imeRequestToken = imeRequestToken,
                        onImeCommitText = onImeCommitText,
                        onImeDeleteSurroundingText = onImeDeleteSurroundingText,
                        onImeKeyEvent = onImeKeyEvent,
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
                                haptic.contextClick()
                                onOpenFullscreen()
                            }
                        },
                        modifier = Modifier.alpha(alpha),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fullscreen,
                            contentDescription = stringResource(R.string.cd_fullscreen),
                        )
                        Spacer(Modifier.width(UiSpacing.SectionTitleBottom))
                        Text(stringResource(R.string.button_fullscreen))
                    }
                }
            }
        }
    }
}

@Composable
internal fun VirtualButtonCard(
    modifier: Modifier = Modifier,
    busy: Boolean,
    outsideActions: List<VirtualButtonAction>,
    moreActions: List<VirtualButtonAction>,
    showText: Boolean,
    onAction: (VirtualButtonAction) -> Unit,
    passwordPopupContent: (@Composable (onDismissRequest: () -> Unit) -> Unit)? = null,
    popupBottomPadding: Dp = 0.dp,
) {
    val bar = remember(outsideActions, moreActions) {
        VirtualButtonBar(
            outsideActions = outsideActions,
            moreActions = moreActions,
        )
    }

    Card(modifier = modifier) {
        bar.Preview(
            enabled = true,
            showText = showText,
            onAction = { if (!busy) onAction(it) },
            passwordPopupContent = passwordPopupContent,
            popupBottomPadding = popupBottomPadding,
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiSpacing.ContentVertical),
        )
    }
}

@Composable
internal fun ConfigPanel(
    busy: Boolean,
    activeProfileId: String,
    activeBundle: ScrcpyOptions.Bundle,
    hideSimpleConfigItems: Boolean,
    audioForwardingSupported: Boolean,
    cameraMirroringSupported: Boolean,
    adbConnecting: Boolean,
    isQuickConnected: Boolean,
    advancedEndActionText: String,
    allAppsEndActionText: String,
    onOpenAllApps: () -> Unit,
    recentTasksEndActionText: String,
    onOpenRecentTasks: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onStartStopHaptic: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    sessionInfo: Scrcpy.Session.SessionInfo?,
    onDisconnect: () -> Unit = {},
    showFullscreenAction: Boolean = false,
    onOpenFullscreen: () -> Unit = {},
    reverseSideActions: Boolean = false,
) {
    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    val sessionStarted = sessionInfo != null

    val activeProfileIdLatest by rememberUpdatedState(activeProfileId)
    val activeBundleLatest by rememberUpdatedState(activeBundle)
    var soBundle by rememberSaveable(activeProfileId, activeBundle) { mutableStateOf(activeBundle) }
    val soBundleLatest by rememberUpdatedState(soBundle)
    LaunchedEffect(activeProfileId, activeBundle) {
        if (soBundle != activeBundle)
            soBundle = activeBundle
    }
    LaunchedEffect(soBundle) {
        delay(Settings.BUNDLE_SAVE_DELAY)
        if (soBundle != activeBundleLatest) {
            if (activeProfileIdLatest == ScrcpyOptions.GLOBAL_PROFILE_ID)
                scrcpyOptions.saveBundle(soBundle)
            else
                Storage.scrcpyProfiles.updateBundle(activeProfileIdLatest, soBundle)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            taskScope.launch {
                if (activeProfileIdLatest == ScrcpyOptions.GLOBAL_PROFILE_ID)
                    scrcpyOptions.saveBundle(soBundleLatest)
                else
                    Storage.scrcpyProfiles.updateBundle(activeProfileIdLatest, soBundleLatest)
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
        if (!hideSimpleConfigItems) {
            SwitchPreference(
                title = stringResource(R.string.device_config_audio_forwarding),
                summary = stringResource(R.string.device_config_audio_forwarding_desc),
                checked = soBundle.audio,
                onCheckedChange = {
                    soBundle = soBundle.copy(
                        audio = it
                    )
                },
                enabled = !sessionStarted
                        && audioForwardingSupported,
            )

            OverlayDropdownPreference(
                title = stringResource(R.string.device_config_audio_codec),
                summary = "--audio-codec",
                items = audioCodecItems,
                selectedIndex = audioCodecIndex,
                onSelectedIndexChange = {
                    val codec = Codec.AUDIO[it]
                    soBundle = soBundle.copy(
                        audioCodec = codec.string
                    )
                    if (codec == Codec.FLAC)
                        AppRuntime.snackbar(R.string.device_config_audio_codec_note)
                },
                enabled = !sessionStarted && soBundle.audio,
            )
            AnimatedVisibility(audioBitRateVisibility) {
                SuperSlider(
                    title = stringResource(R.string.device_config_audio_bitrate),
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
                    zeroStateText = stringResource(R.string.text_default),
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
                title = stringResource(R.string.device_config_video_codec),
                summary = "--video-codec",
                items = videoCodecItems,
                selectedIndex = videoCodecIndex,
                onSelectedIndexChange = {
                    val codec = Codec.VIDEO[it]
                    soBundle = soBundle.copy(
                        videoCodec = codec.string
                    )
                    if (codec == Codec.AV1)
                        AppRuntime.snackbar(R.string.device_config_video_codec_note)
                },
                enabled = !sessionStarted,
            )
            SuperSlider(
                title = stringResource(R.string.device_config_video_bitrate),
                summary = "--video-bit-rate",
                value = soBundle.videoBitRate / 1_000_000f,
                onValueChange = { mbps ->
                    soBundle = soBundle.copy(
                        videoBitRate = (mbps * 10).roundToInt() * (1_000_000 / 10)
                    )
                },
                valueRange = 0f..40f,
                steps = 400 - 0 - 1,
                unit = "Mbps",
                zeroStateText = stringResource(R.string.text_default),
                displayFormatter = { "%.1f".format(it) },
                inputInitialValue =
                    if (soBundle.videoBitRate <= 0) ""
                    else "%.1f".format(soBundle.videoBitRate / 1_000_000f),
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
        }

        ArrowPreference(
            title = stringResource(
                if (!hideSimpleConfigItems) R.string.device_config_more_params
                else R.string.device_config_all_params
            ),
            summary = stringResource(R.string.device_config_all_scrcpy_params),
            endActions = {
                Text(
                    text = advancedEndActionText,
                    color = colorScheme.onSurfaceVariantActions,
                    fontSize = textStyles.body2.fontSize,
                )
            },
            onClick = onOpenAdvanced,
            enabled = !sessionStarted,
        )

        ArrowPreference(
            title = stringResource(R.string.bottomsheet_all_apps),
            endActions = {
                Text(
                    text = allAppsEndActionText,
                    color = colorScheme.onSurfaceVariantActions,
                    fontSize = textStyles.body2.fontSize,
                )
            },
            onClick = onOpenAllApps,
            enabled = !busy && !adbConnecting,
        )
        ArrowPreference(
            title = stringResource(R.string.bottomsheet_recent_tasks),
            endActions = {
                Text(
                    text = recentTasksEndActionText,
                    color = colorScheme.onSurfaceVariantActions,
                    fontSize = textStyles.body2.fontSize,
                )
            },
            onClick = onOpenRecentTasks,
            enabled = !busy && !adbConnecting,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = UiSpacing.ContentVertical),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
        ) {
            val sideButtonWeight = 1f / 4f
            val mainButtonWeight = 1f -
                    if (isQuickConnected || showFullscreenAction)
                        sideButtonWeight * listOf(isQuickConnected, showFullscreenAction)
                            .count { it }
                    else 0f

            @Composable
            fun DisconnectButton() {
                if (isQuickConnected) TextButton(
                    text = stringResource(R.string.button_disconnect),
                    onClick = {
                        onStartStopHaptic()
                        onDisconnect()
                    },
                    modifier = Modifier.weight(sideButtonWeight),
                    enabled = !busy,
                )
            }

            @Composable
            fun MainButton() {
                if (!sessionStarted) TextButton(
                    text = stringResource(R.string.button_start),
                    onClick = {
                        onStartStopHaptic()
                        onStart()
                    },
                    modifier = Modifier.weight(mainButtonWeight),
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
                if (sessionStarted) TextButton(
                    text = stringResource(R.string.button_stop),
                    onClick = {
                        onStartStopHaptic()
                        onStop()
                    },
                    modifier = Modifier.weight(mainButtonWeight),
                    enabled = !busy,
                )
            }

            @Composable
            fun FullscreenButton() {
                if (showFullscreenAction) TextButton(
                    text = stringResource(R.string.button_fullscreen),
                    onClick = {
                        onStartStopHaptic()
                        onOpenFullscreen()
                    },
                    modifier = Modifier.weight(sideButtonWeight),
                    enabled = !busy,
                )
            }

            if (reverseSideActions) {
                FullscreenButton()
                MainButton()
                DisconnectButton()
            } else {
                DisconnectButton()
                MainButton()
                FullscreenButton()
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
        title = stringResource(R.string.device_pairing_title),
        summary = stringResource(R.string.device_pairing_desc),
        defaultWindowInsetsPadding = false,
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
                label = stringResource(R.string.label_ip_address),
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
                label = stringResource(R.string.label_port),
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
                label = stringResource(R.string.label_wlan_pairing_code),
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
                text = stringResource(
                    if (!discoveringPort) R.string.button_auto_discover
                    else R.string.button_discovering
                ),
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
                    text = stringResource(R.string.button_cancel),
                    onClick = {
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.button_pair),
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
    imeRequestToken: Int = 0,
    onImeCommitText: (suspend (String) -> Unit)? = null,
    onImeDeleteSurroundingText: (suspend (beforeLength: Int, afterLength: Int) -> Unit)? = null,
    onImeKeyEvent: (suspend (KeyEvent) -> Boolean)? = null,
) {

    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val scope = rememberCoroutineScope()
    val imeInputMutex = remember { Mutex() }

    val lifecycleOwner = LocalLifecycleOwner.current
    var currentSurface by remember { mutableStateOf<Surface?>(null) }
    var currentSurfaceView by remember { mutableStateOf<ScrcpyInputSurfaceView?>(null) }

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

    LaunchedEffect(imeRequestToken, currentSurfaceView) {
        if (imeRequestToken == 0) return@LaunchedEffect
        val surfaceView = currentSurfaceView ?: return@LaunchedEffect
        surfaceView.setCommitTextEnabled(true)
        io.github.miuzarte.scrcpyforandroid.services.LocalInputService.showSoftKeyboard(surfaceView)
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
            ScrcpyInputSurfaceView(context).apply {
                currentSurfaceView = this
                inputCallbacks = object : ScrcpyInputSurfaceView.InputCallbacks {
                    override fun handleKeyEvent(event: KeyEvent): Boolean {
                        val handler = onImeKeyEvent ?: return false
                        taskScope.launch {
                            imeInputMutex.withLock {
                                handler(event)
                            }
                        }
                        return true
                    }

                    override fun handleCommitText(text: CharSequence): Boolean {
                        val handler = onImeCommitText ?: return false
                        taskScope.launch {
                            imeInputMutex.withLock {
                                handler(text.toString())
                            }
                        }
                        return true
                    }

                    override fun handleDeleteSurroundingText(
                        beforeLength: Int,
                        afterLength: Int
                    ): Boolean {
                        val handler = onImeDeleteSurroundingText ?: return false
                        taskScope.launch {
                            imeInputMutex.withLock {
                                handler(beforeLength, afterLength)
                            }
                        }
                        return true
                    }
                }
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
    val haptic = LocalHapticFeedback.current
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
                if (isConnected) colorScheme.surfaceContainer
                else colorScheme.surfaceContainer.copy(alpha = 0.6f),
        ),
        pressFeedbackType = if (!editing) PressFeedbackType.Sink else PressFeedbackType.None,
        onClick = haptic::contextClick,
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
                                else colorScheme.outline,
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
                        color = colorScheme.onSurface,
                    )
                    Text(
                        "${device.host}:${device.port}",
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantSummary,
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
                    text = stringResource(
                        if (!isConnected) R.string.button_connect
                        else R.string.button_disconnect
                    ),
                    onClick = onAction,
                    enabled = actionEnabled && !actionInProgress,
                    colors =
                        if (!isConnected && device.startScrcpyOnConnect)
                            ButtonDefaults.textButtonColorsPrimary()
                        else
                            ButtonDefaults.textButtonColors(),
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
                        label = stringResource(R.string.label_device_name),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SuperTextField(
                        value = currentDraft.host,
                        onValueChange = { draft = currentDraft.copy(host = it) },
                        label = stringResource(R.string.label_ip_address),
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
                        label = stringResource(R.string.label_port),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CheckboxPreference(
                        title = stringResource(R.string.device_config_start_immediately),
                        checkboxLocation = CheckboxLocation.End,
                        checked = currentDraft.startScrcpyOnConnect,
                        onCheckedChange = {
                            draft = currentDraft.copy(startScrcpyOnConnect = it)
                        },
                    )
                    AnimatedVisibility(currentDraft.startScrcpyOnConnect) {
                        CheckboxPreference(
                            title = stringResource(R.string.device_config_direct_fullscreen),
                            checkboxLocation = CheckboxLocation.End,
                            checked = currentDraft.startScrcpyOnConnect
                                    && currentDraft.openFullscreenOnStart,
                            enabled = currentDraft.startScrcpyOnConnect,
                            onCheckedChange = {
                                draft = currentDraft.copy(openFullscreenOnStart = it)
                            },
                        )
                    }
                    val textGlobal = stringResource(R.string.text_global)
                    OverlayDropdownPreference(
                        title = stringResource(R.string.device_config_scrcpy_config),
                        items = profileNames,
                        selectedIndex = profileDropdownIndex,
                        onSelectedIndexChange = {
                            val profileId = profileIds.getOrElse(it) {
                                ScrcpyOptions.GLOBAL_PROFILE_ID
                            }
                            val profileName = profileNames.getOrElse(it) { textGlobal }
                            val deviceName = currentDraft.name.ifBlank { currentDraft.host }
                            draft = currentDraft.copy(scrcpyProfileId = profileId)
                            AppRuntime.snackbar(
                                R.string.device_switched_profile,
                                deviceName,
                                profileName,
                            )
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
                        text = stringResource(R.string.button_cancel),
                        onClick = {
                            onEditorSave(currentOriginalDraft)
                            onEditorCancel()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.button_delete),
                        onClick = onEditorDelete,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.button_done),
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
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    Card(
        colors = CardDefaults.defaultColors(color = colorScheme.primaryContainer),
        pressFeedbackType =
            if (enabled) PressFeedbackType.Tilt
            else PressFeedbackType.None,
        insideMargin = PaddingValues(UiSpacing.Content),
        onClick = haptic::contextClick,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical)) {
            Row(
                modifier = Modifier.padding(horizontal = UiSpacing.Small),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddLink,
                    contentDescription = stringResource(R.string.device_quick_connect_title),
                    tint = colorScheme.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.device_quick_connect_title),
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onPrimaryContainer,
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
                    text = stringResource(R.string.button_add_device),
                    onClick = onAddDevice,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                )
                TextButton(
                    text = stringResource(R.string.button_direct_connect),
                    onClick = onConnect,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
