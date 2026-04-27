package io.github.miuzarte.scrcpyforandroid.pages

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FileOpen
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import io.github.miuzarte.scrcpyforandroid.BuildConfig
import io.github.miuzarte.scrcpyforandroid.LockscreenPasswordActivity
import io.github.miuzarte.scrcpyforandroid.constants.ThemeModes
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.SectionSmallTitle
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperSlider
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperTextField
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.AppUpdateChecker
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings.FullscreenVirtualButtonDock
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.MonetKeyColorOptions
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import java.io.File
import kotlin.math.roundToInt
import android.provider.Settings as AndroidSettings

private const val TERMINAL_FONT_RELATIVE_PATH = "terminal/font.ttf"
private val monetPaletteStyleOptions = ThemePaletteStyle.entries.map { it.name }
private val monetColorSpecOptions = ThemeColorSpec.entries.map { it.name }

suspend fun clearTerminalFont(context: Context) =
    withContext(Dispatchers.IO) {
        val target = File(
            context.filesDir,
            TERMINAL_FONT_RELATIVE_PATH,
        )
        target.exists() && target.delete()
    }

@Composable
fun SettingsScreen(
    scrollBehavior: ScrollBehavior,
    bottomInnerPadding: Dp,
    onOpenReorderDevices: () -> Unit,
) {
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                TopAppBar(
                    title = "设置",
                    color =
                        if (blurActive) Color.Transparent
                        else colorScheme.surface,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { pagePadding ->
        Box(
            modifier =
                if (blurActive) Modifier.layerBackdrop(blurBackdrop)
                else Modifier,
        ) {
            SettingsPage(
                contentPadding = pagePadding,
                scrollBehavior = scrollBehavior,
                bottomInnerPadding = bottomInnerPadding,
                onOpenReorderDevices = onOpenReorderDevices,
            )
        }
    }
}

@Composable
fun SettingsPage(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    bottomInnerPadding: Dp,
    onOpenReorderDevices: () -> Unit,
) {
    val context = LocalContext.current
    val updateState by AppUpdateChecker.state.collectAsState()

    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val scope = rememberCoroutineScope()

    val snackbar = LocalSnackbarController.current
    val navigator = LocalRootNavigator.current
    val serverPicker = LocalServerPicker.current
    val terminalFontPicker = LocalTerminalFontPicker.current
    val isScrcpyStreaming = AppRuntime.scrcpy?.isStarted() == true

    val asBundleShared by appSettings.bundleState.collectAsState()
    val asBundleSharedLatest by rememberUpdatedState(asBundleShared)
    var asBundle by rememberSaveable(asBundleShared) { mutableStateOf(asBundleShared) }
    val asBundleLatest by rememberUpdatedState(asBundle)
    LaunchedEffect(asBundleShared) {
        if (asBundle != asBundleShared) {
            asBundle = asBundleShared
        }
    }
    LaunchedEffect(asBundle) {
        delay(Settings.BUNDLE_SAVE_DELAY)
        if (asBundle != asBundleSharedLatest) {
            appSettings.saveBundle(asBundle)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            taskScope.launch {
                appSettings.saveBundle(asBundleLatest)
            }
        }
    }

    val themeItems = rememberSaveable { ThemeModes.baseOptions.map { it.label } }

    val fullscreenVirtualButtonDock = remember(asBundle.fullscreenVirtualButtonDock) {
        FullscreenVirtualButtonDock.fromStoredValue(asBundle.fullscreenVirtualButtonDock)
    }

    val customServerVersionShowInput = rememberSaveable(asBundle.customServerUri) {
        asBundle.customServerUri.isNotBlank()
    }
    var customServerVersionInput by rememberSaveable(asBundle.customServerVersion) {
        mutableStateOf(asBundle.customServerVersion)
    }
    var serverRemotePathInput by rememberSaveable(asBundle.serverRemotePath) {
        mutableStateOf(
            if (asBundle.serverRemotePath == AppSettings.SERVER_REMOTE_PATH.defaultValue) ""
            else asBundle.serverRemotePath
        )
    }

    var adbKeyNameInput by rememberSaveable(asBundle.adbKeyName) {
        mutableStateOf(
            if (asBundle.adbKeyName == AppSettings.ADB_KEY_NAME.defaultValue) ""
            else asBundle.adbKeyName
        )
    }

    val updateSummary = remember(updateState) {
        "当前版本 ${BuildConfig.VERSION_NAME}" + when (val state = updateState) {
            AppUpdateChecker.State.Idle -> ""
            AppUpdateChecker.State.Checking -> "，正在检查更新"
            AppUpdateChecker.State.Error -> "，检查更新失败"

            is AppUpdateChecker.State.Ready -> when {
                state.release.hasUpdate ->
                    "，发现新版本 ${state.release.latestVersion}"

                state.release.currentVersion == state.release.latestVersion.removePrefix("v")
                        || state.release.currentVersion == state.release.latestVersion ->
                    "，已是最新版本"

                else -> "，高于最新发布版本 ${state.release.latestVersion}"
            }
        }
    }
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    // 设置
    LazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
        state = listState,
        bottomInnerPadding = bottomInnerPadding,
    ) {
        item {
            SectionSmallTitle("主题")
            Card {
                OverlayDropdownPreference(
                    title = "外观模式",
                    summary = "选择应用的外观模式",
                    items = themeItems,
                    selectedIndex = asBundle.themeBaseIndex
                        .coerceIn(0, ThemeModes.baseOptions.lastIndex),
                    onSelectedIndexChange = {
                        asBundle = asBundle.copy(
                            themeBaseIndex = it
                        )
                    },
                )
                SwitchPreference(
                    title = "Monet 颜色",
                    summary = "开启后使用 Monet 动态配色",
                    checked = asBundle.monet,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            monet = it
                        )
                    },
                )
                AnimatedVisibility(asBundle.monet) {
                    Column {
                        OverlayDropdownPreference(
                            title = "Monet Key Color",
                            summary = "设置 Monet 强调色",
                            items = MonetKeyColorOptions,
                            selectedIndex = asBundle.monetSeedIndex
                                .coerceIn(0, MonetKeyColorOptions.lastIndex),
                            onSelectedIndexChange = {
                                asBundle = asBundle.copy(
                                    monetSeedIndex = it
                                )
                            },
                        )
                    }
                }
                AnimatedVisibility(asBundle.monet && asBundle.monetSeedIndex > 0) {
                    Column {
                        OverlayDropdownPreference(
                            title = "Monet Palette Style",
                            summary = "设置 Monet 调色板风格",
                            items = monetPaletteStyleOptions,
                            selectedIndex = asBundle.monetPaletteStyle
                                .coerceIn(0, monetPaletteStyleOptions.lastIndex),
                            onSelectedIndexChange = {
                                asBundle = asBundle.copy(
                                    monetPaletteStyle = it
                                )
                            },
                        )
                        OverlayDropdownPreference(
                            title = "Monet Color Spec",
                            summary = "设置 Monet 色彩规格",
                            items = monetColorSpecOptions,
                            selectedIndex = asBundle.monetColorSpec
                                .coerceIn(0, monetColorSpecOptions.lastIndex),
                            onSelectedIndexChange = {
                                asBundle = asBundle.copy(
                                    monetColorSpec = it
                                )
                            },
                        )
                    }
                }
                SwitchPreference(
                    title = "模糊",
                    summary = "启用顶栏和底栏的模糊效果",
                    checked = asBundle.blur,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            blur = it
                        )
                    }
                )
                SwitchPreference(
                    title = "悬浮底栏",
                    summary = "使用 Apple 风格的悬浮底栏",
                    checked = asBundle.floatingBottomBar,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            floatingBottomBar = it
                        )
                    }
                )
                AnimatedVisibility(asBundle.floatingBottomBar && asBundle.blur) {
                    Column {
                        SwitchPreference(
                            title = "液态玻璃",
                            summary = "启用悬浮底栏的液态玻璃效果",
                            checked = asBundle.floatingBottomBar && asBundle.blur
                                    && asBundle.floatingBottomBarBlur,
                            onCheckedChange = {
                                asBundle = asBundle.copy(
                                    floatingBottomBarBlur = it
                                )
                            }
                        )
                    }
                }
                SwitchPreference(
                    title = "平滑圆角",
                    summary = "启用全局平滑圆角效果",
                    checked = asBundle.smoothCorner,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            smoothCorner = it
                        )
                    }
                )
            }
        }

        item {
            SectionSmallTitle("投屏")
            Card {
                SwitchPreference(
                    title = "低延迟音频（实验性）",
                    summary =
                        """
                            启用后将尝试使用低延迟音频路径
                            推荐配合 RAW PCM 编解码
                            修改后建议划卡重启应用
                        """.trimIndent(),
                    enabled = !isScrcpyStreaming,
                    checked = asBundle.lowLatency,
                    onCheckedChange = {
                        if (!isScrcpyStreaming)
                            asBundle = asBundle.copy(
                                lowLatency = it
                            )
                    },
                )
                SwitchPreference(
                    title = "启用调试信息",
                    summary = "在全屏界面悬浮显示分辨率、帧率和触点信息",
                    checked = asBundle.fullscreenDebugInfo,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            fullscreenDebugInfo = it
                        )
                    },
                )
                SwitchPreference(
                    title = "设备页隐藏简单设置项",
                    summary = "启用后设备页仅保留更多参数、所有应用、最近任务和启动/停止按钮",
                    checked = asBundle.hideSimpleConfigItems,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            hideSimpleConfigItems = it
                        )
                    },
                )
                SuperSlider(
                    title = "预览卡高度",
                    summary = "设备页预览卡高度",
                    value = asBundle.devicePreviewCardHeightDp.toFloat(),
                    onValueChange = {
                        asBundle = asBundle.copy(
                            devicePreviewCardHeightDp =
                                it.roundToInt().coerceAtLeast(120)
                        )
                    },
                    valueRange = 160f..600f,
                    steps = 600 - 160 - 1,
                    unit = "dp",
                    displayFormatter = { it.roundToInt().toString() },
                    inputInitialValue = asBundle.devicePreviewCardHeightDp.toString(),
                    inputFilter = { it.filter(Char::isDigit) },
                    inputValueRange = 120f..UShort.MAX_VALUE.toFloat(),
                    onInputConfirm = { input ->
                        input.toIntOrNull()?.let {
                            asBundle = asBundle.copy(
                                devicePreviewCardHeightDp = it.coerceAtLeast(120)
                            )
                        }
                    },
                )
                ArrowPreference(
                    title = "快速设备排序",
                    summary = "手动排序设备页的快速设备",
                    onClick = onOpenReorderDevices,
                )
                ArrowPreference(
                    title = "虚拟按钮排序",
                    summary = "手动排序预览/全屏时的虚拟按钮，并选择哪些按钮展示在外",
                    onClick = { navigator.push(RootScreen.VirtualButtonOrder) },
                )
                ArrowPreference(
                    title = "锁屏密码自动填充",
                    summary = "管理用于自动填充的锁屏密码",
                    onClick = {
                        context.startActivity(LockscreenPasswordActivity.createIntent(context))
                    },
                )
                SwitchPreference(
                    title = "实时同步剪贴板到受控机",
                    summary =
                        """
                            本机剪贴板更新后会自动同步到受控机
                            禁用后需要使用虚拟按钮中的粘贴才能粘贴本机内容
                            MIUI 完全不允许后台监听剪贴板，因此该选项在小米设备上可能无效
                        """.trimIndent(),
                    checked = asBundle.realtimeClipboardSyncToDevice,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            realtimeClipboardSyncToDevice = it
                        )
                    },
                )
            }
        }

        item {
            SectionSmallTitle("全屏")
            Card {
                SwitchPreference(
                    title = "全屏时不跟随系统旋转锁定",
                    summary = "启用后使用传感器方向，忽略系统自动旋转锁定状态",
                    checked = asBundle.fullscreenControlIgnoreSystemRotationLock,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            fullscreenControlIgnoreSystemRotationLock = it
                        )
                    },
                )
                SwitchPreference(
                    title = "全屏时返回键发送到远程",
                    summary =
                        """
                            启用后系统返回键会发送给设备，不再退出全屏控制页
                            此时退出全屏需要回到桌面通过图标重新进入应用
                        """.trimIndent(),
                    checked = asBundle.fullscreenControlBackToDevice,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            fullscreenControlBackToDevice = it
                        )
                    },
                )
                SwitchPreference(
                    title = "全屏时显示虚拟按钮",
                    summary = "在全屏控制页中显示返回键、主页键等虚拟按钮",
                    checked = asBundle.showFullscreenVirtualButtons,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            showFullscreenVirtualButtons = it
                        )
                    },
                )
                AnimatedVisibility(asBundle.showFullscreenVirtualButtons) {
                    Column {
                        OverlayDropdownPreference(
                            entries = listOf(
                                DropdownEntry(
                                    items = FullscreenVirtualButtonDock.modeItems.map { DropdownItem(it) },
                                    selectedIndex = fullscreenVirtualButtonDock.modeIndex,
                                    onSelectedIndexChange = { modeIndex ->
                                        asBundle = asBundle.copy(
                                            fullscreenVirtualButtonDock = FullscreenVirtualButtonDock
                                                .fromModeAndDirection(
                                                    modeIndex = modeIndex,
                                                    directionIndex = fullscreenVirtualButtonDock.directionIndex,
                                                )
                                                .toStoredValue()
                                        )
                                    },
                                ),
                                DropdownEntry(
                                    items = FullscreenVirtualButtonDock.directionItems.map { DropdownItem(it) },
                                    selectedIndex = fullscreenVirtualButtonDock.directionIndex,
                                    onSelectedIndexChange = { directionIndex ->
                                        asBundle = asBundle.copy(
                                            fullscreenVirtualButtonDock = FullscreenVirtualButtonDock
                                                .fromModeAndDirection(
                                                    modeIndex = fullscreenVirtualButtonDock.modeIndex,
                                                    directionIndex = directionIndex,
                                                )
                                                .toStoredValue()
                                        )
                                    },
                                ),
                            ),
                            title = "虚拟按钮方向",
                            summary = fullscreenVirtualButtonDock.summary,
                        )
                        SuperSlider(
                            title = "虚拟按钮高度",
                            value = asBundle.fullscreenVirtualButtonHeightDp.toFloat(),
                            onValueChange = {
                                asBundle = asBundle.copy(
                                    fullscreenVirtualButtonHeightDp =
                                        it.roundToInt().coerceIn(16, 80)
                                )
                            },
                            valueRange = 16f..80f,
                            steps = 80 - 16 - 1,
                            unit = "dp",
                            displayFormatter = { it.roundToInt().toString() },
                            inputInitialValue = asBundle.fullscreenVirtualButtonHeightDp.toString(),
                            inputFilter = { it.filter(Char::isDigit) },
                            inputValueRange = 1f..160f,
                            onInputConfirm = { input ->
                                input.toIntOrNull()?.let {
                                    asBundle = asBundle.copy(
                                        fullscreenVirtualButtonHeightDp =
                                            it.coerceIn(1, 160)
                                    )
                                }
                            },
                        )
                    }
                }
                SwitchPreference(
                    title = "全屏时显示悬浮球",
                    summary = "在全屏控制页中显示可拖动的悬浮球，点击后弹出完整虚拟按键菜单",
                    checked = asBundle.showFullscreenFloatingButton,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            showFullscreenFloatingButton = it
                        )
                    },
                )
                AnimatedVisibility(asBundle.showFullscreenFloatingButton) {
                    Column {
                        SuperSlider(
                            title = "悬浮球尺寸",
                            value = asBundle.fullscreenFloatingButtonSizeDp.toFloat(),
                            onValueChange = {
                                asBundle = asBundle.copy(
                                    fullscreenFloatingButtonSizeDp =
                                        it.roundToInt().coerceIn(32, 64)
                                )
                            },
                            valueRange = 32f..64f,
                            steps = 64 - 32 - 1,
                            unit = "dp",
                            displayFormatter = { it.roundToInt().toString() },
                            inputInitialValue = asBundle.fullscreenFloatingButtonSizeDp.toString(),
                            inputFilter = { it.filter(Char::isDigit) },
                            inputValueRange = 16f..96f,
                            onInputConfirm = { input ->
                                input.toIntOrNull()?.let {
                                    asBundle = asBundle.copy(
                                        fullscreenFloatingButtonSizeDp =
                                            it.coerceIn(16, 96)
                                    )
                                }
                            },
                        )
                        SuperSlider(
                            title = "悬浮球背景透明度",
                            value = asBundle.fullscreenFloatingButtonBackgroundAlphaPercent.toFloat(),
                            onValueChange = {
                                asBundle = asBundle.copy(
                                    fullscreenFloatingButtonBackgroundAlphaPercent =
                                        it.roundToInt().coerceIn(10, 100)
                                )
                            },
                            valueRange = 10f..100f,
                            steps = 100 - 10 - 1,
                            unit = "%",
                            displayFormatter = { it.roundToInt().toString() },
                            inputInitialValue = asBundle.fullscreenFloatingButtonBackgroundAlphaPercent.toString(),
                            inputFilter = { it.filter(Char::isDigit) },
                            inputValueRange = 10f..100f,
                            onInputConfirm = { input ->
                                input.toIntOrNull()?.let {
                                    asBundle = asBundle.copy(
                                        fullscreenFloatingButtonBackgroundAlphaPercent =
                                            it.coerceIn(10, 100)
                                    )
                                }
                            },
                        )
                        SuperSlider(
                            title = "悬浮球白环透明度",
                            value = asBundle.fullscreenFloatingButtonRingAlphaPercent.toFloat(),
                            onValueChange = {
                                asBundle = asBundle.copy(
                                    fullscreenFloatingButtonRingAlphaPercent =
                                        it.roundToInt().coerceIn(0, 100)
                                )
                            },
                            valueRange = 0f..100f,
                            steps = 100 - 0 - 1,
                            unit = "%",
                            displayFormatter = { it.roundToInt().toString() },
                            inputInitialValue = asBundle.fullscreenFloatingButtonRingAlphaPercent.toString(),
                            inputFilter = { it.filter(Char::isDigit) },
                            inputValueRange = 0f..100f,
                            onInputConfirm = { input ->
                                input.toIntOrNull()?.let {
                                    asBundle = asBundle.copy(
                                        fullscreenFloatingButtonRingAlphaPercent =
                                            it.coerceIn(0, 100)
                                    )
                                }
                            },
                        )
                    }
                }
                SwitchPreference(
                    title = "全屏兼容模式",
                    summary = "启用后全屏控制页不再跨 Activity，会导致画中画不可用",
                    checked = asBundle.fullscreenCompatibilityMode,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            fullscreenCompatibilityMode = it
                        )
                    },
                )
            }
        }

        item {
            SectionSmallTitle("scrcpy-server")
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
                            text = "自定义 binary",
                            fontWeight = FontWeight.Medium,
                        )
                        TextField(
                            value = asBundle.customServerUri,
                            onValueChange = {},
                            readOnly = true,
                            label = Scrcpy.DEFAULT_SERVER_ASSET_NAME,
                            useLabelAsPlaceholder = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                Row(
                                    modifier = Modifier
                                        .padding(end = UiSpacing.Medium),
                                ) {
                                    if (asBundle.customServerUri.isNotBlank())
                                        IconButton(
                                            onClick = {
                                                asBundle = asBundle.copy(
                                                    customServerUri = "",
                                                    customServerVersion = "",
                                                )
                                            },
                                        ) {
                                            Icon(
                                                Icons.Rounded.Clear,
                                                contentDescription = "清空",
                                            )
                                        }
                                    IconButton(onClick = serverPicker.pick) {
                                        Icon(
                                            Icons.Rounded.FileOpen,
                                            contentDescription = "选择文件",
                                        )
                                    }
                                }
                            },
                        )
                    }
                    AnimatedVisibility(customServerVersionShowInput) {
                        Column(
                            modifier = Modifier.padding(horizontal = UiSpacing.Large),
                            verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                        ) {
                            Text(
                                text = "自定义 binary version",
                                fontWeight = FontWeight.Medium,
                            )
                            SuperTextField(
                                value = customServerVersionInput,
                                onValueChange = { customServerVersionInput = it },
                                onFocusLost = {
                                    if (customServerVersionInput == AppSettings.CUSTOM_SERVER_VERSION.defaultValue)
                                        customServerVersionInput = ""
                                    asBundle = asBundle.copy(
                                        customServerVersion = customServerVersionInput
                                            .ifBlank { AppSettings.CUSTOM_SERVER_VERSION.defaultValue }
                                    )
                                },
                                label = Scrcpy.DEFAULT_SERVER_VERSION,
                                useLabelAsPlaceholder = true,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = UiSpacing.Large),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                    ) {
                        Text(
                            text = "Remote Path",
                            fontWeight = FontWeight.Medium,
                        )
                        SuperTextField(
                            value = serverRemotePathInput,
                            onValueChange = { serverRemotePathInput = it },
                            onFocusLost = {
                                if (serverRemotePathInput == AppSettings.SERVER_REMOTE_PATH.defaultValue)
                                    serverRemotePathInput = ""
                                asBundle = asBundle.copy(
                                    serverRemotePath = serverRemotePathInput
                                        .ifBlank { AppSettings.SERVER_REMOTE_PATH.defaultValue }
                                )
                            },
                            label = Scrcpy.DEFAULT_REMOTE_PATH,
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        item {
            SectionSmallTitle("ADB")
            Card {
                ArrowPreference(
                    title = "调整后台电池策略",
                    summary =
                        """
                            解决 Scrcpy 切换到后台时无法联网导致 ADB 断连
                            应用的电池使用情况 -> 允许后台使用 -> 无限制
                            国产ROM魔改的电源设置一般都可在对应的魔改应用设置中找到
                        """.trimIndent(),
                    onClick = {
                        val appInfoArgs = android.os.Bundle().apply {
                            putString("package", context.packageName)
                            putInt("uid", context.applicationInfo.uid)
                        }
                        val appDetailsIntent = Intent(Intent.ACTION_MAIN).apply {
                            setClassName(
                                "com.android.settings",
                                "com.android.settings.SubSettings"
                            )
                            putExtra(
                                ":settings:show_fragment",
                                "com.android.settings.applications.appinfo.AppInfoDashboardFragment"
                            )
                            putExtra(
                                ":settings:show_fragment_title",
                                "应用信息"
                            )
                            putExtra(":settings:show_fragment_args", appInfoArgs)
                            putExtra("package", context.packageName)
                            putExtra("uid", context.applicationInfo.uid)
                            putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
                        }
                        val requestIntent = Intent(
                            AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            "package:${context.packageName}".toUri()
                        )
                        val fallbackIntent = Intent(
                            AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                        )
                        runCatching { context.startActivity(appDetailsIntent) }
                            .recoverCatching { context.startActivity(requestIntent) }
                            .recoverCatching { context.startActivity(fallbackIntent) }
                            .onFailure { snackbar.show("无法打开设置") }
                    },
                )
                Column(
                    modifier = Modifier.padding(vertical = UiSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = UiSpacing.Large),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                    ) {
                        Text(
                            text = "自定义 ADB 密钥名",
                            fontWeight = FontWeight.Medium,
                        )
                        SuperTextField(
                            value = adbKeyNameInput,
                            onValueChange = { adbKeyNameInput = it },
                            onFocusLost = {
                                if (adbKeyNameInput == AppSettings.ADB_KEY_NAME.defaultValue)
                                    adbKeyNameInput = ""
                                asBundle = asBundle.copy(
                                    adbKeyName = adbKeyNameInput
                                        .ifBlank { AppSettings.ADB_KEY_NAME.defaultValue }
                                )
                            },
                            label = AppSettings.ADB_KEY_NAME.defaultValue,
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                SwitchPreference(
                    title = "配对时自动启用发现服务",
                    summary = "打开配对弹窗后自动搜索可用配对端口",
                    checked = asBundle.adbPairingAutoDiscoverOnDialogOpen,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            adbPairingAutoDiscoverOnDialogOpen = it
                        )
                    },
                )
                SwitchPreference(
                    title = "自动重连已配对设备",
                    summary = "自动发现开启无线调试的设备，更新快速设备的随机端口并尝试连接（效果比较随缘）",
                    checked = asBundle.adbAutoReconnectPairedDevice,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            adbAutoReconnectPairedDevice = it
                        )
                    },
                )
                SwitchPreference(
                    title = "连接后自动获取应用列表",
                    summary = "ADB 连接成功后立刻执行 --list-apps，用于补全最近任务列表应用名",
                    checked = asBundle.adbAutoLoadAppListOnConnect,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            adbAutoLoadAppListOnConnect = it
                        )
                        if (it) snackbar.show(
                            "--list-apps 操作可能非常耗时（特别是在息屏状态下），启用后可能导致连接设备后阻塞过久！"
                        )
                    },
                )
            }
        }

        item {
            SectionSmallTitle("终端")
            Card {
                SuperSlider(
                    title = "终端字号",
                    summary = "也可以在终端上双指缩放调整",
                    value = asBundle.terminalFontSizeSp,
                    onValueChange = {
                        asBundle = asBundle.copy(
                            terminalFontSizeSp = it.roundToInt().toFloat()
                        )
                    },
                    valueRange = 1f..32f,
                    steps = 32 - 1 - 1,
                    unit = "sp",
                    displayFormatter = { it.roundToInt().toString() },
                    inputInitialValue = asBundle.terminalFontSizeSp.roundToInt().toString(),
                    inputFilter = { input -> input.filter(Char::isDigit) },
                    inputValueRange = 1f..32f,
                    onInputConfirm = { input ->
                        input.toIntOrNull()?.let {
                            asBundle = asBundle.copy(
                                terminalFontSizeSp = it.coerceIn(1, 32).toFloat()
                            )
                        }
                    },
                )
                Column(
                    modifier = Modifier.padding(vertical = UiSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = UiSpacing.Large),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                    ) {
                        Text(
                            text = "自定义终端字体",
                            fontWeight = FontWeight.Medium,
                        )
                        TextField(
                            value = asBundle.terminalFontDisplayName,
                            onValueChange = {},
                            readOnly = true,
                            label = "内置等宽字体",
                            useLabelAsPlaceholder = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                Row(modifier = Modifier.padding(end = UiSpacing.Medium)) {
                                    if (asBundle.terminalFontDisplayName.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    val cleared = clearTerminalFont(context)
                                                    asBundle = asBundle.copy(
                                                        terminalFontDisplayName = ""
                                                    )
                                                    snackbar.show(
                                                        if (cleared) "已恢复默认终端字体"
                                                        else "当前没有可清除的自定义字体"
                                                    )
                                                }
                                            },
                                        ) {
                                            Icon(
                                                Icons.Rounded.Clear,
                                                contentDescription = "清空",
                                            )
                                        }
                                    }
                                    IconButton(onClick = terminalFontPicker.pick) {
                                        Icon(
                                            Icons.Rounded.FileOpen,
                                            contentDescription = "选择字体",
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        item {
            SectionSmallTitle("杂项")
            Card {
                SwitchPreference(
                    title = "退出应用时清除日志",
                    summary = "双击返回退出应用时清除日志",
                    checked = asBundle.clearLogsOnExit,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            clearLogsOnExit = it
                        )
                    },
                )
                SwitchPreference(
                    title = "隐藏设备页日志框",
                    summary = "隐藏设备页最下方的日志框",
                    checked = asBundle.hideDeviceLogs,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            hideDeviceLogs = it
                        )
                    },
                )
            }
        }

        item {
            SectionSmallTitle("")
            Card {
                ArrowPreference(
                    title = "关于",
                    summary = updateSummary,
                    onClick = { navigator.push(RootScreen.About) },
                )
            }
        }
    }
}
