package io.github.miuzarte.scrcpyforandroid.scaffolds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SuperSlider(
    title: String,
    summary: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean = true,
    unit: String = "",
    zeroStateText: String? = null,
    showUnitWhenZeroState: Boolean = false,
    showKeyPoints: Boolean = false,
    keyPoints: List<Float> = emptyList(),
    displayFormatter: (Float) -> String = { it.toInt().toString() },
    displayText: String? = null,
    inputTitle: String = title,
    inputHint: String = unit,
    inputInitialValue: String = displayFormatter(value),
    inputFilter: (String) -> String = { text -> text.filter { it.isDigit() || it == '.' } },
    inputValueRange: ClosedFloatingPointRange<Float>? = null,
    onInputConfirm: (String) -> Unit,
) {
    var showInputDialog by remember { mutableStateOf(false) }
    var holdArrow by remember { mutableStateOf(false) }

    SuperArrow(
        title = title,
        summary = summary,
        onClick = {
            showInputDialog = true
            holdArrow = true
        },
        holdDownState = holdArrow,
        endActions = {
            val isZeroState = value == 0f && zeroStateText != null
            val valueText =
                if (isZeroState) zeroStateText else (displayText ?: displayFormatter(value))
            val shouldShowUnit = unit.isNotBlank() && (!isZeroState || showUnitWhenZeroState)
            val text = if (shouldShowUnit) "$valueText $unit" else valueText
            Text(
                text = text,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        enabled = enabled,
        bottomAction = {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                showKeyPoints = showKeyPoints,
                keyPoints = keyPoints,
                enabled = enabled,
            )
        },
    )

    SliderInputDialog(
        showDialog = showInputDialog,
        title = inputTitle,
        summary = inputHint,
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
    summary: String,
    initialValue: String,
    inputFilter: (String) -> String,
    inputValueRange: ClosedFloatingPointRange<Float>,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    SuperDialog(
        show = showDialog,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        content = {
            var text by remember(initialValue) { mutableStateOf(initialValue) }
            
            TextField(
                modifier = Modifier.padding(bottom = 16.dp),
                value = text,
                maxLines = 1,
                onValueChange = { newValue ->
                    text = inputFilter(newValue)
                },
            )
            
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    text = "取消",
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = "确定",
                    onClick = {
                        val inputValue = text.toFloatOrNull() ?: 0f
                        if (inputValue >= inputValueRange.start && inputValue <= inputValueRange.endInclusive) {
                            onConfirm(text.trim())
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        },
    )
}
