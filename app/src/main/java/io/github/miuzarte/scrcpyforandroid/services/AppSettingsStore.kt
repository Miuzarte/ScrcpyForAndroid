package io.github.miuzarte.scrcpyforandroid.services

import android.content.Context
import androidx.core.content.edit
import io.github.miuzarte.scrcpyforandroid.constants.AppDefaults
import io.github.miuzarte.scrcpyforandroid.constants.AppPreferenceKeys

internal data class MainSettings(
    val themeBaseIndex: Int = AppDefaults.DefaultThemeBaseIndex,
    val monetEnabled: Boolean = AppDefaults.DefaultMonetEnabled,
    val fullscreenDebugInfoEnabled: Boolean = AppDefaults.DefaultFullscreenDebugInfoEnabled,
    val showFullscreenVirtualButtons: Boolean = AppDefaults.DefaultShowFullscreenVirtualButtons,
    val devicePreviewCardHeightDp: Int = AppDefaults.DefaultDevicePreviewCardHeightDp,
    val keepScreenOnWhenStreamingEnabled: Boolean = AppDefaults.DefaultKeepScreenOnWhenStreamingEnabled,
    val virtualButtonsOutside: String = AppDefaults.DefaultVirtualButtonsOutside,
    val virtualButtonsInMore: String = AppDefaults.DefaultVirtualButtonsInMore,
    val customServerUri: String? = null,
    val serverRemotePath: String = AppDefaults.DefaultServerRemotePathInput,
    val videoCodec: String = AppDefaults.DefaultVideoCodec,
    val audioEnabled: Boolean = AppDefaults.DefaultAudioEnabled,
    val audioCodec: String = AppDefaults.DefaultAudioCodec,
    val adbKeyName: String = AppDefaults.DefaultAdbKeyNameInput,
)

internal data class DevicePageSettings(
    val pairHost: String = AppDefaults.DefaultPairHost,
    val pairPort: String = AppDefaults.DefaultPairPort,
    val pairCode: String = AppDefaults.DefaultPairCode,
    val quickConnectInput: String = AppDefaults.DefaultQuickConnectInput,
    val bitRateMbps: Float = AppDefaults.DefaultBitRateMbps,
    val bitRateInput: String = AppDefaults.DefaultBitRateInput,
    val audioBitRateKbps: Int = AppDefaults.DefaultAudioBitRateKbps,
    val maxSizeInput: String = AppDefaults.DefaultMaxSizeInput,
    val maxFpsInput: String = AppDefaults.DefaultMaxFpsInput,
    val noControl: Boolean = AppDefaults.DefaultNoControl,
    val videoEncoder: String = AppDefaults.DefaultVideoEncoder,
    val videoCodecOptions: String = AppDefaults.DefaultVideoCodecOptions,
    val audioEncoder: String = AppDefaults.DefaultAudioEncoder,
    val audioCodecOptions: String = AppDefaults.DefaultAudioCodecOptions,
    val audioDup: Boolean = AppDefaults.DefaultAudioDup,
    val audioSourcePreset: String = AppDefaults.DefaultAudioSourcePreset,
    val audioSourceCustom: String = AppDefaults.DefaultAudioSourceCustom,
    val videoSourcePreset: String = AppDefaults.DefaultVideoSourcePreset,
    val cameraIdInput: String = AppDefaults.DefaultCameraIdInput,
    val cameraFacingPreset: String = AppDefaults.DefaultCameraFacingPreset,
    val cameraSizePreset: String = AppDefaults.DefaultCameraSizePreset,
    val cameraSizeCustom: String = AppDefaults.DefaultCameraSizeCustom,
    val cameraArInput: String = AppDefaults.DefaultCameraArInput,
    val cameraFpsInput: String = AppDefaults.DefaultCameraFpsInput,
    val cameraHighSpeed: Boolean = AppDefaults.DefaultCameraHighSpeed,
    val noAudioPlayback: Boolean = AppDefaults.DefaultNoAudioPlayback,
    val noVideo: Boolean = AppDefaults.DefaultNoVideo,
    val requireAudio: Boolean = AppDefaults.DefaultRequireAudio,
    val turnScreenOff: Boolean = AppDefaults.DefaultTurnScreenOff,
    val newDisplayWidth: String = AppDefaults.DefaultNewDisplayWidth,
    val newDisplayHeight: String = AppDefaults.DefaultNewDisplayHeight,
    val newDisplayDpi: String = AppDefaults.DefaultNewDisplayDpi,
    val displayIdInput: String = AppDefaults.DefaultDisplayIdInput,
    val cropWidth: String = AppDefaults.DefaultCropWidth,
    val cropHeight: String = AppDefaults.DefaultCropHeight,
    val cropX: String = AppDefaults.DefaultCropX,
    val cropY: String = AppDefaults.DefaultCropY,
)

internal fun loadMainSettings(context: Context): MainSettings {
    val prefs = context.getSharedPreferences(AppPreferenceKeys.PrefsName, Context.MODE_PRIVATE)
    return MainSettings(
        themeBaseIndex = prefs.getInt(AppPreferenceKeys.ThemeBaseIndex, AppDefaults.DefaultThemeBaseIndex),
        monetEnabled = prefs.getBoolean(AppPreferenceKeys.MonetEnabled, AppDefaults.DefaultMonetEnabled),
        fullscreenDebugInfoEnabled = prefs.getBoolean(
            AppPreferenceKeys.FullscreenDebugInfoEnabled,
            AppDefaults.DefaultFullscreenDebugInfoEnabled,
        ),
        showFullscreenVirtualButtons = prefs.getBoolean(
            AppPreferenceKeys.ShowFullscreenVirtualButtons,
            AppDefaults.DefaultShowFullscreenVirtualButtons,
        ),
        devicePreviewCardHeightDp = prefs.getInt(
            AppPreferenceKeys.DevicePreviewCardHeightDp,
            AppDefaults.DefaultDevicePreviewCardHeightDp,
        ).coerceAtLeast(120),
        keepScreenOnWhenStreamingEnabled = prefs.getBoolean(
            AppPreferenceKeys.KeepScreenOnWhenStreamingEnabled,
            AppDefaults.DefaultKeepScreenOnWhenStreamingEnabled,
        ),
        virtualButtonsOutside = prefs.getString(
            AppPreferenceKeys.VirtualButtonsOutside,
            AppDefaults.DefaultVirtualButtonsOutside,
        ).orEmpty().ifBlank { AppDefaults.DefaultVirtualButtonsOutside },
        virtualButtonsInMore = prefs.getString(
            AppPreferenceKeys.VirtualButtonsInMore,
            AppDefaults.DefaultVirtualButtonsInMore,
        ).orEmpty().ifBlank { AppDefaults.DefaultVirtualButtonsInMore },
        customServerUri = prefs.getString(AppPreferenceKeys.CustomServerUri, null),
        serverRemotePath = prefs.getString(
            AppPreferenceKeys.ServerRemotePath,
            AppDefaults.DefaultServerRemotePathInput,
        ).orEmpty(),
        videoCodec = prefs.getString(AppPreferenceKeys.VideoCodec, AppDefaults.DefaultVideoCodec)
            .orEmpty()
            .ifBlank { AppDefaults.DefaultVideoCodec },
        audioEnabled = prefs.getBoolean(AppPreferenceKeys.AudioEnabled, AppDefaults.DefaultAudioEnabled),
        audioCodec = prefs.getString(AppPreferenceKeys.AudioCodec, AppDefaults.DefaultAudioCodec)
            .orEmpty()
            .ifBlank { AppDefaults.DefaultAudioCodec },
        adbKeyName = prefs.getString(AppPreferenceKeys.AdbKeyName, AppDefaults.DefaultAdbKeyNameInput).orEmpty(),
    )
}

internal fun saveMainSettings(context: Context, settings: MainSettings) {
    context.getSharedPreferences(AppPreferenceKeys.PrefsName, Context.MODE_PRIVATE)
        .edit {
            putInt(AppPreferenceKeys.ThemeBaseIndex, settings.themeBaseIndex)
                .putBoolean(AppPreferenceKeys.MonetEnabled, settings.monetEnabled)
                .putBoolean(AppPreferenceKeys.FullscreenDebugInfoEnabled, settings.fullscreenDebugInfoEnabled)
                .putBoolean(AppPreferenceKeys.ShowFullscreenVirtualButtons, settings.showFullscreenVirtualButtons)
                .putInt(AppPreferenceKeys.DevicePreviewCardHeightDp, settings.devicePreviewCardHeightDp.coerceAtLeast(120))
                .putBoolean(AppPreferenceKeys.KeepScreenOnWhenStreamingEnabled, settings.keepScreenOnWhenStreamingEnabled)
                .putString(AppPreferenceKeys.VirtualButtonsOutside, settings.virtualButtonsOutside)
                .putString(AppPreferenceKeys.VirtualButtonsInMore, settings.virtualButtonsInMore)
                .putString(AppPreferenceKeys.CustomServerUri, settings.customServerUri)
                .putString(AppPreferenceKeys.ServerRemotePath, settings.serverRemotePath)
                .putString(AppPreferenceKeys.VideoCodec, settings.videoCodec)
                .putBoolean(AppPreferenceKeys.AudioEnabled, settings.audioEnabled)
                .putString(AppPreferenceKeys.AudioCodec, settings.audioCodec)
                .putString(AppPreferenceKeys.AdbKeyName, settings.adbKeyName)
        }
}

internal fun loadDevicePageSettings(context: Context): DevicePageSettings {
    val prefs = context.getSharedPreferences(AppPreferenceKeys.PrefsName, Context.MODE_PRIVATE)
    return DevicePageSettings(
        pairHost = AppDefaults.DefaultPairHost,
        pairPort = AppDefaults.DefaultPairPort,
        pairCode = AppDefaults.DefaultPairCode,
        quickConnectInput = prefs.getString(AppPreferenceKeys.QuickConnectInput, AppDefaults.DefaultQuickConnectInput).orEmpty(),
        bitRateMbps = prefs.getFloat(AppPreferenceKeys.BitRateMbps, AppDefaults.DefaultBitRateMbps),
        bitRateInput = prefs.getString(AppPreferenceKeys.BitRateInput, AppDefaults.DefaultBitRateInput)
            .orEmpty()
            .ifBlank { AppDefaults.DefaultBitRateInput },
        maxSizeInput = prefs.getString(AppPreferenceKeys.MaxSizeInput, AppDefaults.DefaultMaxSizeInput).orEmpty(),
        audioBitRateKbps = prefs.getInt(AppPreferenceKeys.AudioBitRateKbps, AppDefaults.DefaultAudioBitRateKbps),
        maxFpsInput = prefs.getString(AppPreferenceKeys.MaxFpsInput, AppDefaults.DefaultMaxFpsInput).orEmpty(),
        noControl = prefs.getBoolean(AppPreferenceKeys.NoControl, AppDefaults.DefaultNoControl),
        videoEncoder = prefs.getString(AppPreferenceKeys.VideoEncoder, AppDefaults.DefaultVideoEncoder).orEmpty(),
        videoCodecOptions = prefs.getString(AppPreferenceKeys.VideoCodecOptions, AppDefaults.DefaultVideoCodecOptions).orEmpty(),
        audioEncoder = prefs.getString(AppPreferenceKeys.AudioEncoder, AppDefaults.DefaultAudioEncoder).orEmpty(),
        audioCodecOptions = prefs.getString(AppPreferenceKeys.AudioCodecOptions, AppDefaults.DefaultAudioCodecOptions).orEmpty(),
        audioDup = prefs.getBoolean(AppPreferenceKeys.AudioDup, AppDefaults.DefaultAudioDup),
        audioSourcePreset = prefs.getString(AppPreferenceKeys.AudioSourcePreset, AppDefaults.DefaultAudioSourcePreset)
            .orEmpty()
            .ifBlank { AppDefaults.DefaultAudioSourcePreset },
        audioSourceCustom = prefs.getString(AppPreferenceKeys.AudioSourceCustom, AppDefaults.DefaultAudioSourceCustom).orEmpty(),
        videoSourcePreset = prefs.getString(AppPreferenceKeys.VideoSourcePreset, AppDefaults.DefaultVideoSourcePreset)
            .orEmpty()
            .ifBlank { AppDefaults.DefaultVideoSourcePreset },
        cameraIdInput = prefs.getString(AppPreferenceKeys.CameraIdInput, AppDefaults.DefaultCameraIdInput).orEmpty(),
        cameraFacingPreset = prefs.getString(AppPreferenceKeys.CameraFacingPreset, AppDefaults.DefaultCameraFacingPreset).orEmpty(),
        cameraSizePreset = prefs.getString(AppPreferenceKeys.CameraSizePreset, AppDefaults.DefaultCameraSizePreset).orEmpty(),
        cameraSizeCustom = prefs.getString(AppPreferenceKeys.CameraSizeCustom, AppDefaults.DefaultCameraSizeCustom).orEmpty(),
        cameraArInput = prefs.getString(AppPreferenceKeys.CameraArInput, AppDefaults.DefaultCameraArInput).orEmpty(),
        cameraFpsInput = prefs.getString(AppPreferenceKeys.CameraFpsInput, AppDefaults.DefaultCameraFpsInput).orEmpty(),
        cameraHighSpeed = prefs.getBoolean(AppPreferenceKeys.CameraHighSpeed, AppDefaults.DefaultCameraHighSpeed),
        noAudioPlayback = prefs.getBoolean(AppPreferenceKeys.NoAudioPlayback, AppDefaults.DefaultNoAudioPlayback),
        noVideo = prefs.getBoolean(AppPreferenceKeys.NoVideo, AppDefaults.DefaultNoVideo),
        requireAudio = prefs.getBoolean(AppPreferenceKeys.RequireAudio, AppDefaults.DefaultRequireAudio),
        turnScreenOff = prefs.getBoolean(AppPreferenceKeys.TurnScreenOff, AppDefaults.DefaultTurnScreenOff),
        newDisplayWidth = prefs.getString(AppPreferenceKeys.NewDisplayWidth, AppDefaults.DefaultNewDisplayWidth).orEmpty(),
        newDisplayHeight = prefs.getString(AppPreferenceKeys.NewDisplayHeight, AppDefaults.DefaultNewDisplayHeight).orEmpty(),
        newDisplayDpi = prefs.getString(AppPreferenceKeys.NewDisplayDpi, AppDefaults.DefaultNewDisplayDpi).orEmpty(),
        displayIdInput = prefs.getString(AppPreferenceKeys.DisplayIdInput, AppDefaults.DefaultDisplayIdInput).orEmpty(),
        cropWidth = prefs.getString(AppPreferenceKeys.CropWidth, AppDefaults.DefaultCropWidth).orEmpty(),
        cropHeight = prefs.getString(AppPreferenceKeys.CropHeight, AppDefaults.DefaultCropHeight).orEmpty(),
        cropX = prefs.getString(AppPreferenceKeys.CropX, AppDefaults.DefaultCropX).orEmpty(),
        cropY = prefs.getString(AppPreferenceKeys.CropY, AppDefaults.DefaultCropY).orEmpty(),
    )
}

internal fun saveDevicePageSettings(context: Context, settings: DevicePageSettings) {
    context.getSharedPreferences(AppPreferenceKeys.PrefsName, Context.MODE_PRIVATE)
        .edit {
            remove(AppPreferenceKeys.PairHost)
                .remove(AppPreferenceKeys.PairPort)
                .remove(AppPreferenceKeys.PairCode)
                .putString(AppPreferenceKeys.QuickConnectInput, settings.quickConnectInput)
                .putFloat(AppPreferenceKeys.BitRateMbps, settings.bitRateMbps)
                .putString(AppPreferenceKeys.BitRateInput, settings.bitRateInput)
                .putInt(AppPreferenceKeys.AudioBitRateKbps, settings.audioBitRateKbps)
                .putString(AppPreferenceKeys.MaxSizeInput, settings.maxSizeInput)
                .putString(AppPreferenceKeys.MaxFpsInput, settings.maxFpsInput)
                .putBoolean(AppPreferenceKeys.NoControl, settings.noControl)
                .putString(AppPreferenceKeys.VideoEncoder, settings.videoEncoder)
                .putString(AppPreferenceKeys.VideoCodecOptions, settings.videoCodecOptions)
                .putString(AppPreferenceKeys.AudioEncoder, settings.audioEncoder)
                .putString(AppPreferenceKeys.AudioCodecOptions, settings.audioCodecOptions)
                .putBoolean(AppPreferenceKeys.AudioDup, settings.audioDup)
                .putString(AppPreferenceKeys.AudioSourcePreset, settings.audioSourcePreset)
                .putString(AppPreferenceKeys.AudioSourceCustom, settings.audioSourceCustom)
                .putString(AppPreferenceKeys.VideoSourcePreset, settings.videoSourcePreset)
                .putString(AppPreferenceKeys.CameraIdInput, settings.cameraIdInput)
                .putString(AppPreferenceKeys.CameraFacingPreset, settings.cameraFacingPreset)
                .putString(AppPreferenceKeys.CameraSizePreset, settings.cameraSizePreset)
                .putString(AppPreferenceKeys.CameraSizeCustom, settings.cameraSizeCustom)
                .putString(AppPreferenceKeys.CameraArInput, settings.cameraArInput)
                .putString(AppPreferenceKeys.CameraFpsInput, settings.cameraFpsInput)
                .putBoolean(AppPreferenceKeys.CameraHighSpeed, settings.cameraHighSpeed)
                .putBoolean(AppPreferenceKeys.NoAudioPlayback, settings.noAudioPlayback)
                .putBoolean(AppPreferenceKeys.NoVideo, settings.noVideo)
                .putBoolean(AppPreferenceKeys.RequireAudio, settings.requireAudio)
                .putBoolean(AppPreferenceKeys.TurnScreenOff, settings.turnScreenOff)
                .putString(AppPreferenceKeys.NewDisplayWidth, settings.newDisplayWidth)
                .putString(AppPreferenceKeys.NewDisplayHeight, settings.newDisplayHeight)
                .putString(AppPreferenceKeys.NewDisplayDpi, settings.newDisplayDpi)
                .putString(AppPreferenceKeys.DisplayIdInput, settings.displayIdInput)
                .putString(AppPreferenceKeys.CropWidth, settings.cropWidth)
                .putString(AppPreferenceKeys.CropHeight, settings.cropHeight)
                .putString(AppPreferenceKeys.CropX, settings.cropX)
                .putString(AppPreferenceKeys.CropY, settings.cropY)
        }
}
