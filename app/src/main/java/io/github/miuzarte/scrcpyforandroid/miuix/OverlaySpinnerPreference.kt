package io.github.miuzarte.scrcpyforandroid.miuix

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Copy of miuix [OverlaySpinnerPreference] (popup mode) using our custom
 * [SpinnerEntry] with per-item [SpinnerEntry.enabled] passed through to
 * [SpinnerItemImpl].
 */
@Composable
fun OverlaySpinnerPreference(
    items: List<SpinnerEntry>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    spinnerColors: DropdownColors = DropdownDefaults.dropdownColors(),
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    renderInRootScaffold: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDropdownExpanded = rememberSaveable { mutableStateOf(false) }
    val isHoldDown = remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
    val currentOnExpandedChange = rememberUpdatedState(onExpandedChange)
    val setExpanded: (Boolean) -> Unit = remember {
        { expanded ->
            if (isDropdownExpanded.value != expanded) {
                isDropdownExpanded.value = expanded
                currentOnExpandedChange.value?.invoke(expanded)
            }
        }
    }

    val itemsNotEmpty = items.isNotEmpty()
    val actualEnabled = enabled && itemsNotEmpty

    val actionColor = if (actualEnabled) {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }

    val handleClick = remember(actualEnabled) {
        {
            if (actualEnabled) {
                setExpanded(!isDropdownExpanded.value)
                if (isDropdownExpanded.value) {
                    isHoldDown.value = true
                    currentHapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            }
        }
    }

    BasicComponent(
        modifier = modifier,
        interactionSource = interactionSource,
        insideMargin = insideMargin,
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = {
            if (showValue && itemsNotEmpty) {
                Text(
                    text = items[selectedIndex].title ?: "",
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .align(Alignment.CenterVertically)
                        .weight(1f, fill = false),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = actionColor,
                    textAlign = TextAlign.End,
                )
            }
            DropdownArrowEndAction(actionColor = actionColor)
            if (itemsNotEmpty) {
                OverlaySpinnerPopup(
                    items = items,
                    selectedIndex = selectedIndex,
                    isDropdownExpanded = isDropdownExpanded.value,
                    onDismiss = { setExpanded(false) },
                    onDismissFinished = { isHoldDown.value = false },
                    maxHeight = maxHeight,
                    hapticFeedback = hapticFeedback,
                    spinnerColors = spinnerColors,
                    renderInRootScaffold = renderInRootScaffold,
                    onSelectedIndexChange = onSelectedIndexChange,
                )
            }
        },
        bottomAction = bottomAction,
        onClick = handleClick,
        holdDownState = isHoldDown.value,
        enabled = actualEnabled,
    )
}

@Composable
private fun OverlaySpinnerPopup(
    items: List<SpinnerEntry>,
    selectedIndex: Int,
    isDropdownExpanded: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    maxHeight: Dp?,
    hapticFeedback: HapticFeedback,
    spinnerColors: DropdownColors,
    renderInRootScaffold: Boolean,
    onSelectedIndexChange: ((Int) -> Unit)?,
) {
    val onSelectState = rememberUpdatedState(onSelectedIndexChange)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
    val onItemSelected: (Int) -> Unit = remember {
        { selectedIdx ->
            currentHapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            onSelectState.value?.invoke(selectedIdx)
            currentOnDismiss()
        }
    }
    OverlayListPopup(
        show = isDropdownExpanded,
        alignment = PopupPositionProvider.Align.End,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        maxHeight = maxHeight,
        renderInRootScaffold = renderInRootScaffold,
    ) {
        ListPopupColumn {
            items.forEachIndexed { index, spinnerEntry ->
                key(index) {
                    SpinnerItemImpl(
                        entry = spinnerEntry,
                        entryCount = items.size,
                        isSelected = selectedIndex == index,
                        index = index,
                        spinnerColors = spinnerColors,
                        dialogMode = false,
                        enabled = spinnerEntry.enabled,
                        onSelectedIndexChange = onItemSelected,
                    )
                }
            }
        }
    }
}
