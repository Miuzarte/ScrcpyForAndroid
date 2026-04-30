package io.github.miuzarte.scrcpyforandroid.services

import androidx.annotation.StringRes
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import java.time.LocalDateTime

object RecordFilenameTemplate {
    data class Entry(
        val value: String,
        @field:StringRes val descriptionResId: Int?,
        val isTemplate: Boolean,
    )

    fun resolve(
        template: String,
        sessionInfo: Scrcpy.Session.SessionInfo,
        now: LocalDateTime = LocalDateTime.now(),
    ) =
        if (template.isBlank()) ""
        else buildString(template.length) {
            var index = 0
            while (index < template.length) {
                val start = template.indexOf($$"${", index)
                if (start < 0) {
                    append(template.substring(index))
                    break
                }
                append(template.substring(index, start))
                val end = template.indexOf('}', start + 2)
                if (end < 0) {
                    append(template.substring(start))
                    break
                }
                val key = template.substring(start + 2, end)
                if (key.all { it.isLetterOrDigit() || it == '_' }) {
                    append(valueOf(key, sessionInfo, now))
                } else {
                    append(template.substring(start, end + 1))
                }
                index = end + 1
            }
        }.trim()


    val entries
        get() = listOf(
            Entry("-", null, false),
            Entry("_", null, false),
            Entry($$"${YYYY}", R.string.record_desc_yyyy, true),
            Entry($$"${YY}", R.string.record_desc_yy, true),
            Entry($$"${MM}", R.string.record_desc_MM, true),
            Entry($$"${M}", R.string.record_desc_M, true),
            Entry($$"${DD}", R.string.record_desc_dd, true),
            Entry($$"${D}", R.string.record_desc_d, true),
            Entry($$"${HH}", R.string.record_desc_HH, true),
            Entry($$"${H}", R.string.record_desc_H, true),
            Entry($$"${hh}", R.string.record_desc_hh, true),
            Entry($$"${h}", R.string.record_desc_h, true),
            Entry($$"${mm}", R.string.record_desc_mm, true),
            Entry($$"${m}", R.string.record_desc_m, true),
            Entry($$"${SS}", R.string.record_desc_ss, true),
            Entry($$"${S}", R.string.record_desc_s, true),
            Entry($$"${timestamp}", R.string.record_desc_timestamp, true),
            Entry($$"${deviceName}", R.string.record_desc_device_name, true),
            Entry($$"${deviceIp}", R.string.record_desc_device_ip, true),
            Entry($$"${devicePort}", R.string.record_desc_device_port, true),
            Entry($$"${videoCodec}", R.string.record_desc_video_codec, true),
            Entry($$"${audioCodec}", R.string.record_desc_audio_codec, true),
            Entry($$"${width}", R.string.record_desc_width, true),
            Entry($$"${height}", R.string.record_desc_height, true),
            Entry(".mp4", null, false),
            Entry(".m4a", null, false),
            Entry(".aac", null, false),
            Entry(".wav", null, false),
        )

    private fun valueOf(
        key: String,
        sessionInfo: Scrcpy.Session.SessionInfo,
        now: LocalDateTime,
    ) = when (key) {
        "YYYY" -> now.year.toString().padStart(4, '0')
        "YY" -> (now.year % 100).toString().padStart(2, '0')
        "MM" -> now.monthValue.toString().padStart(2, '0')
        "M" -> now.monthValue.toString()
        "DD" -> now.dayOfMonth.toString().padStart(2, '0')
        "D" -> now.dayOfMonth.toString()
        "HH" -> now.hour.toString().padStart(2, '0')
        "H" -> now.hour.toString()
        "hh" -> (now.hour % 12).let { h -> ((if (h == 0) 12 else h).toString().padStart(2, '0')) }
        "h" -> (now.hour % 12).let { h -> (if (h == 0) 12 else h).toString() }
        "mm" -> now.minute.toString().padStart(2, '0')
        "m" -> now.minute.toString()
        "SS" -> now.second.toString().padStart(2, '0')
        "S" -> now.second.toString()
        "timestamp" -> now.atZone(java.time.ZoneId.systemDefault()).toEpochSecond().toString()
        "deviceName" -> sessionInfo.deviceName
        "deviceIp" -> sessionInfo.host
        "devicePort" -> sessionInfo.port.toString()
        "videoCodec" -> sessionInfo.codec?.string ?: "unknown"
        "audioCodec" -> sessionInfo.audioCodec?.string ?: "unknown"
        "width" -> sessionInfo.width.toString()
        "height" -> sessionInfo.height.toString()
        else -> $$"${$$key}"
    }
}
