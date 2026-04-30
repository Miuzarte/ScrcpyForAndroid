package io.github.miuzarte.scrcpyforandroid.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.ReorderableList
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonAction
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
internal fun VirtualButtonOrderScreen(
    scrollBehavior: ScrollBehavior,
) {
    val navigator = LocalRootNavigator.current
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                TopAppBar(
                    title = stringResource(R.string.vb_order_title),
                    color =
                        if (blurActive) Color.Transparent
                        else colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = navigator.pop) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { pagePadding ->
        Box(modifier = if (blurActive) Modifier.layerBackdrop(blurBackdrop) else Modifier) {
            VirtualButtonOrderPage(
                contentPadding = pagePadding,
                scrollBehavior = scrollBehavior,
            )
        }
    }
}

@Composable
internal fun VirtualButtonOrderPage(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
) {
    val context = LocalContext.current
    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    val asBundleShared by appSettings.bundleState.collectAsState()
    val asBundleSharedLatest by rememberUpdatedState(asBundleShared)
    var asBundle by rememberSaveable(asBundleShared) { mutableStateOf(asBundleShared) }
    val asBundleLatest by rememberUpdatedState(asBundle)
    LaunchedEffect(asBundleShared) {
        if (asBundle != asBundleShared)
            asBundle = asBundleShared
    }
    LaunchedEffect(asBundle) {
        delay(Settings.BUNDLE_SAVE_DELAY)
        if (asBundle != asBundleSharedLatest)
            appSettings.saveBundle(asBundle)
    }
    DisposableEffect(Unit) {
        onDispose {
            taskScope.launch {
                appSettings.saveBundle(asBundleLatest)
            }
        }
    }

    var buttonItems by remember(asBundle.virtualButtonsLayout) {
        mutableStateOf(VirtualButtonActions.parseStoredLayout(asBundle.virtualButtonsLayout))
    }

    LazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
        bottomInnerPadding = UiSpacing.PageBottom,
    ) {
        item {
            Card {
                SwitchPreference(
                    title = stringResource(R.string.vb_order_button_text),
                    summary = stringResource(R.string.vb_order_hint),
                    checked = asBundle.previewVirtualButtonShowText,
                    onCheckedChange = {
                        asBundle = asBundle.copy(previewVirtualButtonShowText = it)
                    },
                )
            }
        }

        item { Spacer(Modifier.height(UiSpacing.Medium)) }

        item {
            val textExternal = stringResource(R.string.vb_order_display_external)
            val textMoreMenu = stringResource(R.string.vb_order_display_more_menu)
            ReorderableList(
                itemsProvider = {
                    buttonItems.map { item ->
                        val action = item.action
                        val actionTitle = context.getString(action.titleResId)
                        ReorderableList.Item(
                            id = action.id,
                            icon = action.icon,
                            title =
                                if (action.keycode == null) actionTitle
                                else "$actionTitle (${action.keycode})",
                            subtitle =
                                if (item.showOutside) textExternal
                                else textMoreMenu,
                            endActions = listOf(
                                ReorderableList.EndAction.Checkbox(
                                    checked = item.showOutside,
                                    enabled = action != VirtualButtonAction.MORE,
                                    onClick = {
                                        val checked = !item.showOutside
                                        buttonItems = buttonItems.map { current ->
                                            if (current.action.id == action.id)
                                                current.copy(showOutside = checked)
                                            else current
                                        }
                                        asBundle = asBundle.copy(
                                            virtualButtonsLayout = VirtualButtonActions
                                                .encodeStoredLayout(buttonItems)
                                        )
                                    },
                                )
                            ),
                        )
                    }
                },
                orientation = ReorderableList.Orientation.Column,
                onSettle = { fromIndex, toIndex ->
                    buttonItems = buttonItems.toMutableList()
                        .apply { add(toIndex, removeAt(fromIndex)) }
                    asBundle = asBundle.copy(
                        virtualButtonsLayout = VirtualButtonActions
                            .encodeStoredLayout(buttonItems)
                    )
                },
            )()
        }
    }
}
