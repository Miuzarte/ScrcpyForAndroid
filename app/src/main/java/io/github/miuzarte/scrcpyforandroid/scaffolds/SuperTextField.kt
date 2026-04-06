package io.github.miuzarte.scrcpyforandroid.scaffolds

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A wrapped [TextField] component with state change callbacks.
 *
 * @param value The text to be displayed in the text field.
 * @param onValueChange The callback to be called when the value changes.
 * @param modifier The modifier to be applied to the [TextField].
 * @param label The label to be displayed when the [TextField] is empty.
 * @param enabled Whether the [TextField] is enabled.
 * @param readOnly Whether the [TextField] is read-only.
 * @param singleLine Whether the text field is single line.
 * @param maxLines The maximum number of lines allowed to be displayed.
 * @param minLines The minimum number of lines allowed to be displayed.
 * @param useLabelAsPlaceholder Whether to use the label as a placeholder.
 * @param keyboardOptions The keyboard options to be applied to the [TextField].
 * @param keyboardActions The keyboard actions to be applied to the [TextField].
 * @param visualTransformation The visual transformation to be applied to the [TextField].
 * @param onFocusGained The callback to be called when the text field gains focus.
 * @param onFocusLost The callback to be called when the text field loses focus.
 * @param insideMargin The margin inside the [TextField].
 * @param backgroundColor The background color of the [TextField].
 * @param cornerRadius The corner radius of the [TextField].
 * @param labelColor The color of the label.
 * @param borderColor The color of the border when the [TextField] is focused.
 * @param textStyle The text style to be applied to the [TextField].
 * @param cursorBrush The brush to be used for the cursor.
 * @param leadingIcon The leading icon to be displayed in the [TextField].
 * @param trailingIcon The trailing icon to be displayed in the [TextField].
 */
@Composable
fun SuperTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    useLabelAsPlaceholder: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onFocusGained: (() -> Unit)? = null,
    onFocusLost: (() -> Unit)? = null,
    insideMargin: DpSize = DpSize(16.dp, 16.dp),
    backgroundColor: Color = MiuixTheme.colorScheme.secondaryContainer,
    cornerRadius: Dp = 16.dp,
    labelColor: Color = MiuixTheme.colorScheme.onSecondaryContainer,
    borderColor: Color = MiuixTheme.colorScheme.primary,
    textStyle: TextStyle = MiuixTheme.textStyles.main,
    cursorBrush: Brush = SolidColor(MiuixTheme.colorScheme.primary),
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }

    // 监听焦点状态变化并触发回调
    LaunchedEffect(isFocused) {
        if (isFocused) onFocusGained?.invoke()
        else onFocusLost?.invoke()
    }

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusRequester(focusRequester),
        label = label,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        useLabelAsPlaceholder = useLabelAsPlaceholder,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        insideMargin = insideMargin,
        backgroundColor = backgroundColor,
        cornerRadius = cornerRadius,
        labelColor = labelColor,
        borderColor = borderColor,
        textStyle = textStyle,
        cursorBrush = cursorBrush,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        interactionSource = interactionSource,
    )
}
