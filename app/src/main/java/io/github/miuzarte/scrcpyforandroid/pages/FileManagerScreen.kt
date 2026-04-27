package io.github.miuzarte.scrcpyforandroid.pages

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.RawOff
import androidx.compose.material.icons.rounded.RawOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.services.DirectoryDownloadSnapshot
import io.github.miuzarte.scrcpyforandroid.services.DirectorySnapshotSession
import io.github.miuzarte.scrcpyforandroid.services.FileManagerService
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.services.RemoteFileEntry
import io.github.miuzarte.scrcpyforandroid.services.RemoteFileKind
import io.github.miuzarte.scrcpyforandroid.services.RemoteFileStat
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

private const val ROOT_REMOTE_PATH = "/"
private const val INITIAL_REMOTE_PATH = "/storage/emulated/0"

private enum class FileManagerSortField {
    NAME,
    SIZE,
    TIME,
    EXTENSION,
}

private data class FileManagerScrollPosition(
    val index: Int,
    val offset: Int,
)

private sealed interface PendingTreeDownload {
    data class File(
        val remotePath: String,
        val fileName: String,
    ) : PendingTreeDownload

    data class Directory(
        val snapshot: DirectoryDownloadSnapshot,
    ) : PendingTreeDownload
}

@Composable
fun FileManagerScreen(
    bottomInnerPadding: Dp,
    onCanNavigateUpChange: (Boolean) -> Unit = {},
    onNavigateUpActionChange: (((() -> Boolean)?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null
    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val layoutDirection = LocalLayoutDirection.current
    val asBundle by appSettings.bundleState.collectAsState()

    val pathStack = remember {
        mutableStateListOf<String>().apply {
            addAll(buildPathStack(INITIAL_REMOTE_PATH))
        }
    }
    val currentPath = pathStack.lastOrNull() ?: INITIAL_REMOTE_PATH
    val directoryCache = remember { mutableStateMapOf<String, List<RemoteFileEntry>>() }
    val directoryScrollCache = remember { mutableStateMapOf<String, FileManagerScrollPosition>() }
    var loading by rememberSaveable { mutableStateOf(false) }
    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by rememberSaveable { mutableStateOf(false) }
    var showPathDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateFolderDialog by rememberSaveable { mutableStateOf(false) }
    var showDetailsSheet by rememberSaveable { mutableStateOf(false) }
    var showRawDetails by rememberSaveable { mutableStateOf(false) }
    var pathInput by rememberSaveable { mutableStateOf(INITIAL_REMOTE_PATH) }
    var newFolderName by rememberSaveable { mutableStateOf("") }
    var selectedEntry by remember { mutableStateOf<RemoteFileEntry?>(null) }
    var selectedStat by remember { mutableStateOf<RemoteFileStat?>(null) }
    var selectedTargetStat by remember { mutableStateOf<RemoteFileStat?>(null) }
    var selectedSnapshot by remember { mutableStateOf<DirectoryDownloadSnapshot?>(null) }
    var detailLoading by rememberSaveable { mutableStateOf(false) }
    var pendingTreeDownload by remember { mutableStateOf<PendingTreeDownload?>(null) }
    var activeSnapshotSession by remember { mutableStateOf<DirectorySnapshotSession?>(null) }

    val sortField = remember(asBundle.fileManagerSortBy) {
        runCatching { FileManagerSortField.valueOf(asBundle.fileManagerSortBy) }
            .getOrDefault(FileManagerSortField.NAME)
    }
    val sortDescending = asBundle.fileManagerSortDescending
    val cachedEntries = directoryCache[currentPath]
    val displayedEntries = remember(cachedEntries, sortField, sortDescending) {
        sortEntries(cachedEntries.orEmpty(), sortField, sortDescending)
    }

    fun clearDetails() {
        selectedEntry = null
        selectedStat = null
        selectedTargetStat = null
        selectedSnapshot = null
        detailLoading = false
        showRawDetails = false
        val session = activeSnapshotSession
        activeSnapshotSession = null
        if (session != null) {
            taskScope.launch {
                runCatching { session.interrupt() }
            }
        }
    }

    fun dismissDetails() {
        showDetailsSheet = false
    }

    fun rememberCurrentScrollPosition() {
        directoryScrollCache[currentPath] = FileManagerScrollPosition(
            index = listState.firstVisibleItemIndex,
            offset = listState.firstVisibleItemScrollOffset,
        )
    }

    fun navigateUp(): Boolean {
        if (pathStack.size <= 1) {
            return false
        }
        rememberCurrentScrollPosition()
        pathStack.removeAt(pathStack.lastIndex)
        return true
    }

    fun jumpToPath(rawPath: String) {
        val normalized = normalizePath(rawPath)
        rememberCurrentScrollPosition()
        pathStack.clear()
        pathStack.addAll(buildPathStack(normalized))
    }

    suspend fun reloadCurrentDirectory(force: Boolean) {
        val cached = directoryCache[currentPath]
        if (cached != null && !force) {
            errorText = null
            return
        }
        loading = cached == null
        errorText = null
        val result = runCatching {
            FileManagerService.listDirectory(currentPath)
        }
        loading = false
        isRefreshing = false
        result.onSuccess { directoryCache[currentPath] = it }
            .onFailure { error ->
                errorText = error.message ?: error.javaClass.simpleName
            }
    }

    suspend fun startDownloadToTree(treeUri: Uri, request: PendingTreeDownload) {
        when (request) {
            is PendingTreeDownload.File -> {
                FileManagerService.downloadFileToTree(
                    context = context,
                    treeUri = treeUri,
                    remotePath = request.remotePath,
                    fileName = request.fileName,
                )
            }

            is PendingTreeDownload.Directory -> {
                FileManagerService.downloadDirectoryToTree(
                    context = context,
                    treeUri = treeUri,
                    snapshot = request.snapshot,
                )
            }
        }
    }

    fun requestDownload(entry: RemoteFileEntry) {
        val snapshot = selectedSnapshot
        dismissDetails()
        snackbar.show("开始下载")
        taskScope.launch {
            if (entry.isDirectory) {
                if (snapshot == null) {
                    withContext(Dispatchers.Main) {
                        snackbar.show("目录信息仍在加载，请稍后重试")
                    }
                    return@launch
                }
                val directSaved = FileManagerService.downloadDirectoryToPublicDownloads(snapshot)
                if (directSaved) {
                    withContext(Dispatchers.Main) { snackbar.show("已下载到 Download/Scrcpy") }
                } else {
                    withContext(Dispatchers.Main) {
                        pendingTreeDownload = PendingTreeDownload.Directory(snapshot)
                        snackbar.show("无法直接写入 Download/Scrcpy，请选择保存目录")
                    }
                }
            } else {
                val directSaved = FileManagerService.downloadFileToPublicDownloads(
                    remotePath = entry.fullPath,
                    fileName = entry.name,
                )
                if (directSaved) {
                    withContext(Dispatchers.Main) { snackbar.show("已下载到 Download/Scrcpy") }
                } else {
                    withContext(Dispatchers.Main) {
                        pendingTreeDownload = PendingTreeDownload.File(entry.fullPath, entry.name)
                        snackbar.show("无法直接写入 Download/Scrcpy，请选择保存目录")
                    }
                }
            }
        }
    }

    fun updateSort(sortBy: FileManagerSortField? = null, descending: Boolean? = null) {
        rememberCurrentScrollPosition()
        taskScope.launch {
            appSettings.updateBundle {
                it.copy(
                    fileManagerSortBy = (sortBy ?: sortField).name,
                    fileManagerSortDescending = descending ?: sortDescending,
                )
            }
        }
    }

    fun openPathDialog() {
        pathInput = currentPath
        showPathDialog = true
    }

    fun openCreateFolderDialog() {
        newFolderName = ""
        showCreateFolderDialog = true
    }

    val uploadLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            taskScope.launch {
                val result = runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    FileManagerService.uploadFile(context, uri, currentPath)
                }
                withContext(Dispatchers.Main) {
                    result.onSuccess {
                        snackbar.show("已上传到 $currentPath")
                        directoryCache.remove(currentPath)
                        isRefreshing = true
                    }.onFailure { error ->
                        snackbar.show("上传失败: ${error.message ?: error.javaClass.simpleName}")
                    }
                }
            }
        }

    val treeLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val request = pendingTreeDownload
            pendingTreeDownload = null
            if (uri == null || request == null) return@rememberLauncherForActivityResult
            taskScope.launch {
                val result = runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    startDownloadToTree(uri, request)
                }
                withContext(Dispatchers.Main) {
                    result.onSuccess {
                        snackbar.show("下载完成")
                    }.onFailure { error ->
                        snackbar.show("下载失败: ${error.message ?: error.javaClass.simpleName}")
                    }
                }
            }
        }

    LaunchedEffect(currentPath) {
        reloadCurrentDirectory(force = false)
    }

    LaunchedEffect(currentPath, sortField, sortDescending, displayedEntries.size, loading) {
        if (loading || displayedEntries.isEmpty()) return@LaunchedEffect

        val scrollPosition = directoryScrollCache[currentPath] ?: return@LaunchedEffect

        val targetIndex = scrollPosition.index.coerceIn(0, displayedEntries.lastIndex)
        listState.scrollToItem(targetIndex, scrollPosition.offset)
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) reloadCurrentDirectory(force = true)
    }

    LaunchedEffect(pendingTreeDownload) {
        if (pendingTreeDownload != null) treeLauncher.launch(null)
    }

    DisposableEffect(pathStack.size) {
        val canNavigateUp = pathStack.size > 1
        onCanNavigateUpChange(canNavigateUp)
        onNavigateUpActionChange?.invoke(::navigateUp)
        onDispose {
            onCanNavigateUpChange(false)
            onNavigateUpActionChange?.invoke(null)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            clearDetails()
            taskScope.cancel()
        }
    }

    fun openEntry(entry: RemoteFileEntry) {
        when {
            entry.isDirectory -> {
                rememberCurrentScrollPosition()
                pathStack.add(normalizePath(entry.fullPath))
            }

            entry.kind == RemoteFileKind.Link || entry.symlinkTarget != null -> {
                taskScope.launch {
                    val targetPath = resolveLinkTarget(entry)
                    if (targetPath == null) {
                        withContext(Dispatchers.Main) {
                            snackbar.show("链接目标不可用，长按查看信息")
                        }
                        return@launch
                    }
                    val result = runCatching {
                        FileManagerService.stat(targetPath)
                    }
                    withContext(Dispatchers.Main) {
                        result.onSuccess { targetStat ->
                            if (isDirectoryStat(targetStat)) {
                                jumpToPath(targetPath)
                            } else {
                                snackbar.show("链接目标不是文件夹，长按查看信息")
                            }
                        }.onFailure { error ->
                            snackbar.show("读取链接目标失败: ${error.message ?: error.javaClass.simpleName}")
                        }
                    }
                }
            }

            else -> snackbar.show("长按可查看文件详情")
        }
    }

    fun showEntryDetails(entry: RemoteFileEntry) {
        clearDetails()
        selectedEntry = entry
        showDetailsSheet = true
        detailLoading = true
        taskScope.launch {
            val statResult = runCatching {
                FileManagerService.stat(entry.fullPath)
            }
            val linkTargetPath = resolveLinkTarget(entry)
            val targetStatResult =
                if (linkTargetPath != null)
                    runCatching { FileManagerService.stat(linkTargetPath) }
                else null

            val snapshotResult =
                if (entry.isDirectory)
                    runCatching {
                        val session = DirectorySnapshotSession.open()
                        withContext(Dispatchers.Main) {
                            activeSnapshotSession = session
                        }
                        session.load(entry.fullPath)
                    }
                else null

            withContext(Dispatchers.Main) {
                detailLoading = false
                statResult
                    .onSuccess { selectedStat = it }
                    .onFailure { error ->
                        snackbar.show("读取详情失败: ${error.message ?: error.javaClass.simpleName}")
                        if (selectedEntry === entry)
                            selectedEntry = null
                    }
                targetStatResult
                    ?.onSuccess { selectedTargetStat = it }
                snapshotResult
                    ?.onSuccess { selectedSnapshot = it }
                    ?.onFailure { error ->
                        snackbar.show("目录扫描失败: ${error.message ?: error.javaClass.simpleName}")
                    }
            }
        }
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                SmallTopAppBar(
                    title = "文件",
                    color = if (blurActive) Color.Transparent else colorScheme.surface,
                    navigationIcon = {
                        IconButton(
                            onClick = ::navigateUp,
                            enabled = pathStack.size > 1,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "上一层",
                            )
                        }
                    },
                    bottomContent = {
                        Text(
                            text = currentPath,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = ::openPathDialog,
                                    onLongClick = ::openPathDialog,
                                )
                                .padding(
                                    start = UiSpacing.PageHorizontal,
                                    end = UiSpacing.PageHorizontal,
                                    bottom = UiSpacing.Medium,
                                ),
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    },
                    actions = {
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                holdDownState = showSortMenu,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Tune,
                                    contentDescription = "排序",
                                )
                            }
                            OverlayListPopup(
                                show = showSortMenu,
                                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                ListPopupColumn {
                                    // sort field group
                                    val sortOptions = listOf("文件名", "大小", "时间", "扩展名")
                                    val sortFieldIdx = when (sortField) {
                                        FileManagerSortField.NAME -> 0
                                        FileManagerSortField.SIZE -> 1
                                        FileManagerSortField.TIME -> 2
                                        FileManagerSortField.EXTENSION -> 3
                                    }
                                    sortOptions.forEachIndexed { i, option ->
                                        DropdownImpl(
                                            text = option,
                                            optionSize = sortOptions.size,
                                            isSelected = i == sortFieldIdx,
                                            index = i,
                                            onSelectedIndexChange = { index ->
                                                updateSort(
                                                    sortBy = when (index) {
                                                        1 -> FileManagerSortField.SIZE
                                                        2 -> FileManagerSortField.TIME
                                                        3 -> FileManagerSortField.EXTENSION
                                                        else -> FileManagerSortField.NAME
                                                    }
                                                )
                                            },
                                        )
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                                    // sort direction group
                                    val dirOptions = listOf("正序", "倒序")
                                    val dirIdx = if (sortDescending) 1 else 0
                                    dirOptions.forEachIndexed { i, option ->
                                        DropdownImpl(
                                            text = option,
                                            optionSize = dirOptions.size,
                                            isSelected = i == dirIdx,
                                            index = i,
                                            onSelectedIndexChange = { index ->
                                                updateSort(descending = index == 1)
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                holdDownState = showMenu,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.More,
                                    contentDescription = "更多",
                                )
                            }
                            OverlayListPopup(
                                show = showMenu,
                                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                onDismissRequest = { showMenu = false },
                            ) {
                                ListPopupColumn {
                                    DropdownImpl(
                                        text = "创建文件夹",
                                        optionSize = 2,
                                        isSelected = false,
                                        index = 0,
                                        onSelectedIndexChange = {
                                            showMenu = false
                                            openCreateFolderDialog()
                                        },
                                    )
                                    DropdownImpl(
                                        text = "上传文件到该目录",
                                        optionSize = 2,
                                        isSelected = false,
                                        index = 1,
                                        onSelectedIndexChange = {
                                            showMenu = false
                                            uploadLauncher.launch(arrayOf("*/*"))
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
    ) { pagePadding ->
        Box(
            modifier =
                if (blurActive) Modifier.layerBackdrop(blurBackdrop)
                else Modifier,
        ) {
            PullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = { isRefreshing = true },
                pullToRefreshState = pullToRefreshState,
                refreshTexts = listOf("下拉刷新", "释放刷新", "正在刷新...", "刷新完成"),
                contentPadding = PaddingValues(
                    top = pagePadding.calculateTopPadding() + 12.dp,
                ),
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val fileCardMinWidth = 220.dp
                    val listHorizontalPadding =
                        pagePadding.calculateLeftPadding(layoutDirection) +
                                pagePadding.calculateRightPadding(layoutDirection) +
                                UiSpacing.PageHorizontal * 2
                    val availableListWidth =
                        (maxWidth - listHorizontalPadding).coerceAtLeast(fileCardMinWidth)
                    val columns = (
                            (availableListWidth.value + UiSpacing.PageItem.value) /
                                    (fileCardMinWidth.value + UiSpacing.PageItem.value)
                            ).toInt().coerceAtLeast(1)
                    val fileRows = remember(displayedEntries, columns) {
                        displayedEntries.chunked(columns)
                    }

                    @Composable
                    fun FileStateContent() {
                        when {
                            loading && cachedEntries == null ->
                                FileManagerStatusCard(
                                    message = "加载中",
                                    modifier = Modifier.fillMaxWidth(),
                                )

                            errorText != null && cachedEntries == null ->
                                FileManagerStatusCard(
                                    message = "加载失败: $errorText",
                                    modifier = Modifier.fillMaxWidth(),
                                )

                            displayedEntries.isEmpty() ->
                                FileManagerStatusCard(
                                    message = "空目录",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                        }
                    }

                    LazyColumn(
                        contentPadding = pagePadding,
                        bottomInnerPadding = bottomInnerPadding,
                        state = listState,
                        limitLandscapeWidth = false,
                    ) {
                        if (loading && cachedEntries == null ||
                            errorText != null && cachedEntries == null ||
                            displayedEntries.isEmpty()
                        ) {
                            item { FileStateContent() }
                        } else {
                            items(fileRows) { rowEntries ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(UiSpacing.PageItem),
                                ) {
                                    rowEntries.forEach { entry ->
                                        FileManagerItemCard(
                                            entry = entry,
                                            summary = FileManagerService.formatSummary(entry),
                                            onClick = { openEntry(entry) },
                                            onLongClick = { showEntryDetails(entry) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(72.dp),
                                        )
                                    }
                                    repeat(columns - rowEntries.size) {
                                        Box(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val entry = selectedEntry
            if (entry != null || showDetailsSheet) {
                FileDetailsBottomSheet(
                    show = showDetailsSheet,
                    content = when {
                        detailLoading -> "正在加载详情"
                        entry != null && selectedStat != null -> buildDetailsText(
                            stat = selectedStat!!,
                            targetStat = selectedTargetStat,
                            directorySnapshot =
                                if (entry.isDirectory) selectedSnapshot
                                else null,
                            showRaw = showRawDetails,
                        )

                        else -> "暂无详情"
                    },
                    onDismissRequest = ::dismissDetails,
                    onDismissFinished = ::clearDetails,
                    onToggleRaw = { showRawDetails = !showRawDetails },
                    showingRaw = showRawDetails,
                    onDownload = { entry?.let(::requestDownload) },
                    downloadEnabled = entry != null
                            && !detailLoading
                            && (!entry.isDirectory || selectedSnapshot != null),
                )
            }

            PathJumpDialog(
                show = showPathDialog,
                path = pathInput,
                onPathChange = { pathInput = it },
                onDismissRequest = { showPathDialog = false },
                onConfirm = {
                    showPathDialog = false
                    jumpToPath(pathInput)
                },
            )

            CreateFolderDialog(
                show = showCreateFolderDialog,
                folderName = newFolderName,
                onFolderNameChange = { newFolderName = it },
                onDismissRequest = { showCreateFolderDialog = false },
                onConfirm = {
                    showCreateFolderDialog = false
                    val folderName = newFolderName.trim()
                    if (folderName.isBlank())
                        snackbar.show("文件夹名称不能为空")
                    else taskScope.launch {
                        val result = runCatching {
                            FileManagerService.createDirectory(currentPath, folderName)
                        }
                        withContext(Dispatchers.Main) {
                            result.onSuccess {
                                snackbar.show("已创建文件夹")
                                directoryCache.remove(currentPath)
                                isRefreshing = true
                            }.onFailure { error ->
                                snackbar.show("创建失败: ${error.message ?: error.javaClass.simpleName}")
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun FileManagerStatusCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun FileManagerItemCard(
    entry: RemoteFileEntry,
    summary: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = iconForEntry(entry),
                contentDescription = entry.name,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = UiSpacing.Medium),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = entry.name,
                        maxLines = 2,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = summary,
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileDetailsBottomSheet(
    show: Boolean,
    content: String,
    onDismissRequest: () -> Unit,
    onDismissFinished: (() -> Unit)? = null,
    onToggleRaw: () -> Unit,
    showingRaw: Boolean,
    onDownload: () -> Unit,
    downloadEnabled: Boolean,
    title: String = "详细信息",
) {
    OverlayBottomSheet(
        show = show,
        title = title,
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        startAction = {
            IconButton(
                onClick = onToggleRaw,
            ) {
                Icon(
                    imageVector =
                        if (!showingRaw) Icons.Rounded.RawOff
                        else Icons.Rounded.RawOn,
                    contentDescription =
                        if (!showingRaw) "显示原文"
                        else "显示解析",
                )
            }
        },
        endAction = {
            IconButton(
                onClick = onDownload,
                enabled = downloadEnabled,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "下载",
                )
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(2f / 3f),
        ) {
            item {
                TextField(
                    value = content,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    useLabelAsPlaceholder = true,
                )
            }
        }
    }
}

@Composable
private fun PathJumpDialog(
    show: Boolean,
    path: String,
    onPathChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = "跳转路径",
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
        ) {
            TextField(
                value = path,
                onValueChange = onPathChange,
                label = "/storage/emulated/0",
                useLabelAsPlaceholder = true,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "确定",
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun CreateFolderDialog(
    show: Boolean,
    folderName: String,
    onFolderNameChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = "创建文件夹",
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
        ) {
            TextField(
                value = folderName,
                onValueChange = onFolderNameChange,
                label = "新建文件夹",
                useLabelAsPlaceholder = true,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "确定",
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

private fun iconForEntry(entry: RemoteFileEntry): ImageVector = when (entry.kind) {
    RemoteFileKind.Directory -> Icons.Rounded.Folder
    RemoteFileKind.Image -> Icons.Rounded.Image
    RemoteFileKind.Video -> Icons.Rounded.Movie
    RemoteFileKind.Audio -> Icons.Rounded.AudioFile
    RemoteFileKind.Archive -> Icons.Rounded.Archive
    RemoteFileKind.Apk -> Icons.Rounded.Android
    RemoteFileKind.Text -> Icons.Rounded.Description
    RemoteFileKind.Link -> Icons.Rounded.Link
    RemoteFileKind.Other -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

private fun buildPathStack(path: String): List<String> {
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

private fun normalizePath(path: String): String {
    val trimmed = path.trim()
    if (trimmed.isBlank()) {
        return ROOT_REMOTE_PATH
    }
    return "/" + trimmed
        .trim('/')
        .split('/')
        .filter { it.isNotBlank() }
        .joinToString("/")
        .ifBlank { ROOT_REMOTE_PATH.removePrefix("/") }
}

private fun sortEntries(
    entries: List<RemoteFileEntry>,
    field: FileManagerSortField,
    descending: Boolean,
): List<RemoteFileEntry> {
    val contentComparator = when (field) {
        FileManagerSortField.NAME
            -> compareBy<RemoteFileEntry> { it.name.lowercase() }

        FileManagerSortField.SIZE
            -> compareBy<RemoteFileEntry> { it.sizeBytes ?: -1L }
            .thenBy { it.name.lowercase() }

        FileManagerSortField.TIME
            -> compareBy<RemoteFileEntry> { it.modifiedAt }
            .thenBy { it.name.lowercase() }

        FileManagerSortField.EXTENSION
            -> compareBy<RemoteFileEntry> { extensionSortBucket(it) }
            .thenBy { extensionSortKey(it) }
            .thenBy { it.name.lowercase() }
    }
    if (field == FileManagerSortField.EXTENSION) {
        val extensionComparator =
            if (descending)
                compareBy<RemoteFileEntry> { extensionSortBucket(it) }
                    .then(compareByDescending<RemoteFileEntry> { extensionSortKey(it) })
                    .thenBy { it.name.lowercase() }
            else contentComparator
        return entries.sortedWith(extensionComparator)
    }
    val orderComparator =
        if (descending) contentComparator.reversed()
        else contentComparator
    return entries.sortedWith(
        compareByDescending<RemoteFileEntry> { it.isDirectory }
            .then(orderComparator)
    )
}

private fun resolveLinkTarget(entry: RemoteFileEntry): String? {
    val target = entry.symlinkTarget?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return if (target.startsWith("/")) normalizePath(target)
    else normalizePath(entry.fullPath.substringBeforeLast('/', "") + "/" + target)
}

private fun isDirectoryStat(stat: RemoteFileStat): Boolean {
    return stat.permissions?.startsWith("d") == true
            || stat.typeLabel?.contains("directory", ignoreCase = true) == true
}

private fun buildDetailsText(
    stat: RemoteFileStat,
    targetStat: RemoteFileStat?,
    directorySnapshot: DirectoryDownloadSnapshot?,
    showRaw: Boolean,
): String {
    val details = StringBuilder(
        if (showRaw) stat.rawOutput
        else FileManagerService.formatStatDetails(stat, directorySnapshot)
    )
    if (targetStat != null) {
        details.append("\n\n目标信息\n")
        details.append(
            if (showRaw) targetStat.rawOutput
            else FileManagerService.formatStatDetails(targetStat)
        )
    }
    return details.toString()
}

private fun extensionSortBucket(entry: RemoteFileEntry): Int {
    return if (entry.isDirectory || extensionSortKey(entry).isEmpty()) 0 else 1
}

private fun extensionSortKey(entry: RemoteFileEntry): String {
    if (entry.isDirectory) return ""
    return entry.name.substringAfterLast('.', "").lowercase()
}
