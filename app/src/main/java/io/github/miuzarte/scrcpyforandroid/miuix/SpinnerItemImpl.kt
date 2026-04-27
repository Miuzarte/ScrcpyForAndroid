package io.github.miuzarte.scrcpyforandroid.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SpinnerColors
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SpinnerItemImpl(
    entry: SpinnerEntry,
    entryCount: Int,
    isSelected: Boolean,
    index: Int,
    spinnerColors: SpinnerColors,
    dialogMode: Boolean = false,
    enabled: Boolean = true,
    onSelectedIndexChange: (Int) -> Unit,
) {
    val additionalTopPadding = if (!dialogMode && index == 0) 20.dp else 12.dp
    val additionalBottomPadding = if (!dialogMode && index == entryCount - 1) 20.dp else 12.dp

    val disabled = !enabled
    val (titleColor, summaryColor, backgroundColor) = when {
        disabled -> Triple(
            MiuixTheme.colorScheme.disabledOnSecondaryVariant,
            MiuixTheme.colorScheme.disabledOnSecondaryVariant,
            spinnerColors.containerColor,
        )
        isSelected -> Triple(
            spinnerColors.selectedContentColor,
            spinnerColors.selectedSummaryColor,
            spinnerColors.selectedContainerColor,
        )
        else -> Triple(
            spinnerColors.contentColor,
            spinnerColors.summaryColor,
            spinnerColors.containerColor,
        )
    }

    val selectColor = when {
        disabled -> Color.Transparent
        isSelected -> spinnerColors.selectedIndicatorColor
        else -> Color.Transparent
    }

    val currentOnSelectedIndexChange by rememberUpdatedState(onSelectedIndexChange)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .drawBehind { drawRect(backgroundColor) }
            .clickable(enabled = !disabled) { currentOnSelectedIndexChange(index) }
            .then(
                if (dialogMode) {
                    Modifier
                        .heightIn(min = 56.dp)
                        .widthIn(min = 200.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                } else {
                    Modifier.padding(horizontal = 20.dp)
                },
            )
            .padding(top = additionalTopPadding, bottom = additionalBottomPadding),
    ) {
        Row(
            modifier = if (dialogMode) Modifier else Modifier.widthIn(max = 216.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            entry.icon?.let {
                it(Modifier.sizeIn(minWidth = 26.dp, minHeight = 26.dp).padding(end = 12.dp))
            }
            Column {
                entry.title?.let {
                    Text(
                        text = it,
                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = titleColor,
                    )
                }
                entry.summary?.let {
                    Text(
                        text = it,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = summaryColor,
                    )
                }
            }
        }
        if (!disabled) {
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 8.dp, minHeight = 8.dp)
                    .padding(
                        start = if (dialogMode) 20.dp else 12.dp,
                        top = 1.dp,
                        bottom = 1.dp,
                    )
                    .clip(RectangleShape)
                    .background(selectColor),
            )
        }
    }
}
