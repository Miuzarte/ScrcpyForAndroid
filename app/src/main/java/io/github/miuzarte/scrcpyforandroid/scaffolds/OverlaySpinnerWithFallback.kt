package io.github.miuzarte.scrcpyforandroid.scaffolds

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.miuzarte.scrcpyforandroid.miuix.OverlaySpinnerPreference
import io.github.miuzarte.scrcpyforandroid.miuix.SpinnerEntry
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SpinnerColors
import top.yukonga.miuix.kmp.basic.SpinnerDefaults

/**
 * [OverlaySpinnerPreference] wrapper with fallback value display and loading state.
 *
 * When [dataLoaded] is false and [overrideEndActionValue] is set, the saved value is
 * appended as a synthetic item so it remains visible before the list is fetched.
 * When [dataLoading] is true, a "加载中..." entry with an [InfiniteProgressIndicator]
 * icon is appended at the end.
 */
@Composable
fun OverlaySpinnerWithFallback(
    items: List<SpinnerEntry>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    spinnerColors: SpinnerColors = SpinnerDefaults.spinnerColors(),
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    renderInRootScaffold: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
    dataLoaded: Boolean = true,
    dataLoading: Boolean = false,
    overrideEndActionValue: String? = null,
) {
    val fallbackActive = !dataLoaded && !overrideEndActionValue.isNullOrBlank()
    val fallbackIdx = items.size
    val effectiveItems = remember(items, dataLoading, fallbackActive, overrideEndActionValue) {
        buildList {
            addAll(items)
            if (fallbackActive) {
                add(SpinnerEntry(title = overrideEndActionValue!!))
            }
            if (dataLoading) {
                add(
                    SpinnerEntry(
                        icon = { mod -> InfiniteProgressIndicator(mod) },
                        title = "加载中...",
                        enabled = false,
                    )
                )
            }
        }
    }

    val effectiveIndex = if (fallbackActive) fallbackIdx else selectedIndex

    OverlaySpinnerPreference(
        items = effectiveItems,
        selectedIndex = effectiveIndex,
        title = title,
        modifier = modifier,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        spinnerColors = spinnerColors,
        startAction = startAction,
        bottomAction = bottomAction,
        insideMargin = insideMargin,
        maxHeight = maxHeight,
        enabled = enabled,
        showValue = showValue,
        renderInRootScaffold = renderInRootScaffold,
        onExpandedChange = onExpandedChange,
        onSelectedIndexChange = { idx ->
            if (dataLoading) return@OverlaySpinnerPreference
            if (fallbackActive && idx >= fallbackIdx) return@OverlaySpinnerPreference
            onSelectedIndexChange?.invoke(idx)
        },
    )
}
