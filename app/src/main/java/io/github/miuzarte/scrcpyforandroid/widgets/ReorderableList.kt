package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.haptics.rememberAppHaptics
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableRow
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

class ReorderableList(
    private val itemsProvider: () -> List<Item>,
    private val orientation: Orientation = Orientation.Column,
    private val onSettle: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    private val modifier: Modifier = Modifier,
    private val showCheckbox: Boolean = false,
    private val onCheckboxChange: ((String, Boolean) -> Unit)? = null,
) {
    enum class Orientation { Column, Row; }

    data class Item(
        val id: String,
        val icon: ImageVector? = null,
        val title: String,
        val subtitle: String,
        val checked: Boolean = true,
        val checkboxEnabled: Boolean = true,
    )

    @Composable
    operator fun invoke() {
        val haptics = rememberAppHaptics()
        val items = itemsProvider()
        when (orientation) {
            Orientation.Column -> {
                ReorderableColumn(
                    list = items,
                    modifier = modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.Small),
                    onSettle = onSettle,
                    onMove = haptics.segmentTick,
                ) { _, item, _ ->
                    key(item.id) {
                        ReorderableItem {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = UiSpacing.CardTitle,
                                            vertical = UiSpacing.FieldLabelBottom
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small)
                                    ) {
                                        if (item.icon != null) Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.title
                                        )
                                        Column {
                                            Text(
                                                text = item.title,
                                                color = MiuixTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            if (item.subtitle.isNotBlank()) Text(
                                                text = item.subtitle,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                fontSize = 13.sp,
                                            )
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small)
                                    ) {
                                        if (showCheckbox) Checkbox(
                                            state = if (item.checked) ToggleableState.On else ToggleableState.Off,
                                            onClick = {
                                                onCheckboxChange?.invoke(item.id, !item.checked)
                                            },
                                            enabled = item.checkboxEnabled
                                        )
                                        IconButton(
                                            onClick = {
                                                haptics.contextClick()
                                            },
                                            modifier = Modifier
                                                .draggableHandle(
                                                    onDragStarted = {
                                                        haptics.longPress()
                                                    },
                                                    onDragStopped = {
                                                        haptics.confirm()
                                                    },
                                                ),
                                        ) {
                                            Icon(
                                                Icons.Rounded.DragIndicator,
                                                contentDescription = "拖动排序",
                                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            )
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
                    onMove = haptics.segmentTick,
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
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (showCheckbox) {
                                            Checkbox(
                                                state = if (item.checked) ToggleableState.On else ToggleableState.Off,
                                                onClick = {
                                                    onCheckboxChange?.invoke(item.id, !item.checked)
                                                },
                                                enabled = item.checkboxEnabled
                                            )
                                            Spacer(Modifier.padding(horizontal = 4.dp))
                                        }
                                        IconButton(
                                            onClick = {},
                                            modifier = Modifier
                                                .draggableHandle(
                                                    onDragStarted = {
                                                        haptics.longPress()
                                                    },
                                                    onDragStopped = {
                                                        haptics.confirm()
                                                    },
                                                ),
                                        ) {
                                            Icon(
                                                Icons.Rounded.DragIndicator,
                                                contentDescription = "拖动排序",
                                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.padding(UiSpacing.ContentVertical))
                                    Text(
                                        text = item.title,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (item.subtitle.isNotBlank()) {
                                        Text(
                                            text = item.subtitle,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
