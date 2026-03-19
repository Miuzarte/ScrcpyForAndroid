package io.github.miuzarte.scrcpyforandroid.constants

object AppDefaults {
    const val MaxEventLogLines = 512
    const val DefaultAdbPort = 5555

    // Devices
    const val DefaultQuickConnectInput = ""

    const val DefaultPairHost = ""
    const val DefaultServerRemotePathInput = ""
    const val DefaultAdbKeyNameInput = ""
    const val DefaultPairPort = ""
    const val DefaultPairCode = ""

    const val DefaultAudioEnabled = true
    const val DefaultAudioCodec = "opus"
    const val DefaultAudioBitRateKbps = 128
    const val DefaultVideoCodec = "h264"
    const val DefaultBitRateMbps = 8f
    const val DefaultBitRateInput = "8.0"

    const val DefaultTurnScreenOff = false
    const val DefaultNoControl = false
    const val DefaultNoVideo = false

    const val DefaultVideoSourcePreset = "display"
    const val DefaultDisplayIdInput = ""

    const val DefaultCameraIdInput = ""
    const val DefaultCameraFacingPreset = ""
    const val DefaultCameraSizePreset = ""
    const val DefaultCameraSizeCustom = ""
    const val DefaultCameraArInput = ""
    const val DefaultCameraFpsInput = ""
    const val DefaultCameraHighSpeed = false

    const val DefaultAudioSourcePreset = "output"
    const val DefaultAudioSourceCustom = ""
    const val DefaultAudioDup = false
    const val DefaultNoAudioPlayback = false
    const val DefaultRequireAudio = false

    const val DefaultMaxSizeInput = ""
    const val DefaultMaxFpsInput = ""

    const val DefaultVideoEncoder = ""
    const val DefaultVideoCodecOptions = ""
    const val DefaultAudioEncoder = ""
    const val DefaultAudioCodecOptions = ""

    const val DefaultNewDisplayWidth = ""
    const val DefaultNewDisplayHeight = ""
    const val DefaultNewDisplayDpi = ""

    const val DefaultCropWidth = ""
    const val DefaultCropHeight = ""
    const val DefaultCropX = ""
    const val DefaultCropY = ""

    // Settings
    const val DefaultThemeBaseIndex = 0
    const val DefaultMonetEnabled = false

    const val DefaultFullscreenDebugInfoEnabled = false
    const val DefaultShowFullscreenVirtualButtons = true
    const val DefaultDevicePreviewCardHeightDp = 320
    const val DefaultKeepScreenOnWhenStreamingEnabled = false
    const val DefaultVirtualButtonsOutside = "more,home,back"
    const val DefaultVirtualButtonsInMore = "app_switch,menu,notification,volume_up,volume_down,volume_mute,power,screenshot"

    const val DefaultServerRemotePath = "/data/local/tmp/scrcpy-server.jar"

    const val DefaultAdbKeyName = "scrcpy"
}
