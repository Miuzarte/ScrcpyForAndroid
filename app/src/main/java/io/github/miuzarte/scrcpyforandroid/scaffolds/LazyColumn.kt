package io.github.miuzarte.scrcpyforandroid.scaffolds

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun LazyColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollBehavior: ScrollBehavior? = null,
    state: LazyListState = rememberLazyListState(),
    itemSpacing: Dp = UiSpacing.PageItem,
    horizontalPadding: Dp = UiSpacing.PageHorizontal,
    verticalPadding: Dp = UiSpacing.PageVertical,
    bottomInnerPadding: Dp? = null,
    clearFocusOnTap: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val focusManager = LocalFocusManager.current

    val mergedContentPadding = PaddingValues(
        start = contentPadding.calculateLeftPadding(layoutDirection) + horizontalPadding,
        top = contentPadding.calculateTopPadding() + verticalPadding,
        end = contentPadding.calculateRightPadding(layoutDirection) + horizontalPadding,
        bottom = contentPadding.calculateBottomPadding() + verticalPadding,
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (clearFocusOnTap)
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onTap = { focusManager.clearFocus() })
                    } else Modifier
            )
            .overScrollVertical()
            .then(
                if (scrollBehavior != null)
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                else Modifier
            ),
        state = state,
        contentPadding = mergedContentPadding,
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
        content()
        bottomInnerPadding?.let { padding ->
            (padding - itemSpacing)
                .takeIf { it > 0.dp }
                ?.let { item { Spacer(Modifier.height(it)) } }
        }
    }
}
