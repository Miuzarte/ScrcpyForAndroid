package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class MultiGroupsDropdownGroup(
    val options: List<String>,
    val selectedIndex: Int,
    val onSelectedIndexChange: (Int) -> Unit,
)

@Composable
fun MultiGroupsDropdownPreference(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    groups: List<MultiGroupsDropdownGroup>,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    enabled: Boolean = true,
    showValue: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isExpanded = remember { mutableStateOf(false) }
    val isHoldDown = remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val hapticLatest by rememberUpdatedState(haptic)

    val hasOptions = groups.any { it.options.isNotEmpty() }
    val actualEnabled = enabled && hasOptions
    val actionColor = if (actualEnabled) {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }
    val selectedValueText = groups.joinToString("\n") { group ->
        group.options.getOrNull(group.selectedIndex).orEmpty()
    }.ifBlank { null }

    BasicComponent(
        modifier = modifier,
        interactionSource = interactionSource,
        insideMargin = insideMargin,
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        endActions = {
            if (showValue && !selectedValueText.isNullOrBlank()) {
                Text(
                    text = selectedValueText,
                    modifier = Modifier.padding(end = 8.dp),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = actionColor,
                    textAlign = TextAlign.End,
                    lineHeight = MiuixTheme.textStyles.body2.lineHeight,
                )
            }
            DropdownArrowEndAction(actionColor = actionColor)
            if (hasOptions) {
                OverlayListPopup(
                    show = isExpanded.value,
                    alignment = PopupPositionProvider.Align.End,
                    onDismissRequest = { isExpanded.value = false },
                    onDismissFinished = { isHoldDown.value = false },
                ) {
                    ListPopupColumn {
                        MultiGroupsDropdown(
                            groups = groups,
                            dropdownColors = dropdownColors,
                        )
                    }
                }
            }
        },
        onClick = {
            if (actualEnabled) {
                isExpanded.value = !isExpanded.value
                if (isExpanded.value) {
                    isHoldDown.value = true
                    hapticLatest.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            }
        },
        holdDownState = isHoldDown.value,
        enabled = actualEnabled,
    )
}

@Composable
fun MultiGroupsDropdown(
    groups: List<MultiGroupsDropdownGroup>,
    modifier: Modifier = Modifier,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
) {
    groups.forEachIndexed { groupIndex, group ->
        group.options.forEachIndexed { optionIndex, option ->
            DropdownImpl(
                text = option,
                optionSize = group.options.size,
                isSelected = optionIndex == group.selectedIndex,
                dropdownColors = dropdownColors,
                index = optionIndex,
                onSelectedIndexChange = group.onSelectedIndexChange,
            )
        }
        if (groupIndex != groups.lastIndex) {
            HorizontalDivider(modifier = modifier.padding(horizontal = 20.dp))
        }
    }
}
