package io.github.miuzarte.scrcpyforandroid.pages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.services.*
import io.github.miuzarte.scrcpyforandroid.ui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

private const val INITIAL_REMOTE_PATH = "/storage/emulated/0"

@Composable
fun FileManagerScreen(
    bottomInnerPadding: Dp,
    onCanNavigateUpChange: (Boolean) -> Unit = {},
    onNavigateUpActionChange: (((() -> Boolean)?) -> Unit)? = null,
) {
    val viewModel: FileManagerViewModel = viewModel()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val layoutDirection = LocalLayoutDirection.current

    val breadcrumbState by viewModel.breadcrumbState.collectAsState()
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

    DisposableEffect(canNavigateUp) {
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
                            onClick = {
                                haptic.contextClick()
                                viewModel.navigateUp()
                            },
                            enabled = canNavigateUp,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.fm_cd_parent),
                            )
                        }
                    },
                    bottomContent = {
                        val breadcrumbItems = remember(breadcrumbState.stack) {
                            breadcrumbState.stack.map { path ->
                                BreadcrumbItem(
                                    path = path,
                                    text = path.substringAfterLast('/').ifEmpty { "/" },
                                )
                            }
                        }
                        BreadcrumbBar(
                            items = breadcrumbItems,
                            onItemClick = { index ->
                                haptic.contextClick()
                                viewModel.jumpToPath(breadcrumbState.stack[index])
                            },
                            highlightIndex = breadcrumbState.highlightIndex,
                        )
                    },
                    actions = {
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
                        val dirOptions = listOf(
                            stringResource(R.string.fm_sort_asc),
                            stringResource(R.string.fm_sort_desc),
                        )
                        val dirIdx = if (sortDescending) 1 else 0
                        OverlayIconDropdownMenu(
                            entries = listOf(
                                DropdownEntry(
                                    items = sortOptions.mapIndexed { i, option ->
                                        DropdownItem(
                                            text = option,
                                            selected = i == sortFieldIdx,
                                            onClick = {
                                                viewModel.updateSort(
                                                    sortBy = when (i) {
                                                        1 -> FileManagerSortField.SIZE
                                                        2 -> FileManagerSortField.TIME
                                                        3 -> FileManagerSortField.EXTENSION
                                                        else -> FileManagerSortField.NAME
                                                    },
                                                )
                                            },
                                        )
                                    },
                                ),
                                DropdownEntry(
                                    items = dirOptions.mapIndexed { i, option ->
                                        DropdownItem(
                                            text = option,
                                            selected = i == dirIdx,
                                            onClick = {
                                                viewModel.updateSort(
                                                    descending = i == 1,
                                                )
                                            },
                                        )
                                    },
                                ),
                            ),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Tune,
                                contentDescription = stringResource(R.string.fm_cd_sort),
                            )
                        }

                        OverlayIconDropdownMenu(
                            entry = DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = stringResource(R.string.fm_goto_path),
                                        onClick = {
                                            pathInput = currentPath
                                            showPathDialog = true
                                        },
                                    ),
                                    DropdownItem(
                                        text = stringResource(R.string.fm_menu_create_folder),
                                        onClick = {
                                            newFolderName = ""
                                            showCreateFolderDialog = true
                                        },
                                    ),
                                    DropdownItem(
                                        text = stringResource(R.string.fm_menu_upload),
                                        onClick = {
                                            uploadLauncher.launch(arrayOf("*/*"))
                                        },
                                    ),
                                ),
                            ),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.More,
                                contentDescription = stringResource(R.string.cd_more),
                            )
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
                        listState.firstVisibleItemScrollOffset,
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

    PathJumpDialog(
        show = showPathDialog,
        path = pathInput,
        onPathChange = { pathInput = it },
        onDismissRequest = { showPathDialog = false },
        onConfirm = {
            showPathDialog = false
            viewModel.jumpToPath(pathInput)
        },
    )

    CreateFolderDialog(
        show = showCreateFolderDialog,
        folderName = newFolderName,
        onFolderNameChange = { newFolderName = it },
        onDismissRequest = { showCreateFolderDialog = false },
        onConfirm = {
            showCreateFolderDialog = false
            viewModel.createFolder(newFolderName)
        },
    )
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
                        modifier = Modifier.fillMaxWidth(),
                    )

                    errorText != null -> FileManagerStatusCard(
                        message = stringResource(R.string.fm_load_failed, errorText),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    displayedEntries.isEmpty() -> FileManagerStatusCard(
                        message = stringResource(R.string.fm_empty_dir),
                        modifier = Modifier.fillMaxWidth(),
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
                            horizontalArrangement = Arrangement.spacedBy(UiSpacing.PageItem),
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
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = modifier
            .combinedClickable(
                onClick = {
                    haptic.contextClick()
                    onClick()
                },
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
    val haptic = LocalHapticFeedback.current

    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.fm_file_details),
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        startAction = {
            IconButton(
                onClick = {
                    haptic.contextClick()
                    onToggleRaw()
                },
            ) {
                Icon(
                    imageVector =
                        if (!showingRaw) Icons.Rounded.RawOff
                        else Icons.Rounded.RawOn,
                    contentDescription = stringResource(
                        if (!showingRaw) R.string.fm_show_raw
                        else R.string.fm_show_parsed,
                    ),
                )
            }
        },
        endAction = {
            IconButton(
                onClick = {
                    haptic.contextClick()
                    onDownload()
                },
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
    val haptic = LocalHapticFeedback.current
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(path))
    }
    LaunchedEffect(path) {
        if (textFieldValue.text != path) {
            textFieldValue = TextFieldValue(path, TextRange(path.length))
        }
    }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.fm_goto_path),
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical)) {
            TextField(
                value = textFieldValue,
                onValueChange = {
                    textFieldValue = it
                    onPathChange(it.text)
                },
                // label = "/storage/emulated/0",
                // useLabelAsPlaceholder = true,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
            ) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = {
                        haptic.contextClick()
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.button_confirm),
                    onClick = {
                        haptic.confirm()
                        onConfirm()
                    },
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
    val haptic = LocalHapticFeedback.current
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(folderName))
    }
    LaunchedEffect(folderName) {
        if (textFieldValue.text != folderName) {
            textFieldValue = TextFieldValue(folderName, TextRange(folderName.length))
        }
    }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.fm_title_create_folder),
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical)) {
            TextField(
                value = textFieldValue,
                onValueChange = {
                    textFieldValue = it
                    onFolderNameChange(it.text)
                },
                label = stringResource(R.string.fm_label_new_folder),
                useLabelAsPlaceholder = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.PageItem),
            ) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = {
                        haptic.contextClick()
                        onDismissRequest()
                    },
                )
                TextButton(
                    text = stringResource(R.string.fm_button_create),
                    onClick = {
                        haptic.confirm()
                        onConfirm()
                    },
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
        else FileManagerService.formatStatDetails(stat, directorySnapshot),
    )
    if (targetStat != null) {
        details.append("\n\n${AppRuntime.stringResource(R.string.fm_stat_target_info)}\n")
        details.append(
            if (showRaw) targetStat.rawOutput
            else FileManagerService.formatStatDetails(targetStat),
        )
    }
    return details.toString()
}
