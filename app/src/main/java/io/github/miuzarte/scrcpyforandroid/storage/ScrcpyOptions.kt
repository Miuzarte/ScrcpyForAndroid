package io.github.miuzarte.scrcpyforandroid.storage

import android.content.Context
import android.os.Parcelable
import androidx.datastore.preferences.core.Preferences
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.parcelize.Parcelize

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
            0,
        )
        val AUDIO_BIT_RATE = Pair(
            intPreferencesKey("audio_bit_rate"),
            0,
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

    @Parcelize
    data class Bundle(
        val crop: String,
        val recordFilename: String,
        val videoCodecOptions: String,
        val audioCodecOptions: String,
        val videoEncoder: String,
        val audioEncoder: String,
        val cameraId: String,
        val cameraSize: String,
        val cameraSizeCustom: String,
        val cameraSizeUseCustom: Boolean,
        val cameraAr: String,
        val cameraFps: Int,
        val logLevel: String,
        val videoCodec: String,
        val audioCodec: String,
        val videoSource: String,
        val audioSource: String,
        val recordFormat: String,
        val cameraFacing: String,
        val maxSize: Int,
        val videoBitRate: Int,
        val audioBitRate: Int,
        val maxFps: String,
        val angle: String,
        val captureOrientation: Int,
        val captureOrientationLock: String,
        val displayOrientation: Int,
        val recordOrientation: Int,
        val displayImePolicy: String,
        val displayId: Int,
        val screenOffTimeout: Long,
        val showTouches: Boolean,
        val fullscreen: Boolean,
        val control: Boolean,
        val videoPlayback: Boolean,
        val audioPlayback: Boolean,
        val turnScreenOff: Boolean,
        val stayAwake: Boolean,
        val disableScreensaver: Boolean,
        val powerOffOnClose: Boolean,
        val cleanup: Boolean,
        val powerOn: Boolean,
        val video: Boolean,
        val audio: Boolean,
        val requireAudio: Boolean,
        val killAdbOnClose: Boolean,
        val cameraHighSpeed: Boolean,
        val list: String,
        val audioDup: Boolean,
        val newDisplay: String,
        val startApp: String,
        val vdDestroyContent: Boolean,
        val vdSystemDecorations: Boolean
    ) : Parcelable {
    }

    private val bundleFields = arrayOf(
        bundleField(CROP) { bundle: Bundle -> bundle.crop },
        bundleField(RECORD_FILENAME) { bundle: Bundle -> bundle.recordFilename },
        bundleField(VIDEO_CODEC_OPTIONS) { bundle: Bundle -> bundle.videoCodecOptions },
        bundleField(AUDIO_CODEC_OPTIONS) { bundle: Bundle -> bundle.audioCodecOptions },
        bundleField(VIDEO_ENCODER) { bundle: Bundle -> bundle.videoEncoder },
        bundleField(AUDIO_ENCODER) { bundle: Bundle -> bundle.audioEncoder },
        bundleField(CAMERA_ID) { bundle: Bundle -> bundle.cameraId },
        bundleField(CAMERA_SIZE) { bundle: Bundle -> bundle.cameraSize },
        bundleField(CAMERA_SIZE_CUSTOM) { bundle: Bundle -> bundle.cameraSizeCustom },
        bundleField(CAMERA_SIZE_USE_CUSTOM) { bundle: Bundle -> bundle.cameraSizeUseCustom },
        bundleField(CAMERA_AR) { bundle: Bundle -> bundle.cameraAr },
        bundleField(CAMERA_FPS) { bundle: Bundle -> bundle.cameraFps },
        bundleField(LOG_LEVEL) { bundle: Bundle -> bundle.logLevel },
        bundleField(VIDEO_CODEC) { bundle: Bundle -> bundle.videoCodec },
        bundleField(AUDIO_CODEC) { bundle: Bundle -> bundle.audioCodec },
        bundleField(VIDEO_SOURCE) { bundle: Bundle -> bundle.videoSource },
        bundleField(AUDIO_SOURCE) { bundle: Bundle -> bundle.audioSource },
        bundleField(RECORD_FORMAT) { bundle: Bundle -> bundle.recordFormat },
        bundleField(CAMERA_FACING) { bundle: Bundle -> bundle.cameraFacing },
        bundleField(MAX_SIZE) { bundle: Bundle -> bundle.maxSize },
        bundleField(VIDEO_BIT_RATE) { bundle: Bundle -> bundle.videoBitRate },
        bundleField(AUDIO_BIT_RATE) { bundle: Bundle -> bundle.audioBitRate },
        bundleField(MAX_FPS) { bundle: Bundle -> bundle.maxFps },
        bundleField(ANGLE) { bundle: Bundle -> bundle.angle },
        bundleField(CAPTURE_ORIENTATION) { bundle: Bundle -> bundle.captureOrientation },
        bundleField(CAPTURE_ORIENTATION_LOCK) { bundle: Bundle -> bundle.captureOrientationLock },
        bundleField(DISPLAY_ORIENTATION) { bundle: Bundle -> bundle.displayOrientation },
        bundleField(RECORD_ORIENTATION) { bundle: Bundle -> bundle.recordOrientation },
        bundleField(DISPLAY_IME_POLICY) { bundle: Bundle -> bundle.displayImePolicy },
        bundleField(DISPLAY_ID) { bundle: Bundle -> bundle.displayId },
        bundleField(SCREEN_OFF_TIMEOUT) { bundle: Bundle -> bundle.screenOffTimeout },
        bundleField(SHOW_TOUCHES) { bundle: Bundle -> bundle.showTouches },
        bundleField(FULLSCREEN) { bundle: Bundle -> bundle.fullscreen },
        bundleField(CONTROL) { bundle: Bundle -> bundle.control },
        bundleField(VIDEO_PLAYBACK) { bundle: Bundle -> bundle.videoPlayback },
        bundleField(AUDIO_PLAYBACK) { bundle: Bundle -> bundle.audioPlayback },
        bundleField(TURN_SCREEN_OFF) { bundle: Bundle -> bundle.turnScreenOff },
        bundleField(STAY_AWAKE) { bundle: Bundle -> bundle.stayAwake },
        bundleField(DISABLE_SCREENSAVER) { bundle: Bundle -> bundle.disableScreensaver },
        bundleField(POWER_OFF_ON_CLOSE) { bundle: Bundle -> bundle.powerOffOnClose },
        bundleField(CLEANUP) { bundle: Bundle -> bundle.cleanup },
        bundleField(POWER_ON) { bundle: Bundle -> bundle.powerOn },
        bundleField(VIDEO) { bundle: Bundle -> bundle.video },
        bundleField(AUDIO) { bundle: Bundle -> bundle.audio },
        bundleField(REQUIRE_AUDIO) { bundle: Bundle -> bundle.requireAudio },
        bundleField(KILL_ADB_ON_CLOSE) { bundle: Bundle -> bundle.killAdbOnClose },
        bundleField(CAMERA_HIGH_SPEED) { bundle: Bundle -> bundle.cameraHighSpeed },
        bundleField(LIST) { bundle: Bundle -> bundle.list },
        bundleField(AUDIO_DUP) { bundle: Bundle -> bundle.audioDup },
        bundleField(NEW_DISPLAY) { bundle: Bundle -> bundle.newDisplay },
        bundleField(START_APP) { bundle: Bundle -> bundle.startApp },
        bundleField(VD_DESTROY_CONTENT) { bundle: Bundle -> bundle.vdDestroyContent },
        bundleField(VD_SYSTEM_DECORATIONS) { bundle: Bundle -> bundle.vdSystemDecorations },
    )

    val bundleState: StateFlow<Bundle> = createBundleState(::bundleFromPreferences)

    private fun bundleFromPreferences(preferences: Preferences) = Bundle(
        crop = preferences.read(CROP),
        recordFilename = preferences.read(RECORD_FILENAME),
        videoCodecOptions = preferences.read(VIDEO_CODEC_OPTIONS),
        audioCodecOptions = preferences.read(AUDIO_CODEC_OPTIONS),
        videoEncoder = preferences.read(VIDEO_ENCODER),
        audioEncoder = preferences.read(AUDIO_ENCODER),
        cameraId = preferences.read(CAMERA_ID),
        cameraSize = preferences.read(CAMERA_SIZE),
        cameraSizeCustom = preferences.read(CAMERA_SIZE_CUSTOM),
        cameraSizeUseCustom = preferences.read(CAMERA_SIZE_USE_CUSTOM),
        cameraAr = preferences.read(CAMERA_AR),
        cameraFps = preferences.read(CAMERA_FPS),
        logLevel = preferences.read(LOG_LEVEL),
        videoCodec = preferences.read(VIDEO_CODEC),
        audioCodec = preferences.read(AUDIO_CODEC),
        videoSource = preferences.read(VIDEO_SOURCE),
        audioSource = preferences.read(AUDIO_SOURCE),
        recordFormat = preferences.read(RECORD_FORMAT),
        cameraFacing = preferences.read(CAMERA_FACING),
        maxSize = preferences.read(MAX_SIZE),
        videoBitRate = preferences.read(VIDEO_BIT_RATE),
        audioBitRate = preferences.read(AUDIO_BIT_RATE),
        maxFps = preferences.read(MAX_FPS),
        angle = preferences.read(ANGLE),
        captureOrientation = preferences.read(CAPTURE_ORIENTATION),
        captureOrientationLock = preferences.read(CAPTURE_ORIENTATION_LOCK),
        displayOrientation = preferences.read(DISPLAY_ORIENTATION),
        recordOrientation = preferences.read(RECORD_ORIENTATION),
        displayImePolicy = preferences.read(DISPLAY_IME_POLICY),
        displayId = preferences.read(DISPLAY_ID),
        screenOffTimeout = preferences.read(SCREEN_OFF_TIMEOUT),
        showTouches = preferences.read(SHOW_TOUCHES),
        fullscreen = preferences.read(FULLSCREEN),
        control = preferences.read(CONTROL),
        videoPlayback = preferences.read(VIDEO_PLAYBACK),
        audioPlayback = preferences.read(AUDIO_PLAYBACK),
        turnScreenOff = preferences.read(TURN_SCREEN_OFF),
        stayAwake = preferences.read(STAY_AWAKE),
        disableScreensaver = preferences.read(DISABLE_SCREENSAVER),
        powerOffOnClose = preferences.read(POWER_OFF_ON_CLOSE),
        cleanup = preferences.read(CLEANUP),
        powerOn = preferences.read(POWER_ON),
        video = preferences.read(VIDEO),
        audio = preferences.read(AUDIO),
        requireAudio = preferences.read(REQUIRE_AUDIO),
        killAdbOnClose = preferences.read(KILL_ADB_ON_CLOSE),
        cameraHighSpeed = preferences.read(CAMERA_HIGH_SPEED),
        list = preferences.read(LIST),
        audioDup = preferences.read(AUDIO_DUP),
        newDisplay = preferences.read(NEW_DISPLAY),
        startApp = preferences.read(START_APP),
        vdDestroyContent = preferences.read(VD_DESTROY_CONTENT),
        vdSystemDecorations = preferences.read(VD_SYSTEM_DECORATIONS),
    )

    suspend fun loadBundle() = loadBundle(::bundleFromPreferences)

    suspend fun saveBundle(new: Bundle) = saveBundle(bundleState.value, new, bundleFields)

    suspend fun updateBundle(transform: (Bundle) -> Bundle) {
        saveBundle(transform(bundleState.value))
    }

    fun validate(): Boolean = runBlocking {
        runCatching {
            toClientOptions().validate()
            true
        }.getOrDefault(false)
    }

    // TODO: 处理空值
    fun toClientOptions() = toClientOptions(bundleState.value)

    fun toClientOptions(bundle: Bundle) = ClientOptions(
        crop = bundle.crop,
        recordFilename = bundle.recordFilename,
        videoCodecOptions = bundle.videoCodecOptions,
        audioCodecOptions = bundle.audioCodecOptions,
        videoEncoder = bundle.videoEncoder,
        audioEncoder = bundle.audioEncoder,
        cameraId = bundle.cameraId,
        cameraSize = if (!bundle.cameraSizeUseCustom) bundle.cameraSize else bundle.cameraSizeCustom,
        cameraAr = bundle.cameraAr,
        cameraFps = bundle.cameraFps.toUShort(),
        logLevel = LogLevel.valueOf(bundle.logLevel.uppercase()),
        videoCodec = Codec.fromString(bundle.videoCodec),
        audioCodec = Codec.fromString(bundle.audioCodec),
        videoSource = VideoSource.fromString(bundle.videoSource),
        audioSource = AudioSource.fromString(bundle.audioSource),
        recordFormat = ClientOptions.RecordFormat.valueOf(bundle.recordFormat.uppercase()),
        cameraFacing = CameraFacing.fromString(bundle.cameraFacing),
        maxSize = bundle.maxSize.toUShort(),
        videoBitRate = bundle.videoBitRate,
        audioBitRate = bundle.audioBitRate,
        maxFps = bundle.maxFps,
        angle = bundle.angle,
        captureOrientation = Orientation.fromInt(bundle.captureOrientation),
        captureOrientationLock = OrientationLock.valueOf(
            bundle.captureOrientationLock.uppercase()
        ),
        displayOrientation = Orientation.fromInt(bundle.displayOrientation),
        recordOrientation = Orientation.fromInt(bundle.recordOrientation),
        displayImePolicy = DisplayImePolicy.valueOf(bundle.displayImePolicy.uppercase()),
        displayId = bundle.displayId,
        screenOffTimeout = Tick(bundle.screenOffTimeout),
        showTouches = bundle.showTouches,
        fullscreen = bundle.fullscreen,
        control = bundle.control,
        videoPlayback = bundle.videoPlayback,
        audioPlayback = bundle.audioPlayback,
        turnScreenOff = bundle.turnScreenOff,
        stayAwake = bundle.stayAwake,
        disableScreensaver = bundle.disableScreensaver,
        powerOffOnClose = bundle.powerOffOnClose,
        cleanUp = bundle.cleanup,
        powerOn = bundle.powerOn,
        video = bundle.video,
        audio = bundle.audio,
        requireAudio = bundle.requireAudio,
        killAdbOnClose = bundle.killAdbOnClose,
        cameraHighSpeed = bundle.cameraHighSpeed,
        list = ListOptions.valueOf(bundle.list.uppercase()),
        audioDup = bundle.audioDup,
        newDisplay = bundle.newDisplay,
        startApp = bundle.startApp,
        vdDestroyContent = bundle.vdDestroyContent,
        vdSystemDecorations = bundle.vdSystemDecorations
    )
}
