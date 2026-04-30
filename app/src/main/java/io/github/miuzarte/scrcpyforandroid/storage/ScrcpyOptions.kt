package io.github.miuzarte.scrcpyforandroid.storage

import android.content.Context
import android.os.Parcelable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.scrcpy.ClientOptions
import io.github.miuzarte.scrcpyforandroid.scrcpy.ClientOptions.KeyInjectMode
import io.github.miuzarte.scrcpyforandroid.scrcpy.ClientOptions.RecordFormat
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
        const val GLOBAL_PROFILE_ID = "global"
        val GLOBAL_PROFILE_NAME_RES_ID = R.string.text_global
        val NEW_PROFILE_NAME_RES_ID = R.string.profile_new_name

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
            LogLevel.INFO.string,
        )
        val VIDEO_CODEC = Pair(
            stringPreferencesKey("video_codec"),
            Codec.H264.string,
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
            DisplayImePolicy.UNDEFINED.string,
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
        val KEY_INJECT_MODE = Pair(
            stringPreferencesKey("key_inject_mode"),
            KeyInjectMode.MIXED.string,
        )
        val FORWARD_KEY_REPEAT = Pair(
            booleanPreferencesKey("forward_key_repeat"),
            true,
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
        val LEGACY_PASTE = Pair(
            booleanPreferencesKey("legacy_paste"),
            false,
        )
        val CLIPBOARD_AUTOSYNC = Pair(
            booleanPreferencesKey("clipboard_autosync"),
            true,
        )
        val DOWNSIZE_ON_ERROR = Pair(
            booleanPreferencesKey("downsize_on_error"),
            true,
        )
        val MOUSE_HOVER = Pair(
            booleanPreferencesKey("mouse_hover"),
            true,
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
        val START_APP_CUSTOM = Pair(
            stringPreferencesKey("start_app_custom"),
            "",
        )
        val START_APP_USE_CUSTOM = Pair(
            booleanPreferencesKey("start_app_use_custom"),
            false,
        )
        val VD_DESTROY_CONTENT = Pair(
            booleanPreferencesKey("vd_destroy_content"),
            true,
        )
        val VD_SYSTEM_DECORATIONS = Pair(
            booleanPreferencesKey("vd_system_decorations"),
            true,
        )

        fun defaultBundle() = Bundle(
            crop = CROP.defaultValue,
            recordFilename = RECORD_FILENAME.defaultValue,
            videoCodecOptions = VIDEO_CODEC_OPTIONS.defaultValue,
            audioCodecOptions = AUDIO_CODEC_OPTIONS.defaultValue,
            videoEncoder = VIDEO_ENCODER.defaultValue,
            audioEncoder = AUDIO_ENCODER.defaultValue,
            cameraId = CAMERA_ID.defaultValue,
            cameraSize = CAMERA_SIZE.defaultValue,
            cameraSizeCustom = CAMERA_SIZE_CUSTOM.defaultValue,
            cameraSizeUseCustom = CAMERA_SIZE_USE_CUSTOM.defaultValue,
            cameraAr = CAMERA_AR.defaultValue,
            cameraFps = CAMERA_FPS.defaultValue,
            logLevel = LOG_LEVEL.defaultValue,
            videoCodec = VIDEO_CODEC.defaultValue,
            audioCodec = AUDIO_CODEC.defaultValue,
            videoSource = VIDEO_SOURCE.defaultValue,
            audioSource = AUDIO_SOURCE.defaultValue,
            recordFormat = RECORD_FORMAT.defaultValue,
            cameraFacing = CAMERA_FACING.defaultValue,
            maxSize = MAX_SIZE.defaultValue,
            videoBitRate = VIDEO_BIT_RATE.defaultValue,
            audioBitRate = AUDIO_BIT_RATE.defaultValue,
            maxFps = MAX_FPS.defaultValue,
            angle = ANGLE.defaultValue,
            captureOrientation = CAPTURE_ORIENTATION.defaultValue,
            captureOrientationLock = CAPTURE_ORIENTATION_LOCK.defaultValue,
            displayOrientation = DISPLAY_ORIENTATION.defaultValue,
            recordOrientation = RECORD_ORIENTATION.defaultValue,
            displayImePolicy = DISPLAY_IME_POLICY.defaultValue,
            displayId = DISPLAY_ID.defaultValue,
            screenOffTimeout = SCREEN_OFF_TIMEOUT.defaultValue,
            showTouches = SHOW_TOUCHES.defaultValue,
            fullscreen = FULLSCREEN.defaultValue,
            control = CONTROL.defaultValue,
            videoPlayback = VIDEO_PLAYBACK.defaultValue,
            audioPlayback = AUDIO_PLAYBACK.defaultValue,
            turnScreenOff = TURN_SCREEN_OFF.defaultValue,
            keyInjectMode = KEY_INJECT_MODE.defaultValue,
            forwardKeyRepeat = FORWARD_KEY_REPEAT.defaultValue,
            stayAwake = STAY_AWAKE.defaultValue,
            disableScreensaver = DISABLE_SCREENSAVER.defaultValue,
            powerOffOnClose = POWER_OFF_ON_CLOSE.defaultValue,
            legacyPaste = LEGACY_PASTE.defaultValue,
            clipboardAutosync = CLIPBOARD_AUTOSYNC.defaultValue,
            downsizeOnError = DOWNSIZE_ON_ERROR.defaultValue,
            mouseHover = MOUSE_HOVER.defaultValue,
            cleanup = CLEANUP.defaultValue,
            powerOn = POWER_ON.defaultValue,
            video = VIDEO.defaultValue,
            audio = AUDIO.defaultValue,
            requireAudio = REQUIRE_AUDIO.defaultValue,
            killAdbOnClose = KILL_ADB_ON_CLOSE.defaultValue,
            cameraHighSpeed = CAMERA_HIGH_SPEED.defaultValue,
            list = LIST.defaultValue,
            audioDup = AUDIO_DUP.defaultValue,
            newDisplay = NEW_DISPLAY.defaultValue,
            startApp = START_APP.defaultValue,
            startAppCustom = START_APP_CUSTOM.defaultValue,
            startAppUseCustom = START_APP_USE_CUSTOM.defaultValue,
            vdDestroyContent = VD_DESTROY_CONTENT.defaultValue,
            vdSystemDecorations = VD_SYSTEM_DECORATIONS.defaultValue,
        )
    }

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
        val keyInjectMode: String,
        val forwardKeyRepeat: Boolean,
        val stayAwake: Boolean,
        val disableScreensaver: Boolean,
        val powerOffOnClose: Boolean,
        val legacyPaste: Boolean,
        val clipboardAutosync: Boolean,
        val downsizeOnError: Boolean,
        val mouseHover: Boolean,
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
        val startAppCustom: String,
        val startAppUseCustom: Boolean,
        val vdDestroyContent: Boolean,
        val vdSystemDecorations: Boolean
    ) : Parcelable {
    }

    private val bundleFields = arrayOf<BundleField<Bundle>>(
        bundleField(CROP) { it.crop },
        bundleField(RECORD_FILENAME) { it.recordFilename },
        bundleField(VIDEO_CODEC_OPTIONS) { it.videoCodecOptions },
        bundleField(AUDIO_CODEC_OPTIONS) { it.audioCodecOptions },
        bundleField(VIDEO_ENCODER) { it.videoEncoder },
        bundleField(AUDIO_ENCODER) { it.audioEncoder },
        bundleField(CAMERA_ID) { it.cameraId },
        bundleField(CAMERA_SIZE) { it.cameraSize },
        bundleField(CAMERA_SIZE_CUSTOM) { it.cameraSizeCustom },
        bundleField(CAMERA_SIZE_USE_CUSTOM) { it.cameraSizeUseCustom },
        bundleField(CAMERA_AR) { it.cameraAr },
        bundleField(CAMERA_FPS) { it.cameraFps },
        bundleField(LOG_LEVEL) { it.logLevel },
        bundleField(VIDEO_CODEC) { it.videoCodec },
        bundleField(AUDIO_CODEC) { it.audioCodec },
        bundleField(VIDEO_SOURCE) { it.videoSource },
        bundleField(AUDIO_SOURCE) { it.audioSource },
        bundleField(RECORD_FORMAT) { it.recordFormat },
        bundleField(CAMERA_FACING) { it.cameraFacing },
        bundleField(MAX_SIZE) { it.maxSize },
        bundleField(VIDEO_BIT_RATE) { it.videoBitRate },
        bundleField(AUDIO_BIT_RATE) { it.audioBitRate },
        bundleField(MAX_FPS) { it.maxFps },
        bundleField(ANGLE) { it.angle },
        bundleField(CAPTURE_ORIENTATION) { it.captureOrientation },
        bundleField(CAPTURE_ORIENTATION_LOCK) { it.captureOrientationLock },
        bundleField(DISPLAY_ORIENTATION) { it.displayOrientation },
        bundleField(RECORD_ORIENTATION) { it.recordOrientation },
        bundleField(DISPLAY_IME_POLICY) { it.displayImePolicy },
        bundleField(DISPLAY_ID) { it.displayId },
        bundleField(SCREEN_OFF_TIMEOUT) { it.screenOffTimeout },
        bundleField(SHOW_TOUCHES) { it.showTouches },
        bundleField(FULLSCREEN) { it.fullscreen },
        bundleField(CONTROL) { it.control },
        bundleField(VIDEO_PLAYBACK) { it.videoPlayback },
        bundleField(AUDIO_PLAYBACK) { it.audioPlayback },
        bundleField(TURN_SCREEN_OFF) { it.turnScreenOff },
        bundleField(KEY_INJECT_MODE) { it.keyInjectMode },
        bundleField(FORWARD_KEY_REPEAT) { it.forwardKeyRepeat },
        bundleField(STAY_AWAKE) { it.stayAwake },
        bundleField(DISABLE_SCREENSAVER) { it.disableScreensaver },
        bundleField(POWER_OFF_ON_CLOSE) { it.powerOffOnClose },
        bundleField(LEGACY_PASTE) { it.legacyPaste },
        bundleField(CLIPBOARD_AUTOSYNC) { it.clipboardAutosync },
        bundleField(MOUSE_HOVER) { it.mouseHover },
        bundleField(CLEANUP) { it.cleanup },
        bundleField(POWER_ON) { it.powerOn },
        bundleField(VIDEO) { it.video },
        bundleField(AUDIO) { it.audio },
        bundleField(REQUIRE_AUDIO) { it.requireAudio },
        bundleField(KILL_ADB_ON_CLOSE) { it.killAdbOnClose },
        bundleField(CAMERA_HIGH_SPEED) { it.cameraHighSpeed },
        bundleField(LIST) { it.list },
        bundleField(AUDIO_DUP) { it.audioDup },
        bundleField(NEW_DISPLAY) { it.newDisplay },
        bundleField(START_APP) { it.startApp },
        bundleField(START_APP_CUSTOM) { it.startAppCustom },
        bundleField(START_APP_USE_CUSTOM) { it.startAppUseCustom },
        bundleField(VD_DESTROY_CONTENT) { it.vdDestroyContent },
        bundleField(VD_SYSTEM_DECORATIONS) { it.vdSystemDecorations },
    )

    val bundleState: StateFlow<Bundle> = createBundleState(::bundleFromPreferences)

    private fun bundleFromPreferences(preferences: Preferences) = defaultBundle().copy(
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
        keyInjectMode = preferences.read(KEY_INJECT_MODE),
        forwardKeyRepeat = preferences.read(FORWARD_KEY_REPEAT),
        stayAwake = preferences.read(STAY_AWAKE),
        disableScreensaver = preferences.read(DISABLE_SCREENSAVER),
        powerOffOnClose = preferences.read(POWER_OFF_ON_CLOSE),
        legacyPaste = preferences.read(LEGACY_PASTE),
        clipboardAutosync = preferences.read(CLIPBOARD_AUTOSYNC),
        downsizeOnError = preferences.read(DOWNSIZE_ON_ERROR),
        mouseHover = preferences.read(MOUSE_HOVER),
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
        startAppCustom = preferences.read(START_APP_CUSTOM),
        startAppUseCustom = preferences.read(START_APP_USE_CUSTOM),
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
        logLevel = LogLevel.fromString(bundle.logLevel),
        videoCodec = Codec.fromString(bundle.videoCodec),
        audioCodec = Codec.fromString(bundle.audioCodec),
        videoSource = VideoSource.fromString(bundle.videoSource),
        audioSource = AudioSource.fromString(bundle.audioSource),
        recordFormat = RecordFormat.fromString(bundle.recordFormat),
        cameraFacing = CameraFacing.fromString(bundle.cameraFacing),
        maxSize = bundle.maxSize.toUShort(),
        videoBitRate = bundle.videoBitRate,
        audioBitRate = bundle.audioBitRate,
        maxFps = bundle.maxFps,
        angle = bundle.angle,
        captureOrientation = Orientation.fromInt(bundle.captureOrientation),
        captureOrientationLock = OrientationLock.fromString(bundle.captureOrientationLock),
        displayOrientation = Orientation.fromInt(bundle.displayOrientation),
        recordOrientation = Orientation.fromInt(bundle.recordOrientation),
        displayImePolicy = DisplayImePolicy.fromString(bundle.displayImePolicy),
        displayId = bundle.displayId,
        screenOffTimeout = Tick(bundle.screenOffTimeout),
        showTouches = bundle.showTouches,
        fullscreen = bundle.fullscreen,
        control = bundle.control,
        videoPlayback = bundle.videoPlayback,
        audioPlayback = bundle.audioPlayback,
        turnScreenOff = bundle.turnScreenOff,
        keyInjectMode = KeyInjectMode.fromString(bundle.keyInjectMode),
        forwardKeyRepeat = bundle.forwardKeyRepeat,
        stayAwake = bundle.stayAwake,
        disableScreensaver = bundle.disableScreensaver,
        powerOffOnClose = bundle.powerOffOnClose,
        legacyPaste = bundle.legacyPaste,
        clipboardAutosync = bundle.clipboardAutosync,
        downsizeOnError = bundle.downsizeOnError,
        mouseHover = bundle.mouseHover,
        cleanup = bundle.cleanup,
        powerOn = bundle.powerOn,
        video = bundle.video,
        audio = bundle.audio,
        requireAudio = bundle.requireAudio,
        killAdbOnClose = bundle.killAdbOnClose,
        cameraHighSpeed = bundle.cameraHighSpeed,
        list = ListOptions.fromString(bundle.list),
        audioDup = bundle.audioDup,
        newDisplay = bundle.newDisplay,
        startApp = if (!bundle.startAppUseCustom) bundle.startApp else bundle.startAppCustom,
        vdDestroyContent = bundle.vdDestroyContent,
        vdSystemDecorations = bundle.vdSystemDecorations
    )
}
