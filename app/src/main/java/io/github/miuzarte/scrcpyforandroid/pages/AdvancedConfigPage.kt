package io.github.miuzarte.scrcpyforandroid.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.miuzarte.scrcpyforandroid.constants.ScrcpyPresets
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.models.ScrcpyOptions.Crop
import io.github.miuzarte.scrcpyforandroid.models.ScrcpyOptions.NewDisplay
import io.github.miuzarte.scrcpyforandroid.scaffolds.AppPageLazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperSlider
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperTextField
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.scrcpy.ServerParams
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSpinner
import top.yukonga.miuix.kmp.extra.SuperSwitch
import kotlin.math.roundToInt

@Composable
internal fun AdvancedConfigPage(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    snackbarHostState: SnackbarHostState,
    scrcpy: Scrcpy,
) {
    val scrcpyOptions = Storage.scrcpyOptions

    val focusManager = LocalFocusManager.current

    val scope = rememberCoroutineScope()
    var refreshBusy by remember { mutableStateOf(false) }

    // TODO: handle custom value
    // TODO: handle empty input
    var turnScreenOff by scrcpyOptions.turnScreenOff.asMutableState()
    var control by scrcpyOptions.control.asMutableState()
    var video by scrcpyOptions.video.asMutableState()

    var videoSource by scrcpyOptions.videoSource.asMutableState()
    val videoSourceItems = remember { Shared.VideoSource.entries.map { it.string } }
    val videoSourceIndex = remember(videoSource) {
        Shared.VideoSource.entries.indexOfFirst { it.string == videoSource }.coerceAtLeast(0)
    }
    var displayId by scrcpyOptions.displayId.asMutableState()

    var cameraId by scrcpyOptions.cameraId.asMutableState()
    var cameraFacing by scrcpyOptions.cameraFacing.asMutableState()
    val cameraFacingItems = remember {
        listOf("默认") + Shared.CameraFacing.entries.drop(1).map { it.string }
    }
    val cameraFacingIndex = remember(cameraFacing) {
        if (cameraFacing.isEmpty()) {
            0
        } else {
            val idx = Shared.CameraFacing.entries.indexOfFirst { it.string == cameraFacing }
            if (idx > 0) idx else 0
        }
    }

    var cameraSize by scrcpyOptions.cameraSize.asMutableState()
    var cameraSizeCustom by scrcpyOptions.cameraSizeCustom.asMutableState()
    var cameraSizeUseCustom by scrcpyOptions.cameraSizeUseCustom.asMutableState()

    var cameraSizeCustomInput by rememberSaveable { mutableStateOf(cameraSizeCustom) }
    val cameraSizeDropdownItems = rememberSaveable(scrcpy.cameraSizes) {
        listOf("自动", "自定义") + scrcpy.cameraSizes
    }
    var cameraSizeDropdownIndex by rememberSaveable {
        mutableIntStateOf(
            when {
                cameraSizeUseCustom -> 1 // "自定义"
                cameraSize.isEmpty() -> 0 // "自动"
                cameraSize in scrcpy.cameraSizes -> scrcpy.cameraSizes.indexOf(cameraSize) + 2
                else -> 0 // 默认自动
            }
        )
    }

    var cameraAr by scrcpyOptions.cameraAr.asMutableState()
    var cameraFps by scrcpyOptions.cameraFps.asMutableState()
    val cameraFpsPresetIndex = ScrcpyPresets.CameraFps.indexOfOrNearest(cameraFps)
    var cameraHighSpeed by scrcpyOptions.cameraHighSpeed.asMutableState()

    var audioSource by scrcpyOptions.audioSource.asMutableState()
    val audioSourceItems = remember {
        Shared.AudioSource.entries.map { it.string }
    }
    val audioSourceIndex = remember(audioSource) {
        Shared.AudioSource.entries.indexOfFirst { it.string == audioSource }.coerceAtLeast(0)
    }
    var audioDup by scrcpyOptions.audioDup.asMutableState()
    var audioPlayback by scrcpyOptions.audioPlayback.asMutableState()
    var requireAudio by scrcpyOptions.requireAudio.asMutableState()

    var maxSize by scrcpyOptions.maxSize.asMutableState()
    val maxSizePresetIndex = ScrcpyPresets.MaxSize.indexOfOrNearest(maxSize)
    var maxFps by scrcpyOptions.maxFps.asMutableState()
    val maxFpsPresetIndex = ScrcpyPresets.MaxFPS.indexOfOrNearest(maxFps.toIntOrNull() ?: 0)

    var videoEncoder by scrcpyOptions.videoEncoder.asMutableState()
    var videoCodecOptions by scrcpyOptions.videoCodecOptions.asMutableState()
    var audioEncoder by scrcpyOptions.audioEncoder.asMutableState()
    var audioCodecOptions by scrcpyOptions.audioCodecOptions.asMutableState()

    val videoEncoderDropdownItems = remember(scrcpy.videoEncoders) {
        listOf("") + scrcpy.videoEncoders
    }
    val videoEncoderIndex = remember(videoEncoder, scrcpy.videoEncoders) {
        (scrcpy.videoEncoders.indexOf(videoEncoder) + 1).coerceAtLeast(0)
    }
    val audioEncoderDropdownItems = remember(scrcpy.audioEncoders) {
        listOf("") + scrcpy.audioEncoders
    }
    val audioEncoderIndex = remember(audioEncoder, scrcpy.audioEncoders) {
        (scrcpy.audioEncoders.indexOf(audioEncoder) + 1).coerceAtLeast(0)
    }
    val videoEncoderEntries = videoEncoderDropdownItems.map { encoderName ->
        if (encoderName == "") {
            SpinnerEntry(title = "自动")
        } else {
            SpinnerEntry(
                title = encoderName,
                summary = scrcpy.videoEncoderTypes[encoderName],
            )
        }
    }
    val audioEncoderEntries = audioEncoderDropdownItems.map { encoderName ->
        if (encoderName == "") {
            SpinnerEntry(title = "自动")
        } else {
            SpinnerEntry(
                title = encoderName,
                summary = scrcpy.audioEncoderTypes[encoderName],
            )
        }
    }

    // [<width>x<height>][/<dpi>]
    var newDisplay by scrcpyOptions.newDisplay.asMutableState()
    val (width, height, dpi) = NewDisplay.parseFrom(newDisplay)
    var newDisplayWidth by remember(newDisplay) { mutableStateOf(width?.toString() ?: "") }
    var newDisplayHeight by remember(newDisplay) { mutableStateOf(height?.toString() ?: "") }
    var newDisplayDpi by remember(newDisplay) { mutableStateOf(dpi?.toString() ?: "") }
    fun updateNewDisplay() {
        newDisplay = NewDisplay
            .parseFrom(newDisplayWidth, newDisplayHeight, newDisplayDpi)
            .toString()
    }

    // width:height:x:y
    var crop by scrcpyOptions.crop.asMutableState()
    val (cWidth, cHeight, cX, cY) = Crop.parseFrom(crop)
    var cropWidth by remember(crop) { mutableStateOf(cWidth?.toString() ?: "") }
    var cropHeight by remember(crop) { mutableStateOf(cHeight?.toString() ?: "") }
    var cropX by remember(crop) { mutableStateOf(cX?.toString() ?: "") }
    var cropY by remember(crop) { mutableStateOf(cY?.toString() ?: "") }
    fun updateCrop() {
        crop = Crop
            .parseFrom(cropWidth, cropHeight, cropX, cropY)
            .toString()
    }

    var serverParamsPreview by rememberSaveable {
        mutableStateOf(runBlocking {
            scrcpyOptions
                .toClientOptions()
                .toServerParams(0u)
                .toList(simplify = true)
                .joinToString(ServerParams.SEPARATOR)
        })
    }

    // 监听所有选项变化，自动更新 serverParams 预览
    LaunchedEffect(
        turnScreenOff, control, video,
        videoSource, displayId,
        cameraId, cameraFacing, cameraSize, cameraAr, cameraFps, cameraHighSpeed,
        audioSource, audioDup, audioPlayback, requireAudio,
        maxSize, maxFps,
        videoEncoder, videoCodecOptions,
        audioEncoder, audioCodecOptions,
        newDisplay, crop,
    ) {
        val clientOptions = scrcpyOptions.toClientOptions()

        try {
            clientOptions.validate()
        } catch (e: IllegalArgumentException) {
            snackbarHostState.showSnackbar("Invalid options: ${e.message}")
            return@LaunchedEffect
        }

        serverParamsPreview = clientOptions
            .toServerParams(0u)
            .toList(simplify = true)
            .joinToString(ServerParams.SEPARATOR)
    }

    // 高级参数
    AppPageLazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
    ) {
        item {
            Card {
                TextField(
                    value = serverParamsPreview,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Card {
                SuperSwitch(
                    title = "启动后关闭屏幕",
                    summary = "--turn-screen-off",
                    checked = turnScreenOff,
                    onCheckedChange = {
                        turnScreenOff = it
                        if (it) scope.launch {
                            // github.com/Genymobile/scrcpy/issues/3376
                            // github.com/Genymobile/scrcpy/issues/4587
                            // github.com/Genymobile/scrcpy/issues/5676
                            snackbarHostState.showSnackbar("注意：大部分设备在关闭屏幕后刷新率会降低/减半")
                        }
                    },
                )
                SuperSwitch(
                    title = "禁用控制",
                    summary = "--no-control",
                    checked = !control,
                    onCheckedChange = { control = !it },
                )
                SuperSwitch(
                    title = "禁用视频",
                    summary = "--no-video",
                    checked = !video,
                    onCheckedChange = { video = !it },
                )
            }
        }

        item {
            Card {
                SuperDropdown(
                    title = "视频来源",
                    summary = "--video-source",
                    items = videoSourceItems,
                    selectedIndex = videoSourceIndex,
                    onSelectedIndexChange = {
                        videoSource = Shared.VideoSource.entries[it].string
                    },
                )
                AnimatedVisibility(videoSource == "display") {
                    TextField(
                        value = if (displayId == -1) "" else displayId.toString(),
                        onValueChange = { displayId = it.toIntOrNull() ?: -1 },
                        label = "--display-id",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UiSpacing.CardContent)
                            .padding(bottom = UiSpacing.CardContent),
                    )
                }
                AnimatedVisibility(videoSource == "camera") {
                    TextField(
                        value = cameraId,
                        onValueChange = { cameraId = it },
                        label = "--camera-id",
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UiSpacing.CardContent)
                            .padding(bottom = UiSpacing.CardContent),
                    )
                }
                AnimatedVisibility(videoSource == "camera") {
                    SuperArrow(
                        title = "重新获取 Camera Sizes",
                        summary = "--list-camera-sizes",
                        onClick = {
                            if (refreshBusy) return@SuperArrow
                            scope.launch {
                                refreshBusy = true
                                try {
                                    scrcpy.refreshCameraSizes()
                                    snackbarHostState.showSnackbar("Camera Sizes 已刷新")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("刷新失败: ${e.message}")
                                } finally {
                                    refreshBusy = false
                                }
                            }
                        },
                    )
                }
                AnimatedVisibility(videoSource == "camera") {
                    SuperDropdown(
                        title = "摄像头朝向",
                        summary = "--camera-facing",
                        items = cameraFacingItems,
                        selectedIndex = cameraFacingIndex,
                        onSelectedIndexChange = {
                            cameraFacing =
                                if (it == 0) "" else Shared.CameraFacing.entries[it].string
                        },
                    )
                }
                AnimatedVisibility(videoSource == "camera") {
                    SuperDropdown(
                        title = "摄像头分辨率",
                        summary = "--camera-size",
                        items = cameraSizeDropdownItems,
                        selectedIndex = cameraSizeDropdownIndex,
                        onSelectedIndexChange = {
                            cameraSizeDropdownIndex = it
                            cameraSizeUseCustom = it == 1
                            when (it) {
                                0 -> {
                                    // "自动"
                                    cameraSize = ""
                                    cameraSizeCustomInput = ""
                                }

                                1 -> {
                                    // "自定义" - 进入自定义输入模式
                                    cameraSizeCustomInput = cameraSize.takeIf { size ->
                                        size.isNotEmpty() && size !in scrcpy.cameraSizes
                                    } ?: ""
                                }

                                else -> {
                                    // 选择列表中的实际分辨率
                                    cameraSize = cameraSizeDropdownItems[it]
                                    cameraSizeCustomInput = ""
                                }
                            }
                        },
                    )
                }
                // 只在选择"自定义"时显示输入框
                AnimatedVisibility(videoSource == "camera" && cameraSizeUseCustom) {
                    SuperTextField(
                        value = cameraSizeCustomInput,
                        onValueChange = { cameraSizeCustomInput = it },
                        onFocusLost = {
                            if (cameraSizeCustomInput in scrcpy.cameraSizes) {
                                cameraSizeDropdownIndex =
                                    scrcpy.cameraSizes.indexOf(cameraSizeCustomInput) + 2
                                cameraSizeUseCustom = false
                            } else {
                                cameraSizeCustom = cameraSizeCustomInput
                            }
                        },
                        label = "--camera-size",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UiSpacing.CardContent)
                            .padding(bottom = UiSpacing.CardContent),
                    )
                }
                AnimatedVisibility(videoSource == "camera") {
                    TextField(
                        value = cameraAr,
                        onValueChange = { cameraAr = it },
                        label = "--camera-ar",
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UiSpacing.CardContent)
                            .padding(bottom = UiSpacing.CardContent),
                    )
                }
                AnimatedVisibility(videoSource == "camera") {
                    SuperSlider(
                        title = "摄像头帧率",
                        summary = "--camera-fps",
                        value = cameraFpsPresetIndex.toFloat(),
                        onValueChange = { value ->
                            val idx =
                                value.roundToInt().coerceIn(0, ScrcpyPresets.CameraFps.lastIndex)
                            cameraFps = ScrcpyPresets.CameraFps[idx]
                        },
                        valueRange = 0f..ScrcpyPresets.CameraFps.lastIndex.toFloat(),
                        steps = (ScrcpyPresets.CameraFps.size - 2).coerceAtLeast(0),
                        unit = "fps",
                        zeroStateText = "默认",
                        showUnitWhenZeroState = false,
                        showKeyPoints = true,
                        keyPoints = ScrcpyPresets.CameraFps.indices.map { it.toFloat() },
                        displayText = cameraFps.toString(),
                        inputHint = "0 或留空表示默认",
                        inputInitialValue = cameraFps.toString(),
                        inputFilter = { it.filter(Char::isDigit) },
                        inputValueRange = 0f..UShort.MAX_VALUE.toFloat(),
                        onInputConfirm = { input ->
                            input.toIntOrNull()
                                ?.let { cameraFps = it }
                                ?: run { cameraFps = 0 }
                        },
                    )
                }
                AnimatedVisibility(videoSource == "camera") {
                    SuperSwitch(
                        title = "高帧率模式",
                        summary = "--camera-high-speed",
                        checked = cameraHighSpeed,
                        onCheckedChange = { cameraHighSpeed = it },
                    )
                }
            }
        }

        item {
            Card {
                SuperDropdown(
                    title = "音频来源",
                    summary = "--audio-source",
                    items = audioSourceItems,
                    selectedIndex = audioSourceIndex,
                    onSelectedIndexChange = {
                        audioSource = Shared.AudioSource.entries[it].string
                    },
                )
                SuperSwitch(
                    title = "音频双路输出",
                    summary = "--audio-dup",
                    checked = audioDup,
                    onCheckedChange = { audioDup = it },
                )
                SuperSwitch(
                    title = "仅转发不播放",
                    summary = "--no-audio-playback",
                    checked = !audioPlayback,
                    onCheckedChange = { audioPlayback = !it },
                )
                SuperSwitch(
                    title = "音频失败时终止 [TODO]",
                    summary = "--require-audio",
                    checked = requireAudio,
                    onCheckedChange = { requireAudio = it },
                    enabled = false,
                )
            }
        }

        item {
            Card {
                SuperSlider(
                    title = "最大分辨率",
                    summary = "--max-size",
                    value = maxSizePresetIndex.toFloat(),
                    onValueChange = { value ->
                        val idx =
                            value.roundToInt().coerceIn(0, ScrcpyPresets.MaxSize.lastIndex)
                        maxSize = ScrcpyPresets.MaxSize[idx]
                    },
                    valueRange = 0f..ScrcpyPresets.MaxSize.lastIndex.toFloat(),
                    steps = (ScrcpyPresets.MaxSize.size - 2).coerceAtLeast(0),
                    unit = "px",
                    zeroStateText = "关闭",
                    showUnitWhenZeroState = false,
                    showKeyPoints = true,
                    keyPoints = ScrcpyPresets.MaxSize.indices.map { it.toFloat() },
                    displayText = maxSize.toString(),
                    inputHint = "0 或留空表示关闭",
                    inputInitialValue = maxSize.toString(),
                    inputFilter = { it.filter(Char::isDigit) },
                    inputValueRange = 0f..UInt.MAX_VALUE.toFloat(),
                    onInputConfirm = { input -> input.toIntOrNull()?.let { maxSize = it } },
                )
                SuperSlider(
                    title = "最大帧率",
                    summary = "--max-fps",
                    value = maxFpsPresetIndex.toFloat(),
                    onValueChange = { value ->
                        val idx = value.roundToInt().coerceIn(0, ScrcpyPresets.MaxFPS.lastIndex)
                        maxFps = if (idx == 0) "" else ScrcpyPresets.MaxFPS[idx].toString()
                    },
                    valueRange = 0f..ScrcpyPresets.MaxFPS.lastIndex.toFloat(),
                    steps = (ScrcpyPresets.MaxFPS.size - 2).coerceAtLeast(0),
                    unit = "fps",
                    zeroStateText = "关闭",
                    showUnitWhenZeroState = false,
                    showKeyPoints = true,
                    keyPoints = ScrcpyPresets.MaxFPS.indices.map { it.toFloat() },
                    displayText = maxFps,
                    inputHint = "0 或留空表示关闭",
                    inputInitialValue = maxFps,
                    inputFilter = { it.filter(Char::isDigit) },
                    inputValueRange = 0f..UShort.MAX_VALUE.toFloat(),
                    onInputConfirm = { maxFps = it },
                )
            }
        }

        item {
            Card {
                SuperArrow(
                    title = "重新获取编码器列表",
                    summary = "--list-encoders",
                    onClick = {
                        if (refreshBusy) return@SuperArrow
                        scope.launch {
                            refreshBusy = true
                            try {
                                scrcpy.refreshEncoders()
                                snackbarHostState.showSnackbar("编码器列表已刷新")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("刷新失败: ${e.message}")
                            } finally {
                                refreshBusy = false
                            }
                        }
                    },
                )
                SuperSpinner(
                    title = "视频编码器",
                    summary = "--video-encoder",
                    items = videoEncoderEntries,
                    selectedIndex = videoEncoderIndex,
                    onSelectedIndexChange = {
                        videoEncoder = videoEncoderEntries[it].title ?: ""
                    },
                )
                TextField(
                    value = videoCodecOptions,
                    onValueChange = { videoCodecOptions = it },
                    label = "--video-codec-options",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.CardContent)
                        .padding(bottom = UiSpacing.CardContent),
                )
                SuperSpinner(
                    title = "音频编码器",
                    summary = "--audio-encoder",
                    items = audioEncoderEntries,
                    selectedIndex = audioEncoderIndex,
                    onSelectedIndexChange = {
                        audioEncoder = audioEncoderEntries[it].title ?: ""
                    },
                )
                TextField(
                    value = audioCodecOptions,
                    onValueChange = { audioCodecOptions = it },
                    label = "--audio-codec-options",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.CardContent)
                        .padding(bottom = UiSpacing.CardContent),
                )
            }
        }

        item {
            Card {
                Text(
                    text = "--new-display",
                    modifier = Modifier
                        .padding(horizontal = UiSpacing.CardTitle)
                        .padding(
                            top = UiSpacing.CardContent,
                            bottom = UiSpacing.FieldLabelBottom,
                        ),
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.CardContent)
                        .padding(bottom = UiSpacing.CardContent),
                    horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                ) {
                    TextField(
                        label = "width",
                        value = newDisplayWidth,
                        onValueChange = { newDisplayWidth = it; updateNewDisplay() },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Next) },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    TextField(
                        label = "height",
                        value = newDisplayHeight,
                        onValueChange = { newDisplayHeight = it; updateNewDisplay() },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Next) },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    TextField(
                        label = "dpi",
                        value = newDisplayDpi,
                        onValueChange = { newDisplayDpi = it; updateNewDisplay() },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Card {
                Text(
                    text = "--crop",
                    modifier = Modifier
                        .padding(horizontal = UiSpacing.CardTitle)
                        .padding(
                            top = UiSpacing.CardContent,
                            bottom = UiSpacing.FieldLabelBottom,
                        ),
                    fontWeight = FontWeight.Medium,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.CardContent)
                        .padding(bottom = UiSpacing.CardContent),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                    ) {
                        TextField(
                            label = "width",
                            value = cropWidth,
                            onValueChange = { cropWidth = it; updateCrop() },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Next) },
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        TextField(
                            label = "height",
                            value = cropHeight,
                            onValueChange = { cropHeight = it; updateCrop() },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Next) },
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                    ) {
                        TextField(
                            label = "x",
                            value = cropX,
                            onValueChange = { cropX = it; updateCrop() },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Next) },
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        TextField(
                            label = "y",
                            value = cropY,
                            onValueChange = { cropY = it; updateCrop() },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() },
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // TODO: 放进 [AppPageLazyColumn] 里
        item { Spacer(Modifier.height(UiSpacing.BottomContent)) }
    }
}
