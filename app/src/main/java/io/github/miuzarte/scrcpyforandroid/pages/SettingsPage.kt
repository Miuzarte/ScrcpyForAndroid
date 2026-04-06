package io.github.miuzarte.scrcpyforandroid.pages

import android.content.Intent
import android.os.Process
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.net.toUri
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.scaffolds.AppPageLazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperSlider
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperTextField
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.PreferenceMigration
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import io.github.miuzarte.scrcpyforandroid.widgets.SectionSmallTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import kotlin.math.roundToInt
import kotlin.system.exitProcess

private data class ThemeModeOption(
    val label: String,
    val mode: ColorSchemeMode,
)

private val THEME_BASE_OPTIONS = listOf(
    ThemeModeOption("跟随系统", ColorSchemeMode.System),
    ThemeModeOption("浅色", ColorSchemeMode.Light),
    ThemeModeOption("深色", ColorSchemeMode.Dark),
)

fun resolveThemeMode(baseIndex: Int, monet: Boolean): ColorSchemeMode {
    return when (baseIndex.coerceIn(0, 2)) {
        0 -> if (monet) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
        1 -> if (monet) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
        else -> if (monet) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
    }
}

private fun resolveThemeLabel(baseIndex: Int, monetEnabled: Boolean): String {
    val base = THEME_BASE_OPTIONS.getOrNull(baseIndex.coerceIn(0, 2))?.label ?: "跟随系统"
    return if (monetEnabled) "Monet（$base）" else base
}

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenReorderDevices: () -> Unit,
    onOpenVirtualButtonOrder: () -> Unit,
    onPickServer: () -> Unit,
    scrollBehavior: ScrollBehavior,
) {
    val appContext = LocalContext.current.applicationContext
    val appSettings = Storage.appSettings
    var needMigration by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        needMigration = PreferenceMigration(appContext).needsMigration()
    }

    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    val snackHostState = remember { SnackbarHostState() }

    val baseModeItems = THEME_BASE_OPTIONS.map { it.label }

    // 主题
    var themeBaseIndex by appSettings.themeBaseIndex.asMutableState()
    var monet by appSettings.monet.asMutableState()

    // 投屏
    var fullscreenDebugInfo by appSettings.fullscreenDebugInfo.asMutableState()
    var keepScreenOnWhenStreaming by appSettings.keepScreenOnWhenStreaming.asMutableState()
    var devicePreviewCardHeightDp by appSettings.devicePreviewCardHeightDp.asMutableState()
    var showFullscreenVirtualButtons by appSettings.showFullscreenVirtualButtons.asMutableState()

    // scrcpy-server
    var customServerUri by appSettings.customServerUri.asMutableState()
    var serverRemotePath by appSettings.serverRemotePath.asMutableState()
    var serverRemotePathInput by rememberSaveable {
        mutableStateOf(
            // 默认值留空显示为 hint
            if (runBlocking { appSettings.serverRemotePath.isDefaultValue() }) ""
            else serverRemotePath
        )
    }

    // ADB
    var adbKeyName by appSettings.adbKeyName.asMutableState()
    var adbKeyNameInput by rememberSaveable {
        mutableStateOf(
            if (runBlocking { appSettings.adbKeyName.isDefaultValue() }) ""
            else adbKeyName
        )
    }
    var adbPairingAutoDiscoverOnDialogOpen by appSettings.adbPairingAutoDiscoverOnDialogOpen.asMutableState()
    var adbAutoReconnectPairedDevice by appSettings.adbAutoReconnectPairedDevice.asMutableState()

    // 设置
    AppPageLazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
    ) {
        item {
            SectionSmallTitle("主题")
            Card {
                SuperDropdown(
                    title = "外观模式",
                    summary = resolveThemeLabel(themeBaseIndex, monet),
                    items = baseModeItems,
                    selectedIndex = themeBaseIndex.coerceIn(0, baseModeItems.lastIndex),
                    onSelectedIndexChange = { themeBaseIndex = it },
                )
                SuperSwitch(
                    title = "Monet",
                    summary = "开启后使用 Monet 动态配色",
                    checked = monet,
                    onCheckedChange = { monet = it },
                )
            }

            SectionSmallTitle("投屏")
            Card {
                SuperSwitch(
                    title = "启用调试信息",
                    summary = "在全屏界面显示触点数量、设备分辨率和实时 FPS",
                    checked = fullscreenDebugInfo,
                    onCheckedChange = { fullscreenDebugInfo = it },
                )
                SuperSwitch(
                    title = "投屏时保持屏幕常亮",
                    summary = "Scrcpy 启动后保持本机屏幕常亮，避免锁屏导致 ADB 断开",
                    checked = keepScreenOnWhenStreaming,
                    onCheckedChange = { keepScreenOnWhenStreaming = it },
                )
                SuperSlider(
                    title = "预览卡高度",
                    summary = "设备页预览卡高度",
                    value = devicePreviewCardHeightDp.toFloat(),
                    onValueChange = {
                        devicePreviewCardHeightDp = it.roundToInt().coerceAtLeast(120)
                    },
                    valueRange = 160f..600f,
                    steps = 600-160-1,
                    unit = "dp",
                    displayFormatter = { it.roundToInt().toString() },
                    inputInitialValue = devicePreviewCardHeightDp.toString(),
                    inputFilter = { it.filter(Char::isDigit) },
                    inputValueRange = 120f..UShort.MAX_VALUE.toFloat(),
                    onInputConfirm = { input ->
                        input.toIntOrNull()?.let {
                            devicePreviewCardHeightDp = it.coerceAtLeast(120)
                        }
                    },
                )
                SuperArrow(
                    title = "快速设备排序",
                    summary = "手动排序设备页的快速设备",
                    onClick = onOpenReorderDevices,
                )
                SuperArrow(
                    title = "虚拟按钮排序",
                    summary = "手动排序预览/全屏时的虚拟按钮，并选择哪些按钮展示在外",
                    onClick = onOpenVirtualButtonOrder,
                )
                SuperSwitch(
                    title = "全屏显示虚拟按钮",
                    summary = "在全屏控制页底部显示返回键、主页键等虚拟按钮",
                    checked = showFullscreenVirtualButtons,
                    onCheckedChange = { showFullscreenVirtualButtons = it },
                )
            }

            SectionSmallTitle("scrcpy-server")
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
                    value = customServerUri,
                    onValueChange = {},
                    readOnly = true,
                    label = "scrcpy-server-v3.3.4",
                    useLabelAsPlaceholder = customServerUri.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.CardContent)
                        .padding(bottom = UiSpacing.CardContent),
                    trailingIcon = {
                        Row(modifier = Modifier.padding(end = UiSpacing.SectionTitleLeadingGap)) {
                            if (customServerUri.isNotBlank())
                                IconButton(onClick = { customServerUri = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "清空")
                                }
                            IconButton(onClick = onPickServer) {
                                Icon(Icons.Rounded.FileOpen, contentDescription = "选择文件")
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
                SuperTextField(
                    value = serverRemotePathInput,
                    onValueChange = { serverRemotePathInput = it },
                    onFocusLost = {
                        if (serverRemotePathInput == AppSettings.SERVER_REMOTE_PATH.defaultValue)
                            serverRemotePathInput = ""
                    },
                    label = AppSettings.SERVER_REMOTE_PATH.defaultValue,
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.CardContent)
                        .padding(bottom = UiSpacing.CardContent),
                )
            }

            SectionSmallTitle("ADB")
            Card {
                Text(
                    text = "自定义 ADB 密钥名",
                    modifier = Modifier
                        .padding(horizontal = UiSpacing.CardTitle)
                        .padding(top = UiSpacing.CardContent, bottom = UiSpacing.FieldLabelBottom),
                    fontWeight = FontWeight.Medium,
                )
                SuperTextField(
                    value = adbKeyNameInput,
                    onValueChange = { adbKeyNameInput = it },
                    onFocusLost = {
                        if (adbKeyNameInput == AppSettings.ADB_KEY_NAME.defaultValue)
                            adbKeyNameInput = ""
                    },
                    label = AppSettings.ADB_KEY_NAME.defaultValue,
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.CardContent)
                        .padding(bottom = UiSpacing.CardContent),
                )
                SuperSwitch(
                    title = "配对时自动启用发现服务",
                    summary = "打开配对弹窗后自动搜索可用配对端口",
                    checked = adbPairingAutoDiscoverOnDialogOpen,
                    onCheckedChange = { adbPairingAutoDiscoverOnDialogOpen = it },
                )
                SuperSwitch(
                    title = "自动重连已配对设备",
                    summary = "自动发现开启无线调试的设备，更新快速设备的随机端口并尝试连接（效果比较随缘）",
                    checked = adbAutoReconnectPairedDevice,
                    onCheckedChange = { adbAutoReconnectPairedDevice = it },
                )
            }

            // 这部分应该不会显示出来,
            // 应用启动时就会执行迁移与旧数据的删除
            AnimatedVisibility(needMigration) {
                SectionSmallTitle("应用")
            }
            AnimatedVisibility(needMigration) {
                Card {
                    SuperArrow(
                        title = "恢复旧版本配置",
                        summary = "从旧版本的 SharedPreferences 恢复至 DataStore",
                        onClick = {
                            scope.launch {
                                val migration = PreferenceMigration(appContext)
                                migration.migrate(clearSharedPrefs = false)
                                snackHostState.showSnackbar("迁移完成，应用将重启")

                                delay(1000)

                                val intent = context.packageManager.getLaunchIntentForPackage(
                                    context.packageName
                                )
                                intent?.apply {
                                    addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK
                                                or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    )
                                }
                                context.startActivity(intent)

                                Process.killProcess(Process.myPid())
                                exitProcess(0)
                            }
                        },
                    )
                }
            }

            SectionSmallTitle("关于")
            Card {
                SuperArrow(
                    title = "前往仓库",
                    summary = "github.com/Miuzarte/ScrcpyForAndroid",
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "https://github.com/Miuzarte/ScrcpyForAndroid".toUri(),
                            )
                        )
                    },
                )
            }
        }

        item { Spacer(Modifier.height(UiSpacing.BottomContent)) }
    }
}
