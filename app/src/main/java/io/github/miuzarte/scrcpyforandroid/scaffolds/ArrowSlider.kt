package io.github.miuzarte.scrcpyforandroid.scaffolds

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.ui.confirm
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults.SliderHapticEffect
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles

@Composable
fun ArrowSlider(
    title: String,
    summary: String? = null,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    hapticEffect: SliderHapticEffect = SliderHapticEffect.Edge,
    showKeyPoints: Boolean = false,
    keyPoints: List<Float> = emptyList(),
    magnetThreshold: Float = 0.02f,
    unit: String = "",
    zeroStateText: String? = null,
    showUnitWhenZeroState: Boolean = false,
    displayFormatter: (Float) -> String = { it.toInt().toString() },
    displayText: String? = null,
    inputTitle: String = title,
    inputSummary: String? = summary,
    inputLabel: String = unit,
    useLabelAsPlaceholder: Boolean = false,
    inputInitialValue: String = displayFormatter(value),
    inputFilter: (String) -> String = { text -> text.filter { it.isDigit() || it == '.' } },
    inputValueRange: ClosedFloatingPointRange<Float>? = null,
    onInputConfirm: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    var showInputDialog by remember { mutableStateOf(false) }
    var holdArrow by remember { mutableStateOf(false) }

    ArrowPreference(
        title = title,
        summary = summary,
        onClick = {
            haptic.contextClick()
            showInputDialog = true
            holdArrow = true
        },
        holdDownState = holdArrow,
        endActions = {
            val isZeroState = value == 0f && zeroStateText != null
            val valueText =
                if (isZeroState) zeroStateText
                else (displayText ?: displayFormatter(value))
            val shouldShowUnit = unit.isNotBlank() && (!isZeroState || showUnitWhenZeroState)
            val text = if (shouldShowUnit) "$valueText $unit" else valueText
            Text(
                text = text,
                color = colorScheme.onSurfaceVariantActions,
                fontSize = textStyles.body2.fontSize,
            )
        },
        enabled = enabled,
        bottomAction = {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                onValueChangeFinished = onValueChangeFinished,
                enabled = enabled,
                hapticEffect = hapticEffect,
                showKeyPoints = showKeyPoints,
                keyPoints = keyPoints,
                magnetThreshold = magnetThreshold,
            )
        },
    )

    SliderInputDialog(
        showDialog = showInputDialog,
        title = inputTitle,
        summary = inputSummary,
        label = inputLabel,
        useLabelAsPlaceholder = useLabelAsPlaceholder,
        initialValue = inputInitialValue,
        inputFilter = inputFilter,
        inputValueRange = inputValueRange ?: valueRange,
        onDismissRequest = { showInputDialog = false },
        onDismissFinished = { holdArrow = false },
        onConfirm = { input ->
            onInputConfirm(input)
            showInputDialog = false
        },
    )
}

@Composable
private fun SliderInputDialog(
    showDialog: Boolean,
    title: String,
    summary: String? = null,
    label: String = "",
    useLabelAsPlaceholder: Boolean = false,
    initialValue: String,
    inputFilter: (String) -> String,
    inputValueRange: ClosedFloatingPointRange<Float>,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    OverlayDialog(
        show = showDialog,
        title = title,
        summary = summary,
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
    ) {
        var text by rememberSaveable(initialValue) { mutableStateOf(initialValue) }

        SuperTextField(
            modifier = Modifier.padding(bottom = 16.dp),
            value = text,
            label = label,
            useLabelAsPlaceholder = useLabelAsPlaceholder,
            maxLines = 1,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            onValueChange = { newValue ->
                text = inputFilter(newValue)
            },
        )

        Row(horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                text = stringResource(R.string.button_cancel),
                onClick = {
                    haptic.contextClick()
                    onDismissRequest()
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(20.dp))
            TextButton(
                text = stringResource(R.string.button_confirm),
                onClick = {
                    haptic.confirm()
                    val inputValue = text.toFloatOrNull() ?: 0f
                    if (inputValue >= inputValueRange.start && inputValue <= inputValueRange.endInclusive) {
                        onConfirm(text.trim())
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
