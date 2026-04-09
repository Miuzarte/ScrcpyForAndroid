package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.constants.UiAndroidKeycodes
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.haptics.rememberAppHaptics
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.SpinnerDefaults
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.SpinnerItemImpl
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class VirtualButtonAction(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val keycode: Int?,
) {
    MORE(
        "more",
        "更多",
        Icons.Rounded.MoreVert,
        null
    ),
    HOME(
        "home",
        "主页",
        Icons.Rounded.Home,
        UiAndroidKeycodes.HOME
    ),
    BACK(
        "back",
        "返回",
        Icons.AutoMirrored.Filled.ArrowBack,
        UiAndroidKeycodes.BACK
    ),
    APP_SWITCH(
        "app_switch",
        "多任务",
        Icons.Rounded.Apps,
        UiAndroidKeycodes.APP_SWITCH
    ),
    MENU(
        "menu",
        "菜单",
        Icons.Rounded.Menu,
        UiAndroidKeycodes.MENU
    ),
    NOTIFICATION(
        "notification",
        "通知栏",
        Icons.Rounded.Notifications,
        UiAndroidKeycodes.NOTIFICATION
    ),
    VOLUME_UP(
        "volume_up",
        "音量+",
        Icons.AutoMirrored.Rounded.VolumeUp,
        UiAndroidKeycodes.VOLUME_UP
    ),
    VOLUME_DOWN(
        "volume_down",
        "音量-",
        Icons.AutoMirrored.Rounded.VolumeDown,
        UiAndroidKeycodes.VOLUME_DOWN
    ),
    VOLUME_MUTE(
        "volume_mute",
        "静音",
        Icons.AutoMirrored.Rounded.VolumeOff,
        UiAndroidKeycodes.VOLUME_MUTE
    ),
    POWER(
        "power",
        "锁屏",
        Icons.Rounded.PowerSettingsNew,
        UiAndroidKeycodes.POWER
    ),
    SCREENSHOT(
        "screenshot",
        "截图",
        Icons.Rounded.Screenshot,
        UiAndroidKeycodes.SYSRQ
    );
}

data class VirtualButtonItem(
    val action: VirtualButtonAction,
    val showOutside: Boolean,
)

object VirtualButtonActions {
    val all = VirtualButtonAction.entries

    private val byId = all.associateBy { it.id }

    fun parseStoredLayout(raw: String): List<VirtualButtonItem> {
        if (raw.isBlank())
            return parseStoredLayout(AppSettings.VIRTUAL_BUTTONS_LAYOUT.defaultValue)

        return raw.split(',').mapNotNull { item ->
            val parts = item.trim().split(':')
            if (parts.size != 2) return@mapNotNull null
            val id = parts[0]
            val showOutside = parts[1] == "1"
            val action = byId[id] ?: return@mapNotNull null
            VirtualButtonItem(action, showOutside)
        }
    }

    fun encodeStoredLayout(items: List<VirtualButtonItem>): String {
        return items.joinToString(",") { item ->
            "${item.action.id}:${if (item.showOutside) "1" else "0"}"
        }
    }

    fun splitLayout(items: List<VirtualButtonItem>): Pair<List<VirtualButtonAction>, List<VirtualButtonAction>> {
        val outside = items.filter { it.showOutside }.map { it.action }
        val more = items.filter { !it.showOutside }.map { it.action }
        return outside to more
    }
}

class VirtualButtonBar(
    private val outsideActions: List<VirtualButtonAction>,
    private val moreActions: List<VirtualButtonAction>,
) {
    @Composable
    fun Preview(
        enabled: Boolean,
        showText: Boolean,
        onAction: (VirtualButtonAction) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val haptics = rememberAppHaptics()
        val activeContainerColor = MiuixTheme.colorScheme.primary
        val disabledContainerColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.35f)
        val activeContentColor = MiuixTheme.colorScheme.onPrimary
        val disabledContentColor = MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.45f)

        var showMorePopup by remember { mutableStateOf(false) }
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
        ) {
            outsideActions.forEach { action ->
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = {
                            haptics.contextClick()
                            if (action == VirtualButtonAction.MORE) {
                                showMorePopup = true
                            } else {
                                onAction(action)
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            color = activeContainerColor,
                            disabledColor = disabledContainerColor,
                        ),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        val contentColor = if (enabled) activeContentColor else disabledContentColor
                        Icon(
                            action.icon,
                            contentDescription = action.title,
                            modifier = Modifier.size(18.dp),
                            tint = contentColor,
                        )
                        if (showText) {
                            Spacer(Modifier.width(UiSpacing.Small))
                            Text(action.title, color = contentColor)
                        }
                    }
                    if (action == VirtualButtonAction.MORE) {
                        MorePopup(
                            show = showMorePopup,
                            moreActions = moreActions,
                            onDismiss = { showMorePopup = false },
                            onAction = {
                                onAction(it)
                                showMorePopup = false
                            },
                            renderInRootScaffold = false,
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun Fullscreen(
        onAction: suspend (VirtualButtonAction) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val scope = rememberCoroutineScope()
        val haptics = rememberAppHaptics()
        var showMorePopup by remember { mutableStateOf(false) }

        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            outsideActions.forEach { action ->
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = {
                            haptics.contextClick()
                            if (action == VirtualButtonAction.MORE) {
                                showMorePopup = true
                            } else {
                                scope.launch {
                                    onAction(action)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 0.dp,
                        minHeight = 16.dp,
                        insideMargin = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            color = Color.Black.copy(alpha = 0.1f),
                        ),
                    ) {
                        Icon(action.icon, contentDescription = action.title, tint = Color.White)
                    }

                    if (action == VirtualButtonAction.MORE) {
                        MorePopup(
                            show = showMorePopup,
                            moreActions = moreActions,
                            onDismiss = { showMorePopup = false },
                            onAction = {
                                onAction(it)
                                showMorePopup = false
                            },
                            renderInRootScaffold = true,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun MorePopup(
        show: Boolean,
        moreActions: List<VirtualButtonAction>,
        onDismiss: () -> Unit,
        onAction: suspend (VirtualButtonAction) -> Unit,
        renderInRootScaffold: Boolean,
    ) {
        val scope = rememberCoroutineScope()
        val haptics = rememberAppHaptics()
        val spinnerItems = remember(moreActions) {
            moreActions.map { action ->
                SpinnerEntry(
                    icon = {
                        Icon(
                            action.icon,
                            contentDescription = action.title,
                            modifier = Modifier
                                .padding(end = UiSpacing.ContentVertical),
                        )
                    },
                    title = action.title,
                )
            }
        }

        SuperListPopup(
            show = show,
            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
            alignment = PopupPositionProvider.Align.TopEnd,
            onDismissRequest = onDismiss,
            renderInRootScaffold = renderInRootScaffold,
            enableWindowDim = false,
        ) {
            ListPopupColumn {
                spinnerItems.forEachIndexed { index, entry ->
                    SpinnerItemImpl(
                        entry = entry,
                        entryCount = spinnerItems.size,
                        isSelected = false,
                        index = index,
                        spinnerColors = SpinnerDefaults.spinnerColors(),
                        dialogMode = false,
                        onSelectedIndexChange = { selectedIdx ->
                            haptics.confirm()
                            scope.launch {
                                onAction(moreActions[selectedIdx])
                            }
                        },
                    )
                }
            }
        }
    }
}
