package io.github.miuzarte.scrcpyforandroid.pages

import android.content.Intent
import android.os.Process
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.miuzarte.scrcpyforandroid.BuildConfig
import io.github.miuzarte.scrcpyforandroid.constants.ThemeModes
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperSlider
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperTextField
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.services.AppUpdateChecker
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.PreferenceMigration
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.widgets.SectionSmallTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import kotlin.math.roundToInt
import kotlin.system.exitProcess

@Composable
fun SettingsScreen(
    scrollBehavior: ScrollBehavior,
    onOpenReorderDevices: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = "设置",
                scrollBehavior = scrollBehavior,
            )
        },
    ) { pagePadding ->
        SettingsPage(
            contentPadding = pagePadding,
            scrollBehavior = scrollBehavior,
            onOpenReorderDevices = onOpenReorderDevices,
        )
    }
}

@Composable
fun SettingsPage(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onOpenReorderDevices: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var needMigration by remember { mutableStateOf(false) }
    val updateState by AppUpdateChecker.state.collectAsState()
    LaunchedEffect(Unit) {
        needMigration = PreferenceMigration(appContext).needsMigration()
    }

    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val scope = rememberCoroutineScope()

    val snackbar = LocalSnackbarController.current
    val navigator = LocalRootNavigator.current
    val serverPicker = LocalServerPicker.current
    val floatingWindowPermissionRequester = LocalFloatingWindowPermissionRequester.current
    val isFloatingWindowPermissionGranted by floatingWindowPermissionRequester.permissionGrantedState
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, floatingWindowPermissionRequester) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                floatingWindowPermissionRequester.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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

    // 设置
    LazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
    ) {
        item {
            SectionSmallTitle("主题")
            Card {
                OverlayDropdownPreference(
                    title = "外观模式",
                    summary = ThemeModes.baseOptions
                        .getOrNull(
                            asBundle.themeBaseIndex.coerceIn(
                                0,
                                ThemeModes.baseOptions.lastIndex
                            )
                        )
                        ?.label
                        ?: "跟随系统",
                    items = themeItems,
                    selectedIndex = asBundle.themeBaseIndex.coerceIn(
                        0,
                        ThemeModes.baseOptions.lastIndex
                    ),
                    onSelectedIndexChange = {
                        asBundle = asBundle.copy(themeBaseIndex = it)
                    },
                )
                SwitchPreference(
                    title = "Monet",
                    summary = "开启后使用 Monet 动态配色",
                    checked = asBundle.monet,
                    onCheckedChange = {
                        asBundle = asBundle.copy(monet = it)
                    },
                )
            }
        }

        item {
            SectionSmallTitle("投屏")
            Card {
                SwitchPreference(
                    title = "启用调试信息",
                    summary = "在全屏界面悬浮显示分辨率、帧率和触点信息",
                    checked = asBundle.fullscreenDebugInfo,
                    onCheckedChange = {
                        asBundle = asBundle.copy(fullscreenDebugInfo = it)
                    },
                )
                SuperSlider(
                    title = "预览卡高度",
                    summary = "设备页预览卡高度",
                    value = asBundle.devicePreviewCardHeightDp.toFloat(),
                    onValueChange = {
                        asBundle = asBundle.copy(
                            devicePreviewCardHeightDp = it.roundToInt().coerceAtLeast(120)
                        )
                    },
                    valueRange = 160f..600f,
                    steps = 600 - 160 - 2,
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
                SwitchPreference(
                    title = "全屏显示虚拟按钮",
                    summary = "在全屏控制页底部显示返回键、主页键等虚拟按钮",
                    checked = asBundle.showFullscreenVirtualButtons,
                    onCheckedChange = {
                        asBundle = asBundle.copy(showFullscreenVirtualButtons = it)
                    },
                )
                SwitchPreference(
                    title = "全屏显示悬浮球",
                    summary = "在全屏控制页显示可拖动的悬浮球，点击后弹出完整虚拟按键菜单",
                    checked = asBundle.showFullscreenFloatingButton,
                    onCheckedChange = {
                        asBundle = asBundle.copy(showFullscreenFloatingButton = it)
                    },
                )
                ArrowPreference(
                    title = "系统悬浮窗权限",
                    summary =
                        if (isFloatingWindowPermissionGranted) "已授予"
                        else "未授予",
                    onClick = floatingWindowPermissionRequester.request,
                )
                SuperSlider(
                    title = "悬浮窗最小宽度",
                    summary = "系统悬浮窗宽度基准，按受控机分辨率自动等比计算小窗尺寸",
                    value = asBundle.floatingOverlayMinWidthDp.toFloat(),
                    onValueChange = {
                        asBundle = asBundle.copy(
                            floatingOverlayMinWidthDp = it.roundToInt().coerceIn(100, 300)
                        )
                    },
                    valueRange = 100f..300f,
                    steps = 300 - 100 - 2,
                    unit = "dp",
                    displayFormatter = { it.roundToInt().toString() },
                    inputInitialValue = asBundle.floatingOverlayMinWidthDp.toString(),
                    inputFilter = { it.filter(Char::isDigit) },
                    inputValueRange = 32f..UShort.MAX_VALUE.toFloat(),
                    onInputConfirm = { input ->
                        input.toIntOrNull()?.let {
                            asBundle = asBundle.copy(
                                floatingOverlayMinWidthDp = it.coerceAtLeast(32)
                            )
                        }
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
            }
        }

        if (needMigration) item {
            // 这部分应该不会显示出来,
            // 应用启动时就会执行迁移与旧数据的删除
            SectionSmallTitle("应用")
            Card {
                ArrowPreference(
                    title = "恢复旧版本配置",
                    summary = "从旧版本的 SharedPreferences 恢复至 DataStore",
                    onClick = {
                        scope.launch {
                            val migration = PreferenceMigration(appContext)
                            migration.migrate(clearSharedPrefs = false)
                            snackbar.show("迁移完成，应用将重启")

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

        item { Spacer(Modifier.height(UiSpacing.PageBottom)) }
    }
}
