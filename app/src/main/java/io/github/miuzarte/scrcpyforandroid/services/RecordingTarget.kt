package io.github.miuzarte.scrcpyforandroid.services

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * 录制输出目标
 *
 * Android 10+ 通过 MediaStore 在 Movies/Scrcpy (音频回退到 Music/Scrcpy) 创建 pending 文件,
 * 避免直接写 /storage/emulated/0 触发 EPERM; 旧版本退回应用专属外部目录.
 */
class RecordingTarget private constructor(
    val displayName: String,
    private val context: Context,
    private val uri: Uri?,
    private val descriptor: ParcelFileDescriptor,
    private val file: File?,
) {
    private var finished = false

    val fileDescriptor get() = descriptor.fileDescriptor

    fun outputStream(): FileOutputStream = FileOutputStream(descriptor.fileDescriptor)

    /**
     * 结束录制
     *
     * @param keep true 发布文件 (清除 IS_PENDING), false 删除残留文件
     */
    @Synchronized
    fun finish(keep: Boolean) {
        if (finished) return
        finished = true
        runCatching { descriptor.close() }
        val outputUri = uri
        if (outputUri != null) {
            if (keep) {
                runCatching {
                    context.contentResolver.update(
                        outputUri,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                        null,
                        null,
                    )
                }.onFailure {
                    Log.w(TAG, "finish(): failed to publish $outputUri", it)
                }
            } else {
                runCatching { context.contentResolver.delete(outputUri, null, null) }
            }
        } else if (!keep) {
            file?.delete()
        }
    }

    companion object {
        private const val TAG = "RecordingTarget"

        fun create(
            context: Context,
            displayName: String,
            mimeType: String,
            isVideo: Boolean,
        ): RecordingTarget {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                createMediaStore(context, displayName, mimeType, isVideo)
            } else {
                createAppSpecific(context, displayName)
            }
        }

        private fun createMediaStore(
            context: Context,
            displayName: String,
            mimeType: String,
            isVideo: Boolean,
        ): RecordingTarget {
            val collection = if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            // MediaStore 对音频只允许 Music 等固定目录, 视频放 Movies/Scrcpy, 音频放 Music/Scrcpy
            val relativeDirectories = if (isVideo) {
                listOf("${Environment.DIRECTORY_MOVIES}/$ROOT_DIRECTORY")
            } else {
                listOf("${Environment.DIRECTORY_MUSIC}/$ROOT_DIRECTORY")
            }
            var lastError: Exception? = null
            for (relativeDirectory in relativeDirectories) {
                try {
                    val resolvedName = uniqueDisplayName(
                        context = context,
                        collection = collection,
                        relativeDirectory = relativeDirectory,
                        displayName = displayName,
                    )
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, resolvedName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDirectory)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val uri = context.contentResolver.insert(collection, values)
                    if (uri == null) {
                        lastError = IllegalStateException(
                            "MediaStore insert returned null for $relativeDirectory",
                        )
                        continue
                    }
                    val descriptor = context.contentResolver.openFileDescriptor(uri, "rw")
                    if (descriptor == null) {
                        runCatching { context.contentResolver.delete(uri, null, null) }
                        lastError = IllegalStateException(
                            "MediaStore openFileDescriptor returned null for $uri",
                        )
                        continue
                    }
                    return RecordingTarget(resolvedName, context, uri, descriptor, null)
                } catch (e: Exception) {
                    lastError = e
                }
            }
            throw IllegalArgumentException(
                "Unable to create recording file in ${relativeDirectories.joinToString()}",
                lastError,
            )
        }

        private fun uniqueDisplayName(
            context: Context,
            collection: Uri,
            relativeDirectory: String,
            displayName: String,
        ): String {
            val existing = mutableSetOf<String>()
            runCatching {
                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                    "${'$'}{MediaStore.MediaColumns.RELATIVE_PATH}=?",
                    arrayOf(relativeDirectory),
                    null,
                )?.use { cursor ->
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        while (cursor.moveToNext()) {
                            cursor.getString(index)?.let { existing += it }
                        }
                    }
                }
            }
            if (displayName !in existing) return displayName
            val baseName = displayName.substringBeforeLast('.', displayName)
            val extension = displayName.substringAfterLast('.', "")
            var index = 1
            while (true) {
                val candidate = if (extension.isBlank()) {
                    "$baseName ($index)"
                } else {
                    "$baseName ($index).$extension"
                }
                if (candidate !in existing) return candidate
                index += 1
            }
        }

        private fun createAppSpecific(
            context: Context,
            displayName: String,
        ): RecordingTarget {
            val root = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: File(context.filesDir, Environment.DIRECTORY_MOVIES)
            val directory = File(root, ROOT_DIRECTORY).apply {
                if (!exists() && !mkdirs()) {
                    throw IllegalStateException("Unable to create directory: $absolutePath")
                }
            }
            val file = uniqueFile(directory, displayName)
            val descriptor = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_CREATE or
                        ParcelFileDescriptor.MODE_READ_WRITE or
                        ParcelFileDescriptor.MODE_TRUNCATE,
            )
            return RecordingTarget(displayName, context, null, descriptor, file)
        }

        private fun uniqueFile(directory: File, fileName: String): File {
            val baseName = fileName.substringBeforeLast('.', fileName)
            val extension = fileName.substringAfterLast('.', "")
            var index = 0
            var candidate = File(directory, fileName)
            while (candidate.exists()) {
                index += 1
                val suffix = " ($index)"
                candidate = if (extension.isBlank()) {
                    File(directory, baseName + suffix)
                } else {
                    File(directory, "$baseName$suffix.$extension")
                }
            }
            return candidate
        }

        private const val ROOT_DIRECTORY = "Scrcpy"
    }
}
