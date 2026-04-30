package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.R
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
    @field:StringRes val titleResId: Int,
    val icon: ImageVector,
    val keycode: Int?,
) {
    MORE(
        "more",
        R.string.vb_more,
        MiuixIcons.More,
        null
    ),
    HOME(
        "home",
        R.string.vb_home,
        Icons.Rounded.Home,
        UiAndroidKeycodes.HOME
    ),
    BACK(
        "back",
        R.string.vb_back,
        Icons.AutoMirrored.Rounded.ArrowBack,
        UiAndroidKeycodes.BACK
    ),
    APP_SWITCH(
        "app_switch",
        R.string.vb_app_switch,
        Icons.Rounded.Apps,
        UiAndroidKeycodes.APP_SWITCH
    ),
    MENU(
        "menu",
        R.string.vb_menu,
        Icons.Rounded.Menu,
        UiAndroidKeycodes.MENU
    ),
    NOTIFICATION(
        "notification",
        R.string.vb_notifications,
        Icons.Rounded.Notifications,
        UiAndroidKeycodes.NOTIFICATION
    ),
    VOLUME_UP(
        "volume_up",
        R.string.vb_volume_up,
        Icons.AutoMirrored.Rounded.VolumeUp,
        UiAndroidKeycodes.VOLUME_UP
    ),
    VOLUME_DOWN(
        "volume_down",
        R.string.vb_volume_down,
        Icons.AutoMirrored.Rounded.VolumeDown,
        UiAndroidKeycodes.VOLUME_DOWN
    ),
    VOLUME_MUTE(
        "volume_mute",
        R.string.vb_volume_mute,
        Icons.AutoMirrored.Rounded.VolumeOff,
        UiAndroidKeycodes.VOLUME_MUTE
    ),
    POWER(
        "power",
        R.string.vb_lock_screen,
        Icons.Rounded.PowerSettingsNew,
        UiAndroidKeycodes.POWER
    ),
    SCREENSHOT(
        "screenshot",
        R.string.vb_screenshot,
        Icons.Rounded.Screenshot,
        UiAndroidKeycodes.SYSRQ
    ),
    PASSWORD_INPUT(
        "password_input",
        R.string.vb_fill_password,
        Icons.Rounded.Password,
        null
    ),
    ALL_APPS(
        "all_apps",
        R.string.vb_all_apps,
        Icons.Rounded.Apps,
        null
    ),
    RECENT_TASKS(
        "recent_tasks",
        R.string.vb_recent_tasks,
        Icons.Rounded.DashboardCustomize,
        null
    ),
    TOGGLE_IME(
        "toggle_ime",
        R.string.vb_toggle_ime,
        Icons.Rounded.Keyboard,
        null
    ),
    PASTE_LOCAL_CLIPBOARD(
        "paste_local_clipboard",
        R.string.vb_paste_clipboard,
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
        popupBottomPadding: Dp = 0.dp,
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
                            imageVector = action.icon,
                            contentDescription = stringResource(action.titleResId),
                            modifier = Modifier.size(18.dp),
                            tint = contentColor,
                        )
                        if (showText) {
                            Spacer(Modifier.width(UiSpacing.Small))
                            Text(stringResource(action.titleResId), color = contentColor)
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
                            popupBottomPadding = popupBottomPadding,
                        )
                    }
                    if (
                        action == VirtualButtonAction.PASSWORD_INPUT &&
                        passwordPopupContent != null
                    ) {
                        OverlayListPopup(
                            show = showPasswordPopup,
                            popupPositionProvider =
                                rememberBottomSafeContextMenuPositionProvider(popupBottomPadding),
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
                        Icon(
                            imageVector = action.icon,
                            contentDescription = stringResource(action.titleResId),
                            tint = Color.White
                        )
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
                        Icon(
                            imageVector = action.icon,
                            contentDescription = stringResource(action.titleResId),
                            tint = Color.White
                        )
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
        popupBottomPadding: Dp = 0.dp,
    ) {
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val spinnerItems = actions.map { action ->
            val title = stringResource(action.titleResId)
            SpinnerEntry(
                icon = {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = title,
                        modifier = Modifier
                            .padding(end = UiSpacing.ContentVertical),
                    )
                },
                title = title,
            )
        }

        NavOverlayListPopup(
            show = show,
            startDestination = ActionPopupDestination.Actions,
            popupAlignment = popupAlignment,
            onDismiss = onDismiss,
            renderInRootScaffold = renderInRootScaffold,
            popupBottomPadding = popupBottomPadding,
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
        popupBottomPadding: Dp = 0.dp,
        content: @Composable (
            destination: Destination,
            navigateTo: (Destination) -> Unit,
            dismiss: () -> Unit,
        ) -> Unit,
    ) {
        var destination by remember(show, startDestination) { mutableStateOf(startDestination) }
        OverlayListPopup(
            show = show,
            popupPositionProvider =
                rememberBottomSafeContextMenuPositionProvider(popupBottomPadding),
            alignment = popupAlignment,
            onDismissRequest = onDismiss,
            renderInRootScaffold = renderInRootScaffold,
            enableWindowDim = false,
        ) {
            content(destination, { destination = it }, onDismiss)
        }
    }

    @Composable
    private fun rememberBottomSafeContextMenuPositionProvider(
        bottomPadding: Dp,
    ): PopupPositionProvider = remember(bottomPadding) {
        if (bottomPadding <= 0.dp) {
            ListPopupDefaults.ContextMenuPositionProvider
        } else {
            BottomSafeContextMenuPositionProvider(bottomPadding)
        }
    }

    private class BottomSafeContextMenuPositionProvider(
        private val bottomPadding: Dp,
    ) : PopupPositionProvider {
        private val delegate = ListPopupDefaults.ContextMenuPositionProvider

        override fun calculatePosition(
            anchorBounds: IntRect,
            windowBounds: IntRect,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
            popupMargin: IntRect,
            alignment: PopupPositionProvider.Align,
        ): IntOffset = delegate.calculatePosition(
            anchorBounds = anchorBounds,
            windowBounds = windowBounds,
            layoutDirection = layoutDirection,
            popupContentSize = popupContentSize,
            popupMargin = popupMargin,
            alignment = alignment,
        )

        override fun getMargins(): PaddingValues = PaddingValues(bottom = bottomPadding)
    }
}
