package io.github.miuzarte.scrcpyforandroid.scaffolds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.ui.confirm
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.longPress
import io.github.miuzarte.scrcpyforandroid.ui.segmentTick
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableRow
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

class ReorderableList(
    private val itemsProvider: () -> List<Item>,
    private val orientation: Orientation = Orientation.Column,
    private val onSettle: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    private val modifier: Modifier = Modifier,
) {
    enum class Orientation { Column, Row; }

    sealed interface EndAction {
        data class Icon(
            val icon: ImageVector,
            val contentDescription: String,
            val onClick: () -> Unit,
        ) : EndAction

        data class Checkbox(
            val checked: Boolean,
            val enabled: Boolean = true,
            val onClick: () -> Unit,
        ) : EndAction
    }

    data class Item(
        val id: String,
        val icon: ImageVector? = null,
        val title: String,
        val subtitle: String,
        val onClick: (() -> Unit)? = null,
        val dragEnabled: Boolean = true,
        val endActions: List<EndAction> = emptyList(),
    )

    @Composable
    operator fun invoke() {
        val haptic = LocalHapticFeedback.current
        val items = itemsProvider()
        when (orientation) {
            Orientation.Column -> {
                ReorderableColumn(
                    list = items,
                    modifier = modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.Small),
                    onSettle = onSettle,
                    onMove = haptic::segmentTick,
                ) { _, item, _ ->
                    key(item.id) {
                        ReorderableItem {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .padding(
                                            horizontal = UiSpacing.CardTitle,
                                            vertical = UiSpacing.FieldLabelBottom
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .then(
                                                if (item.onClick != null) {
                                                    Modifier.clickable(onClick = item.onClick)
                                                } else {
                                                    Modifier
                                                }
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small)
                                    ) {
                                        if (item.icon != null) Icon(
                                            item.icon,
                                            contentDescription = item.title
                                        )
                                        Column {
                                            Text(
                                                text = item.title,
                                                color = colorScheme.onSurface,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            if (item.subtitle.isNotBlank()) Text(
                                                text = item.subtitle,
                                                color = colorScheme.onSurfaceVariantSummary,
                                                fontSize = 13.sp,
                                            )
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small)
                                    ) {
                                        item.endActions.forEach { action ->
                                            EndActionView(
                                                action = action,
                                                fallbackContentDescription = item.title,
                                            )
                                        }
                                        if (item.dragEnabled) {
                                            IconButton(
                                                onClick = haptic::contextClick,
                                                modifier = Modifier
                                                    .draggableHandle(
                                                        onDragStarted = { haptic.longPress() },
                                                        onDragStopped = { haptic.confirm() },
                                                    ),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DragIndicator,
                                                    contentDescription = stringResource(R.string.cd_drag_sort),
                                                    tint = colorScheme.onSurfaceVariantSummary,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Orientation.Row -> {
                ReorderableRow(
                    list = items,
                    modifier = modifier.fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small),
                    onSettle = onSettle,
                    onMove = haptic::segmentTick,
                ) { _, item, _ ->
                    key(item.id) {
                        ReorderableItem {
                            Card(
                                modifier = Modifier.fillMaxHeight(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(
                                            horizontal = UiSpacing.CardTitle,
                                            vertical = UiSpacing.FieldLabelBottom
                                        ),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Row(
                                        modifier = Modifier.then(
                                            if (item.onClick != null) {
                                                Modifier.clickable(onClick = item.onClick)
                                            } else {
                                                Modifier
                                            }
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        item.endActions.forEach { action ->
                                            EndActionView(
                                                action = action,
                                                fallbackContentDescription = item.title,
                                            )
                                            Spacer(Modifier.padding(horizontal = 4.dp))
                                        }
                                        if (item.dragEnabled) {
                                            IconButton(
                                                onClick = {},
                                                modifier = Modifier
                                                    .draggableHandle(
                                                        onDragStarted = { haptic.longPress() },
                                                        onDragStopped = { haptic.confirm() },
                                                    ),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DragIndicator,
                                                    contentDescription = stringResource(R.string.cd_drag_sort),
                                                    tint = colorScheme.onSurfaceVariantSummary,
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.padding(UiSpacing.ContentVertical))
                                    Text(
                                        text = item.title,
                                        color = colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (item.subtitle.isNotBlank()) {
                                        Text(
                                            text = item.subtitle,
                                            color = colorScheme.onSurfaceVariantSummary,
                                            fontSize = 13.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EndActionView(
    action: ReorderableList.EndAction,
    fallbackContentDescription: String,
) {
    when (action) {
        is ReorderableList.EndAction.Checkbox -> Checkbox(
            state = if (action.checked) ToggleableState.On else ToggleableState.Off,
            onClick = action.onClick,
            enabled = action.enabled,
        )

        is ReorderableList.EndAction.Icon -> IconButton(
            onClick = action.onClick,
        ) {
            Icon(
                action.icon,
                contentDescription = action.contentDescription.ifBlank {
                    fallbackContentDescription
                },
                tint = colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
