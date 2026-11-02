package io.github.miuzarte.scrcpyforandroid.services

import android.content.Context
import io.github.miuzarte.scrcpyforandroid.scrcpy.ClientOptions
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import java.time.LocalDateTime

object RecordingFileResolver {
    fun resolve(
        context: Context,
        options: ClientOptions,
        sessionInfo: Scrcpy.Session.SessionInfo,
        now: LocalDateTime = LocalDateTime.now(),
    ): RecordingTarget {
        val resolvedName = RecordFilenameTemplate.resolve(options.recordFilename, sessionInfo, now)
        val fileName = ensureExtension(
            sanitizeFileName(resolvedName).ifBlank {
                throw IllegalArgumentException("Recording filename resolved to blank")
            },
            options.recordFormat,
        )
        val mimeType = mimeTypeFor(options)
        val isVideo = options.recordFormat == ClientOptions.RecordFormat.MP4
        return RecordingTarget.create(
            context = context,
            displayName = fileName,
            mimeType = mimeType,
            isVideo = isVideo,
        )
    }

    private fun ensureExtension(
        fileName: String,
        format: ClientOptions.RecordFormat,
    ): String {
        val extension = when (format) {
            ClientOptions.RecordFormat.M4A -> "m4a"
            ClientOptions.RecordFormat.AAC -> "aac"
            ClientOptions.RecordFormat.WAV -> "wav"
            else -> "mp4"
        }
        if (fileName.substringAfterLast('.', "").equals(extension, ignoreCase = true)) {
            return fileName
        }
        return "$fileName.$extension"
    }

    private fun mimeTypeFor(options: ClientOptions): String {
        return when (options.recordFormat) {
            ClientOptions.RecordFormat.M4A -> "audio/mp4"
            ClientOptions.RecordFormat.AAC -> "audio/aac"
            ClientOptions.RecordFormat.WAV -> "audio/x-wav"
            else -> "video/mp4"
        }
    }

    fun sanitizeFileName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "").trim()
}
