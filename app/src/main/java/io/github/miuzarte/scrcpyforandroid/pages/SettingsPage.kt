package io.github.miuzarte.scrcpyforandroid.pages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import io.github.miuzarte.scrcpyforandroid.constants.AppDefaults
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.scaffolds.AppPageLazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperSlide
import io.github.miuzarte.scrcpyforandroid.widgets.SectionSmallTitle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import kotlin.math.roundToInt

private data class ThemeModeOption(
    val label: String,
    val mode: ColorSchemeMode,
)

private val THEME_BASE_OPTIONS = listOf(
    ThemeModeOption("跟随系统", ColorSchemeMode.System),
    ThemeModeOption("浅色", ColorSchemeMode.Light),
    ThemeModeOption("深色", ColorSchemeMode.Dark),
)

fun resolveThemeMode(baseIndex: Int, monetEnabled: Boolean): ColorSchemeMode {
    return when (baseIndex.coerceIn(0, 2)) {
        0 -> if (monetEnabled) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
        1 -> if (monetEnabled) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
        else -> if (monetEnabled) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
    }
}

private fun resolveThemeLabel(baseIndex: Int, monetEnabled: Boolean): String {
    val base = THEME_BASE_OPTIONS.getOrNull(baseIndex.coerceIn(0, 2))?.label ?: "跟随系统"
    return if (monetEnabled) "Monet（$base）" else base
}

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    themeBaseIndex: Int,
    onThemeBaseIndexChange: (Int) -> Unit,
    monetEnabled: Boolean,
    onMonetEnabledChange: (Boolean) -> Unit,
    fullscreenDebugInfoEnabled: Boolean,
    onFullscreenDebugInfoEnabledChange: (Boolean) -> Unit,
    showFullscreenVirtualButtons: Boolean,
    onShowFullscreenVirtualButtonsChange: (Boolean) -> Unit,
    keepScreenOnWhenStreamingEnabled: Boolean,
    onKeepScreenOnWhenStreamingEnabledChange: (Boolean) -> Unit,
    onOpenReorderDevices: () -> Unit,
    onOpenVirtualButtonOrder: () -> Unit,
    devicePreviewCardHeightDp: Int,
    onDevicePreviewCardHeightDpChange: (Int) -> Unit,
    customServerUri: String?,
    onPickServer: () -> Unit,
    onClearServer: () -> Unit,
    serverRemotePath: String,
    onServerRemotePathChange: (String) -> Unit,
    adbKeyName: String,
    onAdbKeyNameChange: (String) -> Unit,
    scrollBehavior: ScrollBehavior,
) {
    val baseModeItems = THEME_BASE_OPTIONS.map { it.label }

    // 设置
    AppPageLazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
    ) {
        item {
            SectionSmallTitle(text = "主题")
            Card {
                SuperDropdown(
                    title = "外观模式",
                    summary = resolveThemeLabel(themeBaseIndex, monetEnabled),
                    items = baseModeItems,
                    selectedIndex = themeBaseIndex.coerceIn(0, baseModeItems.lastIndex),
                    onSelectedIndexChange = onThemeBaseIndexChange,
                )
                SuperSwitch(
                    title = "Monet",
                    summary = "开启后使用 Monet 动态配色",
                    checked = monetEnabled,
                    onCheckedChange = onMonetEnabledChange,
                )
            }

            SectionSmallTitle(text = "投屏")
            Card {
                SuperSwitch(
                    title = "启用调试信息",
                    summary = "在全屏界面显示触点数量、设备分辨率和实时 FPS",
                    checked = fullscreenDebugInfoEnabled,
                    onCheckedChange = onFullscreenDebugInfoEnabledChange,
                )
                SuperSwitch(
                    title = "全屏显示虚拟按钮",
                    summary = "在全屏模式底部显示返回/主页等虚拟按钮",
                    checked = showFullscreenVirtualButtons,
                    onCheckedChange = onShowFullscreenVirtualButtonsChange,
                )
                SuperSwitch(
                    title = "投屏时不允许息屏",
                    summary = "Scrcpy 启动后保持本机常亮，避免锁屏导致 ADB 断开",
                    checked = keepScreenOnWhenStreamingEnabled,
                    onCheckedChange = onKeepScreenOnWhenStreamingEnabledChange,
                )
                SuperSlide(
                    title = "预览卡高度",
                    summary = "设备页预览卡高度",
                    value = devicePreviewCardHeightDp.toFloat(),
                    onValueChange = { onDevicePreviewCardHeightDpChange(it.roundToInt().coerceAtLeast(120)) },
                    valueRange = 160f..600f,
                    steps = 439,
                    unit = "dp",
                    displayFormatter = { it.roundToInt().toString() },
                    inputInitialValue = devicePreviewCardHeightDp.toString(),
                    inputFilter = { it.filter(Char::isDigit) },
                    inputValueRange = 120f..Float.MAX_VALUE,
                    onInputConfirm = { raw ->
                        raw.toIntOrNull()?.let { onDevicePreviewCardHeightDpChange(it.coerceAtLeast(120)) }
                    },
                )
                SuperArrow(
                    title = "调整设备排序",
                    summary = "调整设备页快捷设备列表顺序",
                    onClick = onOpenReorderDevices,
                )
                SuperArrow(
                    title = "虚拟按钮排序",
                    summary = "调整底部虚拟按钮与更多菜单顺序",
                    onClick = onOpenVirtualButtonOrder,
                )
            }

            SectionSmallTitle(text = "scrcpy-server")
            Card {
                Spacer(modifier = Modifier.padding(top = UiSpacing.CardContent))
                Text(
                    text = "自定义 binary",
                    modifier = Modifier
                        .padding(horizontal = UiSpacing.CardTitle)
                        .padding(bottom = UiSpacing.FieldLabelBottom),
                    fontWeight = FontWeight.Medium,
                )
                TextField(
                    value = customServerUri ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = "scrcpy-server-v3.3.4",
                    useLabelAsPlaceholder = customServerUri == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.CardContent)
                        .padding(bottom = UiSpacing.CardContent),
                    trailingIcon = {
                        Row(modifier = Modifier.padding(end = UiSpacing.SectionTitleLeadingGap)) {
                            if (customServerUri != null) IconButton(onClick = onClearServer) {
                                Icon(Icons.Default.Clear, contentDescription = "清空")
                            }
                            IconButton(onClick = onPickServer) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "选择文件")
                            }
                        }
                    },
                )
                Text(
                    text = "Remote Path",
                    modifier = Modifier
                        .padding(horizontal = UiSpacing.CardTitle)
                        .padding(bottom = UiSpacing.FieldLabelBottom),
                    fontWeight = FontWeight.Medium,
                )
                TextField(
                    value = serverRemotePath,
                    onValueChange = onServerRemotePathChange,
                    label = AppDefaults.DefaultServerRemotePath,
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.CardContent)
                        .padding(bottom = UiSpacing.CardContent),
                )
            }

            SectionSmallTitle(text = "ADB")
            Card {
                Text(
                    text = "自定义 ADB 密钥名",
                    modifier = Modifier
                        .padding(horizontal = UiSpacing.CardTitle)
                        .padding(top = UiSpacing.CardContent, bottom = UiSpacing.FieldLabelBottom),
                    fontWeight = FontWeight.Medium,
                )
                TextField(
                    value = adbKeyName,
                    onValueChange = onAdbKeyNameChange,
                    label = AppDefaults.DefaultAdbKeyName,
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.CardContent)
                        .padding(bottom = UiSpacing.CardContent),
                )
            }
        }
    }
}
