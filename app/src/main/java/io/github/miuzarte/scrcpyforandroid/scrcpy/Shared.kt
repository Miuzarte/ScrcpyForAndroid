package io.github.miuzarte.scrcpyforandroid.scrcpy

import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

// 参考官方实现尽量统一行为

class Shared {
    @JvmInline
    value class Tick(val value: Long) {
        fun toNs(): Long = value * 1000
        fun toUs(): Long = value
        fun toMs(): Long = value / 1000
        fun toSec(): Long = value / 1_000_000
        fun toSecDouble(): Double = value / 1_000_000.0
        val ns: Long get() = toNs()
        val us: Long get() = toUs()
        val ms: Long get() = toMs()
        val sec: Long get() = toSec()
        val secDouble: Double get() = toSecDouble()

        operator fun plus(other: Tick): Tick = Tick(value + other.value)
        operator fun minus(other: Tick): Tick = Tick(value - other.value)
        operator fun times(scale: Long): Tick = Tick(value * scale)
        operator fun div(scale: Long): Tick = Tick(value / scale)
        operator fun compareTo(other: Tick): Int = value.compareTo(other.value)
        operator fun compareTo(other: Long): Int = value.compareTo(other)
        operator fun compareTo(other: Int): Int = value.compareTo(other.toLong())

        companion object {
            const val FREQ = 1_000_000  // 微秒/秒
            fun fromNs(ns: Long): Tick = Tick(ns / 1000)
            fun fromUs(us: Long): Tick = Tick(us)
            fun fromMs(ms: Long): Tick = Tick(ms * 1000)
            fun fromSec(sec: Long): Tick = Tick(sec * 1_000_000)
            fun fromSecDouble(sec: Double): Tick = Tick((sec * 1_000_000).toLong())
            fun fromDuration(duration: Duration): Tick = Tick(duration.inWholeMicroseconds)
        }

        fun toDuration(): Duration = value.microseconds
        fun Duration.toTick(): Tick = Tick(this.inWholeMicroseconds)
    }

    enum class ListOptions(val string: String, val value: Int) {
        NULL("null", 0x0),
        ENCODERS("encoders", 0x1),         // --list-encoders
        DISPLAYS("displays", 0x2),         // --list-displays
        CAMERAS("cameras", 0x4),           // --list-cameras
        CAMERA_SIZES("camera_sizes", 0x8), // --list-camera-sizes
        APPS("apps", 0x10);                // --list-apps

        infix fun or(other: ListOptions) = value or other.value
        infix fun and(other: ListOptions) = value and other.value
        infix fun has(other: ListOptions) = this and other != 0

        companion object {
            fun fromString(value: String) =
                entries.find { it.string.equals(value, ignoreCase = true) }
                    ?: NULL
        }
    }

    enum class EncoderType(val s: String) {
        HARDWARE("hw"),
        SOFTWARE("sw"),
    }

    enum class LogLevel(val string: String) {
        VERBOSE("verbose"),
        DEBUG("debug"),
        INFO("info"),
        WARN("warn"),
        ERROR("error");

        companion object {
            fun fromString(value: String) =
                entries.find { it.string.equals(value, ignoreCase = true) }
                    ?: VERBOSE
        }
    }

    enum class Codec(
        val string: String,
        val displayName: String,
        val type: Type,
        val id: Int,
        val isLossless: Boolean,
    ) {
        H264("h264", "H.264", Type.VIDEO, 0x68323634, false), // default, ignore when passing
        H265("h265", "H.265", Type.VIDEO, 0x68323635, false),
        AV1("av1", "AV1", Type.VIDEO, 0x00617631, false),

        OPUS("opus", "Opus", Type.AUDIO, 0x6f707573, false), // default, ignore when passing
        AAC("aac", "AAC", Type.AUDIO, 0x00616163, false),
        FLAC("flac", "FLAC", Type.AUDIO, 0x666c6163, true),
        RAW("raw", "RAW", Type.AUDIO, 0x00726177, true); // wav raw

        enum class Type { VIDEO, AUDIO }

        companion object {
            val VIDEO = entries.filter { it.type == Type.VIDEO }
            val AUDIO = entries.filter { it.type == Type.AUDIO }

            fun fromString(value: String, type: Type = Type.VIDEO) =
                entries.find { it.string.equals(value, ignoreCase = true) }
                    ?: when (type) {
                        Type.VIDEO -> H264
                        Type.AUDIO -> OPUS
                    }

            fun fromId(value: Int, type: Type? = null): Codec? =
                entries.find { it.id == value && (type == null || it.type == type) }
        }
    }

    enum class VideoSource(val string: String) {
        DISPLAY("display"), // default, ignore when passing
        CAMERA("camera");

        companion object {
            fun fromString(value: String) =
                entries.find { it.string.equals(value, ignoreCase = true) }
                    ?: DISPLAY
        }
    }

    enum class AudioSource(val string: String) {
        AUTO("auto"), // OUTPUT for video DISPLAY, MIC for video CAMERA
        OUTPUT("output"),
        MIC("mic"),
        PLAYBACK("playback"),
        MIC_UNPROCESSED("mic-unprocessed"),
        MIC_CAMCORDER("mic-camcorder"),
        MIC_VOICE_RECOGNITION("mic-voice-recognition"),
        MIC_VOICE_COMMUNICATION("mic-voice-communication"),
        VOICE_CALL("voice-call"),
        VOICE_CALL_UPLINK("voice-call-uplink"),
        VOICE_CALL_DOWNLINK("voice-call-downlink"),
        VOICE_PERFORMANCE("voice-performance");

        companion object {
            fun fromString(value: String) =
                entries.find { it.string.equals(value, ignoreCase = true) }
                    ?: AUTO
        }
    }

    enum class CameraFacing(val string: String) {
        ANY("any"), // default, ignore when passing
        FRONT("front"),
        BACK("back"),
        EXTERNAL("external");

        companion object {
            fun fromString(value: String) =
                entries.find { it.string.equals(value, ignoreCase = true) }
                    ?: ANY
        }
    }

    enum class Orientation(val string: String) {
        ORIENT_0("0"),
        ORIENT_90("90"),
        ORIENT_180("180"),
        ORIENT_270("270"),
        FLIP_0("flip0"),
        FLIP_90("flip90"),
        FLIP_180("flip180"),
        FLIP_270("flip270");

        fun isMirror(): Boolean = ordinal and 4 != 0
        fun isSwap(): Boolean = ordinal and 1 != 0
        fun getRotation(): Orientation {
            return when (ordinal and 3) {
                0 -> ORIENT_0
                1 -> ORIENT_90
                2 -> ORIENT_180
                3 -> ORIENT_270
                else -> error("Invalid rotation value")
            }
        }

        companion object {
            fun fromInt(value: Int): Orientation {
                return entries.getOrNull(value)
                    ?: error("Invalid orientation value: $value")
            }
        }
    }

    enum class OrientationLock(val string: String) {
        UNLOCKED("unlocked"), // ignore
        LOCKED_VALUE("locked_value"), // "@${orientation.string}"
        LOCKED_INITIAL("locked_initial"); // "@"

        companion object {
            fun fromString(value: String) =
                entries.find { it.string.equals(value, ignoreCase = true) }
                    ?: UNLOCKED
        }
    }

    enum class DisplayImePolicy(val string: String) {
        UNDEFINED("undefined"), // default, ignore when passing
        LOCAL("local"),
        FALLBACK("fallback"),
        HIDE("hide");

        companion object {
            fun fromString(value: String) =
                entries.find { it.string.equals(value, ignoreCase = true) }
                    ?: UNDEFINED
        }
    }
}
