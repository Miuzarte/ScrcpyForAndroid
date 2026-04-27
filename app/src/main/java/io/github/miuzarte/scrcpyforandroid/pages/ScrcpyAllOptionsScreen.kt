package io.github.miuzarte.scrcpyforandroid.pages

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.constants.ScrcpyPresets
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcuts
import io.github.miuzarte.scrcpyforandroid.models.ScrcpyOptions.Crop
import io.github.miuzarte.scrcpyforandroid.models.ScrcpyOptions.NewDisplay
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.ReorderableList
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperSlider
import io.github.miuzarte.scrcpyforandroid.scaffolds.OverlaySpinnerWithFallback
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperTextField
import io.github.miuzarte.scrcpyforandroid.scrcpy.ClientOptions
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.AudioSource
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.CameraFacing
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.Codec
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.DisplayImePolicy
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.LogLevel
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.Tick
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.VideoSource
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyOptions
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyProfiles
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.quickDevices
import io.github.miuzarte.scrcpyforandroid.storage.Storage.scrcpyOptions
import io.github.miuzarte.scrcpyforandroid.storage.Storage.scrcpyProfiles
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import io.github.miuzarte.scrcpyforandroid.widgets.RecordPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHost
import io.github.miuzarte.scrcpyforandroid.miuix.SpinnerEntry
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import kotlin.math.roundToInt

@Composable
internal fun ScrcpyAllOptionsScreen(
    scrollBehavior: ScrollBehavior,
    scrcpy: Scrcpy,
) {
    val navigator = LocalRootNavigator.current
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarController.current
    var showProfileMenu by rememberSaveable { mutableStateOf(false) }
    var showManageProfilesSheet by rememberSaveable { mutableStateOf(false) }
    val qdBundleShared by quickDevices.bundleState.collectAsState()
    val soBundleShared by scrcpyOptions.bundleState.collectAsState()
    val scrcpyProfilesState by scrcpyProfiles.state.collectAsState()
    val initialSelectedProfileId = remember(qdBundleShared.quickDevicesList) {
        val currentTarget = AppRuntime.currentConnectionTarget
        if (currentTarget == null) {
            ScrcpyOptions.GLOBAL_PROFILE_ID
        } else {
            DeviceShortcuts.unmarshalFrom(qdBundleShared.quickDevicesList)
                .get(currentTarget.host, currentTarget.port)
                ?.scrcpyProfileId
                ?: ScrcpyOptions.GLOBAL_PROFILE_ID
        }
    }
    val selectedProfileIdState = rememberSaveable(initialSelectedProfileId) {
        mutableStateOf(initialSelectedProfileId)
    }
    var selectedProfileId by selectedProfileIdState
    val soBundleState = rememberSaveable(selectedProfileId, soBundleShared, scrcpyProfilesState) {
        mutableStateOf(
            if (selectedProfileId == ScrcpyOptions.GLOBAL_PROFILE_ID) {
                soBundleShared
            } else {
                scrcpyProfilesState.profiles
                    .firstOrNull { it.id == selectedProfileId }
                    ?.bundle ?: soBundleShared
            }
        )
    }
    val lastValidSoBundleState = rememberSaveable(selectedProfileId) {
        mutableStateOf(soBundleState.value)
    }
    val profileTabs = remember(scrcpyProfilesState.profiles) {
        scrcpyProfilesState.profiles.map { it.name }
    }
    val profileIds = remember(scrcpyProfilesState.profiles) {
        scrcpyProfilesState.profiles.map { it.id }
    }
    val selectedProfileIndex = remember(selectedProfileId, profileIds) {
        profileIds.indexOf(selectedProfileId).coerceAtLeast(0)
    }
    val currentConnectedDeviceName = remember(qdBundleShared.quickDevicesList) {
        val currentTarget = AppRuntime.currentConnectionTarget
        if (currentTarget == null) {
            null
        } else {
            DeviceShortcuts.unmarshalFrom(qdBundleShared.quickDevicesList)
                .get(currentTarget.host, currentTarget.port)
                ?.name
                ?.ifBlank { currentTarget.host }
                ?: currentTarget.host
        }
    }
    var activeProfileDialog by rememberSaveable { mutableStateOf<ProfileDialogMode?>(null) }
    var profileDialogTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var profileDialogInput by rememberSaveable { mutableStateOf("") }
    var profileDialogCopySourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingProfileId by rememberSaveable { mutableStateOf<String?>(null) }

    suspend fun saveBundleForProfile(profileId: String, bundle: ScrcpyOptions.Bundle) {
        if (profileId == ScrcpyOptions.GLOBAL_PROFILE_ID) {
            scrcpyOptions.saveBundle(bundle)
        } else {
            scrcpyProfiles.updateBundle(profileId, bundle)
        }
    }

    suspend fun rebindDeletedProfileReferences(profileId: String) {
        val shortcuts = DeviceShortcuts.unmarshalFrom(qdBundleShared.quickDevicesList)
        val updated = shortcuts.copy(
            devices = shortcuts.map { device ->
                if (device.scrcpyProfileId == profileId) {
                    device.copy(scrcpyProfileId = ScrcpyOptions.GLOBAL_PROFILE_ID)
                } else {
                    device
                }
            }
        )
        if (updated != shortcuts) {
            quickDevices.updateBundle { bundle ->
                bundle.copy(quickDevicesList = updated.marshalToString())
            }
        }
    }

    suspend fun bindCurrentConnectedDevice(profileId: String) {
        val target = AppRuntime.currentConnectionTarget ?: return
        val shortcuts = DeviceShortcuts.unmarshalFrom(qdBundleShared.quickDevicesList)
        val updated = shortcuts.update(
            host = target.host,
            port = target.port,
            scrcpyProfileId = profileId,
        )
        if (updated != shortcuts) {
            quickDevices.updateBundle { bundle ->
                bundle.copy(quickDevicesList = updated.marshalToString())
            }
        }
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                TopAppBar(
                    title = "所有参数",
                    color =
                        if (blurActive) Color.Transparent
                        else colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = navigator.pop) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(
                                onClick = { showProfileMenu = true },
                                holdDownState = showProfileMenu,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.More,
                                    contentDescription = "配置管理",
                                )
                            }
                            OverlayListPopup(
                                show = showProfileMenu,
                                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                alignment = PopupPositionProvider.Align.TopEnd,
                                onDismissRequest = { showProfileMenu = false },
                            ) {
                                ListPopupColumn {
                                    DropdownImpl(
                                        text = "管理配置",
                                        optionSize = 1,
                                        isSelected = false,
                                        index = 0,
                                        onSelectedIndexChange = {
                                            showManageProfilesSheet = true
                                            showProfileMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    bottomContent = {
                        TabRow(
                            tabs = profileTabs,
                            selectedTabIndex = selectedProfileIndex,
                            onTabSelected = { index ->
                                val nextProfileId = profileIds.getOrNull(index)
                                    ?: return@TabRow
                                if (nextProfileId == selectedProfileId) return@TabRow
                                scope.launch {
                                    saveBundleForProfile(selectedProfileId, soBundleState.value)
                                    bindCurrentConnectedDevice(nextProfileId)
                                    selectedProfileId = nextProfileId
                                    val profileName = profileTabs.getOrElse(index) { "全局" }
                                    currentConnectedDeviceName?.let { deviceName ->
                                        snackbar.show("$deviceName 已切换到配置 $profileName")
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(bottom = UiSpacing.Medium)
                                .padding(horizontal = UiSpacing.Medium),
                            minWidth = 96.dp,
                            maxWidth = 192.dp,
                            height = 48.dp,
                            itemSpacing = UiSpacing.Medium,
                        )
                    }
                )
            }
        },
        snackbarHost = {
            val snackbar = LocalSnackbarController.current
            SnackbarHost(snackbar.hostState)
        },
    ) { contentPadding ->
        Box(
            modifier =
                if (blurActive) Modifier.layerBackdrop(blurBackdrop)
                else Modifier,
        ) {
            ScrcpyAllOptionsPage(
                contentPadding = contentPadding,
                scrollBehavior = scrollBehavior,
                scrcpy = scrcpy,
                soBundleShared = soBundleShared,
                scrcpyProfilesState = scrcpyProfilesState,
                selectedProfileIdState = selectedProfileIdState,
                soBundleState = soBundleState,
                lastValidSoBundleState = lastValidSoBundleState,
                onSaveBundleForProfile = ::saveBundleForProfile,
            )
        }

        ProfileNameDialog(
            mode = activeProfileDialog,
            initialInput = profileDialogInput,
            profiles = scrcpyProfilesState.profiles,
            initialCopySourceProfileId = profileDialogCopySourceId,
            onDismissRequest = {
                activeProfileDialog = null
                profileDialogTargetId = null
            },
        ) { input, copySourceProfileId ->
            scope.launch {
                when (activeProfileDialog) {
                    ProfileDialogMode.Create -> {
                        saveBundleForProfile(selectedProfileId, soBundleState.value)
                        val copySourceBundle = when (copySourceProfileId) {
                            null -> ScrcpyOptions.defaultBundle()
                            selectedProfileId -> soBundleState.value
                            ScrcpyOptions.GLOBAL_PROFILE_ID -> soBundleShared
                            else -> scrcpyProfilesState.profiles
                                .firstOrNull { it.id == copySourceProfileId }
                                ?.bundle
                                ?: soBundleShared
                        }
                        val created = scrcpyProfiles.createProfile(
                            requestedName = input,
                            bundle = copySourceBundle,
                        )
                        selectedProfileId = created.id
                    }

                    ProfileDialogMode.Rename -> {
                        val profileId = profileDialogTargetId ?: return@launch
                        scrcpyProfiles.renameProfile(
                            id = profileId,
                            requestedName = input,
                        )
                    }

                    null -> Unit
                }
                profileDialogTargetId = null
                profileDialogCopySourceId = selectedProfileId
                activeProfileDialog = null
            }
        }

        ManageProfilesSheet(
            show = showManageProfilesSheet,
            profiles = scrcpyProfilesState.profiles,
            selectedProfileId = selectedProfileId,
            onDismissRequest = { showManageProfilesSheet = false },
            onCreateProfile = {
                profileDialogTargetId = null
                profileDialogInput = "新配置"
                profileDialogCopySourceId = selectedProfileId
                activeProfileDialog = ProfileDialogMode.Create
            },
            onRenameProfile = { profileId ->
                profileDialogTargetId = profileId
                profileDialogInput = scrcpyProfilesState.profiles
                    .firstOrNull { it.id == profileId }
                    ?.name.orEmpty()
                activeProfileDialog = ProfileDialogMode.Rename
            },
            onDeleteProfile = { profileId ->
                deletingProfileId = profileId
            },
            onMoveProfile = { fromIndex, toIndex ->
                scope.launch {
                    scrcpyProfiles.moveProfile(fromIndex, toIndex)
                }
            },
        )

        DeleteProfileDialog(
            show = deletingProfileId != null,
            profileName = scrcpyProfilesState.profiles
                .firstOrNull { it.id == deletingProfileId }
                ?.name.orEmpty(),
            onDismissRequest = { deletingProfileId = null },
        ) {
            scope.launch {
                val profileId = deletingProfileId ?: return@launch
                val deleted = scrcpyProfiles.deleteProfile(profileId)
                if (deleted) {
                    rebindDeletedProfileReferences(profileId)
                    if (selectedProfileId == profileId) {
                        selectedProfileId = ScrcpyOptions.GLOBAL_PROFILE_ID
                    }
                }
                deletingProfileId = null
            }
        }
    }
}

@Composable
internal fun ScrcpyAllOptionsPage(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    scrcpy: Scrcpy,
    soBundleShared: ScrcpyOptions.Bundle,
    scrcpyProfilesState: ScrcpyProfiles.State,
    selectedProfileIdState: MutableState<String>,
    soBundleState: MutableState<ScrcpyOptions.Bundle>,
    lastValidSoBundleState: MutableState<ScrcpyOptions.Bundle>,
    onSaveBundleForProfile: suspend (String, ScrcpyOptions.Bundle) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val taskScope = remember { CoroutineScope(Dispatchers.IO + SupervisorJob()) }
    val snackbar = LocalSnackbarController.current

    val refreshBusy by scrcpy.listings.refreshBusyState.collectAsState()
    val listRefreshVersion by scrcpy.listings.refreshVersionState.collectAsState()

    var selectedProfileId by selectedProfileIdState
    val selectedProfileIdLatest by rememberUpdatedState(selectedProfileId)
    var soBundle by soBundleState
    var lastValidSoBundle by lastValidSoBundleState
    val soBundleLatest by rememberUpdatedState(soBundle)
    fun resolveProfileBundle(profileId: String): ScrcpyOptions.Bundle {
        if (profileId == ScrcpyOptions.GLOBAL_PROFILE_ID) return soBundleShared
        return scrcpyProfilesState.profiles.firstOrNull { it.id == profileId }?.bundle
            ?: soBundleShared
    }

    LaunchedEffect(selectedProfileId, soBundleShared, scrcpyProfilesState) {
        val bundle = resolveProfileBundle(selectedProfileId)
        if (soBundle != bundle) {
            soBundle = bundle
        }
        lastValidSoBundle = bundle
    }
    LaunchedEffect(soBundle) {
        delay(Settings.BUNDLE_SAVE_DELAY)
        val currentProfileId = selectedProfileIdLatest
        val savedBundle = resolveProfileBundle(currentProfileId)
        if (soBundle != savedBundle) {
            onSaveBundleForProfile(currentProfileId, soBundle)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            taskScope.launch {
                onSaveBundleForProfile(selectedProfileIdLatest, soBundleLatest)
            }
        }
    }
    val listState = rememberSaveable(
        saver = LazyListState.Saver,
    ) {
        LazyListState()
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

    val videoSourceItems = rememberSaveable { VideoSource.entries.map { it.string } }
    val videoSourceIndex = rememberSaveable(soBundle.videoSource) {
        VideoSource.entries
            .indexOfFirst { it.string == soBundle.videoSource }
            .coerceAtLeast(0)
    }

    val displays = scrcpy.listings.displays
    val displayDropdownItems = rememberSaveable(displays, listRefreshVersion) {
        listOf("默认") + displays.map { "${it.id} (${it.width}x${it.height})" }
    }
    val displaySpinnerItems = remember(displayDropdownItems) {
        displayDropdownItems.map { SpinnerEntry(title = it) }
    }
    val displayDropdownIndex = rememberSaveable(
        soBundle.displayId,
        displays,
        listRefreshVersion,
    ) {
        (displays.indexOfFirst { it.id == soBundle.displayId } + 1).coerceAtLeast(0)
    }
    val displayOverrideEndActionValue = remember(
        displays, listRefreshVersion, soBundle.displayId,
    ) {
        if (displays.isEmpty() && soBundle.displayId >= 0) soBundle.displayId.toString()
        else null
    }

    val cameras = scrcpy.listings.cameras
    val cameraDropdownItems = rememberSaveable(cameras, listRefreshVersion) {
        listOf("默认") + cameras.map { info ->
            buildString {
                append(info.id)
                append(" (")
                append(info.facing.string)
                if (info.activeSize.isNotBlank()) {
                    append(", ")
                    append(info.activeSize)
                }
                append(')')
            }
        }
    }
    val cameraSpinnerItems = remember(cameraDropdownItems) {
        cameraDropdownItems.map { SpinnerEntry(title = it) }
    }
    val cameraDropdownIndex = rememberSaveable(
        soBundle.cameraId,
        cameras,
        listRefreshVersion,
    ) {
        (cameras.indexOfFirst { it.id == soBundle.cameraId } + 1).coerceAtLeast(0)
    }
    val cameraOverrideEndActionValue = remember(
        cameras, listRefreshVersion, soBundle.cameraId,
    ) {
        if (cameras.isEmpty() && soBundle.cameraId.isNotBlank()) soBundle.cameraId
        else null
    }

    val cameraFacingItems = rememberSaveable {
        listOf("默认") + CameraFacing.entries
            .drop(1)
            .map { it.string }
    }
    val cameraFacingIndex = rememberSaveable(soBundle.cameraFacing) {
        if (soBundle.cameraFacing.isEmpty()) {
            0
        } else {
            val idx = CameraFacing.entries
                .indexOfFirst { it.string == soBundle.cameraFacing }
            if (idx > 0) idx else 0
        }
    }

    var cameraSizeCustomInput by rememberSaveable(soBundle.cameraSizeCustom) {
        mutableStateOf(soBundle.cameraSizeCustom)
    }

    val cameraSizes = scrcpy.listings.cameraSizes
    val cameraSizeDropdownItems = rememberSaveable(cameraSizes, listRefreshVersion) {
        listOf("默认", "自定义") + cameraSizes
    }
    val cameraSizeSpinnerItems = remember(cameraSizeDropdownItems) {
        cameraSizeDropdownItems.map { SpinnerEntry(title = it) }
    }
    val cameraSizeDropdownIndex = rememberSaveable(
        soBundle.cameraSize,
        soBundle.cameraSizeCustom,
        soBundle.cameraSizeUseCustom,
        cameraSizes,
        listRefreshVersion,
    ) {
        when {
            soBundle.cameraSizeUseCustom -> 1
            soBundle.cameraSize.isEmpty() -> 0
            soBundle.cameraSize in cameraSizes ->
                cameraSizes.indexOf(soBundle.cameraSize) + 2

            else -> 0
        }
    }
    val cameraSizeOverrideEndActionValue = remember(
        cameraSizes, listRefreshVersion,
        soBundle.cameraSize, soBundle.cameraSizeCustom, soBundle.cameraSizeUseCustom,
    ) {
        when {
            cameraSizes.isNotEmpty() -> null
            soBundle.cameraSizeUseCustom && soBundle.cameraSizeCustom.isNotBlank() -> soBundle.cameraSizeCustom
            soBundle.cameraSize.isNotBlank() -> soBundle.cameraSize
            else -> null
        }
    }

    var cameraArInput by rememberSaveable(soBundle.cameraAr) {
        mutableStateOf(soBundle.cameraAr)
    }

    val cameraFpsPresetIndex = rememberSaveable(soBundle.cameraFps) {
        ScrcpyPresets.CameraFps.indexOfOrNearest(soBundle.cameraFps)
    }

    val screenOffTimeoutPresetIndex = rememberSaveable(soBundle.screenOffTimeout) {
        ScrcpyPresets.ScreenOffTimeout.indexOfOrNearest(
            Tick(soBundle.screenOffTimeout).sec.toInt().coerceAtLeast(0)
        )
    }

    val audioSourceItems = rememberSaveable {
        AudioSource.entries.map { it.string }
    }
    val audioSourceIndex = rememberSaveable(soBundle.audioSource) {
        AudioSource.entries
            .indexOfFirst { it.string == soBundle.audioSource }
            .coerceAtLeast(0)
    }
    val keyInjectModeItems = rememberSaveable {
        listOf("默认", "优先文本", "原始按键事件")
    }
    val keyInjectModeIndex = rememberSaveable(soBundle.keyInjectMode) {
        when (soBundle.keyInjectMode) {
            ClientOptions.KeyInjectMode.PREFER_TEXT.string -> 1
            ClientOptions.KeyInjectMode.RAW.string -> 2
            else -> 0
        }
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

    val videoEncoders = scrcpy.listings.videoEncoders
    val videoEncoderItems by remember(videoEncoders, listRefreshVersion) {
        derivedStateOf {
            buildList {
                add(SpinnerEntry(title = "自动"))
                videoEncoders.forEach { info ->
                    add(
                        SpinnerEntry(
                            title = info.id,
                            summary = info.type.s,
                        )
                    )
                }
            }
        }
    }
    val videoEncoderIndex = rememberSaveable(
        soBundle.videoEncoder,
        videoEncoders,
        listRefreshVersion,
    ) {
        (videoEncoders.indexOfFirst { it.id == soBundle.videoEncoder } + 1)
            .coerceAtLeast(0)
    }
    val videoEncoderOverrideEndActionValue = remember(
        videoEncoders, listRefreshVersion, soBundle.videoEncoder,
    ) {
        if (videoEncoders.isEmpty() && soBundle.videoEncoder.isNotBlank()) soBundle.videoEncoder
        else null
    }

    val audioEncoders = scrcpy.listings.audioEncoders
    val audioEncoderItems by remember(audioEncoders, listRefreshVersion) {
        derivedStateOf {
            buildList {
                add(SpinnerEntry(title = "自动"))
                audioEncoders.forEach { info ->
                    add(
                        SpinnerEntry(
                            title = info.id,
                            summary = info.type.s,
                        )
                    )
                }
            }
        }
    }
    val audioEncoderIndex = rememberSaveable(
        soBundle.audioEncoder,
        audioEncoders,
        listRefreshVersion,
    ) {
        (audioEncoders.indexOfFirst { it.id == soBundle.audioEncoder } + 1)
            .coerceAtLeast(0)
    }
    val audioEncoderOverrideEndActionValue = remember(
        audioEncoders, listRefreshVersion, soBundle.audioEncoder,
    ) {
        if (audioEncoders.isEmpty() && soBundle.audioEncoder.isNotBlank()) soBundle.audioEncoder
        else null
    }

    val displayImePolicyItems = rememberSaveable {
        listOf("默认") + DisplayImePolicy.entries
            .drop(1)
            .map { it.string }
    }
    val displayImePolicyIndex = rememberSaveable(soBundle.displayImePolicy) {
        if (soBundle.displayImePolicy.isEmpty()) {
            0
        } else {
            val idx = DisplayImePolicy.entries
                .indexOfFirst { it.string == soBundle.displayImePolicy }
            if (idx > 0) idx else 0
        }
    }

    val apps = remember(scrcpy.listings.apps, listRefreshVersion) {
        scrcpy.listings.apps // .sortedBy { it.packageName }
    }
    val appDropdownItems by remember(apps, listRefreshVersion) {
        derivedStateOf {
            buildList {
                add(SpinnerEntry(title = "无"))
                add(SpinnerEntry(title = "自定义"))
                apps.forEach { app ->
                    add(
                        SpinnerEntry(
                            icon = app.system?.let { system ->
                                {
                                    Icon(
                                        imageVector =
                                            if (system) Icons.Rounded.Android // MiuixIcons.Tune
                                            else MiuixIcons.Store,
                                        contentDescription = app.label ?: app.packageName,
                                        modifier = Modifier.padding(end = UiSpacing.ContentVertical),
                                    )
                                }
                            },
                            title = app.label?.takeIf { it.isNotBlank() } ?: app.packageName,
                            summary = app.packageName,
                        )
                    )
                }
            }
        }
    }
    val appDropdownIndex = rememberSaveable(
        soBundle.startApp,
        soBundle.startAppCustom,
        soBundle.startAppUseCustom,
        apps,
        listRefreshVersion,
    ) {
        when {
            soBundle.startAppUseCustom -> 1
            soBundle.startApp.isEmpty() -> 0
            apps.any { it.packageName == soBundle.startApp } ->
                apps.indexOfFirst { it.packageName == soBundle.startApp } + 2

            else -> 0
        }
    }
    val startAppOverrideEndActionValue = remember(
        apps, listRefreshVersion, soBundle.startApp,
    ) {
        if (apps.isEmpty() && soBundle.startApp.isNotBlank()) soBundle.startApp
        else null
    }

    var startAppCustomInput by rememberSaveable(soBundle.startAppCustom) {
        mutableStateOf(soBundle.startAppCustom)
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
    val newDisplayWidthBlank = remember(newDisplayWidthInput) {
        newDisplayWidthInput.trim().isEmpty()
    }
    val newDisplayHeightBlank = remember(newDisplayHeightInput) {
        newDisplayHeightInput.trim().isEmpty()
    }
    val newDisplayAllBlank =
        remember(newDisplayWidthBlank, newDisplayHeightBlank, newDisplayDpiInput) {
            newDisplayWidthBlank && newDisplayHeightBlank && newDisplayDpiInput.trim().isEmpty()
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

    val logLevelItems = rememberSaveable { LogLevel.entries.map { it.string } }
    val logLevelIndex = rememberSaveable(soBundle.logLevel) {
        LogLevel.entries
            .indexOfFirst { it.string == soBundle.logLevel }
            .coerceAtLeast(0)
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
            .toList(preview = true)
            // improve readability using hard line breaks
            .joinToString("\n")
    }

    LazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
        state = listState,
        bottomInnerPadding = UiSpacing.PageBottom,
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
                SwitchPreference(
                    title = "scrcpy 启动后熄灭设备屏幕",
                    summary = "--turn-screen-off",
                    checked = soBundle.turnScreenOff,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            turnScreenOff = it
                        )
                        if (it) snackbar.show(
                            // github.com/Genymobile/scrcpy/issues/3376
                            // github.com/Genymobile/scrcpy/issues/4587
                            // github.com/Genymobile/scrcpy/issues/5676
                            "注意：大部分设备在关闭屏幕后刷新率会降低/减半"
                        )
                    },
                )
                SwitchPreference(
                    title = "禁用控制",
                    summary = "--no-control",
                    checked = !soBundle.control,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            control = !it
                        )
                    },
                )
                SwitchPreference(
                    title = "禁用视频",
                    summary = "--no-video",
                    checked = !soBundle.video,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            video = !it
                        )
                    },
                )
                SwitchPreference(
                    title = "仅转发不播放视频",
                    summary = "--no-video-playback",
                    checked = !soBundle.videoPlayback,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            videoPlayback = !it
                        )
                    },
                    enabled = soBundle.video,
                )
                SwitchPreference(
                    title = "禁用音频",
                    summary = "--no-audio",
                    checked = !soBundle.audio,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            audio = !it
                        )
                    },
                )
                SwitchPreference(
                    title = "仅转发不播放",
                    summary = "--no-audio-playback",
                    checked = !soBundle.audioPlayback,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            audioPlayback = !it
                        )
                    },
                    enabled = soBundle.audio,
                )
                SuperSlider(
                    title = "scrcpy 启动后受控机的息屏时间",
                    summary = "--screen-off-timeout",
                    value = screenOffTimeoutPresetIndex.toFloat(),
                    onValueChange = { value ->
                        val idx = value.roundToInt()
                            .coerceIn(0, ScrcpyPresets.ScreenOffTimeout.lastIndex)
                        soBundle = soBundle.copy(
                            screenOffTimeout = ScrcpyPresets.ScreenOffTimeout[idx]
                                .takeIf { it > 0 }
                                ?.toLong()
                                ?.let(Tick::fromSec)
                                ?.value
                                ?: -1
                        )
                    },
                    valueRange = 0f..ScrcpyPresets.ScreenOffTimeout.lastIndex.toFloat(),
                    steps = (ScrcpyPresets.ScreenOffTimeout.size - 2).coerceAtLeast(0),
                    unit = "s",
                    zeroStateText = "不修改",
                    showKeyPoints = true,
                    keyPoints = ScrcpyPresets.ScreenOffTimeout.indices.map { it.toFloat() },
                    displayText = Tick(soBundle.screenOffTimeout).sec.toString(),
                    inputInitialValue =
                        if (soBundle.screenOffTimeout <= 0) ""
                        else Tick(soBundle.screenOffTimeout).sec.toString(),
                    inputFilter = { it.filter(Char::isDigit) },
                    inputValueRange = 0f..86400f,
                    onInputConfirm = {
                        soBundle = soBundle.copy(
                            screenOffTimeout = it.toLongOrNull()
                                ?.takeIf { value -> value > 0 }
                                ?.let(Tick::fromSec)
                                ?.value
                                ?: -1
                        )
                    },
                )
                SwitchPreference(
                    title = "scrcpy 启动时不唤醒屏幕",
                    summary = "--no-power-on",
                    checked = !soBundle.powerOn,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            powerOn = !it
                        )
                    },
                )
                SwitchPreference(
                    title = "scrcpy 结束后息屏",
                    summary = "--power-off-on-close",
                    checked = soBundle.powerOffOnClose,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            powerOffOnClose = it
                        )
                    },
                )
                SwitchPreference(
                    title = "scrcpy 启动后保持设备唤醒状态（插入电源）",
                    summary = "--stay-awake",
                    checked = soBundle.stayAwake,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            stayAwake = it
                        )
                    },
                )
                SwitchPreference(
                    title = "显示物理触控",
                    summary = "--show-touches",
                    checked = soBundle.showTouches,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            showTouches = it
                        )
                    },
                )
                SwitchPreference(
                    title = "启动后进入全屏",
                    summary = "--fullscreen",
                    checked = soBundle.fullscreen,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            fullscreen = it
                        )
                    },
                )
                SwitchPreference(
                    title = "scrcpy 启动后保持本机屏幕常亮",
                    summary = "--disable-screensaver",
                    checked = soBundle.disableScreensaver,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            disableScreensaver = it
                        )
                        if (it) snackbar.show(
                            "注意防烧屏，画中画也生效"
                        )
                    },
                )
                RecordPreferences(
                    profileId = selectedProfileId,
                    recordFilenameTemplate = soBundle.recordFilename,
                    recordFormat = soBundle.recordFormat,
                    enabled = true,
                    onRecordFormatChange = {
                        soBundle = soBundle.copy(recordFormat = it)
                    },
                )
                SwitchPreference(
                    title = "scrcpy 结束时断开 adb",
                    summary = "--kill-adb-on-close",
                    checked = soBundle.killAdbOnClose,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            killAdbOnClose = it
                        )
                    },
                )
            }
        }

        item {
            Card {
                OverlayDropdownPreference(
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

                OverlayDropdownPreference(
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
                OverlayDropdownPreference(
                    title = "视频来源",
                    summary = "--video-source",
                    items = videoSourceItems,
                    selectedIndex = videoSourceIndex,
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            videoSource = VideoSource.entries[it].string
                        )
                    },
                )
                AnimatedVisibility(soBundle.videoSource == "display") {
                    Column {
                        OverlaySpinnerWithFallback(
                            title = "监视器 ID",
                            summary = "--display-id",
                            items = displaySpinnerItems,
                            selectedIndex = displayDropdownIndex,
                            dataLoaded = displays.isNotEmpty(),
                            dataLoading = refreshBusy,
                            overrideEndActionValue = displayOverrideEndActionValue,
                            onExpandedChange = { expanded ->
                                if (expanded && displays.isEmpty()) {
                                    scope.launch {
                                        snackbar.show("获取中")
                                        try {
                                            withContext(Dispatchers.IO) {
                                                scrcpy.listings.getDisplays(forceRefresh = false)
                                            }
                                            snackbar.show("获取成功")
                                        } catch (e: Exception) {
                                            snackbar.show("刷新失败: ${e.message}")
                                        }
                                    }
                                }
                            },
                            onSelectedIndexChange = {
                                soBundle = soBundle.copy(
                                    displayId =
                                        if (it == 0) -1
                                        else displays[it - 1].id
                                )
                            },
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
                            inputInitialValue = soBundle.maxSize
                                .takeIf { it != 0 }
                                ?.toString()
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
                        OverlaySpinnerWithFallback(
                            title = "摄像头 ID",
                            summary = "--camera-id",
                            items = cameraSpinnerItems,
                            selectedIndex = cameraDropdownIndex,
                            dataLoaded = cameras.isNotEmpty(),
                            dataLoading = refreshBusy,
                            overrideEndActionValue = cameraOverrideEndActionValue,
                            onExpandedChange = { expanded ->
                                if (expanded && cameras.isEmpty()) {
                                    scope.launch {
                                        snackbar.show("获取中")
                                        try {
                                            withContext(Dispatchers.IO) {
                                                scrcpy.listings.getCameras(forceRefresh = false)
                                            }
                                            snackbar.show("获取成功")
                                        } catch (e: Exception) {
                                            snackbar.show("刷新失败: ${e.message}")
                                        }
                                    }
                                }
                            },
                            onSelectedIndexChange = {
                                soBundle = soBundle.copy(
                                    cameraId =
                                        if (it == 0) ""
                                        else cameras[it - 1].id
                                )
                            },
                        )
                        OverlayDropdownPreference(
                            title = "摄像头朝向",
                            summary = "--camera-facing",
                            items = cameraFacingItems,
                            selectedIndex = cameraFacingIndex,
                            onSelectedIndexChange = {
                                soBundle = soBundle.copy(
                                    cameraFacing =
                                        if (it == 0) ""
                                        else CameraFacing.entries[it].string
                                )
                            },
                        )
                        OverlaySpinnerWithFallback(
                            title = "摄像头分辨率",
                            summary = "--camera-size",
                            items = cameraSizeSpinnerItems,
                            selectedIndex = cameraSizeDropdownIndex,
                            dataLoaded = cameraSizes.isNotEmpty(),
                            dataLoading = refreshBusy,
                            overrideEndActionValue = cameraSizeOverrideEndActionValue,
                            onExpandedChange = { expanded ->
                                if (expanded && cameraSizes.isEmpty()) {
                                    scope.launch {
                                        snackbar.show("获取中")
                                        try {
                                            withContext(Dispatchers.IO) {
                                                scrcpy.listings.getCameraSizes(forceRefresh = false)
                                            }
                                            snackbar.show("获取成功")
                                        } catch (e: Exception) {
                                            snackbar.show("刷新失败: ${e.message}")
                                        }
                                    }
                                }
                            },
                            onSelectedIndexChange = {
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
                                        cameraSizeCustomInput = ""
                                    }

                                    else -> {
                                        // 选择列表中的实际分辨率
                                        val selectedCameraSize = cameraSizes[it - 2]
                                        soBundle = soBundle.copy(
                                            cameraSize = selectedCameraSize,
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
                                    soBundle = if (cameraSizeCustomInput in cameraSizes) {
                                        // 输入的值存在于列表中, 取消自定义输入
                                        soBundle.copy(
                                            cameraSize = cameraSizeCustomInput,
                                            cameraSizeUseCustom = false
                                        )
                                    } else {
                                        soBundle.copy(
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
                        SwitchPreference(
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
                OverlayDropdownPreference(
                    title = "音频来源",
                    summary = "--audio-source",
                    items = audioSourceItems,
                    selectedIndex = audioSourceIndex,
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            audioSource = AudioSource.entries[it].string
                        )
                    },
                )
                SwitchPreference(
                    title = "音频双路输出",
                    summary = "--audio-dup",
                    checked = soBundle.audioDup,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            audioDup = it
                        )
                    },
                )
                SwitchPreference(
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
                OverlaySpinnerWithFallback(
                    title = "视频编码器",
                    summary = "--video-encoder",
                    items = videoEncoderItems,
                    selectedIndex = videoEncoderIndex,
                    dataLoaded = videoEncoders.isNotEmpty(),
                    dataLoading = refreshBusy,
                    overrideEndActionValue = videoEncoderOverrideEndActionValue,
                    onExpandedChange = { expanded ->
                        if (expanded && videoEncoders.isEmpty()) {
                            scope.launch {
                                snackbar.show("获取中")
                                try {
                                    withContext(Dispatchers.IO) {
                                        scrcpy.listings.getEncoders(forceRefresh = false)
                                    }
                                    snackbar.show("获取成功")
                                } catch (e: Exception) {
                                    snackbar.show("刷新失败: ${e.message}")
                                }
                            }
                        }
                    },
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            videoEncoder =
                                if (it == 0) ""
                                else videoEncoders[it - 1].id
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
                OverlaySpinnerWithFallback(
                    title = "音频编码器",
                    summary = "--audio-encoder",
                    items = audioEncoderItems,
                    selectedIndex = audioEncoderIndex,
                    dataLoaded = audioEncoders.isNotEmpty(),
                    dataLoading = refreshBusy,
                    overrideEndActionValue = audioEncoderOverrideEndActionValue,
                    onExpandedChange = { expanded ->
                        if (expanded && audioEncoders.isEmpty()) {
                            scope.launch {
                                snackbar.show("获取中")
                                try {
                                    withContext(Dispatchers.IO) {
                                        scrcpy.listings.getEncoders(forceRefresh = false)
                                    }
                                    snackbar.show("获取成功")
                                } catch (e: Exception) {
                                    snackbar.show("刷新失败: ${e.message}")
                                }
                            }
                        }
                    },
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            audioEncoder =
                                if (it == 0) ""
                                else audioEncoders[it - 1].id
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
                OverlayDropdownPreference(
                    title = "输入法显示策略",
                    summary = "--display-ime-policy",
                    items = displayImePolicyItems,
                    selectedIndex = displayImePolicyIndex,
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            displayImePolicy =
                                if (it == 0) ""
                                else DisplayImePolicy.entries[it].string
                        )
                    },
                )
                SwitchPreference(
                    title = "关闭虚拟显示器时保留内容",
                    summary = "--no-vd-destroy-content",
                    checked = !soBundle.vdDestroyContent,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            vdDestroyContent = !it
                        )
                    },
                )
                SwitchPreference(
                    title = "禁用虚拟显示器系统装饰",
                    summary = "--no-vd-system-decorations",
                    checked = !soBundle.vdSystemDecorations,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            vdSystemDecorations = !it
                        )
                    },
                )
                SwitchPreference(
                    title = "禁用 MediaCodec 出错时自动降级",
                    summary = "--no-downsize-on-error",
                    checked = !soBundle.downsizeOnError,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            downsizeOnError = !it
                        )
                        if (it) snackbar.show(
                            """
                                默认情况下，在 MediaCodec 出错时，scrcpy 会自动尝试使用更低的分辨率重新开始
                                此选项将禁用此行为
                            """.trimIndent()
                        )
                    },
                )
                SwitchPreference(
                    title = "粘贴兼容回退（仅支持ASCII/英文字符）",
                    summary = "--legacy-paste",
                    checked = soBundle.legacyPaste,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            legacyPaste = it
                        )
                    },
                )
                OverlayDropdownPreference(
                    title = "键盘注入策略",
                    summary = when (keyInjectModeIndex) {
                        1 -> "--prefer-text"
                        2 -> "--raw-key-events"
                        else -> "默认"
                    },
                    items = keyInjectModeItems,
                    selectedIndex = keyInjectModeIndex,
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            keyInjectMode = when (it) {
                                1 -> ClientOptions.KeyInjectMode.PREFER_TEXT.string
                                2 -> ClientOptions.KeyInjectMode.RAW.string
                                else -> ClientOptions.KeyInjectMode.MIXED.string
                            }
                        )
                    },
                )
                SwitchPreference(
                    title = "禁用按键重复转发",
                    summary = "--no-key-repeat",
                    checked = !soBundle.forwardKeyRepeat,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            forwardKeyRepeat = !it
                        )
                    },
                    enabled = soBundle.keyInjectMode != ClientOptions.KeyInjectMode.PREFER_TEXT.string,
                )
                SwitchPreference(
                    title = "禁用剪贴板双向同步",
                    summary = "--no-clipboard-autosync",
                    checked = !soBundle.clipboardAutosync,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            clipboardAutosync = !it
                        )
                    },
                )
                SwitchPreference(
                    title = "禁用鼠标悬停转发",
                    summary = "--no-mouse-hover",
                    checked = !soBundle.mouseHover,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            mouseHover = !it
                        )
                    },
                )
                SwitchPreference(
                    title = "禁用结束后清理",
                    summary = "--no-cleanup",
                    checked = !soBundle.cleanup,
                    onCheckedChange = {
                        soBundle = soBundle.copy(
                            cleanup = !it
                        )
                        if (it) snackbar.show(
                            """
                                默认情况下，scrcpy 会从设备中移除服务器二进制文件，并在退出时恢复设备状态（显示触摸、保持唤醒和电源模式）
                                此选项将禁用此清理操作
                            """.trimIndent()
                        )
                    },
                )
            }
        }

        if (soBundle.videoSource == "display") item {
            Card {
                OverlaySpinnerWithFallback(
                    title = "scrcpy 启动后打开应用",
                    summary = "--start-app",
                    items = appDropdownItems,
                    selectedIndex = appDropdownIndex,
                    dataLoaded = apps.isNotEmpty(),
                    dataLoading = refreshBusy,
                    overrideEndActionValue = startAppOverrideEndActionValue,
                    onExpandedChange = { expanded ->
                        if (expanded && apps.isEmpty()) {
                            scope.launch {
                                snackbar.show("获取中")
                                try {
                                    withContext(Dispatchers.IO) {
                                        scrcpy.listings.getApps(forceRefresh = false)
                                    }
                                    snackbar.show("获取成功")
                                } catch (e: Exception) {
                                    snackbar.show("刷新失败: ${e.message}")
                                }
                            }
                        }
                    },
                    onSelectedIndexChange = {
                        when (it) {
                            0 -> {
                                soBundle = soBundle.copy(
                                    startApp = "",
                                    startAppUseCustom = false,
                                )
                                startAppCustomInput = ""
                            }

                            1 -> {
                                soBundle = soBundle.copy(
                                    startAppUseCustom = true
                                )
                                startAppCustomInput = ""
                            }

                            else -> {
                                val selectedApp = apps[it - 2]
                                soBundle = soBundle.copy(
                                    startApp = selectedApp.packageName,
                                    startAppUseCustom = false,
                                )
                                startAppCustomInput = ""
                            }
                        }
                    },
                )
                AnimatedVisibility(soBundle.startAppUseCustom) {
                    SuperTextField(
                        value = startAppCustomInput,
                        onValueChange = { startAppCustomInput = it },
                        onFocusLost = {
                            soBundle = if (startAppCustomInput in apps.map { it.packageName }) {
                                soBundle.copy(
                                    startApp = startAppCustomInput,
                                    startAppUseCustom = false,
                                )
                            } else {
                                soBundle.copy(
                                    startAppCustom = startAppCustomInput
                                )
                            }
                        },
                        label = "--start-app",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = UiSpacing.Large),
                    )
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
                                        newDisplay = NewDisplay.parseFrom(
                                            newDisplayWidthInput,
                                            newDisplayHeightInput,
                                            newDisplayDpiInput
                                        ).toString()
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
                                        newDisplay = NewDisplay.parseFrom(
                                            newDisplayWidthInput,
                                            newDisplayHeightInput,
                                            newDisplayDpiInput
                                        ).toString()
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
                                        newDisplay = NewDisplay.parseFrom(
                                            newDisplayWidthInput,
                                            newDisplayHeightInput,
                                            newDisplayDpiInput
                                        ).toString()
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
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UiSpacing.Large),
                    ) {
                        val gap = UiSpacing.ContentHorizontal
                        val inputWidth = (maxWidth - gap * 2) / 3
                        val trailingButtonWidth = inputWidth * 2 + gap
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            TextButton(
                                text = "清空",
                                onClick = {
                                    newDisplayWidthInput = ""
                                    newDisplayHeightInput = ""
                                    newDisplayDpiInput = ""
                                    soBundle = soBundle.copy(newDisplay = "")
                                },
                                modifier = Modifier.width(inputWidth),
                                enabled = !newDisplayAllBlank,
                            )
                            TextButton(
                                text =
                                    if (newDisplayWidthBlank || newDisplayHeightBlank) "本机"
                                    else "交换",
                                onClick = {
                                    if (newDisplayWidthBlank || newDisplayHeightBlank) {
                                        val metrics = resources.displayMetrics
                                        newDisplayWidthInput = metrics.widthPixels.toString()
                                        newDisplayHeightInput = metrics.heightPixels.toString()
                                        newDisplayDpiInput = metrics.densityDpi
                                            .takeIf { it > 0 }
                                            ?.toString()
                                            .orEmpty()
                                    } else {
                                        val currentWidth = newDisplayWidthInput.trim()
                                        val currentHeight = newDisplayHeightInput.trim()
                                        newDisplayWidthInput = currentHeight
                                        newDisplayHeightInput = currentWidth
                                    }
                                    soBundle = soBundle.copy(
                                        newDisplay = NewDisplay.parseFrom(
                                            newDisplayWidthInput,
                                            newDisplayHeightInput,
                                            newDisplayDpiInput
                                        ).toString()
                                    )
                                },
                                modifier = Modifier.width(trailingButtonWidth),
                                colors = ButtonDefaults.textButtonColorsPrimary(),
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
                                            crop = Crop.parseFrom(
                                                cropWidthInput,
                                                cropHeightInput,
                                                cropXInput,
                                                cropYInput
                                            ).toString()
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
                                            crop = Crop.parseFrom(
                                                cropWidthInput,
                                                cropHeightInput,
                                                cropXInput,
                                                cropYInput
                                            ).toString()
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
                                            crop = Crop.parseFrom(
                                                cropWidthInput,
                                                cropHeightInput,
                                                cropXInput,
                                                cropYInput
                                            ).toString()
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
                                            crop = Crop.parseFrom(
                                                cropWidthInput,
                                                cropHeightInput,
                                                cropXInput,
                                                cropYInput
                                            ).toString()
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

        item {
            Card {
                OverlayDropdownPreference(
                    title = "日志等级",
                    summary = "--verbosity",
                    items = logLevelItems,
                    selectedIndex = logLevelIndex,
                    onSelectedIndexChange = {
                        soBundle = soBundle.copy(
                            logLevel = LogLevel.entries[it].string
                        )
                    },
                )
            }
        }

    }
}

private enum class ProfileDialogMode {
    Create,
    Rename,
}

@Composable
private fun ProfileNameDialog(
    mode: ProfileDialogMode?,
    initialInput: String,
    profiles: List<ScrcpyProfiles.Profile>,
    initialCopySourceProfileId: String?,
    onDismissRequest: () -> Unit,
    onConfirm: (String, String?) -> Unit,
) {
    if (mode == null) return
    val focusManager = LocalFocusManager.current
    var input by rememberSaveable(mode, initialInput) { mutableStateOf(initialInput) }
    val profileNames = remember(profiles) { profiles.map { it.name } }
    val profileIds = remember(profiles) { profiles.map { it.id } }
    val copySourceItems = remember(profileNames) { listOf("默认") + profileNames }
    var copySourceProfileId by rememberSaveable(mode, initialCopySourceProfileId) {
        mutableStateOf(initialCopySourceProfileId)
    }
    val copySourceDropdownIndex = remember(copySourceProfileId, profileIds) {
        copySourceProfileId
            ?.let { profileIds.indexOf(it).takeIf { index -> index >= 0 }?.plus(1) }
            ?: 0
    }
    OverlayDialog(
        show = true,
        title = when (mode) {
            ProfileDialogMode.Create -> "新建配置"
            ProfileDialogMode.Rename -> "重命名配置"
        },
        summary = "配置名重复时会自动追加序号",
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                label = "配置名",
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedVisibility(mode == ProfileDialogMode.Create) {
                OverlayDropdownPreference(
                    title = "复制配置",
                    items = copySourceItems,
                    selectedIndex = copySourceDropdownIndex,
                    onSelectedIndexChange = { index ->
                        copySourceProfileId = if (index == 0) {
                            null
                        } else {
                            profileIds.getOrElse(index - 1) {
                                ScrcpyOptions.GLOBAL_PROFILE_ID
                            }
                        }
                    },
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "确定",
                    onClick = { onConfirm(input, copySourceProfileId) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun ManageProfilesSheet(
    show: Boolean,
    profiles: List<ScrcpyProfiles.Profile>,
    selectedProfileId: String,
    onDismissRequest: () -> Unit,
    onCreateProfile: () -> Unit,
    onRenameProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onMoveProfile: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = "管理配置",
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
        endAction = {
            IconButton(
                onClick = onCreateProfile,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "新建配置",
                )
            }
        },
    ) {
        ReorderableList(
            itemsProvider = {
                profiles.map { profile ->
                    ReorderableList.Item(
                        id = profile.id,
                        title = profile.name,
                        subtitle = when (profile.id) {
                            selectedProfileId -> "当前配置"
                            else -> ""
                        },
                        onClick =
                            if (profile.id != ScrcpyOptions.GLOBAL_PROFILE_ID) {
                                { onRenameProfile(profile.id) }
                            } else null,
                        dragEnabled = profile.id != ScrcpyOptions.GLOBAL_PROFILE_ID,
                        endActions = buildList {
                            if (profile.id != ScrcpyOptions.GLOBAL_PROFILE_ID) {
                                add(
                                    ReorderableList.EndAction.Icon(
                                        icon = Icons.Rounded.Edit,
                                        contentDescription = "重命名配置",
                                        onClick = { onRenameProfile(profile.id) },
                                    )
                                )
                                add(
                                    ReorderableList.EndAction.Icon(
                                        icon = Icons.Rounded.DeleteOutline,
                                        contentDescription = "删除配置",
                                        onClick = { onDeleteProfile(profile.id) },
                                    )
                                )
                            }
                        },
                    )
                }
            },
            onSettle = onMoveProfile,
        ).invoke()
        Spacer(Modifier.height(UiSpacing.SheetBottom))
    }
}

@Composable
private fun DeleteProfileDialog(
    show: Boolean,
    profileName: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!show) return
    OverlayDialog(
        show = true,
        title = "删除配置",
        summary = "确认删除 \"$profileName\"？",
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
        ) {
            TextButton(
                text = "取消",
                onClick = onDismissRequest,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "删除",
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
