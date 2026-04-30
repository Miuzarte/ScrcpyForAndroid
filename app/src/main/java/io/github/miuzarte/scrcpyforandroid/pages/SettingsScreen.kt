package io.github.miuzarte.scrcpyforandroid.pages

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import io.github.miuzarte.scrcpyforandroid.BuildConfig
import io.github.miuzarte.scrcpyforandroid.LockscreenPasswordActivity
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.nativecore.DirectAdbTransport
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.SectionSmallTitle
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperSlider
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperTextField
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.AppUpdateChecker
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings.FullscreenVirtualButtonDock
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.adbClientData
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.MonetKeyColorOptions
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
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

suspend fun readTextFromUri(context: Context, uri: Uri): String =
    withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Cannot open selected file")
    }

fun queryAdbKeyDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex >= 0 && cursor.moveToFirst()) cursor.getString(columnIndex)
            else null
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
                    title = stringResource(R.string.settings_title),
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

    val navigator = LocalRootNavigator.current
    val serverPicker = LocalServerPicker.current
    val terminalFontPicker = LocalTerminalFontPicker.current
    val isScrcpyStreaming = AppRuntime.scrcpy?.isStarted() == true

    val acBundle by adbClientData.bundleState.collectAsState()

    val asBundleShared by appSettings.bundleState.collectAsState()
    val asBundleSharedLatest by rememberUpdatedState(asBundleShared)
    var asBundle by rememberSaveable(asBundleShared) { mutableStateOf(asBundleShared) }
    val asBundleLatest by rememberUpdatedState(asBundle)
    LaunchedEffect(asBundleShared) {
        if (asBundle != asBundleShared)
            asBundle = asBundleShared
    }
    LaunchedEffect(asBundle) {
        delay(Settings.BUNDLE_SAVE_DELAY)
        if (asBundle != asBundleSharedLatest)
            appSettings.saveBundle(asBundle)
    }
    DisposableEffect(Unit) {
        onDispose {
            taskScope.launch {
                appSettings.saveBundle(asBundleLatest)
            }
        }
    }

    val themeItems = AppSettings.ThemeModes.baseOptions.map { stringResource(it.labelResId) }

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

    val adbPrivateKeyPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val fileName = queryAdbKeyDisplayName(context, uri)
                    ?.takeIf { it.isNotBlank() }
                    ?: uri.lastPathSegment.orEmpty()
                DirectAdbTransport.importPrivateKey(readTextFromUri(context, uri), fileName)
            }.onSuccess {
                AppRuntime.snackbar(R.string.pref_adb_private_key_imported, it.fingerprint)
            }.onFailure { e ->
                AppRuntime.snackbar(
                    R.string.pref_adb_key_import_failed,
                    e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }
    val adbPublicKeyPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val fileName = queryAdbKeyDisplayName(context, uri)
                    ?.takeIf { it.isNotBlank() }
                    ?: uri.lastPathSegment.orEmpty()
                DirectAdbTransport.importPublicKey(readTextFromUri(context, uri), fileName)
            }.onSuccess {
                AppRuntime.snackbar(R.string.pref_adb_public_key_imported_snackbar, it.fingerprint)
            }.onFailure { e ->
                AppRuntime.snackbar(
                    R.string.pref_adb_key_import_failed,
                    e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    val updateSummary = stringResource(R.string.pref_update_current, BuildConfig.VERSION_NAME) +
            when (val state = updateState) {
                AppUpdateChecker.State.Idle -> ""
                AppUpdateChecker.State.Checking -> stringResource(R.string.pref_update_checking)
                AppUpdateChecker.State.Error -> stringResource(R.string.pref_update_failed)

                is AppUpdateChecker.State.Ready -> when {
                    state.release.hasUpdate ->
                        stringResource(R.string.pref_update_found, state.release.latestVersion)

                    state.release.currentVersion == state.release.latestVersion.removePrefix("v")
                            || state.release.currentVersion == state.release.latestVersion ->
                        stringResource(R.string.pref_update_latest)

                    else -> stringResource(R.string.pref_update_newer, state.release.latestVersion)
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
            SectionSmallTitle(stringResource(R.string.section_theme))
            Card {
                OverlayDropdownPreference(
                    title = stringResource(R.string.pref_title_appearance_mode),
                    summary = stringResource(R.string.pref_summary_appearance_mode),
                    items = themeItems,
                    selectedIndex = asBundle.themeBaseIndex
                        .coerceIn(0, AppSettings.ThemeModes.baseOptions.lastIndex),
                    onSelectedIndexChange = {
                        asBundle = asBundle.copy(
                            themeBaseIndex = it
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_monet),
                    summary = stringResource(R.string.pref_summary_monet),
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
                            title = stringResource(R.string.pref_title_monet_key_color),
                            summary = stringResource(R.string.pref_summary_monet_key_color),
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
                            title = stringResource(R.string.pref_title_monet_palette_style),
                            summary = stringResource(R.string.pref_summary_monet_palette_style),
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
                            title = stringResource(R.string.pref_title_monet_color_spec),
                            summary = stringResource(R.string.pref_summary_monet_color_spec),
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
                    title = stringResource(R.string.pref_title_blur),
                    summary = stringResource(R.string.pref_summary_blur),
                    checked = asBundle.blur,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            blur = it
                        )
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_floating_bottom_bar),
                    summary = stringResource(R.string.pref_summary_floating_bottom_bar),
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
                            title = stringResource(R.string.pref_title_liquid_glass),
                            summary = stringResource(R.string.pref_summary_liquid_glass),
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
                    title = stringResource(R.string.pref_title_smooth_corners),
                    summary = stringResource(R.string.pref_summary_smooth_corners),
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
            SectionSmallTitle(stringResource(R.string.section_screen_mirroring))
            Card {
                SwitchPreference(
                    title = stringResource(R.string.pref_title_low_latency_audio),
                    summary = stringResource(R.string.pref_summary_low_latency_audio),
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
                    title = stringResource(R.string.pref_title_debug_info),
                    summary = stringResource(R.string.pref_summary_debug_info),
                    checked = asBundle.fullscreenDebugInfo,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            fullscreenDebugInfo = it
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_hide_simple_settings),
                    summary = stringResource(R.string.pref_summary_hide_simple_settings),
                    checked = asBundle.hideSimpleConfigItems,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            hideSimpleConfigItems = it
                        )
                    },
                )
                SuperSlider(
                    title = stringResource(R.string.pref_title_preview_card_height),
                    summary = stringResource(R.string.pref_summary_preview_card_height),
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
                    title = stringResource(R.string.pref_title_quick_device_sort),
                    summary = stringResource(R.string.pref_summary_quick_device_sort),
                    onClick = onOpenReorderDevices,
                )
                ArrowPreference(
                    title = stringResource(R.string.pref_title_virtual_button_sort),
                    summary = stringResource(R.string.pref_summary_virtual_button_sort),
                    onClick = { navigator.push(RootScreen.VirtualButtonOrder) },
                )
                ArrowPreference(
                    title = stringResource(R.string.pref_title_password_autofill),
                    summary = stringResource(R.string.pref_summary_password_autofill),
                    onClick = {
                        context.startActivity(LockscreenPasswordActivity.createIntent(context))
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_clipboard_sync),
                    summary = stringResource(R.string.pref_summary_clipboard_sync),
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
            SectionSmallTitle(stringResource(R.string.section_fullscreen))
            Card {
                SwitchPreference(
                    title = stringResource(R.string.pref_title_ignore_rotation_lock),
                    summary = stringResource(R.string.pref_summary_ignore_rotation_lock),
                    checked = asBundle.fullscreenControlIgnoreSystemRotationLock,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            fullscreenControlIgnoreSystemRotationLock = it
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_back_to_device),
                    summary = stringResource(R.string.pref_summary_back_to_device),
                    checked = asBundle.fullscreenControlBackToDevice,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            fullscreenControlBackToDevice = it
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_show_virtual_buttons),
                    summary = stringResource(R.string.pref_summary_show_virtual_buttons),
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
                                    items = FullscreenVirtualButtonDock
                                        .modeItemsResIds.map { DropdownItem(stringResource(it)) },
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
                                    items = FullscreenVirtualButtonDock
                                        .directionItemsResIds.map { DropdownItem(stringResource(it)) },
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
                            title = stringResource(R.string.pref_title_virtual_button_direction),
                            summary = stringResource(
                                if (fullscreenVirtualButtonDock.isFixed) R.string.dock_fixed
                                else R.string.dock_follow
                            ) +
                                    stringResource(R.string.dock_display_on) +
                                    stringResource(fullscreenVirtualButtonDock.directionLabelResId),
                        )
                        SuperSlider(
                            title = stringResource(R.string.pref_title_virtual_button_height),
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
                    title = stringResource(R.string.pref_title_show_floating_button),
                    summary = stringResource(R.string.pref_summary_show_floating_button),
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
                            title = stringResource(R.string.pref_title_floating_button_size),
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
                            title = stringResource(R.string.pref_title_floating_button_bg_opacity),
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
                            title = stringResource(R.string.pref_title_floating_button_ring_opacity),
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
                    title = stringResource(R.string.pref_title_fullscreen_compat_mode),
                    summary = stringResource(R.string.pref_summary_fullscreen_compat_mode),
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
            SectionSmallTitle(stringResource(R.string.section_scrcpy_server))
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
                            text = stringResource(R.string.pref_title_custom_binary),
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
                                        .padding(end = UiSpacing.Medium)
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
                                                imageVector = Icons.Rounded.Clear,
                                                contentDescription = stringResource(R.string.cd_clear),
                                            )
                                        }
                                    IconButton(onClick = serverPicker.pick) {
                                        Icon(
                                            imageVector = Icons.Rounded.FileOpen,
                                            contentDescription = stringResource(R.string.cd_select_file),
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
                                text = stringResource(R.string.pref_title_custom_binary_version),
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
            SectionSmallTitle(stringResource(R.string.section_adb))
            Card {
                val textTitleAppInfo = stringResource(R.string.pref_title_app_info)
                ArrowPreference(
                    title = stringResource(R.string.pref_title_battery_optimization),
                    summary = stringResource(R.string.pref_summary_battery_optimization),
                    onClick = {
                        val appInfoArgs = Bundle().apply {
                            putString("package", context.packageName)
                            putInt("uid", context.applicationInfo.uid)
                        }
                        val appDetailsIntent = Intent(Intent.ACTION_MAIN).apply {
                            setClassName(
                                "com.android.settings",
                                "com.android.settings.SubSettings",
                            )
                            putExtra(
                                ":settings:show_fragment",
                                "com.android.settings.applications.appinfo.AppInfoDashboardFragment",
                            )
                            putExtra(
                                ":settings:show_fragment_title",
                                textTitleAppInfo,
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
                            .onFailure { AppRuntime.snackbar(R.string.pref_cannot_open_settings) }
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
                            text = stringResource(R.string.pref_title_custom_adb_key),
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
                    Column(
                        modifier = Modifier.padding(horizontal = UiSpacing.Large),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                    ) {
                        val hasImportedAdbKey =
                            acBundle.importedPrivateKey.isNotBlank() ||
                                    acBundle.importedPublicKeyX509.isNotBlank()
                        Text(
                            text = stringResource(R.string.pref_title_adb_private_key),
                            fontWeight = FontWeight.Medium,
                        )
                        TextField(
                            value = acBundle.importedPrivateKeyFileName,
                            onValueChange = {},
                            readOnly = true,
                            label = stringResource(
                                when {
                                    acBundle.importedPrivateKey.isBlank() &&
                                            acBundle.rsaPrivateKey.isBlank() ->
                                        R.string.pref_adb_key_not_imported

                                    acBundle.importedPrivateKey.isNotBlank() ->
                                        R.string.pref_adb_key_imported

                                    else -> R.string.pref_adb_key_generated
                                }
                            ),
                            useLabelAsPlaceholder = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                Row(modifier = Modifier.padding(end = UiSpacing.Medium)) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        DirectAdbTransport.resetKeys()
                                                    }
                                                }.onSuccess {
                                                    AppRuntime.snackbar(
                                                        if (it.removedImportedKey)
                                                            R.string.pref_adb_key_removed
                                                        else
                                                            R.string.pref_adb_key_reset,
                                                        it.fingerprint,
                                                    )
                                                }.onFailure { e ->
                                                    AppRuntime.snackbar(
                                                        R.string.pref_adb_key_import_failed,
                                                        e.message ?: e.javaClass.simpleName,
                                                    )
                                                }
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector =
                                                if (hasImportedAdbKey) Icons.Rounded.Clear
                                                else Icons.Rounded.Refresh,
                                            contentDescription = stringResource(
                                                if (hasImportedAdbKey) R.string.pref_adb_key_remove
                                                else R.string.pref_adb_key_regenerate,
                                            ),
                                        )
                                    }
                                    IconButton(
                                        onClick = { adbPrivateKeyPicker.launch(arrayOf("*/*")) },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FileOpen,
                                            contentDescription = stringResource(R.string.cd_select_file),
                                        )
                                    }
                                }
                            },
                        )
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = UiSpacing.Large),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                    ) {
                        val publicKeyLabel = stringResource(
                            when {
                                acBundle.importedPublicKeyX509.isBlank() &&
                                        acBundle.rsaPublicKeyX509.isBlank() ->
                                    R.string.pref_adb_key_not_imported

                                acBundle.importedPublicKeyFileName.isNotBlank() ->
                                    R.string.pref_adb_key_imported

                                else -> R.string.pref_adb_key_generated
                            }
                        )
                        Text(
                            text = stringResource(R.string.pref_title_adb_public_key),
                            fontWeight = FontWeight.Medium,
                        )
                        TextField(
                            value = acBundle.importedPublicKeyFileName,
                            onValueChange = {},
                            readOnly = true,
                            label = publicKeyLabel,
                            useLabelAsPlaceholder = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                Row(modifier = Modifier.padding(end = UiSpacing.Medium)) {
                                    IconButton(
                                        onClick = { adbPublicKeyPicker.launch(arrayOf("*/*")) },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FileOpen,
                                            contentDescription = stringResource(R.string.cd_select_file),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
                SwitchPreference(
                    title = stringResource(R.string.pref_title_auto_discovery),
                    summary = stringResource(R.string.pref_summary_auto_discovery),
                    checked = asBundle.adbPairingAutoDiscoverOnDialogOpen,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            adbPairingAutoDiscoverOnDialogOpen = it
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_auto_reconnect),
                    summary = stringResource(R.string.pref_summary_auto_reconnect),
                    checked = asBundle.adbAutoReconnectPairedDevice,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            adbAutoReconnectPairedDevice = it
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_auto_load_apps),
                    summary = stringResource(R.string.pref_summary_auto_load_apps),
                    checked = asBundle.adbAutoLoadAppListOnConnect,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            adbAutoLoadAppListOnConnect = it
                        )
                        if (it) AppRuntime.snackbar(
                            R.string.pref_warning_list_apps
                        )
                    },
                )
            }
        }

        item {
            SectionSmallTitle(stringResource(R.string.section_terminal))
            Card {
                SuperSlider(
                    title = stringResource(R.string.pref_title_terminal_font_size),
                    summary = stringResource(R.string.pref_summary_terminal_font_size),
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
                            text = stringResource(R.string.pref_title_custom_font),
                            fontWeight = FontWeight.Medium,
                        )
                        TextField(
                            value = asBundle.terminalFontDisplayName,
                            onValueChange = {},
                            readOnly = true,
                            label = stringResource(R.string.pref_hint_builtin_font),
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
                                                    AppRuntime.snackbar(
                                                        if (cleared) R.string.pref_font_restored
                                                        else R.string.pref_no_custom_font
                                                    )
                                                }
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Clear,
                                                contentDescription = stringResource(R.string.cd_clear),
                                            )
                                        }
                                    }
                                    IconButton(onClick = terminalFontPicker.pick) {
                                        Icon(
                                            imageVector = Icons.Rounded.FileOpen,
                                            contentDescription = stringResource(R.string.cd_select_font),
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
            SectionSmallTitle(stringResource(R.string.section_misc))
            Card {
                SwitchPreference(
                    title = stringResource(R.string.pref_title_clear_logs_on_exit),
                    summary = stringResource(R.string.pref_summary_clear_logs_on_exit),
                    checked = asBundle.clearLogsOnExit,
                    onCheckedChange = {
                        asBundle = asBundle.copy(
                            clearLogsOnExit = it
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pref_title_hide_log_box),
                    summary = stringResource(R.string.pref_summary_hide_log_box),
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
                    title = stringResource(R.string.about_title),
                    summary = updateSummary,
                    onClick = { navigator.push(RootScreen.About) },
                )
            }
        }
    }
}
