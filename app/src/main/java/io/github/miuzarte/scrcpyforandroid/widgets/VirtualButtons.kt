package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.constants.UiAndroidKeycodes
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.ui.confirm
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

enum class VirtualButtonAction(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val keycode: Int?,
) {
    MORE(
        "more",
        "更多",
        MiuixIcons.More,
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
        Icons.AutoMirrored.Rounded.ArrowBack,
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
    ),
    PASSWORD_INPUT(
        "password_input",
        "填充锁屏密码",
        Icons.Rounded.Password,
        null
    ),
    ALL_APPS(
        "all_apps",
        "所有应用",
        Icons.Rounded.Apps,
        null
    ),
    RECENT_TASKS(
        "recent_tasks",
        "最近任务",
        Icons.Rounded.DashboardCustomize,
        null
    ),
    TOGGLE_IME(
        "toggle_ime",
        "拉起输入法",
        Icons.Rounded.Keyboard,
        null
    ),
    PASTE_LOCAL_CLIPBOARD(
        "paste_local_clipboard",
        "粘贴本机剪贴板",
        Icons.Rounded.ContentPaste,
        null
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
        val parsed = raw.takeIf { it.isNotBlank() }
            ?.split(',')
            ?.mapNotNull { item ->
                val parts = item.trim().split(':')
                if (parts.size != 2) return@mapNotNull null
                val id = parts[0]
                val showOutside = parts[1] == "1"
                val action = byId[id] ?: return@mapNotNull null
                VirtualButtonItem(action, showOutside)
            }
            .orEmpty()
            .distinctBy { it.action.id }
        val base = parsed.ifEmpty {
            parseStoredLayout(AppSettings.VIRTUAL_BUTTONS_LAYOUT.defaultValue)
        }
        val missing = all
            .filterNot { action -> base.any { it.action == action } }
            .map { action ->
                VirtualButtonItem(
                    action = action,
                    showOutside = action == VirtualButtonAction.MORE,
                )
            }
        return base + missing
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
    enum class FullscreenDock {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT,
    }

    private enum class ActionPopupDestination {
        Actions,
        Passwords,
    }

    @Composable
    fun Preview(
        enabled: Boolean,
        showText: Boolean,
        onAction: (VirtualButtonAction) -> Unit,
        modifier: Modifier = Modifier,
        passwordPopupContent: (@Composable (onDismissRequest: () -> Unit) -> Unit)? = null,
    ) {
        val haptic = LocalHapticFeedback.current

        val activeContainerColor = colorScheme.primary
        val disabledContainerColor = colorScheme.primary.copy(alpha = 0.35f)
        val activeContentColor = colorScheme.onPrimary
        val disabledContentColor = colorScheme.onPrimary.copy(alpha = 0.45f)

        var showMorePopup by remember { mutableStateOf(false) }

        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
        ) {
            outsideActions.forEach { action ->
                var showPasswordPopup by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = {
                            haptic.contextClick()
                            when (action) {
                                VirtualButtonAction.MORE -> {
                                    showMorePopup = true
                                }

                                VirtualButtonAction.PASSWORD_INPUT
                                    if passwordPopupContent != null -> {
                                    showPasswordPopup = true
                                }

                                else -> onAction(action)
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
                        val contentColor =
                            if (enabled) activeContentColor
                            else disabledContentColor
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
                        ActionPopup(
                            show = showMorePopup,
                            actions = moreActions,
                            onDismiss = { showMorePopup = false },
                            onAction = {
                                onAction(it)
                                showMorePopup = false
                            },
                            passwordPopupContent = passwordPopupContent,
                            renderInRootScaffold = false,
                        )
                    }
                    if (
                        action == VirtualButtonAction.PASSWORD_INPUT &&
                        passwordPopupContent != null
                    ) {
                        OverlayListPopup(
                            show = showPasswordPopup,
                            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                            alignment = PopupPositionProvider.Align.TopEnd,
                            onDismissRequest = { showPasswordPopup = false },
                            renderInRootScaffold = false,
                            enableWindowDim = false,
                        ) {
                            passwordPopupContent { showPasswordPopup = false }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Fullscreen(
        onAction: suspend (VirtualButtonAction) -> Unit,
        modifier: Modifier = Modifier,
        dock: FullscreenDock = FullscreenDock.BOTTOM,
        reverseOrder: Boolean = false,
        thickness: Dp = 16.dp,
        passwordPopupContent: (@Composable (onDismissRequest: () -> Unit) -> Unit)? = null,
    ) {
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        var showMorePopup by remember { mutableStateOf(false) }
        var showPasswordPopup by remember { mutableStateOf(false) }

        val isVertical = dock == FullscreenDock.LEFT || dock == FullscreenDock.RIGHT
        val visibleActions =
            if (reverseOrder) outsideActions.asReversed()
            else outsideActions
        val containerModifier =
            if (isVertical) modifier
                .width(thickness)
                .fillMaxHeight()
            else modifier
                .fillMaxWidth()
                .height(thickness)

        val buttonModifier =
            if (isVertical) Modifier
                .fillMaxSize()
            else Modifier
                .fillMaxWidth()
                .height(thickness)

        if (isVertical) Column(
            modifier = containerModifier,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            visibleActions.forEach { action ->
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = {
                            haptic.contextClick()
                            when (action) {
                                VirtualButtonAction.MORE -> {
                                    showMorePopup = true
                                }

                                VirtualButtonAction.PASSWORD_INPUT
                                    if passwordPopupContent != null -> {
                                    showPasswordPopup = true
                                }

                                else -> scope.launch { onAction(action) }
                            }
                        },
                        modifier = buttonModifier,
                        cornerRadius = 0.dp,
                        minHeight = thickness,
                        insideMargin = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            color = Color.Black.copy(alpha = 0.1f),
                        ),
                    ) {
                        Icon(action.icon, contentDescription = action.title, tint = Color.White)
                    }

                    if (action == VirtualButtonAction.MORE) {
                        ActionPopup(
                            show = showMorePopup,
                            actions = moreActions,
                            onDismiss = { showMorePopup = false },
                            onAction = {
                                if (it == VirtualButtonAction.PASSWORD_INPUT
                                    && passwordPopupContent != null
                                ) showPasswordPopup = true
                                else onAction(it)

                                showMorePopup = false
                            },
                            passwordPopupContent = passwordPopupContent,
                            renderInRootScaffold = true,
                        )
                    }
                }
            }
        }
        else Row(
            modifier = containerModifier,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            visibleActions.forEach { action ->
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = {
                            haptic.contextClick()
                            when (action) {
                                VirtualButtonAction.MORE -> {
                                    showMorePopup = true
                                }

                                VirtualButtonAction.PASSWORD_INPUT
                                    if passwordPopupContent != null -> {
                                    showPasswordPopup = true
                                }

                                else -> scope.launch { onAction(action) }
                            }
                        },
                        modifier = buttonModifier,
                        cornerRadius = 0.dp,
                        minHeight = thickness,
                        insideMargin = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            color = Color.Black.copy(alpha = 0.1f),
                        ),
                    ) {
                        Icon(action.icon, contentDescription = action.title, tint = Color.White)
                    }

                    if (action == VirtualButtonAction.MORE) {
                        ActionPopup(
                            show = showMorePopup,
                            actions = moreActions,
                            onDismiss = { showMorePopup = false },
                            onAction = {
                                if (it == VirtualButtonAction.PASSWORD_INPUT
                                    && passwordPopupContent != null
                                ) showPasswordPopup = true
                                else onAction(it)

                                showMorePopup = false
                            },
                            passwordPopupContent = passwordPopupContent,
                            renderInRootScaffold = true,
                        )
                    }
                }
            }
        }

        if (passwordPopupContent != null) {
            OverlayListPopup(
                show = showPasswordPopup,
                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                alignment = PopupPositionProvider.Align.TopEnd,
                onDismissRequest = { showPasswordPopup = false },
                renderInRootScaffold = true,
                enableWindowDim = false,
            ) {
                passwordPopupContent { showPasswordPopup = false }
            }
        }
    }

    @Composable
    fun FloatingBall(
        actions: List<VirtualButtonAction>,
        onAction: suspend (VirtualButtonAction) -> Unit,
        modifier: Modifier = Modifier,
        passwordPopupContent: (@Composable (onDismissRequest: () -> Unit) -> Unit)? = null,
    ) {
        val scope = rememberCoroutineScope()
        val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
        val haptic = LocalHapticFeedback.current
        var showActions by remember { mutableStateOf(false) }
        var showPasswordPopup by remember { mutableStateOf(false) }
        val asBundleShared by appSettings.bundleState.collectAsState()
        val asBundleSharedLatest by rememberUpdatedState(asBundleShared)
        var offsetXFraction by rememberSaveable(asBundleShared.fullscreenFloatingButtonXFraction) {
            mutableFloatStateOf(asBundleShared.fullscreenFloatingButtonXFraction)
        }
        var offsetYFraction by rememberSaveable(asBundleShared.fullscreenFloatingButtonYFraction) {
            mutableFloatStateOf(asBundleShared.fullscreenFloatingButtonYFraction)
        }
        DisposableEffect(Unit) {
            onDispose {
                taskScope.launch {
                    val latest = asBundleSharedLatest
                    if (
                        offsetXFraction != latest.fullscreenFloatingButtonXFraction ||
                        offsetYFraction != latest.fullscreenFloatingButtonYFraction
                    ) {
                        appSettings.saveBundle(
                            latest.copy(
                                fullscreenFloatingButtonXFraction = offsetXFraction,
                                fullscreenFloatingButtonYFraction = offsetYFraction,
                            )
                        )
                    }
                }
            }
        }

        BoxWithConstraints(
            modifier = modifier.fillMaxSize(),
        ) {
            val ballSize = asBundleShared.fullscreenFloatingButtonSizeDp.dp
            val ringSize = ballSize / 2
            val ringWidth = ballSize / 24
            val backgroundAlpha =
                (asBundleShared.fullscreenFloatingButtonBackgroundAlphaPercent / 100f)
                    .coerceIn(0.1f, 1f)
            val ringAlpha =
                (asBundleShared.fullscreenFloatingButtonRingAlphaPercent / 100f)
                    .coerceIn(0f, 1f)
            val maxX = (maxWidth - ballSize).coerceAtLeast(0.dp)
            val maxY = (maxHeight - ballSize).coerceAtLeast(0.dp)
            val currentX =
                maxX * offsetXFraction.coerceIn(0f, 1f)
            val currentY =
                maxY * offsetYFraction.coerceIn(0f, 1f)
            val popupAlignment =
                if (offsetXFraction > 0.5f) PopupPositionProvider.Align.TopEnd
                else PopupPositionProvider.Align.TopStart

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            currentX.roundToPx(),
                            currentY.roundToPx(),
                        )
                    }
                    .size(ballSize)
                    .pointerInput(maxX, maxY) {
                        var dragStartXFraction = offsetXFraction
                        var dragStartYFraction = offsetYFraction
                        detectDragGestures(
                            onDragStart = {
                                dragStartXFraction = offsetXFraction
                                dragStartYFraction = offsetYFraction
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            val nextX = (maxX.toPx() * dragStartXFraction + dragAmount.x)
                                .coerceIn(0f, maxX.toPx())
                            val nextY = (maxY.toPx() * dragStartYFraction + dragAmount.y)
                                .coerceIn(0f, maxY.toPx())
                            val nextXFraction =
                                if (maxX > 0.dp) nextX / maxX.toPx()
                                else 0f
                            val nextYFraction =
                                if (maxY > 0.dp) nextY / maxY.toPx()
                                else 0f
                            dragStartXFraction = nextXFraction
                            dragStartYFraction = nextYFraction
                            offsetXFraction = nextXFraction
                            offsetYFraction = nextYFraction
                        }
                    },
            ) {
                Button(
                    modifier = Modifier.fillMaxSize(),
                    onClick = {
                        haptic.contextClick()
                        showActions = true
                    },
                    cornerRadius = ballSize / 2,
                    minHeight = ballSize,
                    insideMargin = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        color = Color.Black.copy(alpha = backgroundAlpha),
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .size(ringSize)
                            .clip(CircleShape)
                            .then(
                                if (ringAlpha > 0f) {
                                    Modifier.border(
                                        ringWidth,
                                        Color.White.copy(alpha = ringAlpha),
                                        CircleShape,
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                    )
                }

                ActionPopup(
                    show = showActions,
                    actions = actions,
                    onDismiss = { showActions = false },
                    onAction = {
                        if (it == VirtualButtonAction.PASSWORD_INPUT &&
                            passwordPopupContent != null
                        ) showPasswordPopup = true
                        else scope.launch { onAction(it) }

                        showActions = false
                    },
                    passwordPopupContent = passwordPopupContent,
                    renderInRootScaffold = true,
                    popupAlignment = popupAlignment,
                )

                if (passwordPopupContent != null) {
                    OverlayListPopup(
                        show = showPasswordPopup,
                        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                        alignment = popupAlignment,
                        onDismissRequest = { showPasswordPopup = false },
                        renderInRootScaffold = true,
                        enableWindowDim = false,
                    ) {
                        passwordPopupContent { showPasswordPopup = false }
                    }
                }
            }
        }
    }

    @Composable
    private fun ActionPopup(
        show: Boolean,
        actions: List<VirtualButtonAction>,
        onDismiss: () -> Unit,
        onAction: suspend (VirtualButtonAction) -> Unit,
        passwordPopupContent: (@Composable (onDismissRequest: () -> Unit) -> Unit)? = null,
        renderInRootScaffold: Boolean,
        popupAlignment: PopupPositionProvider.Align = PopupPositionProvider.Align.TopEnd,
    ) {
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val spinnerItems = remember(actions) {
            actions.map { action ->
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

        NavOverlayListPopup(
            show = show,
            startDestination = ActionPopupDestination.Actions,
            popupAlignment = popupAlignment,
            onDismiss = onDismiss,
            renderInRootScaffold = renderInRootScaffold,
        ) { destination, navigateTo, dismiss ->
            ListPopupColumn {
                if (destination == ActionPopupDestination.Actions)
                    spinnerItems.forEachIndexed { index, entry ->
                        SpinnerItemImpl(
                            entry = entry,
                            entryCount = spinnerItems.size,
                            isSelected = false,
                            index = index,
                            spinnerColors = SpinnerDefaults.spinnerColors(),
                            dialogMode = false,
                            onSelectedIndexChange = { selectedIdx ->
                                haptic.confirm()
                                val selectedAction = actions[selectedIdx]
                                if (
                                    selectedAction == VirtualButtonAction.PASSWORD_INPUT &&
                                    passwordPopupContent != null
                                ) {
                                    navigateTo(ActionPopupDestination.Passwords)
                                } else {
                                    scope.launch { onAction(selectedAction) }
                                    dismiss()
                                }
                            },
                        )
                    }
                else if (passwordPopupContent != null)
                    passwordPopupContent { dismiss() }
                else
                    dismiss()
            }
        }
    }

    @Composable
    private fun <Destination> NavOverlayListPopup(
        show: Boolean,
        startDestination: Destination,
        popupAlignment: PopupPositionProvider.Align,
        onDismiss: () -> Unit,
        renderInRootScaffold: Boolean,
        content: @Composable (
            destination: Destination,
            navigateTo: (Destination) -> Unit,
            dismiss: () -> Unit,
        ) -> Unit,
    ) {
        var destination by remember(show, startDestination) { mutableStateOf(startDestination) }
        OverlayListPopup(
            show = show,
            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
            alignment = popupAlignment,
            onDismissRequest = onDismiss,
            renderInRootScaffold = renderInRootScaffold,
            enableWindowDim = false,
        ) {
            content(destination, { destination = it }, onDismiss)
        }
    }
}
