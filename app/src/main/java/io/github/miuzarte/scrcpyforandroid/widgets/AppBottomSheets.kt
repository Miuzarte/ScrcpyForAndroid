package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SpinnerColors
import top.yukonga.miuix.kmp.basic.SpinnerDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles

data class AppListEntry(
    val key: String,
    val title: String,
    val summary: String? = null,
    val system: Boolean? = null,
    val onClick: () -> Unit,
)

@Composable
fun AppListBottomSheet(
    show: Boolean,
    title: String,
    loadingText: String,
    emptyText: String,
    entries: List<AppListEntry>,
    refreshBusy: Boolean,
    onDismissRequest: () -> Unit,
    onRefresh: () -> Unit,
) {
    val spinnerColors = SpinnerDefaults.spinnerColors()
    OverlayBottomSheet(
        show = show,
        title = title,
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
        endAction = {
            IconButton(
                onClick = { if (!refreshBusy) onRefresh() },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.cd_refresh, title),
                )
            }
        },
    ) {
        when {
            entries.isEmpty() && refreshBusy -> {
                Text(
                    text = loadingText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiSpacing.Large),
                    textAlign = TextAlign.Center,
                )
            }

            entries.isEmpty() -> {
                Text(
                    text = emptyText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiSpacing.Large),
                    textAlign = TextAlign.Center,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(2f / 3f),
                ) {
                    items(items = entries, key = { it.key }) { entry ->
                        AppListBottomSheetItem(
                            entry = entry,
                            spinnerColors = spinnerColors,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListBottomSheetItem(
    entry: AppListEntry,
    spinnerColors: SpinnerColors,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(spinnerColors.containerColor)
            .clickable(onClick = entry.onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector =
                    if (entry.system == true) Icons.Rounded.Android
                    else MiuixIcons.Store,
                contentDescription = entry.title.ifBlank { entry.summary ?: "" },
                modifier = Modifier
                    .sizeIn(minWidth = 26.dp, minHeight = 26.dp)
                    .padding(end = 12.dp),
            )
            Column {
                Text(
                    text = entry.title,
                    fontSize = textStyles.body1.fontSize,
                    color = spinnerColors.contentColor,
                )
                entry.summary?.let {
                    Text(
                        text = it,
                        fontSize = textStyles.body2.fontSize,
                        color = spinnerColors.summaryColor,
                    )
                }
            }
        }
    }
}
