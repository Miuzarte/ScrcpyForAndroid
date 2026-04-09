package io.github.miuzarte.scrcpyforandroid.pages

import android.annotation.SuppressLint
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperSlider
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperTextField
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.Codec
import io.github.miuzarte.scrcpyforandroid.services.SnackbarController
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.scrcpyOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSpinner
import top.yukonga.miuix.kmp.extra.SuperSwitch
import kotlin.math.roundToInt

@Composable
internal fun ScrcpyAllOptionsScreen(
    onBack: () -> Unit,
    scrollBehavior: ScrollBehavior,
    snackbar: SnackbarController,
    scrcpy: Scrcpy,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = "所有参数",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar.hostState) },
    ) { contentPadding ->
        ScrcpyAllOptionsPage(
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
            snackbar = snackbar,
            scrcpy = scrcpy,
        )
    }
}

@Composable
internal fun ScrcpyAllOptionsPage(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    snackbar: SnackbarController,
    scrcpy: Scrcpy,
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val taskScope = remember { CoroutineScope(Dispatchers.IO + SupervisorJob()) }

    var refreshBusy by rememberSaveable { mutableStateOf(false) }
    var listRefreshVersion by rememberSaveable { mutableIntStateOf(0) }

    val soBundleShared by scrcpyOptions.bundleState.collectAsState()
    val soBundleSharedLatest by rememberUpdatedState(soBundleShared)
    var soBundle by rememberSaveable(soBundleShared) { mutableStateOf(soBundleShared) }
    // 验证配置项合法性用于回滚更改
    var lastValidSoBundle by rememberSaveable(soBundleShared) { mutableStateOf(soBundleShared) }
    val soBundleLatest by rememberUpdatedState(soBundle)
    LaunchedEffect(soBundleShared) {
        if (soBundle != soBundleShared) {
            soBundle = soBundleShared
        }
        lastValidSoBundle = soBundleShared
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

    val audioCodecItems = rememberSaveable { Codec.AUDIO.map { it.displayName } }
    val audioCodecIndex = rememberSaveable(soBundle.audioCodec) {
        Codec.AUDIO
            .indexOfFirst { it.string == soBundle.audioCodec }
            .coerceAtLeast(0)
    }

    val videoCodecItems = rememberSaveable { Codec.VIDEO.map { it.displayName } }
    val videoCodecIndex = rememberSaveable(soBundle.videoCodec) {
        Codec.VIDEO
            .indexOfFirst { it.string == soBundle.videoCodec }
            .coerceAtLeast(0)
    }

    val videoSourceItems = rememberSaveable { Shared.VideoSource.entries.map { it.string } }
    val videoSourceIndex = rememberSaveable(soBundle.videoSource) {
        Shared.VideoSource.entries
            .indexOfFirst { it.string == soBundle.videoSource }
            .coerceAtLeast(0)
    }

    var displayIdInput by rememberSaveable(soBundle.displayId) {
        mutableStateOf(
            if (soBundle.displayId == -1) ""
            else soBundle.displayId.toString()
        )
    }

    var cameraIdInput by rememberSaveable(soBundle.cameraId) {
        mutableStateOf(soBundle.cameraId)
    }

    val cameraFacingItems = rememberSaveable {
        listOf("默认") + Shared.CameraFacing.entries
            .drop(1)
            .map { it.string }
    }
    val cameraFacingIndex = rememberSaveable(soBundle.cameraFacing) {
        if (soBundle.cameraFacing.isEmpty()) {
            0
        } else {
            val idx = Shared.CameraFacing.entries
                .indexOfFirst { it.string == soBundle.cameraFacing }
            if (idx > 0) idx else 0
        }
    }

    var cameraSizeCustomInput by rememberSaveable(soBundle.cameraSizeCustom) {
        mutableStateOf(soBundle.cameraSizeCustom)
    }

    val cameraSizes = scrcpy.listings.cameraSizes
    val cameraSizeDropdownItems = rememberSaveable(cameraSizes, listRefreshVersion) {
        listOf("自动", "自定义") + cameraSizes
    }
    var cameraSizeDropdownIndex by rememberSaveable(
        soBundle.cameraSize,
        soBundle.cameraSizeUseCustom,
        cameraSizes,
        listRefreshVersion,
    ) {
        mutableIntStateOf(
            when {
                soBundle.cameraSizeUseCustom -> 1 // "自定义"
                soBundle.cameraSize.isEmpty() -> 0 // "自动"
                soBundle.cameraSize in cameraSizes ->
                    cameraSizes.indexOf(soBundle.cameraSize) + 2

                else -> 0 // 默认自动
            }
        )
    }

    var cameraArInput by rememberSaveable(soBundle.cameraAr) {
        mutableStateOf(soBundle.cameraAr)
    }

    val cameraFpsPresetIndex = rememberSaveable(soBundle.cameraFps) {
        ScrcpyPresets.CameraFps.indexOfOrNearest(soBundle.cameraFps)
    }

    val audioSourceItems = rememberSaveable {
        Shared.AudioSource.entries.map { it.string }
    }
    val audioSourceIndex = rememberSaveable(soBundle.audioSource) {
        Shared.AudioSource.entries
            .indexOfFirst { it.string == soBundle.audioSource }
            .coerceAtLeast(0)
    }

    val videoEncoderInfos = scrcpy.listings.videoEncoders
    val audioEncoderInfos = scrcpy.listings.audioEncoders
    val videoEncoders = remember(videoEncoderInfos) {
        videoEncoderInfos.map { it.id }
    }
    val audioEncoders = remember(audioEncoderInfos) {
        audioEncoderInfos.map { it.id }
    }
    val videoEncoderTypes = remember(videoEncoderInfos) {
        videoEncoderInfos.associate { it.id to it.type.s }
    }
    val audioEncoderTypes = remember(audioEncoderInfos) {
        audioEncoderInfos.associate { it.id to it.type.s }
    }

    val maxSizePresetIndex = rememberSaveable(soBundle.maxSize) {
        ScrcpyPresets.MaxSize.indexOfOrNearest(soBundle.maxSize)
    }

    val maxFpsPresetIndex = rememberSaveable(soBundle.maxFps) {
        ScrcpyPresets.MaxFPS.indexOfOrNearest(soBundle.maxFps.toIntOrNull() ?: 0)
    }

    var videoCodecOptionsInput by rememberSaveable(soBundle.videoCodecOptions) {
        mutableStateOf(soBundle.videoCodecOptions)
    }

    var audioCodecOptionsInput by rememberSaveable(soBundle.audioCodecOptions) {
        mutableStateOf(soBundle.audioCodecOptions)
    }

    val videoEncoderDropdownItems = rememberSaveable(videoEncoders, listRefreshVersion) {
        listOf("") + videoEncoders
    }
    val videoEncoderIndex = rememberSaveable(soBundle.videoEncoder, videoEncoders, listRefreshVersion) {
        (videoEncoders.indexOf(soBundle.videoEncoder) + 1).coerceAtLeast(0)
    }

    val audioEncoderDropdownItems = rememberSaveable(audioEncoders, listRefreshVersion) {
        listOf("") + audioEncoders
    }
    val audioEncoderIndex = rememberSaveable(soBundle.audioEncoder, audioEncoders, listRefreshVersion) {
        (audioEncoders.indexOf(soBundle.audioEncoder) + 1).coerceAtLeast(0)
    }

    val videoEncoderEntries = videoEncoderDropdownItems.map { encoderName ->
        if (encoderName == "") {
            SpinnerEntry(title = "自动")
        } else {
            SpinnerEntry(
                title = encoderName,
                summary = videoEncoderTypes[encoderName],
            )
        }
    }

    val audioEncoderEntries = audioEncoderDropdownItems.map { encoderName ->
        if (encoderName == "") {
            SpinnerEntry(title = "自动")
        } else {
            SpinnerEntry(
                title = encoderName,
                summary = audioEncoderTypes[encoderName],
            )
        }
    }

    // [<width>x<height>][/<dpi>]
    val (ndWidth, ndHeight, ndDpi) = remember(soBundle.newDisplay) {
        NewDisplay.parseFrom(soBundle.newDisplay)
    }
    var newDisplayWidthInput by rememberSaveable(soBundle.newDisplay) {
        mutableStateOf(ndWidth?.toString() ?: "")
    }
    var newDisplayHeightInput by rememberSaveable(soBundle.newDisplay) {
        mutableStateOf(ndHeight?.toString() ?: "")
    }
    var newDisplayDpiInput by rememberSaveable(soBundle.newDisplay) {
        mutableStateOf(ndDpi?.toString() ?: "")
    }

    // width:height:x:y
    val (cWidth, cHeight, cX, cY) = remember(soBundle.crop) {
        Crop.parseFrom(soBundle.crop)
    }
    var cropWidthInput by rememberSaveable(soBundle.crop) {
        mutableStateOf(cWidth?.toString() ?: "")
    }
    var cropHeightInput by rememberSaveable(soBundle.crop) {
        mutableStateOf(cHeight?.toString() ?: "")
    }
    var cropXInput by rememberSaveable(soBundle.crop) {
        mutableStateOf(cX?.toString() ?: "")
    }
    var cropYInput by rememberSaveable(soBundle.crop) {
        mutableStateOf(cY?.toString() ?: "")
    }

    var serverParamsPreview by rememberSaveable { mutableStateOf("") }
    // 监听选项变化, 自动更新预览
    LaunchedEffect(soBundle) {
        val clientOptions = scrcpyOptions.toClientOptions(soBundle).fix()

        try {
            clientOptions.validate()
        } catch (e: IllegalArgumentException) {
            if (soBundle != lastValidSoBundle) {
                snackbar.show("Invalid options: ${e.message}")
                soBundle = lastValidSoBundle
            }
            return@LaunchedEffect
        }

        lastValidSoBundle = soBundle

        serverParamsPreview = clientOptions
            .toServerParams(0u)
            .toList(simplify = true)
            // improve readability using hard line breaks
            .joinToString("\n")
    }

    LazyColumn(
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
        }

        item {
            Card {
                SuperSwitch(
                    title = "启动后关闭屏幕",
                    summary = "--turn-screen-off",
                    checked = soBundle.turnScreenOff,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            turnScreenOff = it
                        )
                        if (it) {
                            // github.com/Genymobile/scrcpy/issues/3376
                            // github.com/Genymobile/scrcpy/issues/4587
                            // github.com/Genymobile/scrcpy/issues/5676
                            snackbar.show("注意：大部分设备在关闭屏幕后刷新率会降低/减半")
                        }
                    },
                )
                SuperSwitch(
                    title = "禁用控制",
                    summary = "--no-control",
                    checked = !soBundle.control,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            control = !it
                        )
                    },
                    // 拦不住同时点, 弃用
                    // enabled = audio || video,
                )
                SuperSwitch(
                    title = "禁用视频",
                    summary = "--no-video",
                    checked = !soBundle.video,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            video = !it
                        )
                    },
                    // enabled = audio || control,
                )
                SuperSwitch(
                    title = "禁用音频",
                    summary = "--no-audio",
                    checked = !soBundle.audio,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            audio = !it
                        )
                    },
                    // enabled = control || video,
                )
            }
        }

        item {
            Card {
                SuperDropdown(
                    title = "音频编码",
                    summary = "--audio-codec",
                    items = audioCodecItems,
                    selectedIndex = audioCodecIndex,
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            audioCodec = Codec.AUDIO[it].string
                        )
                    },
                )
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
                            ?.let {
                                soBundle = soBundle.copy(
                                    audioBitRate = it * 1000
                                )
                            }
                    },
                )

                SuperDropdown(
                    title = "视频编码",
                    summary = "--video-codec",
                    items = videoCodecItems,
                    selectedIndex = videoCodecIndex,
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            videoCodec = Codec.VIDEO[it].string
                        )
                    },
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
                        soBundle = soBundle.copy(
                            videoSource = Shared.VideoSource.entries[it].string
                        )
                    },
                )
                AnimatedVisibility(soBundle.videoSource == "display") {
                    Column {
                        SuperTextField(
                            value = displayIdInput,
                            onValueChange = { displayIdInput = it },
                            onFocusLost = {
                                soBundle = soBundle.copy(
                                    displayId = displayIdInput.toIntOrNull() ?: -1
                                )
                            },
                            label = "--display-id",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(all = UiSpacing.Large),
                        )
                        SuperSlider(
                            title = "最大分辨率",
                            summary = "--max-size",
                            value = maxSizePresetIndex.toFloat(),
                            onValueChange = {
                                val idx = it.roundToInt()
                                    .coerceIn(0, ScrcpyPresets.MaxSize.lastIndex)
                                soBundle = soBundle.copy(
                                    maxSize = ScrcpyPresets.MaxSize[idx]
                                )
                            },
                            valueRange = 0f..ScrcpyPresets.MaxSize.lastIndex.toFloat(),
                            steps = (ScrcpyPresets.MaxSize.size - 2).coerceAtLeast(0),
                            unit = "px",
                            zeroStateText = "关闭",
                            showUnitWhenZeroState = false,
                            showKeyPoints = true,
                            keyPoints = ScrcpyPresets.MaxSize.indices.map { it.toFloat() },
                            displayText = soBundle.maxSize.toString(),
                            inputInitialValue = soBundle.maxSize.takeIf { it != 0 }?.toString()
                                ?: "",
                            inputFilter = { it.filter(Char::isDigit) },
                            inputValueRange = 0f..UInt.MAX_VALUE.toFloat(),
                            onInputConfirm = {
                                soBundle = soBundle.copy(
                                    maxSize = it.toIntOrNull() ?: run { 0 }
                                )
                            },
                        )
                        SuperSlider(
                            title = "最大帧率",
                            summary = "--max-fps",
                            value = maxFpsPresetIndex.toFloat(),
                            onValueChange = { value ->
                                val idx = value.roundToInt()
                                    .coerceIn(0, ScrcpyPresets.MaxFPS.lastIndex)
                                soBundle = soBundle.copy(
                                    maxFps =
                                        if (idx == 0) ""
                                        else ScrcpyPresets.MaxFPS[idx].toString()
                                )
                            },
                            valueRange = 0f..ScrcpyPresets.MaxFPS.lastIndex.toFloat(),
                            steps = (ScrcpyPresets.MaxFPS.size - 2).coerceAtLeast(0),
                            unit = "fps",
                            zeroStateText = "关闭",
                            showUnitWhenZeroState = false,
                            showKeyPoints = true,
                            keyPoints = ScrcpyPresets.MaxFPS.indices.map { it.toFloat() },
                            displayText = soBundle.maxFps,
                            inputInitialValue = soBundle.maxFps,
                            inputFilter = { it.filter(Char::isDigit) },
                            inputValueRange = 0f..UShort.MAX_VALUE.toFloat(),
                            onInputConfirm = {
                                soBundle = soBundle.copy(
                                    maxFps = it
                                )
                            },
                        )
                    }
                }
                AnimatedVisibility(soBundle.videoSource == "camera") {
                    Column {
                        SuperTextField(
                            value = cameraIdInput,
                            onValueChange = { cameraIdInput = it },
                            onFocusLost = {
                                soBundle = soBundle.copy(
                                    cameraId = cameraIdInput
                                )
                            },
                            label = "--camera-id",
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(all = UiSpacing.Large),
                        )
                        SuperDropdown(
                            title = "摄像头朝向",
                            summary = "--camera-facing",
                            items = cameraFacingItems,
                            selectedIndex = cameraFacingIndex,
                            onSelectedIndexChange = {
                                soBundle = soBundle.copy(
                                    cameraFacing =
                                        if (it == 0) ""
                                        else Shared.CameraFacing.entries[it].string
                                )
                            },
                        )
                        SuperArrow(
                            title = "获取 Camera Sizes",
                            summary = "--list-camera-sizes",
                            onClick = {
                                if (refreshBusy) return@SuperArrow
                                scope.launch {
                                    refreshBusy = true
                                    snackbar.show("获取中")
                                    try {
                                        withContext(Dispatchers.IO) {
                                            scrcpy.listings.getCameraSizes(forceRefresh = true)
                                        }
                                        listRefreshVersion += 1
                                        snackbar.show("获取成功")
                                    } catch (e: Exception) {
                                        snackbar.show("刷新失败: ${e.message}")
                                    } finally {
                                        refreshBusy = false
                                    }
                                }
                            },
                        )
                        SuperDropdown(
                            title = "摄像头分辨率",
                            summary = "--camera-size",
                            items = cameraSizeDropdownItems,
                            selectedIndex = cameraSizeDropdownIndex,
                            onSelectedIndexChange = {
                                cameraSizeDropdownIndex = it
                                when (it) {
                                    0 -> {
                                        // "自动"
                                        soBundle = soBundle.copy(
                                            cameraSize = "",
                                            cameraSizeUseCustom = false,
                                        )
                                        cameraSizeCustomInput = ""
                                    }

                                    1 -> {
                                        // "自定义" - 进入自定义输入模式
                                        soBundle = soBundle.copy(
                                            cameraSizeUseCustom = true
                                        )
                                        cameraSizeCustomInput = soBundle.cameraSize
                                    }

                                    else -> {
                                        // 选择列表中的实际分辨率
                                        soBundle = soBundle.copy(
                                            cameraSize = cameraSizeDropdownItems[it],
                                            cameraSizeUseCustom = false,
                                        )
                                        cameraSizeCustomInput = ""
                                    }
                                }
                            },
                        )
                        // 只在选择"自定义"时显示输入框
                        AnimatedVisibility(soBundle.cameraSizeUseCustom) {
                            SuperTextField(
                                value = cameraSizeCustomInput,
                                onValueChange = { cameraSizeCustomInput = it },
                                onFocusLost = {
                                    if (cameraSizeCustomInput in cameraSizes) {
                                        // 输入的值存在于列表中, 取消自定义输入
                                        cameraSizeDropdownIndex = cameraSizes
                                            .indexOf(cameraSizeCustomInput) + 2
                                        soBundle = soBundle.copy(
                                            cameraSizeUseCustom = false
                                        )
                                    } else {
                                        soBundle = soBundle.copy(
                                            cameraSizeCustom = cameraSizeCustomInput
                                        )
                                    }
                                },
                                label = "--camera-size",
                                useLabelAsPlaceholder = true,
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(all = UiSpacing.Large),
                            )
                        }
                        SuperTextField(
                            value = cameraArInput,
                            onValueChange = { cameraArInput = it },
                            onFocusLost = {
                                soBundle = soBundle.copy(
                                    cameraAr = cameraArInput
                                )
                            },
                            label = "--camera-ar",
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(all = UiSpacing.Large),
                        )
                        SuperSlider(
                            title = "摄像头帧率",
                            summary = "--camera-fps",
                            value = cameraFpsPresetIndex.toFloat(),
                            onValueChange = { value ->
                                val idx = value.roundToInt()
                                    .coerceIn(0, ScrcpyPresets.CameraFps.lastIndex)
                                soBundle = soBundle.copy(
                                    cameraFps = ScrcpyPresets.CameraFps[idx]
                                )
                            },
                            valueRange = 0f..ScrcpyPresets.CameraFps.lastIndex.toFloat(),
                            steps = (ScrcpyPresets.CameraFps.size - 2).coerceAtLeast(0),
                            unit = "fps",
                            zeroStateText = "默认",
                            showKeyPoints = true,
                            keyPoints = ScrcpyPresets.CameraFps.indices.map { it.toFloat() },
                            displayText = soBundle.cameraFps.toString(),
                            inputInitialValue =
                                if (soBundle.cameraFps <= 0) ""
                                else soBundle.cameraFps.toString(),
                            inputFilter = { it.filter(Char::isDigit) },
                            inputValueRange = 0f..UShort.MAX_VALUE.toFloat(),
                            onInputConfirm = {
                                soBundle = soBundle.copy(
                                    cameraFps = it.toIntOrNull() ?: run { 0 }
                                )
                            },
                        )
                        SuperSwitch(
                            title = "高帧率模式",
                            summary = "--camera-high-speed",
                            checked = soBundle.cameraHighSpeed,
                            onCheckedChange = {
                                soBundle = soBundle.copy(
                                    cameraHighSpeed = it
                                )
                            },
                        )
                    }
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
                        soBundle = soBundle.copy(
                            audioSource = Shared.AudioSource.entries[it].string
                        )
                    },
                )
                SuperSwitch(
                    title = "音频双路输出",
                    summary = "--audio-dup",
                    checked = soBundle.audioDup,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            audioDup = it
                        )
                    },
                )
                SuperSwitch(
                    title = "仅转发不播放",
                    summary = "--no-audio-playback",
                    checked = !soBundle.audioPlayback,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            audioPlayback = !it
                        )
                    },
                )
                SuperSwitch(
                    title = "音频转发失败时终止",
                    summary = "--require-audio",
                    checked = soBundle.requireAudio,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            requireAudio = it
                        )
                    },
                )
            }
        }

        item {
            Card {
                SuperArrow(
                    title = "获取编码器列表",
                    summary = "--list-encoders",
                    onClick = {
                        if (refreshBusy) return@SuperArrow
                        scope.launch {
                            refreshBusy = true
                            snackbar.show("获取中")
                            try {
                                withContext(Dispatchers.IO) {
                                    scrcpy.listings.getVideoEncoders(forceRefresh = true)
                                }
                                listRefreshVersion += 1
                                snackbar.show("获取成功")
                            } catch (e: Exception) {
                                snackbar.show("刷新失败: ${e.message}")
                            } finally {
                                refreshBusy = false
                            }
                        }
                    },
                )
                // TODO: 在 SuperSpinner / SuperDropdown 支持展开状态回调后, 在展开时触发获取
                SuperSpinner(
                    title = "视频编码器",
                    summary = "--video-encoder",
                    items = videoEncoderEntries,
                    selectedIndex = videoEncoderIndex,
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            videoEncoder = videoEncoderEntries[it].title ?: ""
                        )
                    },
                )
                SuperTextField(
                    value = videoCodecOptionsInput,
                    onValueChange = { videoCodecOptionsInput = it },
                    onFocusLost = {
                        soBundle = soBundle.copy(
                            videoCodecOptions = videoCodecOptionsInput
                        )
                    },
                    label = "--video-codec-options",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = UiSpacing.Large),
                )
                SuperSpinner(
                    title = "音频编码器",
                    summary = "--audio-encoder",
                    items = audioEncoderEntries,
                    selectedIndex = audioEncoderIndex,
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            audioEncoder = audioEncoderEntries[it].title ?: ""
                        )
                    },
                )
                SuperTextField(
                    value = audioCodecOptionsInput,
                    onValueChange = { audioCodecOptionsInput = it },
                    onFocusLost = {
                        soBundle = soBundle.copy(
                            audioCodecOptions = audioCodecOptionsInput
                        )
                    },
                    label = "--audio-codec-options",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = UiSpacing.Large),
                )
            }
        }

        if (soBundle.videoSource == "display") item {
            Card {
                Column(
                    modifier = Modifier.padding(vertical = UiSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = UiSpacing.Large),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                    ) {
                        Text(
                            text = "--new-display",
                            fontWeight = FontWeight.Medium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
                        ) {
                            SuperTextField(
                                label = "width",
                                value = newDisplayWidthInput,
                                onValueChange = { newDisplayWidthInput = it },
                                onFocusLost = {
                                    soBundle = soBundle.copy(
                                        newDisplay = NewDisplay
                                            .parseFrom(
                                                newDisplayWidthInput,
                                                newDisplayHeightInput,
                                                newDisplayDpiInput
                                            )
                                            .toString()
                                    )
                                },
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
                            SuperTextField(
                                label = "height",
                                value = newDisplayHeightInput,
                                onValueChange = { newDisplayHeightInput = it },
                                onFocusLost = {
                                    soBundle = soBundle.copy(
                                        newDisplay = NewDisplay
                                            .parseFrom(
                                                newDisplayWidthInput,
                                                newDisplayHeightInput,
                                                newDisplayDpiInput
                                            )
                                            .toString()
                                    )
                                },
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
                            SuperTextField(
                                label = "dpi",
                                value = newDisplayDpiInput,
                                onValueChange = { newDisplayDpiInput = it },
                                onFocusLost = {
                                    soBundle = soBundle.copy(
                                        newDisplay = NewDisplay
                                            .parseFrom(
                                                newDisplayWidthInput,
                                                newDisplayHeightInput,
                                                newDisplayDpiInput
                                            )
                                            .toString()
                                    )
                                },
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
        }

        if (soBundle.videoSource == "display") item {
            Card {
                Column(
                    modifier = Modifier.padding(vertical = UiSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = UiSpacing.Large),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                    ) {
                        Text(
                            text = "--crop",
                            fontWeight = FontWeight.Medium,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
                            ) {
                                SuperTextField(
                                    label = "width",
                                    value = cropWidthInput,
                                    onValueChange = { cropWidthInput = it },
                                    onFocusLost = {
                                        soBundle = soBundle.copy(
                                            crop = Crop
                                                .parseFrom(
                                                    cropWidthInput,
                                                    cropHeightInput,
                                                    cropXInput,
                                                    cropYInput
                                                )
                                                .toString()
                                        )
                                    },
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
                                SuperTextField(
                                    label = "height",
                                    value = cropHeightInput,
                                    onValueChange = { cropHeightInput = it },
                                    onFocusLost = {
                                        soBundle = soBundle.copy(
                                            crop = Crop
                                                .parseFrom(
                                                    cropWidthInput,
                                                    cropHeightInput,
                                                    cropXInput,
                                                    cropYInput
                                                )
                                                .toString()
                                        )
                                    },
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
                                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
                            ) {
                                SuperTextField(
                                    label = "x",
                                    value = cropXInput,
                                    onValueChange = { cropXInput = it },
                                    onFocusLost = {
                                        soBundle = soBundle.copy(
                                            crop = Crop
                                                .parseFrom(
                                                    cropWidthInput,
                                                    cropHeightInput,
                                                    cropXInput,
                                                    cropYInput
                                                )
                                                .toString()
                                        )
                                    },
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
                                SuperTextField(
                                    label = "y",
                                    value = cropYInput,
                                    onValueChange = { cropYInput = it },
                                    onFocusLost = {
                                        soBundle = soBundle.copy(
                                            crop = Crop
                                                .parseFrom(
                                                    cropWidthInput,
                                                    cropHeightInput,
                                                    cropXInput,
                                                    cropYInput
                                                )
                                                .toString()
                                        )
                                    },
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
            }
        }

        item { Spacer(Modifier.height(UiSpacing.PageBottom)) }
    }
}
