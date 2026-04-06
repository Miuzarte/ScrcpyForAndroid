package io.github.miuzarte.scrcpyforandroid.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.miuzarte.scrcpyforandroid.scrcpy.ClientOptions
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
import kotlinx.coroutines.runBlocking

class ScrcpyOptions(context: Context) : Settings(context, "ScrcpyOptions") {
    companion object {
        val CROP = Pair(
            stringPreferencesKey("crop"),
            "",
        )
        val RECORD_FILENAME = Pair(
            stringPreferencesKey("record_filename"),
            "",
        )
        val VIDEO_CODEC_OPTIONS = Pair(
            stringPreferencesKey("video_codec_options"),
            "",
        )
        val AUDIO_CODEC_OPTIONS = Pair(
            stringPreferencesKey("audio_codec_options"),
            "",
        )
        val VIDEO_ENCODER = Pair(
            stringPreferencesKey("video_encoder"),
            "",
        )
        val AUDIO_ENCODER = Pair(
            stringPreferencesKey("audio_encoder"),
            "",
        )
        val CAMERA_ID = Pair(
            stringPreferencesKey("camera_id"),
            "",
        )
        val CAMERA_SIZE = Pair(
            stringPreferencesKey("camera_size"),
            "",
        )
        val CAMERA_SIZE_CUSTOM = Pair(
            stringPreferencesKey("camera_size_custom"),
            "",
        )
        val CAMERA_SIZE_USE_CUSTOM = Pair(
            booleanPreferencesKey("camera_size_use_custom"),
            false,
        )
        val CAMERA_AR = Pair(
            stringPreferencesKey("camera_ar"),
            "",
        )
        val CAMERA_FPS = Pair(
            intPreferencesKey("camera_fps"),
            0,
        )
        val LOG_LEVEL = Pair(
            stringPreferencesKey("log_level"),
            "info",
        )
        val VIDEO_CODEC = Pair(
            stringPreferencesKey("video_codec"),
            "h264",
        )
        val AUDIO_CODEC = Pair(
            stringPreferencesKey("audio_codec"),
            "opus",
        )
        val VIDEO_SOURCE = Pair(
            stringPreferencesKey("video_source"),
            "display",
        )
        val AUDIO_SOURCE = Pair(
            stringPreferencesKey("audio_source"),
            "output",
        )
        val RECORD_FORMAT = Pair(
            stringPreferencesKey("record_format"),
            "auto",
        )
        val CAMERA_FACING = Pair(
            stringPreferencesKey("camera_facing"),
            "any",
        )
        val MAX_SIZE = Pair(
            intPreferencesKey("max_size"),
            0,
        )
        val VIDEO_BIT_RATE = Pair(
            intPreferencesKey("video_bit_rate"),
            8000000,
        )
        val AUDIO_BIT_RATE = Pair(
            intPreferencesKey("audio_bit_rate"),
            128000,
        )
        val MAX_FPS = Pair(
            stringPreferencesKey("max_fps"),
            "",
        )
        val ANGLE = Pair(
            stringPreferencesKey("angle"),
            "",
        )
        val CAPTURE_ORIENTATION = Pair(
            intPreferencesKey("capture_orientation"),
            0,
        )
        val CAPTURE_ORIENTATION_LOCK = Pair(
            stringPreferencesKey("capture_orientation_lock"),
            "unlocked",
        )
        val DISPLAY_ORIENTATION = Pair(
            intPreferencesKey("display_orientation"),
            0,
        )
        val RECORD_ORIENTATION = Pair(
            intPreferencesKey("record_orientation"),
            0,
        )
        val DISPLAY_IME_POLICY = Pair(
            stringPreferencesKey("display_ime_policy"),
            "undefined",
        )
        val DISPLAY_ID = Pair(
            intPreferencesKey("display_id"),
            -1, // undefined
        )
        val SCREEN_OFF_TIMEOUT = Pair(
            longPreferencesKey("screen_off_timeout"),
            -1,
        )
        val SHOW_TOUCHES = Pair(
            booleanPreferencesKey("show_touches"),
            false,
        )
        val FULLSCREEN = Pair(
            booleanPreferencesKey("fullscreen"),
            false,
        )
        val CONTROL = Pair(
            booleanPreferencesKey("control"),
            true,
        )
        val VIDEO_PLAYBACK = Pair(
            booleanPreferencesKey("video_playback"),
            true,
        )
        val AUDIO_PLAYBACK = Pair(
            booleanPreferencesKey("audio_playback"),
            true,
        )
        val TURN_SCREEN_OFF = Pair(
            booleanPreferencesKey("turn_screen_off"),
            false,
        )
        val STAY_AWAKE = Pair(
            booleanPreferencesKey("stay_awake"),
            false,
        )
        val DISABLE_SCREENSAVER = Pair(
            booleanPreferencesKey("disable_screensaver"),
            false,
        )
        val POWER_OFF_ON_CLOSE = Pair(
            booleanPreferencesKey("power_off_on_close"),
            false,
        )
        val CLEANUP = Pair(
            booleanPreferencesKey("cleanup"),
            true,
        )
        val POWER_ON = Pair(
            booleanPreferencesKey("power_on"),
            true,
        )
        val VIDEO = Pair(
            booleanPreferencesKey("video"),
            true,
        )
        val AUDIO = Pair(
            booleanPreferencesKey("audio"),
            true,
        )
        val REQUIRE_AUDIO = Pair(
            booleanPreferencesKey("require_audio"),
            false,
        )
        val KILL_ADB_ON_CLOSE = Pair(
            booleanPreferencesKey("kill_adb_on_close"),
            false,
        )
        val CAMERA_HIGH_SPEED = Pair(
            booleanPreferencesKey("camera_high_speed"),
            false,
        )
        val LIST = Pair(
            stringPreferencesKey("list"),
            "null",
        )
        val AUDIO_DUP = Pair(
            booleanPreferencesKey("audio_dup"),
            false,
        )
        val NEW_DISPLAY = Pair(
            stringPreferencesKey("new_display"),
            "",
        )
        val START_APP = Pair(
            stringPreferencesKey("start_app"),
            "",
        )
        val VD_DESTROY_CONTENT = Pair(
            booleanPreferencesKey("vd_destroy_content"),
            true,
        )
        val VD_SYSTEM_DECORATIONS = Pair(
            booleanPreferencesKey("vd_system_decorations"),
            true,
        )
    }

    val crop by setting(CROP)
    val recordFilename by setting(RECORD_FILENAME)
    val videoCodecOptions by setting(VIDEO_CODEC_OPTIONS)
    val audioCodecOptions by setting(AUDIO_CODEC_OPTIONS)
    val videoEncoder by setting(VIDEO_ENCODER)
    val audioEncoder by setting(AUDIO_ENCODER)
    val cameraId by setting(CAMERA_ID)
    val cameraSize by setting(CAMERA_SIZE)
    val cameraSizeCustom by setting(CAMERA_SIZE_CUSTOM)
    val cameraSizeUseCustom by setting(CAMERA_SIZE_USE_CUSTOM)
    val cameraAr by setting(CAMERA_AR)
    val cameraFps by setting(CAMERA_FPS)
    val logLevel by setting(LOG_LEVEL)
    val videoCodec by setting(VIDEO_CODEC)
    val audioCodec by setting(AUDIO_CODEC)
    val videoSource by setting(VIDEO_SOURCE)
    val audioSource by setting(AUDIO_SOURCE)
    val recordFormat by setting(RECORD_FORMAT)
    val cameraFacing by setting(CAMERA_FACING)
    val maxSize by setting(MAX_SIZE)
    val videoBitRate by setting(VIDEO_BIT_RATE)
    val audioBitRate by setting(AUDIO_BIT_RATE)
    val maxFps by setting(MAX_FPS)
    val angle by setting(ANGLE)
    val captureOrientation by setting(CAPTURE_ORIENTATION)
    val captureOrientationLock by setting(CAPTURE_ORIENTATION_LOCK)
    val displayOrientation by setting(DISPLAY_ORIENTATION)
    val recordOrientation by setting(RECORD_ORIENTATION)
    val displayImePolicy by setting(DISPLAY_IME_POLICY)
    val displayId by setting(DISPLAY_ID)
    val screenOffTimeout by setting(SCREEN_OFF_TIMEOUT)
    val showTouches by setting(SHOW_TOUCHES)
    val fullscreen by setting(FULLSCREEN)
    val control by setting(CONTROL)
    val videoPlayback by setting(VIDEO_PLAYBACK)
    val audioPlayback by setting(AUDIO_PLAYBACK)
    val turnScreenOff by setting(TURN_SCREEN_OFF)
    val stayAwake by setting(STAY_AWAKE)
    val disableScreensaver by setting(DISABLE_SCREENSAVER)
    val powerOffOnClose by setting(POWER_OFF_ON_CLOSE)
    val cleanup by setting(CLEANUP)
    val powerOn by setting(POWER_ON)
    val video by setting(VIDEO)
    val audio by setting(AUDIO)
    val requireAudio by setting(REQUIRE_AUDIO)
    val killAdbOnClose by setting(KILL_ADB_ON_CLOSE)
    val cameraHighSpeed by setting(CAMERA_HIGH_SPEED)
    val list by setting(LIST)
    val audioDup by setting(AUDIO_DUP)
    val newDisplay by setting(NEW_DISPLAY)
    val startApp by setting(START_APP)
    val vdDestroyContent by setting(VD_DESTROY_CONTENT)
    val vdSystemDecorations by setting(VD_SYSTEM_DECORATIONS)

    fun validate(): Boolean = runBlocking {
        runCatching {
            toClientOptions().validate()
            true
        }.getOrDefault(false)
    }

    // TODO: 处理空值
    suspend fun toClientOptions() = ClientOptions(
        crop = crop.get(),
        recordFilename = recordFilename.get(),
        videoCodecOptions = videoCodecOptions.get(),
        audioCodecOptions = audioCodecOptions.get(),
        videoEncoder = videoEncoder.get(),
        audioEncoder = audioEncoder.get(),
        cameraId = cameraId.get(),
        cameraSize = if (!cameraSizeUseCustom.get()) cameraSize.get() else cameraSizeCustom.get(),
        cameraAr = cameraAr.get(),
        cameraFps = cameraFps.get().toUShort(),
        logLevel = LogLevel.valueOf(logLevel.get().uppercase()),
        videoCodec = Codec.fromString(videoCodec.get()),
        audioCodec = Codec.fromString(audioCodec.get()),
        videoSource = VideoSource.fromString(videoSource.get()),
        audioSource = AudioSource.fromString(audioSource.get()),
        recordFormat = ClientOptions.RecordFormat.valueOf(recordFormat.get().uppercase()),
        cameraFacing = CameraFacing.fromString(cameraFacing.get()),
        maxSize = maxSize.get().toUShort(),
        videoBitRate = videoBitRate.get(),
        audioBitRate = audioBitRate.get(),
        maxFps = maxFps.get(),
        angle = angle.get(),
        captureOrientation = Orientation.fromInt(captureOrientation.get()),
        captureOrientationLock = OrientationLock.valueOf(
            captureOrientationLock.get().uppercase()
        ),
        displayOrientation = Orientation.fromInt(displayOrientation.get()),
        recordOrientation = Orientation.fromInt(recordOrientation.get()),
        displayImePolicy = DisplayImePolicy.valueOf(displayImePolicy.get().uppercase()),
        displayId = displayId.get(),
        screenOffTimeout = Tick(screenOffTimeout.get()),
        showTouches = showTouches.get(),
        fullscreen = fullscreen.get(),
        control = control.get(),
        videoPlayback = videoPlayback.get(),
        audioPlayback = audioPlayback.get(),
        turnScreenOff = turnScreenOff.get(),
        stayAwake = stayAwake.get(),
        disableScreensaver = disableScreensaver.get(),
        powerOffOnClose = powerOffOnClose.get(),
        cleanUp = cleanup.get(),
        powerOn = powerOn.get(),
        video = video.get(),
        audio = audio.get(),
        requireAudio = requireAudio.get(),
        killAdbOnClose = killAdbOnClose.get(),
        cameraHighSpeed = cameraHighSpeed.get(),
        list = ListOptions.valueOf(list.get().uppercase()),
        audioDup = audioDup.get(),
        newDisplay = newDisplay.get(),
        startApp = startApp.get(),
        vdDestroyContent = vdDestroyContent.get(),
        vdSystemDecorations = vdSystemDecorations.get()
    )
}
