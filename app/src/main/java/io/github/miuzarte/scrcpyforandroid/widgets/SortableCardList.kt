package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class SortTransferDirection {
    NONE,
    TO_LEFT,
    TO_RIGHT,
}

data class SortableCardItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
)

data class SortDropPayload(
    val itemId: String,
    val deltaX: Float,
    val deltaY: Float,
    val transferDirection: SortTransferDirection,
)

/**
 * Displays a titled list of sortable cards.
 *
 * Each item can be long-pressed and dragged. When a drag ends the
 * `onDrop` callback receives a payload describing the item id and
 * the drag delta which callers can use to perform reordering or
 * transfer actions.
 *
 * @param title The section title displayed above the list.
 * @param items The list of items to render as sortable cards.
 * @param modifier Optional [Modifier] applied to the outer column.
 * @param transferDirection Hint for transfer direction used by callers.
 * @param onLongPressHaptic Optional callback invoked when an item is long-pressed.
 * @param onDrop Callback invoked when a dragged item is dropped.
 */
@Composable
fun SortableCardList(
    title: String,
    items: List<SortableCardItem>,
    modifier: Modifier = Modifier,
    transferDirection: SortTransferDirection = SortTransferDirection.NONE,
    onLongPressHaptic: (() -> Unit)? = null,
    onDrop: (SortDropPayload) -> Unit,
) {
    Column(modifier = modifier) {
        SectionSmallTitle(text = title, showLeadingSpacer = false)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.FieldLabelBottom),
        ) {
            items.forEach { item ->
                var dragX by remember(item.id) { mutableFloatStateOf(0f) }
                var dragY by remember(item.id) { mutableFloatStateOf(0f) }
                var dragging by remember(item.id) { androidx.compose.runtime.mutableStateOf(false) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationX = if (dragging) dragX else 0f
                            translationY = if (dragging) dragY else 0f
                        }
                        .pointerInput(item.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragging = true
                                    dragX = 0f
                                    dragY = 0f
                                    onLongPressHaptic?.invoke()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragX += dragAmount.x
                                    dragY += dragAmount.y
                                },
                                onDragEnd = {
                                    onDrop(
                                        SortDropPayload(
                                            itemId = item.id,
                                            deltaX = dragX,
                                            deltaY = dragY,
                                            transferDirection = transferDirection,
                                        ),
                                    )
                                    dragging = false
                                    dragX = 0f
                                    dragY = 0f
                                },
                                onDragCancel = {
                                    dragging = false
                                    dragX = 0f
                                    dragY = 0f
                                },
                            )
                        },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = UiSpacing.CardContent,
                                vertical = UiSpacing.FieldLabelBottom
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
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
                        Icon(
                            Icons.Default.DragIndicator,
                            contentDescription = "拖动排序",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}
