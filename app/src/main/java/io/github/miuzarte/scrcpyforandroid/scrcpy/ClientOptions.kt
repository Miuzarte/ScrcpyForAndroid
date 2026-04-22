package io.github.miuzarte.scrcpyforandroid.scrcpy

import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.AudioSource
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.CameraFacing
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.Codec
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.DisplayImePolicy
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.ListOptions
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.LogLevel
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.Orientation
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.OrientationLock
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.Tick
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.VideoSource

data class ClientOptions(
    // var serial: String = "", // to server

    // --crop=width:height:x:y
    var crop: String = "", // to server

    // TODO: implement
    // --record
    var recordFilename: String = "",

    // var windowTitle: String = "",
    // var pushTarget: String = "",
    // var renderDriver: String = "",

    // --video-codec-options
    var videoCodecOptions: String = "", // to server
    // --audio-codec-options
    var audioCodecOptions: String = "", // to server
    // --video-encoder
    var videoEncoder: String = "", // to server
    // --audio-encoder
    var audioEncoder: String = "", // to server

    // --camera-id
    var cameraId: String = "", // to server
    // --camera-size
    var cameraSize: String = "", // to server
    // --camera-ar
    var cameraAr: String = "", // to server
    // --camera-fps
    var cameraFps: UShort = 0u, // to server

    var logLevel: LogLevel = LogLevel.INFO, // to server

    // --video-codec
    var videoCodec: Codec = Codec.H264, // to server
    // --audio-codec
    var audioCodec: Codec = Codec.OPUS, // to server
    // --video-source
    var videoSource: VideoSource = VideoSource.DISPLAY, // to server
    // --audio-source
    var audioSource: AudioSource = AudioSource.AUTO, // to server

    // --record-format
    var recordFormat: RecordFormat = RecordFormat.AUTO,

    // var keyboardInputMode: KeyboardInputMode,
    // var mouseInputMode: MouseInputMode,
    // var gamepadInputMode: GamepadInputMode,

    // sc_mouse_bindings(pri, sec)
    // var mouseBindings: MouseBindings,

    // --camera-facing
    var cameraFacing: CameraFacing = CameraFacing.ANY, // to server

    // var portRange: PortRange, // sc_port_range(first, last) // to server
    // var tunnelHost: UInt, // to server
    // var tunnelPort: UShort, // to server

    // OR of enum sc_shortcut_mod values
    // var shortcutMods: ShortcutMod,

    // --max-size
    var maxSize: UShort = 0u, // to server

    // --video-bit-rate
    var videoBitRate: Int = 0, // to server
    // --audio-bit-rate
    var audioBitRate: Int = 0, // to server

    // --max-fps
    var maxFps: String = "", // float to be parsed by the server
    var angle: String = "", // float to be parsed by the server

    // --capture-orientation=.*
    var captureOrientation: Orientation = Orientation.ORIENT_0, // to server
    // --capture-orientation=@.*
    var captureOrientationLock: OrientationLock = OrientationLock.UNLOCKED, // to server

    // --display-orientation
    var displayOrientation: Orientation = Orientation.ORIENT_0,
    // --record-orientation
    var recordOrientation: Orientation = Orientation.ORIENT_0,

    // --display-ime-policy
    var displayImePolicy: DisplayImePolicy = DisplayImePolicy.UNDEFINED, // to server

    // var windowX: Short,
    // var windowY: Short,
    // var windowWidth: UShort,
    // var windowHeight: UShort,

    // --display-id
    // -1 for empty text field
    var displayId: Int = -1, // to server

    // var videoBuffer: Tick,
    // var audioBuffer: Tick,
    // var audioOutputBuffer: Tick,
    // var timeLimit: Tick,

    // --screen-off-timeout
    var screenOffTimeout: Tick = Tick(-1), // to server

    // var otg: Boolean,

    // --show-touches
    var showTouches: Boolean = false, // to server
    // 开始后立即进入全屏
    // --fullscreen
    var fullscreen: Boolean = false,

    // var alwaysOnTop: Boolean,

    // --no-control
    var control: Boolean = true, // to server
    // --no-playback
    // --no-video-playback
    var videoPlayback: Boolean = true,
    // --no-playback
    // --no-audio-playback
    var audioPlayback: Boolean = true,
    // --turn-screen-off
    var turnScreenOff: Boolean = false,

    // [sc_key_inject_mode]
    // var keyInjectMode: KeyInjectMode,

    // var windowBorderless: Boolean,
    // var mipmaps: Boolean,

    // --stay-awake
    var stayAwake: Boolean = false, // to server

    // --force-adb-forward
    // var forceAdbForward: Boolean, // to server

    // --disable-screensaver
    var disableScreensaver: Boolean = false,

    // var forwardKeyRepeat: Boolean,
    var legacyPaste: Boolean = false,

    // --power-off-on-close
    var powerOffOnClose: Boolean = false, // to server

    // --no-clipboard-autosync
    var clipboardAutosync: Boolean = true, // to server

    // --no-downsize-on-error
    var downsizeOnError: Boolean = true, // to server

    // var tcpip: Boolean, // to server
    // var tcpipDst: String = "", // to server
    // var selectUsb: Boolean, // to server
    // var selectTcpip: Boolean, // to server

    // --no-cleanup
    var cleanup: Boolean = true, // to server

    // var startFpsCounter: Boolean,

    // --no-power-on
    var powerOn: Boolean = true, // to server
    // --no-video
    var video: Boolean = true, // to server
    // --no-audio
    var audio: Boolean = true, // to server
    // --require-audio
    var requireAudio: Boolean = false,

    // 结束 scrcpy 后断开 adb 连接
    // --kill-adb-on-close
    var killAdbOnClose: Boolean = false, // to server but client side
    // --camera-high-speed
    var cameraHighSpeed: Boolean = false, // to server

    var list: ListOptions = ListOptions.NULL, // to server

    // --no-window
    // var window: Boolean,

    // --no-mouse-hover
    var mouseHover: Boolean = true,

    // --audio-dup
    var audioDup: Boolean = false, // to server
    // --new-display=[<width>x<height>][/<dpi>]
    var newDisplay: String = "", // to server
    // --start-app
    var startApp: String = "",

    // --no-vd-destroy-content
    var vdDestroyContent: Boolean = true, // to server
    // --no-vd-system-decorations
    var vdSystemDecorations: Boolean = true, // to server
) {
    enum class RecordFormat(val string: String) {
        AUTO("auto"), // ignore
        MP4("mp4"),
        MKV("mkv"),
        M4A("m4a"),
        MKA("mka"),
        OPUS("opus"),
        AAC("aac"),
        FLAC("flac"),
        WAV("wav");

        fun isAudioOnly(): Boolean = when (this) {
            M4A, MKA, OPUS, AAC, FLAC, WAV -> true
            else -> false
        }

        companion object {
            fun fromString(value: String) =
                entries.find { it.string.equals(value, ignoreCase = true) }
                    ?: AUTO
        }
    }

    fun fix(): ClientOptions {
        when (videoSource) {
            VideoSource.DISPLAY -> {
                cameraId = ""
                cameraFacing = CameraFacing.ANY
                cameraSize = ""
                cameraAr = ""
                cameraFps = 0u
                cameraHighSpeed = false
            }

            VideoSource.CAMERA -> {
                displayId = 0
                maxSize = 0u
                maxFps = ""
                newDisplay = ""
                crop = ""
            }
        }
        return this
    }

    fun validate(): ClientOptions {
        if (!video) {
            videoPlayback = false
            powerOn = false
        }

        if (!audio) {
            audioPlayback = false
        }

        if (video && !videoPlayback && recordFilename.isBlank()) {
            video = false
        }

        if (audio && !audioPlayback && recordFilename.isBlank()) {
            audio = false
        }

        if (!video && !audio && !control) {
            throw IllegalArgumentException(
                "nothing to do"
            )
        }

        if (!video) {
            requireAudio = true
        }

        if (newDisplay.isNotBlank()) {
            if (videoSource != VideoSource.DISPLAY) {
                throw IllegalArgumentException(
                    "--new-display is only available with --video-source=display"
                )
            }

            if (!video) {
                throw IllegalArgumentException(
                    "--new-display is incompatible with --no-video"
                )
            }
        }

        if (videoSource == VideoSource.CAMERA) {
            if (displayId > 0) {
                throw IllegalArgumentException(
                    "--display-id is only available with --video-source=display"
                )
            }

            if (displayImePolicy != DisplayImePolicy.UNDEFINED) {
                throw IllegalArgumentException(
                    "--display-ime-policy is only available with --video-source=display"
                )
            }

            if (cameraId.isNotBlank() && cameraFacing != CameraFacing.ANY) {
                throw IllegalArgumentException(
                    "Cannot specify both --camera-id and --camera-facing"
                )
            }

            if (cameraSize.isNotBlank()) {
                if (maxSize > 0u) {
                    throw IllegalArgumentException(
                        "Cannot specify both --camera-size and -m/--max-size"
                    )
                }

                if (cameraAr.isNotBlank()) {
                    throw IllegalArgumentException(
                        "--camera-high-speed requires an explicit --camera-fps value"
                    )
                }
            }

            if (cameraHighSpeed && cameraFps > 0u) {
                throw IllegalArgumentException(
                    "--camera-high-speed requires an explicit --camera-fps value"
                )
            }

            if (control) {
                control = false
            }
        } else if (cameraId.isNotBlank()
            || cameraAr.isNotBlank()
            || cameraFacing != CameraFacing.ANY
            || cameraFps > 0u
            || cameraHighSpeed
            || cameraSize.isNotBlank()
        ) {
            throw IllegalArgumentException(
                "Camera options are only available with --video-source=camera"
            )
        }

        if (displayId > 0 && newDisplay.isNotBlank()) {
            throw IllegalArgumentException(
                "Cannot specify both --display-id and --new-display"
            )
        }

        if (displayImePolicy != DisplayImePolicy.UNDEFINED
            && displayId == 0 && newDisplay.isBlank()
        ) {
            throw IllegalArgumentException(
                "--display-ime-policy is only supported on a secondary display"
            )
        }

        if (audio && audioSource == AudioSource.AUTO) {
            // Select the audio source according to the video source
            audioSource = if (videoSource == VideoSource.DISPLAY) {
                if (audioDup) {
                    AudioSource.PLAYBACK
                } else {
                    AudioSource.OUTPUT
                }
            } else {
                AudioSource.MIC
            }
        }

        if (audioDup) {
            if (!audio) {
                throw IllegalArgumentException(
                    "--audio-dup not supported if audio is disabled"
                )
            }

            if (audioSource != AudioSource.PLAYBACK) {
                throw IllegalArgumentException(
                    "--audio-dup is specific to --audio-source=playback"
                )
            }
        }

        if (recordFormat != RecordFormat.AUTO && recordFilename.isNotBlank()) {
            throw IllegalArgumentException(
                "Record format specified without recording"
            )
        }

        if (recordFilename.isNotBlank()) {
            if (!video && !audio) {
                throw IllegalArgumentException(
                    "Video and audio disabled, nothing to record"
                )
            }

            if (recordFormat == RecordFormat.AUTO) {
                // TODO: guess from recordFilename
                recordFormat = RecordFormat.MP4
            }

            if (recordOrientation != Orientation.ORIENT_0) {
                if (recordOrientation.isMirror()) {
                    throw IllegalArgumentException(
                        "Record orientation only supports rotation, " +
                                "not flipping: ${recordOrientation.string}"
                    )
                }
            }

            if (video && recordFormat.isAudioOnly()) {
                throw IllegalArgumentException(
                    "Audio container does not support video stream"
                )
            }

            if (recordFormat == RecordFormat.OPUS && audioCodec != Codec.OPUS) {
                throw IllegalArgumentException(
                    "Recording to OPUS file requires an OPUS audio stream " +
                            "(try with --audio-codec=opus)"
                )
            }

            if (recordFormat == RecordFormat.AAC && audioCodec != Codec.AAC) {
                throw IllegalArgumentException(
                    "Recording to AAC file requires an AAC audio stream " +
                            "(try with --audio-codec=aac)"
                )
            }

            if (recordFormat == RecordFormat.FLAC && audioCodec != Codec.FLAC) {
                throw IllegalArgumentException(
                    "Recording to FLAC file requires an FLAC audio stream " +
                            "(try with --audio-codec=flac)"
                )
            }

            if (recordFormat == RecordFormat.WAV && audioCodec != Codec.RAW) {
                throw IllegalArgumentException(
                    "Recording to WAV file requires an RAW audio stream " +
                            "(try with --audio-codec=raw)"
                )
            }

            if ((recordFormat == RecordFormat.MP4 || recordFormat == RecordFormat.M4A)
                && audioCodec == Codec.RAW
            ) {
                throw IllegalArgumentException(
                    "Recording to MP4 container does not support RAW audio"
                )
            }
        }

        /*
        if (audioCodec == Codec.FLAC && audioBitRate > 0u) {
            // "--audio-bit-rate is ignored for FLAC audio codec"
            // audioBitRate
        }
         */

        /*
        if (audioCodec == Codec.RAW) {
            if (audioBitRate > 0u) {
                // "--audio-bit-rate is ignored for raw audio codec"
                // audioBitRate
            }
            if (audioCodecOptions.isNotBlank()) {
                // "--audio-codec-options is ignored for raw audio codec"
                // audioCodecOptions
            }
            if (audioEncoder.isNotBlank()) {
                // "--audio-encoder is ignored for raw audio codec"
                // audioEncoder
            }
        }
         */

        if (!control) {
            if (turnScreenOff) {
                throw IllegalArgumentException(
                    "Cannot request to turn screen off if control is disabled"
                )
            }
            if (stayAwake) {
                throw IllegalArgumentException(
                    "Cannot request to stay awake if control is disabled"
                )
            }
            if (showTouches) {
                throw IllegalArgumentException(
                    "Cannot request to show touches if control is disabled"
                )
            }
            if (powerOffOnClose) {
                throw IllegalArgumentException(
                    "Cannot request power off on close if control is disabled"
                )
            }
            if (startApp.isNotBlank()) {
                throw IllegalArgumentException(
                    "Cannot start an Android app if control is disabled"
                )
            }
        }

        return this
    }

    fun toServerParams(scid: UInt): ServerParams {
        return ServerParams(
            scid = scid,

            logLevel = logLevel,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            videoSource = videoSource,
            audioSource = audioSource,
            cameraFacing = cameraFacing,
            crop = crop,

            maxSize = maxSize,
            videoBitRate = videoBitRate,
            audioBitRate = audioBitRate,
            maxFps = maxFps,
            angle = angle,
            screenOffTimeout = screenOffTimeout,
            captureOrientation = captureOrientation,
            captureOrientationLock = captureOrientationLock,
            control = control,
            displayId = displayId,
            newDisplay = newDisplay,
            displayImePolicy = displayImePolicy,
            video = video,
            audio = audio,
            audioDup = audioDup,
            showTouches = showTouches,
            stayAwake = stayAwake,
            videoCodecOptions = videoCodecOptions,
            audioCodecOptions = audioCodecOptions,
            videoEncoder = videoEncoder,
            audioEncoder = audioEncoder,
            cameraId = cameraId,
            cameraSize = cameraSize,
            cameraAr = cameraAr,
            cameraFps = cameraFps,

            powerOffOnClose = powerOffOnClose,
            legacyPaste = legacyPaste,
            clipboardAutosync = clipboardAutosync,

            downsizeOnError = downsizeOnError,

            cleanUp = cleanup,
            powerOn = powerOn,

            // killAdbOnClose == killAdbOnClose, // client side

            cameraHighSpeed = cameraHighSpeed,
            vdDestroyContent = vdDestroyContent,
            vdSystemDecorations = vdSystemDecorations,
            list = list,
        )
    }
}
