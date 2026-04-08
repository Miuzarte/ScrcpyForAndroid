package io.github.miuzarte.scrcpyforandroid.scaffolds

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun LazyColumn(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    itemSpacing: Dp = UiSpacing.PageItem,
    horizontalPadding: Dp = UiSpacing.PageHorizontal,
    verticalPadding: Dp = UiSpacing.PageVertical,
    clearFocusOnTap: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val focusClearModifier = if (clearFocusOnTap) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { focusManager.clearFocus() })
        }
    } else {
        Modifier
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .then(focusClearModifier)
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .padding(contentPadding),
        state = state,
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
        content = content,
    )
}
