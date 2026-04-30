package io.github.miuzarte.scrcpyforandroid.pages

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.RawOff
import androidx.compose.material.icons.rounded.RawOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.DirectoryDownloadSnapshot
import io.github.miuzarte.scrcpyforandroid.services.FileManagerService
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.services.RemoteFileEntry
import io.github.miuzarte.scrcpyforandroid.services.RemoteFileKind
import io.github.miuzarte.scrcpyforandroid.services.RemoteFileStat
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.PullToRefreshState
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

private const val INITIAL_REMOTE_PATH = "/storage/emulated/0"

@Composable
fun FileManagerScreen(
    bottomInnerPadding: Dp,
    onCanNavigateUpChange: (Boolean) -> Unit = {},
    onNavigateUpActionChange: (((() -> Boolean)?) -> Unit)? = null,
) {
    val viewModel: FileManagerViewModel = viewModel()
    val context = LocalContext.current
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val layoutDirection = LocalLayoutDirection.current

    val pathStack by viewModel.pathStack.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val cachedEntries by viewModel.cachedEntries.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val errorText by viewModel.errorText.collectAsState()
    val displayedEntries by viewModel.displayedEntries.collectAsState()
    val sortField by viewModel.sortField.collectAsState()
    val sortDescending by viewModel.sortDescending.collectAsState()
    val directoryScrollCache by viewModel.directoryScrollCache.collectAsState()
    val pendingTreeDownload by viewModel.pendingTreeDownload.collectAsState()
    val canNavigateUp by viewModel.canNavigateUp.collectAsState()
    val detailLoading by viewModel.detailLoading.collectAsState()
    val selectedEntry by viewModel.selectedEntry.collectAsState()
    val selectedStat by viewModel.selectedStat.collectAsState()
    val selectedTargetStat by viewModel.selectedTargetStat.collectAsState()
    val selectedSnapshot by viewModel.selectedSnapshot.collectAsState()
    val showDetailsSheet by viewModel.showDetailsSheet.collectAsState()
    val showRawDetails by viewModel.showRawDetails.collectAsState()

    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by rememberSaveable { mutableStateOf(false) }
    var showPathDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateFolderDialog by rememberSaveable { mutableStateOf(false) }
    var pathInput by rememberSaveable { mutableStateOf(INITIAL_REMOTE_PATH) }
    var newFolderName by rememberSaveable { mutableStateOf("") }

    val uploadLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            viewModel.uploadFile(context, uri)
        }

    val treeLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) viewModel.downloadToTree(context, uri)
        }


    LaunchedEffect(currentPath) {
        viewModel.reloadCurrentDirectory(force = false)
    }

    LaunchedEffect(currentPath, sortField, sortDescending, displayedEntries.size, loading) {
        if (loading || displayedEntries.isEmpty()) return@LaunchedEffect
        val scrollPosition = directoryScrollCache[currentPath] ?: return@LaunchedEffect
        val targetIndex = scrollPosition.index.coerceIn(0, displayedEntries.lastIndex)
        listState.scrollToItem(targetIndex, scrollPosition.offset)
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) viewModel.reloadCurrentDirectory(force = true)
    }

    LaunchedEffect(pendingTreeDownload) {
        if (pendingTreeDownload != null) treeLauncher.launch(null)
    }

    DisposableEffect(pathStack.size) {
        onCanNavigateUpChange(canNavigateUp)
        onNavigateUpActionChange?.invoke(viewModel::navigateUp)
        onDispose {
            onCanNavigateUpChange(false)
            onNavigateUpActionChange?.invoke(null)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearDetails() }
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                SmallTopAppBar(
                    title = stringResource(R.string.main_tab_files),
                    color =
                        if (blurActive) Color.Transparent
                        else colorScheme.surface,
                    navigationIcon = {
                        IconButton(
                            onClick = viewModel::navigateUp,
                            enabled = pathStack.size > 1,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.fm_cd_parent),
                            )
                        }
                    },
                    bottomContent = {
                        Text(
                            text = currentPath,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        pathInput = currentPath
                                        showPathDialog = true
                                    },
                                    onLongClick = {
                                        pathInput = currentPath
                                        showPathDialog = true
                                    },
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
                                holdDownState = showSortMenu
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Tune,
                                    contentDescription = stringResource(R.string.fm_cd_sort),
                                )
                            }
                            OverlayListPopup(
                                show = showSortMenu,
                                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                ListPopupColumn {
                                    val sortOptions = listOf(
                                        stringResource(R.string.fm_sort_name),
                                        stringResource(R.string.fm_sort_size),
                                        stringResource(R.string.fm_sort_time),
                                        stringResource(R.string.fm_sort_extension),
                                    )
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
                                                viewModel.updateSort(
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
                                    val dirOptions = listOf(
                                        stringResource(R.string.fm_sort_asc),
                                        stringResource(R.string.fm_sort_desc),
                                    )
                                    val dirIdx = if (sortDescending) 1 else 0
                                    dirOptions.forEachIndexed { i, option ->
                                        DropdownImpl(
                                            text = option,
                                            optionSize = dirOptions.size,
                                            isSelected = i == dirIdx,
                                            index = i,
                                            onSelectedIndexChange = { index ->
                                                viewModel.updateSort(
                                                    descending = index == 1
                                                )
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
                                    contentDescription = stringResource(R.string.cd_more),
                                )
                            }
                            OverlayListPopup(
                                show = showMenu,
                                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                onDismissRequest = { showMenu = false },
                            ) {
                                ListPopupColumn {
                                    DropdownImpl(
                                        text = stringResource(R.string.fm_menu_create_folder),
                                        optionSize = 2,
                                        isSelected = false,
                                        index = 0,
                                        onSelectedIndexChange = {
                                            showMenu = false
                                            newFolderName = ""
                                            showCreateFolderDialog = true
                                        },
                                    )
                                    DropdownImpl(
                                        text = stringResource(R.string.fm_menu_upload),
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
                else Modifier
        ) {
            FileManagerPage(
                contentPadding = pagePadding,
                bottomInnerPadding = bottomInnerPadding,
                loading = loading && cachedEntries == null,
                isRefreshing = isRefreshing,
                errorText = if (cachedEntries == null) errorText else null,
                displayedEntries = displayedEntries,
                pullToRefreshState = pullToRefreshState,
                listState = listState,
                layoutDirection = layoutDirection,
                onRefresh = { viewModel.setRefreshing(true) },
                onOpenEntry = { entry ->
                    viewModel.saveScrollPosition(
                        currentPath,
                        listState.firstVisibleItemIndex,
                        listState.firstVisibleItemScrollOffset
                    )
                    viewModel.openEntry(entry)
                },
                onShowEntryDetails = viewModel::showEntryDetails,
            )
        }
    }

    val entry = selectedEntry
    if (entry != null || showDetailsSheet) {
        FileDetailsBottomSheet(
            show = showDetailsSheet,
            content = when {
                detailLoading -> stringResource(R.string.fm_loading_details)
                entry != null && selectedStat != null -> buildDetailsText(
                    stat = selectedStat!!,
                    targetStat = selectedTargetStat,
                    directorySnapshot =
                        if (entry.isDirectory) selectedSnapshot
                        else null,
                    showRaw = showRawDetails,
                )

                else -> stringResource(R.string.fm_no_details)
            },
            onDismissRequest = viewModel::dismissDetails,
            onDismissFinished = viewModel::clearDetails,
            onToggleRaw = viewModel::toggleRawDetails,
            showingRaw = showRawDetails,
            onDownload = { entry?.let { viewModel.requestDownload(it) } },
            downloadEnabled = entry != null
                    && !detailLoading
                    && (!entry.isDirectory || selectedSnapshot != null),
        )
    }
    if (showPathDialog) {
        PathJumpDialog(
            show = true,
            path = pathInput,
            onPathChange = { pathInput = it },
            onDismissRequest = { showPathDialog = false },
            onConfirm = {
                showPathDialog = false
                viewModel.jumpToPath(pathInput)
            },
        )
    }
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            show = true,
            folderName = newFolderName,
            onFolderNameChange = { newFolderName = it },
            onDismissRequest = { showCreateFolderDialog = false },
            onConfirm = {
                showCreateFolderDialog = false
                viewModel.createFolder(newFolderName)
            },
        )
    }
}

@Composable
private fun FileManagerPage(
    contentPadding: PaddingValues,
    bottomInnerPadding: Dp,
    loading: Boolean,
    isRefreshing: Boolean,
    errorText: String?,
    displayedEntries: List<RemoteFileEntry>,
    pullToRefreshState: PullToRefreshState,
    listState: LazyListState,
    layoutDirection: LayoutDirection,
    onRefresh: () -> Unit,
    onOpenEntry: (RemoteFileEntry) -> Unit,
    onShowEntryDetails: (RemoteFileEntry) -> Unit,
) {
    val fileCardMinWidth = 220.dp
    val listHorizontalPadding =
        contentPadding.calculateLeftPadding(layoutDirection) +
                contentPadding.calculateRightPadding(layoutDirection) +
                UiSpacing.PageHorizontal * 2

    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        pullToRefreshState = pullToRefreshState,
        refreshTexts = listOf(
            stringResource(R.string.fm_pull_refresh),
            stringResource(R.string.fm_release_refresh),
            stringResource(R.string.fm_refreshing),
            stringResource(R.string.fm_refresh_done),
        ),
        contentPadding = PaddingValues(top = contentPadding.calculateTopPadding() + 12.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val availableListWidth = (maxWidth - listHorizontalPadding)
                .coerceAtLeast(fileCardMinWidth)
            val columns = ((availableListWidth.value + UiSpacing.PageItem.value) /
                    (fileCardMinWidth.value + UiSpacing.PageItem.value)).toInt()
                .coerceAtLeast(1)
            val fileRows = remember(displayedEntries, columns) {
                displayedEntries.chunked(columns)
            }

            @Composable
            fun FileStateContent() {
                when {
                    loading -> FileManagerStatusCard(
                        message = stringResource(R.string.text_loading),
                        modifier = Modifier.fillMaxWidth()
                    )

                    errorText != null -> FileManagerStatusCard(
                        message = stringResource(R.string.fm_load_failed, errorText),
                        modifier = Modifier.fillMaxWidth()
                    )

                    displayedEntries.isEmpty() -> FileManagerStatusCard(
                        message = stringResource(R.string.fm_empty_dir),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            LazyColumn(
                contentPadding = contentPadding,
                bottomInnerPadding = bottomInnerPadding,
                state = listState,
                limitLandscapeWidth = false,
            ) {
                if (loading || errorText != null || displayedEntries.isEmpty()) {
                    item { FileStateContent() }
                } else {
                    items(fileRows) { rowEntries ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(UiSpacing.PageItem)
                        ) {
                            rowEntries.forEach { entry ->
                                FileManagerItemCard(
                                    entry = entry,
                                    summary = FileManagerService.formatSummary(entry),
                                    onClick = { onOpenEntry(entry) },
                                    onLongClick = { onShowEntryDetails(entry) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(72.dp),
                                )
                            }
                            repeat(columns - rowEntries.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
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
    onDismissFinished: () -> Unit,
    onToggleRaw: () -> Unit,
    showingRaw: Boolean,
    onDownload: () -> Unit,
    downloadEnabled: Boolean,
) {
    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.fm_file_details),
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        startAction = {
            IconButton(
                onClick = onToggleRaw
            ) {
                Icon(
                    imageVector =
                        if (!showingRaw) Icons.Rounded.RawOff
                        else Icons.Rounded.RawOn,
                    contentDescription = stringResource(
                        if (!showingRaw) R.string.fm_show_raw
                        else R.string.fm_show_parsed
                    ),
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
                    contentDescription = stringResource(R.string.fm_cd_download),
                )
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(2f / 3f)
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
        title = stringResource(R.string.fm_goto_path),
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical)) {
            TextField(
                value = path,
                onValueChange = onPathChange,
                label = "/storage/emulated/0",
                useLabelAsPlaceholder = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.PageItem)) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = onDismissRequest,
                )
                TextButton(
                    text = stringResource(R.string.button_confirm),
                    onClick = onConfirm,
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
        title = stringResource(R.string.fm_title_create_folder),
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical)) {
            TextField(
                value = folderName,
                onValueChange = onFolderNameChange,
                label = stringResource(R.string.fm_label_new_folder),
                useLabelAsPlaceholder = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.PageItem)) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = onDismissRequest,
                )
                TextButton(
                    text = stringResource(R.string.fm_button_create),
                    onClick = onConfirm,
                )
            }
        }
    }
}

private fun iconForEntry(entry: RemoteFileEntry): ImageVector = when (entry.kind) {
    RemoteFileKind.Directory -> Icons.Rounded.Folder
    RemoteFileKind.Image -> Icons.Rounded.Image
    RemoteFileKind.Link -> Icons.Rounded.Link
    else -> Icons.AutoMirrored.Rounded.InsertDriveFile
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
        details.append("\n\n${AppRuntime.stringResource(R.string.fm_stat_target_info)}\n")
        details.append(
            if (showRaw) targetStat.rawOutput
            else FileManagerService.formatStatDetails(targetStat)
        )
    }
    return details.toString()
}
