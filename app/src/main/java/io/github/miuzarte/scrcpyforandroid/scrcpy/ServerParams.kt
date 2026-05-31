package io.github.miuzarte.scrcpyforandroid.scrcpy

import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.*

// 启动 scrcpy 前直接继承自 [ClientOptions],
// 不需要默认值
data class ServerParams(
    val scid: UInt, // (random uint32) & 0x7FFFFFFF

    // val reqSerial: String,

    val logLevel: LogLevel,

    val videoCodec: Codec,
    val audioCodec: Codec,
    val videoSource: VideoSource,
    val audioSource: AudioSource,

    val cameraFacing: CameraFacing,

    val crop: String,

    val videoCodecOptions: String,
    val audioCodecOptions: String,
    val videoEncoder: String,
    val audioEncoder: String,

    val cameraId: String,
    val cameraSize: String,
    val cameraAr: String, // aspect ratio
    val cameraZoom: String,
    val cameraFps: UShort,

    // val portRange: PortRange, // sc_port_range(first, last)
    // val tunnelHost: UInt,
    // val tunnelPort: UShort,

    val maxSize: UShort,
    val minSizeAlignment: UByte,

    val videoBitRate: Int,
    val audioBitRate: Int,

    val maxFps: String, // float to be parsed by the server
    val angle: String, // float to be parsed by the server

    val screenOffTimeout: Tick,

    val captureOrientation: Orientation,
    val captureOrientationLock: OrientationLock,

    val control: Boolean,

    val displayId: Int,
    val newDisplay: String,

    val displayImePolicy: DisplayImePolicy,

    val video: Boolean,
    val audio: Boolean,
    val audioDup: Boolean,
    val showTouches: Boolean,
    val stayAwake: Boolean,

    // val forceAdbForward: Boolean,

    val powerOffOnClose: Boolean,
    val legacyPaste: Boolean,
    val clipboardAutosync: Boolean,

    val downsizeOnError: Boolean,
    // val tcpip: Boolean,
    // val tcpipDst: String,
    // val selectUsb: Boolean,
    // val selectTcpip: Boolean,

    val cleanUp: Boolean,
    val powerOn: Boolean,

    // val killAdbOnClose: Boolean,

    val cameraHighSpeed: Boolean,
    val cameraTorch: Boolean,

    val vdDestroyContent: Boolean,
    val vdSystemDecorations: Boolean,
    val keepActive: Boolean,
    val flexDisplay: Boolean,

    val list: ListOptions,
) {
    companion object {
        const val SEPARATOR: String = " "
        const val ILLEGAL_CHARACTER_SET: String = " ;'\"*$?&`#\\|<>[]{}()!~\r\n"
    }

    private fun validate(str: String) {
        // forbid special shell characters
        if (str.any { it in ILLEGAL_CHARACTER_SET }) {
            throw IllegalArgumentException("Invalid server param: [$str]")
        }
    }

    fun build(vararg extraArgs: String): String {
        return (extraArgs.toList() + this.toList()).joinToString(SEPARATOR)
    }

    fun toList(preview: Boolean = false): MutableList<String> {
        val cmd = mutableListOf<String>()

        if (!preview) cmd.add("scid=${scid.toString(16)}")

        cmd.add("log_level=${logLevel.string}")

        if (!video) {
            cmd.add("video=false")
        }
        if (videoBitRate > 0) {
            cmd.add("video_bit_rate=$videoBitRate")
        }
        if (!audio) {
            cmd.add("audio=false")
        }
        if (audioBitRate > 0) {
            if (!audioCodec.isLossless) {
                // 比官方实现多个判断编解码器类型
                cmd.add("audio_bit_rate=$audioBitRate")
            }
        }
        if (videoCodec != Codec.H264) {
            cmd.add("video_codec=${videoCodec.string}")
        }
        if (audioCodec != Codec.OPUS) {
            cmd.add("audio_codec=${audioCodec.string}")
        }
        if (videoSource != VideoSource.DISPLAY) {
            cmd.add("video_source=${videoSource.string}")
        }
        // audio_source (default: output, only add if different and audio is enabled)
        // Note: AUTO should have been resolved by ClientOptions.validate()
        if (audioSource != AudioSource.OUTPUT && audio) {
            cmd.add("audio_source=${audioSource.string}")
        }
        if (audioDup) {
            cmd.add("audio_dup=true")
        }
        if (maxSize > 0u) {
            cmd.add("max_size=$maxSize")
        }
        if (maxFps.isNotBlank()) {
            validate(maxFps)
            cmd.add("max_fps=${maxFps.trim()}")
        }
        if (minSizeAlignment != 1.toUByte()) {
            cmd.add("min_size_alignment=${minSizeAlignment}")
        }
        if (angle.isNotBlank()) {
            validate(angle)
            cmd.add("angle=${angle.trim()}")
        }
        if (captureOrientationLock != OrientationLock.UNLOCKED
            || captureOrientation != Orientation.ORIENT_0
        ) {
            when (captureOrientationLock) {
                OrientationLock.LOCKED_INITIAL ->
                    cmd.add("capture_orientation=@")

                OrientationLock.LOCKED_VALUE ->
                    cmd.add("capture_orientation=@${captureOrientation.string}")

                OrientationLock.UNLOCKED ->
                    cmd.add("capture_orientation=${captureOrientation.string}")
            }
        }
        // always true cause we are using adb wireless
        cmd.add("tunnel_forward=true")
        if (crop.isNotBlank()) {
            validate(crop)
            cmd.add("crop=${crop.trim()}")
        }
        if (!control) {
            // By default, control is true
            cmd.add("control=false")
        }
        if (displayId >= 0) {
            cmd.add("display_id=$displayId")
        }
        if (cameraId.isNotBlank()) {
            validate(cameraId)
            cmd.add("camera_id=${cameraId.trim()}")
        }
        if (cameraSize.isNotBlank()) {
            validate(cameraSize)
            cmd.add("camera_size=${cameraSize.trim()}")
        }
        if (cameraFacing != CameraFacing.ANY) {
            cmd.add("camera_facing=${cameraFacing.string}")
        }
        if (cameraAr.isNotBlank()) {
            validate(cameraAr)
            cmd.add("camera_ar=${cameraAr.trim()}")
        }
        if (cameraFps > 0u) {
            cmd.add("camera_fps=$cameraFps")
        }
        if (cameraHighSpeed) {
            cmd.add("camera_high_speed=true")
        }
        if (cameraTorch) {
            cmd.add("camera_torch=true")
        }
        if (cameraZoom.isNotBlank()) {
            validate(cameraZoom)
            cmd.add("camera_zoom=${cameraZoom.trim()}")
        }
        if (showTouches) {
            cmd.add("show_touches=true")
        }
        if (stayAwake) {
            cmd.add("stay_awake=true")
        }
        if (screenOffTimeout.value != -1L) {
            require(screenOffTimeout >= 0) {
                "screen_off_timeout must be >= 0"
            }
            cmd.add("screen_off_timeout=${screenOffTimeout.ms}")
        }
        if (videoCodecOptions.isNotBlank()) {
            validate(videoCodecOptions)
            cmd.add("video_codec_options=${videoCodecOptions.trim()}")
        }
        if (audioCodecOptions.isNotBlank()) {
            validate(audioCodecOptions)
            cmd.add("audio_codec_options=${audioCodecOptions.trim()}")
        }
        if (videoEncoder.isNotBlank()) {
            validate(videoEncoder)
            cmd.add("video_encoder=${videoEncoder.trim()}")
        }
        if (audioEncoder.isNotBlank()) {
            validate(audioEncoder)
            cmd.add("audio_encoder=${audioEncoder.trim()}")
        }
        if (powerOffOnClose) {
            cmd.add("power_off_on_close=true")
        }
        if (legacyPaste) {
            cmd.add("legacy_paste=true")
        }
        if (!clipboardAutosync) {
            cmd.add("clipboard_autosync=false")
        }
        if (!downsizeOnError) {
            cmd.add("downsize_on_error=false")
        }
        if (!cleanUp) {
            // By default, cleanup is true
            cmd.add("cleanup=false")
        }
        if (!powerOn) {
            // By default, power_on is true
            cmd.add("power_on=false")
        }
        if (newDisplay.isNotBlank()) {
            validate(newDisplay)
            cmd.add("new_display=${newDisplay.trim()}")
        }
        if (flexDisplay) {
            cmd.add("flex_display=true")
        }
        if (displayImePolicy != DisplayImePolicy.UNDEFINED) {
            cmd.add("display_ime_policy=${displayImePolicy.string}")
        }
        if (!vdDestroyContent) {
            cmd.add("vd_destroy_content=false")
        }
        if (!vdSystemDecorations) {
            cmd.add("vd_system_decorations=false")
        }
        if (keepActive) {
            cmd.add("keep_active=true")
        }
        if (list has ListOptions.ENCODERS) {
            cmd.add("list_encoders=true")
        }
        if (list has ListOptions.DISPLAYS) {
            cmd.add("list_displays=true")
        }
        if (list has ListOptions.CAMERAS) {
            cmd.add("list_cameras=true")
        }
        if (list has ListOptions.CAMERA_SIZES) {
            cmd.add("list_camera_sizes=true")
        }
        if (list has ListOptions.APPS) {
            cmd.add("list_apps=true")
        }
        return cmd
    }
}
