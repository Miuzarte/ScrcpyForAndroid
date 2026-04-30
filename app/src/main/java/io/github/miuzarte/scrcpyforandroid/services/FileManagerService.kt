package io.github.miuzarte.scrcpyforandroid.services

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.nativecore.AdbSocketStream
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class RemoteFileKind {
    Directory,
    Image,
    Video,
    Audio,
    Archive,
    Apk,
    Text,
    Link,
    Other,
}

data class RemoteFileEntry(
    val inode: Long?,
    val permissions: String,
    val hardLinks: Int?,
    val owner: String?,
    val group: String?,
    val sizeBytes: Long?,
    val modifiedAt: LocalDateTime?,
    val name: String,
    val fullPath: String,
    val symlinkTarget: String? = null,
    val kind: RemoteFileKind,
    val isDirectory: Boolean,
)

data class RemoteFileStat(
    val path: String,
    val name: String,
    val typeLabel: String?,
    val sizeBytes: Long?,
    val blocks: Long?,
    val ioBlockBytes: Long?,
    val inode: Long?,
    val hardLinks: Int?,
    val octalMode: String?,
    val permissions: String?,
    val uid: Long?,
    val uidName: String?,
    val gid: Long?,
    val gidName: String?,
    val accessTime: String?,
    val modifyTime: String?,
    val changeTime: String?,
    val device: String?,
    val deviceType: String?,
    val symlinkTarget: String?,
    val rawOutput: String,
) {
    val title: String
        get() = name.ifBlank { path }
}

data class DirectoryDownloadSnapshot(
    val remoteRootPath: String,
    val totalBytes: Long?,
    val directories: List<String>,
    val files: List<String>,
)

object FileManagerService {
    private val listTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val displayTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    private val listLineRegex = Regex(
        """^\s*(\d+)\s+([\-bcdlps][rwxstST-]{9})\s+(\d+)\s+(\S+)\s+(\S+)\s+(\d+)\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})\s+(.+)$"""
    )
    private val sizeFormatter = DecimalFormat("0.00")

    suspend fun listDirectory(path: String): List<RemoteFileEntry> = withContext(Dispatchers.IO) {
        val command = "ls -aFil ${quoteShellArg(pathForListCommand(path))}"
        val output = NativeAdbService.shell(command)
        output.lineSequence()
            .map(String::trimEnd)
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("total ") }
            .mapNotNull { parseListEntry(path, it) }
            .filterNot { it.name == "." || it.name == ".." }
            .sortedWith(
                compareByDescending<RemoteFileEntry> { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
            .toList()
    }

    suspend fun stat(path: String): RemoteFileStat = withContext(Dispatchers.IO) {
        val output = NativeAdbService.shell("stat ${quoteShellArg(path)}")
        parseStat(path, output)
    }

    suspend fun uploadFile(
        context: Context,
        uri: Uri,
        remoteDirectory: String,
    ): String = withContext(Dispatchers.IO) {
        NativeAdbService.ensureConnectionResponsive()
        val fileName = queryDisplayName(context.contentResolver, uri)
            ?: throw IOException(AppRuntime.stringResource(R.string.fm_exception_cannot_read_filename))
        val remotePath = joinRemotePath(remoteDirectory, fileName)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { AppRuntime.stringResource(R.string.fm_exception_cannot_read_selected_file) }
            NativeAdbService.push(input, remotePath)
        }
        return@withContext remotePath
    }

    suspend fun createDirectory(
        parentDirectory: String,
        directoryName: String,
    ): String = withContext(Dispatchers.IO) {
        val sanitizedName = directoryName.trim().trim('/').takeIf { it.isNotBlank() }
            ?: throw IOException(AppRuntime.stringResource(R.string.fm_exception_folder_name_empty))
        val remotePath = joinRemotePath(parentDirectory, sanitizedName)
        NativeAdbService.ensureConnectionResponsive()
        NativeAdbService.shell("mkdir -p ${quoteShellArg(remotePath)}")
        return@withContext remotePath
    }

    suspend fun downloadFileToPublicDownloads(
        remotePath: String,
        fileName: String,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            NativeAdbService.ensureConnectionResponsive()
            val targetRoot = PublicDirs.transferDirectory()
            ensureDirectoryExists(targetRoot)
            val targetFile = uniqueFile(targetRoot, fileName)
            targetFile.outputStream().use { output ->
                NativeAdbService.pull(remotePath, output)
            }
        }.isSuccess
    }

    suspend fun downloadDirectoryToPublicDownloads(
        snapshot: DirectoryDownloadSnapshot,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            NativeAdbService.ensureConnectionResponsive()
            val rootName = snapshot.remoteRootPath.substringAfterLast('/').ifBlank { "Scrcpy" }
            val publicRoot = PublicDirs.transferDirectory()
            ensureDirectoryExists(publicRoot)
            val destinationRoot = uniqueDirectory(publicRoot, rootName)
            ensureDirectoryExists(destinationRoot)
            snapshot.directories.sortedBy { it.length }.forEach { relativePath ->
                ensureDirectoryExists(File(destinationRoot, relativePath))
            }
            snapshot.files.forEach { relativePath ->
                val targetFile = File(destinationRoot, relativePath)
                ensureDirectoryExists(targetFile.parentFile)
                targetFile.outputStream().use { output ->
                    NativeAdbService.pull(
                        joinRemotePath(snapshot.remoteRootPath, relativePath),
                        output
                    )
                }
            }
        }.isSuccess
    }

    suspend fun downloadFileToTree(
        context: Context,
        treeUri: Uri,
        remotePath: String,
        fileName: String,
    ) = withContext(Dispatchers.IO) {
        NativeAdbService.ensureConnectionResponsive()
        val rootDocument = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val target = createUniqueDocument(context, rootDocument, fileName, guessMimeType(fileName))
        context.contentResolver.openOutputStream(target, "w").use { output ->
            requireNotNull(output) { AppRuntime.stringResource(R.string.fm_exception_cannot_write_target) }
            NativeAdbService.pull(remotePath, output)
        }
    }

    suspend fun downloadDirectoryToTree(
        context: Context,
        treeUri: Uri,
        snapshot: DirectoryDownloadSnapshot,
    ) = withContext(Dispatchers.IO) {
        NativeAdbService.ensureConnectionResponsive()
        val rootDocument = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val folderName = snapshot.remoteRootPath.substringAfterLast('/').ifBlank { "Scrcpy" }
        val folderRoot = createUniqueDirectoryDocument(context, rootDocument, folderName)

        snapshot.directories.sortedBy { it.length }.forEach { relativePath ->
            ensureTreeDirectory(context, folderRoot, relativePath)
        }

        snapshot.files.forEach { relativePath ->
            val parentRelativePath = relativePath.substringBeforeLast('/', "")
            val fileName = relativePath.substringAfterLast('/')
            val parentDocument = ensureTreeDirectory(context, folderRoot, parentRelativePath)
            val target =
                createUniqueDocument(context, parentDocument, fileName, guessMimeType(fileName))
            context.contentResolver.openOutputStream(target, "w").use { output ->
                requireNotNull(output) { AppRuntime.stringResource(R.string.fm_exception_cannot_write_target) }
                NativeAdbService.pull(joinRemotePath(snapshot.remoteRootPath, relativePath), output)
            }
        }
    }

    fun formatSummary(entry: RemoteFileEntry): String {
        val timeText = entry.modifiedAt?.format(displayTimeFormatter)
            ?: AppRuntime.stringResource(R.string.fm_unknown)
        return if (entry.isDirectory || entry.sizeBytes == null) timeText
        else "$timeText ${formatSize(entry.sizeBytes)}"
    }

    fun formatStatDetails(
        stat: RemoteFileStat,
        directorySnapshot: DirectoryDownloadSnapshot? = null,
    ): String {
        val entries = mutableListOf<Pair<String, String>>()
        entries.add(AppRuntime.stringResource(R.string.fm_stat_path) to stat.path)
        stat.typeLabel?.let { entries.add(AppRuntime.stringResource(R.string.fm_stat_type) to it) }
        (directorySnapshot?.totalBytes ?: stat.sizeBytes)
            ?.let { entries.add(AppRuntime.stringResource(R.string.fm_stat_size) to "${formatSize(it)} ($it B)") }
        stat.blocks?.let { entries.add(AppRuntime.stringResource(R.string.fm_stat_blocks) to "$it") }
        stat.ioBlockBytes?.let { entries.add(AppRuntime.stringResource(R.string.fm_stat_io_block_size) to "${it}B") }
        stat.inode?.let { entries.add("inode" to "$it") }
        stat.hardLinks?.let { entries.add(AppRuntime.stringResource(R.string.fm_stat_hard_links) to "$it") }
        if (!stat.octalMode.isNullOrBlank() || !stat.permissions.isNullOrBlank())
            entries.add(AppRuntime.stringResource(R.string.fm_stat_permissions) to "${stat.octalMode ?: "?"}/${stat.permissions ?: "?"}")
        if (stat.uid != null || stat.uidName != null)
            entries.add("Uid" to "${stat.uid ?: "?"}/${stat.uidName ?: "?"}")
        if (stat.gid != null || stat.gidName != null)
            entries.add("Gid" to "${stat.gid ?: "?"}/${stat.gidName ?: "?"}")
        stat.device?.let { entries.add(AppRuntime.stringResource(R.string.fm_stat_device) to "$it") }
        stat.deviceType?.let { entries.add(AppRuntime.stringResource(R.string.fm_stat_device_type) to "$it") }
        stat.accessTime?.let { entries.add(AppRuntime.stringResource(R.string.fm_stat_access_time) to "$it") }
        stat.modifyTime?.let { entries.add(AppRuntime.stringResource(R.string.fm_stat_modify_time) to "$it") }
        stat.changeTime?.let { entries.add(AppRuntime.stringResource(R.string.fm_stat_change_time) to "$it") }
        stat.symlinkTarget?.let { entries.add(AppRuntime.stringResource(R.string.fm_stat_link_target) to "$it") }
        directorySnapshot?.let {
            entries.add(AppRuntime.stringResource(R.string.fm_stat_directories) to "${it.directories.size}")
            entries.add(AppRuntime.stringResource(R.string.fm_stat_files) to "${it.files.size}")
        }
        return entries.joinToString(separator = "\n") { "${it.first}: ${it.second}" }
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) {
            return "${bytes}B"
        }
        val units = listOf("K", "M", "G", "T")
        var value = bytes.toDouble()
        var index = -1
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return "${sizeFormatter.format(value)}${units[index]}"
    }

    fun guessKind(name: String, isDirectory: Boolean, permissions: String): RemoteFileKind {
        if (isDirectory) {
            return RemoteFileKind.Directory
        }
        if (permissions.startsWith("l")) {
            return RemoteFileKind.Link
        }
        return when (name.substringAfterLast('.', "").lowercase()) {
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "svg" -> RemoteFileKind.Image

            "mp4", "mkv", "avi", "webm", "mov", "ts", "m4v" -> RemoteFileKind.Video

            "mp3", "flac", "aac", "ogg", "wav", "m4a" -> RemoteFileKind.Audio

            "zip", "rar", "7z", "tar", "gz", "xz", "tgz" -> RemoteFileKind.Archive

            "apk", "apks", "xapk" -> RemoteFileKind.Apk

            "txt", "log", "json", "jsonc", "hjson", "xml", "yml", "yaml", "toml", "md",
            "go", "kt", "java", "c", "cpp", "h", "hpp", "py", "sh", "bat",
                -> RemoteFileKind.Text

            else -> RemoteFileKind.Other
        }
    }

    fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use null
                }
                cursor.getString(0)
            }
    }

    fun quoteShellArg(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }

    private fun parseListEntry(parentPath: String, rawLine: String): RemoteFileEntry? {
        val match = listLineRegex.matchEntire(rawLine) ?: return null
        val permissions = match.groupValues[2]
        val dateTime = runCatching {
            LocalDateTime.parse(
                "${match.groupValues[7]} ${match.groupValues[8]}",
                listTimeFormatter
            )
        }.getOrNull()
        val nameField = match.groupValues[9]
        val rawName = nameField.substringBefore(" -> ")
        val symlinkTarget = nameField.substringAfter(" -> ", missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
        val cleanedName = stripListSuffix(unescapeLsName(rawName), permissions)
        val fullPath = joinRemotePath(parentPath, cleanedName)
        val isDirectory = permissions.startsWith("d") || rawName.endsWith("/")
        return RemoteFileEntry(
            inode = match.groupValues[1].toLongOrNull(),
            permissions = permissions,
            hardLinks = match.groupValues[3].toIntOrNull(),
            owner = match.groupValues[4].ifBlank { null },
            group = match.groupValues[5].ifBlank { null },
            sizeBytes = match.groupValues[6].toLongOrNull(),
            modifiedAt = dateTime,
            name = cleanedName,
            fullPath = fullPath,
            symlinkTarget = symlinkTarget,
            kind = guessKind(cleanedName, isDirectory, permissions),
            isDirectory = isDirectory,
        )
    }

    private fun parseStat(path: String, rawOutput: String): RemoteFileStat {
        var sizeBytes: Long? = null
        var blocks: Long? = null
        var ioBlockBytes: Long? = null
        var typeLabel: String? = null
        var inode: Long? = null
        var hardLinks: Int? = null
        var octalMode: String? = null
        var permissions: String? = null
        var uid: Long? = null
        var uidName: String? = null
        var gid: Long? = null
        var gidName: String? = null
        var accessTime: String? = null
        var modifyTime: String? = null
        var changeTime: String? = null
        var device: String? = null
        var deviceType: String? = null
        var symlinkTarget: String? = null
        var statPath = path

        rawOutput.lineSequence().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("File:") -> {
                    statPath = trimmed.removePrefix("File:").trim()
                    if (" -> " in statPath) {
                        val parts = statPath.split(" -> ", limit = 2)
                        statPath = parts[0].trim()
                        symlinkTarget = parts.getOrNull(1)?.trim()
                    }
                }

                trimmed.startsWith("Size:") -> {
                    val match =
                        Regex("""Size:\s*(\d+)\s+Blocks:\s*(\d+)\s+IO Blocks:\s*(\d+)\s+(.+)""")
                            .find(trimmed)
                    sizeBytes = match?.groupValues?.getOrNull(1)?.toLongOrNull()
                    blocks = match?.groupValues?.getOrNull(2)?.toLongOrNull()
                    ioBlockBytes = match?.groupValues?.getOrNull(3)?.toLongOrNull()
                    typeLabel = match?.groupValues?.getOrNull(4)?.trim()
                }

                trimmed.startsWith("Device:") -> {
                    val match =
                        Regex("""Device:\s*(\S+)\s+Inode:\s*(\d+)\s+Links:\s*(\d+)\s+Device type:\s*(.+)""")
                            .find(trimmed)
                    device = match?.groupValues?.getOrNull(1)
                    inode = match?.groupValues?.getOrNull(2)?.toLongOrNull()
                    hardLinks = match?.groupValues?.getOrNull(3)?.toIntOrNull()
                    deviceType = match?.groupValues?.getOrNull(4)?.trim()
                }

                trimmed.startsWith("Access: (") -> {
                    val modeMatch = Regex("""Access:\s+\(([^/]+)/([^)]+)\)""").find(trimmed)
                    octalMode = modeMatch?.groupValues?.getOrNull(1)
                    permissions = modeMatch?.groupValues?.getOrNull(2)
                    val uidMatch = Regex("""Uid:\s+\(\s*(\d+)/\s*([^)]+)\)""").find(trimmed)
                    uid = uidMatch?.groupValues?.getOrNull(1)?.toLongOrNull()
                    uidName = uidMatch?.groupValues?.getOrNull(2)?.trim()
                    val gidMatch = Regex("""Gid:\s+\(\s*(\d+)/\s*([^)]+)\)""").find(trimmed)
                    gid = gidMatch?.groupValues?.getOrNull(1)?.toLongOrNull()
                    gidName = gidMatch?.groupValues?.getOrNull(2)?.trim()
                }

                trimmed.startsWith("Access:") -> accessTime = trimmed.removePrefix("Access:").trim()
                trimmed.startsWith("Modify:") -> modifyTime = trimmed.removePrefix("Modify:").trim()
                trimmed.startsWith("Change:") -> changeTime = trimmed.removePrefix("Change:").trim()
            }
        }

        return RemoteFileStat(
            path = statPath,
            name = statPath.substringAfterLast('/'),
            typeLabel = typeLabel,
            sizeBytes = sizeBytes,
            blocks = blocks,
            ioBlockBytes = ioBlockBytes,
            inode = inode,
            hardLinks = hardLinks,
            octalMode = octalMode,
            permissions = permissions,
            uid = uid,
            uidName = uidName,
            gid = gid,
            gidName = gidName,
            accessTime = accessTime,
            modifyTime = modifyTime,
            changeTime = changeTime,
            device = device,
            deviceType = deviceType,
            symlinkTarget = symlinkTarget,
            rawOutput = rawOutput.trim(),
        )
    }

    private fun stripListSuffix(name: String, permissions: String): String {
        if (name.isEmpty()) {
            return name
        }
        val suffix = name.last()
        return when {
            permissions.startsWith("d") && suffix == '/' -> name.dropLast(1)
            suffix in listOf('*', '@', '|', '=', '>') -> name.dropLast(1)
            else -> name
        }
    }

    private fun unescapeLsName(value: String): String {
        val builder = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (ch == '\\' && index + 1 < value.length) {
                builder.append(value[index + 1])
                index += 2
            } else {
                builder.append(ch)
                index++
            }
        }
        return builder.toString()
    }

    private fun joinRemotePath(parent: String, child: String): String {
        val normalizedParent = parent.trimEnd('/').ifBlank { "/" }
        val normalizedChild = child.trimStart('/')
        return when (normalizedParent) {
            "/" -> "/$normalizedChild"
            else -> "$normalizedParent/$normalizedChild"
        }
    }

    private fun pathForListCommand(path: String): String {
        val normalized = path.trimEnd('/').ifBlank { "/" }
        return if (normalized == "/") {
            normalized
        } else {
            "$normalized/."
        }
    }

    private fun guessMimeType(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    private fun ensureDirectoryExists(directory: File?) {
        requireNotNull(directory) { AppRuntime.stringResource(R.string.fm_exception_directory_not_found) }
        if (directory.exists()) {
            require(directory.isDirectory) { "${AppRuntime.stringResource(R.string.fm_exception_not_directory)}: ${directory.absolutePath}" }
            return
        }
        if (!directory.mkdirs()) {
            throw IOException("${AppRuntime.stringResource(R.string.fm_exception_cannot_create_dir)}: ${directory.absolutePath}")
        }
    }

    private fun uniqueFile(directory: File, fileName: String): File {
        val baseName = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
        var candidate = File(directory, fileName)
        var index = 1
        while (candidate.exists()) {
            val suffix = " ($index)"
            candidate = if (extension.isBlank() || baseName == fileName) {
                File(directory, "$fileName$suffix")
            } else {
                File(directory, "$baseName$suffix.$extension")
            }
            index++
        }
        return candidate
    }

    private fun uniqueDirectory(parent: File, baseName: String): File {
        var candidate = File(parent, baseName)
        var index = 1
        while (candidate.exists()) {
            candidate = File(parent, "$baseName ($index)")
            index++
        }
        return candidate
    }

    private fun createUniqueDirectoryDocument(
        context: Context,
        parentDocument: Uri,
        baseName: String
    ): Uri {
        var name = baseName
        var index = 1
        while (
            findChildDocument(
                context,
                parentDocument,
                name,
                DocumentsContract.Document.MIME_TYPE_DIR
            ) != null
        ) {
            name = "$baseName ($index)"
            index++
        }
        return DocumentsContract.createDocument(
            context.contentResolver,
            parentDocument,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name,
        ) ?: throw IOException("${AppRuntime.stringResource(R.string.fm_exception_cannot_create_dir)}: $name")
    }

    private fun createUniqueDocument(
        context: Context,
        parentDocument: Uri,
        baseName: String,
        mimeType: String,
    ): Uri {
        val fileBase = baseName.substringBeforeLast('.', baseName)
        val extension = baseName.substringAfterLast('.', "")
        var candidate = baseName
        var index = 1
        while (findChildDocument(context, parentDocument, candidate, mimeType) != null) {
            candidate =
                if (extension.isBlank() || fileBase == baseName) "$baseName ($index)"
                else "$fileBase ($index).$extension"
            index++
        }
        return DocumentsContract.createDocument(
            context.contentResolver,
            parentDocument,
            mimeType,
            candidate,
        ) ?: throw IOException("${AppRuntime.stringResource(R.string.fm_exception_cannot_create_file)}: $candidate")
    }

    private fun ensureTreeDirectory(
        context: Context,
        rootDocument: Uri,
        relativePath: String
    ): Uri {
        var current = rootDocument
        if (relativePath.isBlank()) return current
        relativePath.split('/')
            .filter { it.isNotBlank() }
            .forEach { segment ->
                val existing = findChildDocument(
                    context,
                    current,
                    segment,
                    DocumentsContract.Document.MIME_TYPE_DIR
                )
                current = existing ?: DocumentsContract.createDocument(
                    context.contentResolver,
                    current,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    segment,
                ) ?: throw IOException("${AppRuntime.stringResource(R.string.fm_exception_cannot_create_dir)}: $segment")
            }
        return current
    }

    private fun findChildDocument(
        context: Context,
        parentDocument: Uri,
        displayName: String,
        mimeType: String,
    ): Uri? {
        val parentId = DocumentsContract.getDocumentId(parentDocument)
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(parentDocument, parentId)
        val resolver = context.contentResolver
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val childDisplayName = cursor.getString(1)
                val childMimeType = cursor.getString(2)
                if (childDisplayName == displayName && childMimeType == mimeType) {
                    return DocumentsContract.buildDocumentUriUsingTree(
                        parentDocument,
                        cursor.getString(0),
                    )
                }
            }
        }
        return null
    }
}

class DirectorySnapshotSession private constructor(
    private val shellSession: InteractiveShellSession,
) : Closeable {
    suspend fun load(path: String): DirectoryDownloadSnapshot {
        val normalizedPath = path.trimEnd('/').ifBlank { "/" }
        val totalBytes = parseDuBytes(
            shellSession.execute("du -sb ${FileManagerService.quoteShellArg(normalizedPath)}")
        )

        val directories = shellSession.execute(
            "find ${FileManagerService.quoteShellArg(normalizedPath)} -type d"
        )
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() && it != normalizedPath }
            .map { relativePath(normalizedPath, it) }
            .filter { it.isNotBlank() }
            .toList()

        val files = shellSession.execute(
            "find ${FileManagerService.quoteShellArg(normalizedPath)} -type f"
        )
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() && it != normalizedPath }
            .map { relativePath(normalizedPath, it) }
            .filter { it.isNotBlank() }
            .toList()

        return DirectoryDownloadSnapshot(
            remoteRootPath = normalizedPath,
            totalBytes = totalBytes,
            directories = directories,
            files = files,
        )
    }

    suspend fun interrupt() {
        shellSession.interrupt()
        close()
    }

    override fun close() {
        shellSession.close()
    }

    companion object {
        suspend fun open(): DirectorySnapshotSession {
            return DirectorySnapshotSession(InteractiveShellSession.open())
        }

        private fun parseDuBytes(output: String): Long? {
            return output.lineSequence()
                .map(String::trim).firstNotNullOfOrNull { line ->
                    Regex("""^(\d+)(?:\s+.*)?$""")
                        .find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toLongOrNull()
                }
        }

        private fun relativePath(root: String, fullPath: String): String {
            val prefix = root.trimEnd('/') + "/"
            return if (fullPath.startsWith(prefix)) fullPath.removePrefix(prefix)
            else fullPath
        }
    }
}

private class InteractiveShellSession private constructor(
    private val stream: AdbSocketStream,
) : Closeable {
    private val mutex = Mutex()

    suspend fun execute(command: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val marker = "__SCRCPY_FILEMANAGER_${System.nanoTime()}__"
            val commandBytes = buildString {
                append("(")
                append(command)
                append(")\n")
                append("printf '\\n")
                append(marker)
                append(":%d\\n' $?\n")
            }.toByteArray(StandardCharsets.UTF_8)
            stream.outputStream.write(commandBytes)
            stream.outputStream.flush()
            readUntilMarker(marker)
        }
    }

    suspend fun interrupt() = withContext(Dispatchers.IO) {
        runCatching {
            stream.outputStream.write(byteArrayOf(3))
            stream.outputStream.flush()
        }
    }

    override fun close() {
        runCatching { stream.close() }
    }

    private fun readUntilMarker(marker: String): String {
        val builder = StringBuilder()
        val buffer = ByteArray(4096)
        val markerPrefix = "\n$marker:"

        while (true) {
            val count = stream.inputStream.read(buffer)
            if (count <= 0)
                throw IOException(AppRuntime.stringResource(R.string.fm_exception_remote_shell_closed))

            builder.append(String(buffer, 0, count, StandardCharsets.UTF_8))
            val markerStart = builder.indexOf(markerPrefix)
            if (markerStart >= 0) {
                val markerEnd = builder.indexOf("\n", markerStart + markerPrefix.length)
                if (markerEnd >= 0) {
                    val statusText =
                        builder.substring(markerStart + markerPrefix.length, markerEnd).trim()
                    val status = statusText.toIntOrNull() ?: 1
                    val output = builder.substring(0, markerStart).trimEnd('\r', '\n')
                    if (status != 0) {
                        throw IOException(output.ifBlank { "${AppRuntime.stringResource(R.string.fm_exception_command_failed)} ($status)" })
                    }
                    return output
                }
            }
        }
    }

    companion object {
        suspend fun open(): InteractiveShellSession {
            return withContext(Dispatchers.IO) {
                InteractiveShellSession(NativeAdbService.openShellStream(""))
            }
        }
    }
}
