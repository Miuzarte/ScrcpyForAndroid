package io.github.miuzarte.scrcpyforandroid.pages

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.DirectoryDownloadSnapshot
import io.github.miuzarte.scrcpyforandroid.services.DirectorySnapshotSession
import io.github.miuzarte.scrcpyforandroid.services.FileManagerService
import io.github.miuzarte.scrcpyforandroid.services.RemoteFileEntry
import io.github.miuzarte.scrcpyforandroid.services.RemoteFileKind
import io.github.miuzarte.scrcpyforandroid.services.RemoteFileStat
import io.github.miuzarte.scrcpyforandroid.storage.BundleSyncDelegate
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private const val ROOT_REMOTE_PATH = "/"
private const val INITIAL_REMOTE_PATH = "/storage/emulated/0"

internal enum class FileManagerSortField { NAME, SIZE, TIME, EXTENSION }

internal data class FileManagerScrollPosition(val index: Int, val offset: Int)

internal sealed interface PendingTreeDownload {
    data class File(val remotePath: String, val fileName: String) : PendingTreeDownload
    data class Directory(val snapshot: DirectoryDownloadSnapshot) : PendingTreeDownload
}

internal class FileManagerViewModel : ViewModel() {

    private val asBundleSync = BundleSyncDelegate(
        sharedFlow = appSettings.bundleState,
        save = { appSettings.saveBundle(it) },
        scope = viewModelScope,
    )
    private val asBundle = asBundleSync.value

    private val _pathStack = MutableStateFlow(buildPathStack(INITIAL_REMOTE_PATH))
    val pathStack: StateFlow<List<String>> = _pathStack.asStateFlow()
    val currentPath: StateFlow<String> = _pathStack
        .map { it.lastOrNull() ?: INITIAL_REMOTE_PATH }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            INITIAL_REMOTE_PATH,
        )
    val canNavigateUp: StateFlow<Boolean> = _pathStack
        .map { it.size > 1 }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false,
        )

    private val _directoryCache = MutableStateFlow<Map<String, List<RemoteFileEntry>>>(emptyMap())
    val directoryCache: StateFlow<Map<String, List<RemoteFileEntry>>> =
        _directoryCache.asStateFlow()

    val cachedEntries: StateFlow<List<RemoteFileEntry>?> = combine(
        _directoryCache, currentPath,
    ) { cache, path -> cache[path] }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null,
        )

    val sortField: StateFlow<FileManagerSortField> = asBundle
        .map {
            runCatching { FileManagerSortField.valueOf(it.fileManagerSortBy) }.getOrDefault(
                FileManagerSortField.NAME
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            FileManagerSortField.NAME,
        )
    val sortDescending: StateFlow<Boolean> = asBundle
        .map { it.fileManagerSortDescending }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false,
        )

    val displayedEntries: StateFlow<List<RemoteFileEntry>> = combine(
        cachedEntries, sortField, sortDescending,
    ) { entries, field, descending ->
        sortEntries(entries.orEmpty(), field, descending)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList(),
    )

    private val _directoryScrollCache =
        MutableStateFlow(emptyMap<String, FileManagerScrollPosition>())
    val directoryScrollCache: StateFlow<Map<String, FileManagerScrollPosition>> =
        _directoryScrollCache.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorText = MutableStateFlow<String?>(null)
    val errorText: StateFlow<String?> = _errorText.asStateFlow()

    private val _showDetailsSheet = MutableStateFlow(false)
    val showDetailsSheet: StateFlow<Boolean> = _showDetailsSheet.asStateFlow()

    private val _showRawDetails = MutableStateFlow(false)
    val showRawDetails: StateFlow<Boolean> = _showRawDetails.asStateFlow()

    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    private val _selectedEntry = MutableStateFlow<RemoteFileEntry?>(null)
    val selectedEntry: StateFlow<RemoteFileEntry?> = _selectedEntry.asStateFlow()

    private val _selectedStat = MutableStateFlow<RemoteFileStat?>(null)
    val selectedStat: StateFlow<RemoteFileStat?> = _selectedStat.asStateFlow()

    private val _selectedTargetStat = MutableStateFlow<RemoteFileStat?>(null)
    val selectedTargetStat: StateFlow<RemoteFileStat?> = _selectedTargetStat.asStateFlow()

    private val _selectedSnapshot = MutableStateFlow<DirectoryDownloadSnapshot?>(null)
    val selectedSnapshot: StateFlow<DirectoryDownloadSnapshot?> = _selectedSnapshot.asStateFlow()

    private var activeSnapshotSession: DirectorySnapshotSession? = null

    private val _pendingTreeDownload = MutableStateFlow<PendingTreeDownload?>(null)
    val pendingTreeDownload: StateFlow<PendingTreeDownload?> = _pendingTreeDownload.asStateFlow()

    init {
        asBundleSync.start()
    }

    override fun onCleared() {
        clearDetailsInternal()
        runBlocking(Dispatchers.IO) { asBundleSync.flush() }
    }

    fun navigateUp(): Boolean {
        val stack = _pathStack.value
        if (stack.size <= 1) return false
        _pathStack.update { it.dropLast(1) }
        return true
    }

    fun jumpToPath(rawPath: String) {
        val normalized = normalizePath(rawPath)
        _pathStack.value = buildPathStack(normalized)
    }

    fun openEntry(entry: RemoteFileEntry) {
        when {
            entry.isDirectory -> {
                _pathStack.update { it + normalizePath(entry.fullPath) }
            }

            entry.kind == RemoteFileKind.Link || entry.symlinkTarget != null -> {
                viewModelScope.launch {
                    val targetPath = resolveLinkTarget(entry)
                    if (targetPath == null) {
                        AppRuntime.snackbar(R.string.fm_snack_link_unavailable)
                        return@launch
                    }
                    val result = runCatching { FileManagerService.stat(targetPath) }
                    withContext(Dispatchers.Main) {
                        result.onSuccess { targetStat ->
                            if (isDirectoryStat(targetStat)) jumpToPath(targetPath)
                            else AppRuntime.snackbar(R.string.fm_snack_not_dir)
                        }.onFailure { error ->
                            AppRuntime.snackbar(
                                R.string.fm_snack_link_read_failed,
                                error.message ?: error.javaClass.simpleName,
                            )
                        }
                    }
                }
            }

            else -> AppRuntime.snackbar(R.string.fm_snack_long_press_details)
        }
    }

    suspend fun reloadCurrentDirectory(force: Boolean) {
        val path = currentPath.value
        val cached = _directoryCache.value[path]
        if (!force && cached != null) return
        _loading.value = cached == null
        _errorText.value = null
        val result = runCatching { FileManagerService.listDirectory(path) }
        _loading.value = false
        _isRefreshing.value = false
        result.onSuccess { entries -> _directoryCache.update { cache -> cache + (path to entries) } }
            .onFailure { _errorText.value = it.message ?: it.javaClass.simpleName }
    }

    fun invalidateCacheForCurrentDirectory() {
        _directoryCache.update { it - currentPath.value }
    }

    fun createFolder(folderName: String) {
        val name = folderName.trim()
        if (name.isBlank()) {
            AppRuntime.snackbar(R.string.fm_snack_folder_name_empty)
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                FileManagerService.createDirectory(currentPath.value, name)
            }
            withContext(Dispatchers.Main) {
                result.onSuccess {
                    AppRuntime.snackbar(R.string.fm_snack_folder_created)
                    invalidateCacheForCurrentDirectory()
                    _isRefreshing.value = true
                }.onFailure { error ->
                    AppRuntime.snackbar(
                        R.string.fm_snack_create_failed,
                        error.message ?: error.javaClass.simpleName,
                    )
                }
            }
        }
    }

    fun uploadFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                context.contentResolver
                    .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                FileManagerService.uploadFile(context, uri, currentPath.value)
            }
            withContext(Dispatchers.Main) {
                result.onSuccess {
                    AppRuntime.snackbar(R.string.fm_snack_uploaded, currentPath.value)
                    invalidateCacheForCurrentDirectory()
                    _isRefreshing.value = true
                }.onFailure { error ->
                    AppRuntime.snackbar(
                        R.string.fm_snack_upload_failed,
                        error.message ?: error.javaClass.simpleName,
                    )
                }
            }
        }
    }

    fun showEntryDetails(entry: RemoteFileEntry) {
        clearDetailsInternal()
        _selectedEntry.value = entry
        _showDetailsSheet.value = true
        _detailLoading.value = true
        viewModelScope.launch {
            val statResult = runCatching { FileManagerService.stat(entry.fullPath) }
            val linkTargetPath = resolveLinkTarget(entry)
            val targetStatResult =
                linkTargetPath?.let { runCatching { FileManagerService.stat(it) } }
            val snapshotResult =
                if (entry.isDirectory) runCatching {
                    val session = DirectorySnapshotSession.open()
                    activeSnapshotSession = session
                    session.load(entry.fullPath)
                }
                else null

            withContext(Dispatchers.Main) {
                _detailLoading.value = false
                statResult
                    .onSuccess { _selectedStat.value = it }
                    .onFailure { error ->
                        AppRuntime.snackbar(
                            R.string.fm_snack_read_details_failed,
                            error.message ?: error.javaClass.simpleName,
                        )
                        if (_selectedEntry.value === entry) _selectedEntry.value = null
                    }
                targetStatResult
                    ?.onSuccess { _selectedTargetStat.value = it }
                snapshotResult
                    ?.onSuccess { _selectedSnapshot.value = it }
                    ?.onFailure { error ->
                        AppRuntime.snackbar(
                            R.string.fm_snack_scan_failed,
                            error.message ?: error.javaClass.simpleName,
                        )
                    }
            }
        }
    }

    fun dismissDetails() {
        _showDetailsSheet.value = false
    }

    fun toggleRawDetails() {
        _showRawDetails.update { !it }
    }

    fun clearDetails() {
        clearDetailsInternal()
        _showDetailsSheet.value = false
    }

    private fun clearDetailsInternal() {
        _selectedEntry.value = null
        _selectedStat.value = null
        _selectedTargetStat.value = null
        _selectedSnapshot.value = null
        _detailLoading.value = false
        _showRawDetails.value = false
        val session = activeSnapshotSession
        activeSnapshotSession = null
        if (session != null) {
            viewModelScope.launch { runCatching { session.interrupt() } }
        }
    }

    fun requestDownload(entry: RemoteFileEntry) {
        val snapshot = _selectedSnapshot.value
        dismissDetails()
        AppRuntime.snackbar(R.string.fm_snack_download_starting)
        viewModelScope.launch {
            if (entry.isDirectory) {
                if (snapshot == null) {
                    AppRuntime.snackbar(R.string.fm_snack_dir_loading)
                    return@launch
                }
                val directSaved = FileManagerService.downloadDirectoryToPublicDownloads(snapshot)
                if (directSaved)
                    AppRuntime.snackbar(R.string.fm_snack_downloaded)
                else _pendingTreeDownload.value = PendingTreeDownload.Directory(snapshot)
            } else {
                val directSaved =
                    FileManagerService.downloadFileToPublicDownloads(entry.fullPath, entry.name)
                if (directSaved) AppRuntime.snackbar(R.string.fm_snack_downloaded)
                else _pendingTreeDownload.value =
                    PendingTreeDownload.File(entry.fullPath, entry.name)
            }
            if (_pendingTreeDownload.value != null)
                AppRuntime.snackbar(R.string.fm_snack_cannot_save)
        }
    }

    suspend fun startDownloadToTree(context: Context, treeUri: Uri, request: PendingTreeDownload) {
        when (request) {
            is PendingTreeDownload.File -> FileManagerService.downloadFileToTree(
                context = context,
                treeUri = treeUri,
                remotePath = request.remotePath,
                fileName = request.fileName,
            )

            is PendingTreeDownload.Directory -> FileManagerService.downloadDirectoryToTree(
                context = context, treeUri = treeUri, snapshot = request.snapshot,
            )
        }
    }

    fun consumePendingTreeDownload() {
        val pending = _pendingTreeDownload.value
        _pendingTreeDownload.value = null
    }

    fun restorePendingTreeDownload(request: PendingTreeDownload) {
        _pendingTreeDownload.value = request
    }

    fun saveScrollPosition(path: String, index: Int, offset: Int) {
        _directoryScrollCache.update { it + (path to FileManagerScrollPosition(index, offset)) }
    }

    fun setRefreshing(value: Boolean) {
        _isRefreshing.value = value
    }

    fun downloadToTree(context: Context, treeUri: Uri) {
        val request = _pendingTreeDownload.value
        _pendingTreeDownload.value = null
        if (request == null) return
        viewModelScope.launch {
            val result = runCatching {
                context.contentResolver
                    .takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                startDownloadToTree(context, treeUri, request)
            }
            withContext(Dispatchers.Main) {
                result
                    .onSuccess { AppRuntime.snackbar(R.string.fm_snack_download_complete) }
                    .onFailure { error ->
                        AppRuntime.snackbar(
                            R.string.fm_snack_download_failed,
                            error.message ?: error.javaClass.simpleName,
                        )
                    }
            }
        }
    }

    fun updateSort(sortBy: FileManagerSortField? = null, descending: Boolean? = null) {
        viewModelScope.launch {
            appSettings.updateBundle {
                it.copy(
                    fileManagerSortBy = (sortBy ?: sortField.value).name,
                    fileManagerSortDescending = descending ?: sortDescending.value,
                )
            }
        }
    }
}

internal fun buildPathStack(path: String): List<String> {
    val normalized = normalizePath(path)
    if (normalized == ROOT_REMOTE_PATH) return listOf(ROOT_REMOTE_PATH)
    val parts = normalized.trim('/').split('/').filter { it.isNotBlank() }
    val stack = mutableListOf(ROOT_REMOTE_PATH)
    var current = ""
    parts.forEach { part ->
        current += "/$part"
        stack += current
    }
    return stack
}

internal fun normalizePath(path: String): String {
    val trimmed = path.trim()
    if (trimmed.isBlank()) return ROOT_REMOTE_PATH
    return "/" + trimmed
        .trim('/')
        .split('/')
        .filter { it.isNotBlank() }
        .joinToString("/")
        .ifBlank { ROOT_REMOTE_PATH.removePrefix("/") }
}

internal fun sortEntries(
    entries: List<RemoteFileEntry>,
    field: FileManagerSortField,
    descending: Boolean,
): List<RemoteFileEntry> {
    val comparator: Comparator<RemoteFileEntry> = when (field) {
        FileManagerSortField.NAME -> compareBy { it.name.lowercase() }
        FileManagerSortField.SIZE -> compareBy<RemoteFileEntry> {
            it.sizeBytes ?: -1L
        }.thenBy { it.name.lowercase() }

        FileManagerSortField.TIME -> compareBy<RemoteFileEntry> { it.modifiedAt }.thenBy { it.name.lowercase() }
        FileManagerSortField.EXTENSION -> compareBy<RemoteFileEntry>(::extensionSortBucket)
            .thenBy(::extensionSortKey).thenBy { it.name.lowercase() }
    }
    return entries.sortedWith(
        compareByDescending<RemoteFileEntry> { it.isDirectory }
            .then(if (descending) comparator.reversed() else comparator)
    )
}

internal fun resolveLinkTarget(entry: RemoteFileEntry): String? {
    val target = entry.symlinkTarget?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return if (target.startsWith("/")) normalizePath(target)
    else normalizePath(entry.fullPath.substringBeforeLast('/', "") + "/" + target)
}

internal fun isDirectoryStat(stat: RemoteFileStat): Boolean =
    stat.permissions
        ?.startsWith("d") == true ||
            stat.typeLabel
                ?.contains("directory", ignoreCase = true) == true

private fun extensionSortBucket(entry: RemoteFileEntry) =
    if (entry.isDirectory || extensionSortKey(entry).isEmpty()) 0 else 1

private fun extensionSortKey(entry: RemoteFileEntry) =
    if (entry.isDirectory) ""
    else entry.name.substringAfterLast('.', "").lowercase()

