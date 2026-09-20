package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import kotlin.ranges.coerceAtLeast

// 动作语义: 决定动作由谁执行, 分发方只需按行为分支, 不必再按具体动作穷举
enum class VirtualButtonBehavior {
    // 注入设备按键: 由客户端向设备发送 keycode
    INJECT_KEYCODE,

    // 客户端本地动作: 需要宿主的 scrcpy 会话 / 界面状态, 客户端内部消化
    HOST_ACTION,
}

// 动作出现的界面: 用于把只在流媒体全屏页有意义的动作挡在预览卡与排序页之外
enum class VirtualButtonSurface {
    // 设备页预览卡 + 虚拟按钮排序页 (按同一套布局渲染)
    PREVIEW,

    // 流媒体全屏页的停靠栏
    FULLSCREEN,
}

enum class VirtualButtonAction(
    val id: String,
    @field:StringRes val titleResId: Int,
    val icon: ImageVector,
    val keycode: Int?,
    val behavior: VirtualButtonBehavior,
    // 是否只在流媒体全屏页显示, 预览卡与排序页会整体隐藏该动作
    val fullscreenOnly: Boolean = false,
) {
    MORE(
        id = "more",
        titleResId = R.string.vb_more,
        icon = MiuixIcons.More,
        keycode = null,
        behavior = VirtualButtonBehavior.HOST_ACTION,
    ),
    HOME(
        id = "home",
        titleResId = R.string.vb_home,
        icon = Icons.Rounded.Home,
        keycode = UiAndroidKeycodes.HOME,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    BACK(
        id = "back",
        titleResId = R.string.vb_back,
        icon = Icons.AutoMirrored.Rounded.ArrowBack,
        keycode = UiAndroidKeycodes.BACK,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    APP_SWITCH(
        id = "app_switch",
        titleResId = R.string.vb_app_switch,
        icon = Icons.Rounded.Apps,
        keycode = UiAndroidKeycodes.APP_SWITCH,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    MENU(
        id = "menu",
        titleResId = R.string.vb_menu,
        icon = Icons.Rounded.Menu,
        keycode = UiAndroidKeycodes.MENU,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    NOTIFICATION(
        id = "notification",
        titleResId = R.string.vb_notifications,
        icon = Icons.Rounded.Notifications,
        keycode = UiAndroidKeycodes.NOTIFICATION,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    VOLUME_UP(
        id = "volume_up",
        titleResId = R.string.vb_volume_up,
        icon = Icons.AutoMirrored.Rounded.VolumeUp,
        keycode = UiAndroidKeycodes.VOLUME_UP,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    VOLUME_DOWN(
        id = "volume_down",
        titleResId = R.string.vb_volume_down,
        icon = Icons.AutoMirrored.Rounded.VolumeDown,
        keycode = UiAndroidKeycodes.VOLUME_DOWN,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    VOLUME_MUTE(
        id = "volume_mute",
        titleResId = R.string.vb_volume_mute,
        icon = Icons.AutoMirrored.Rounded.VolumeOff,
        keycode = UiAndroidKeycodes.VOLUME_MUTE,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    POWER(
        id = "power",
        titleResId = R.string.vb_lock_screen,
        icon = Icons.Rounded.PowerSettingsNew,
        keycode = UiAndroidKeycodes.POWER,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    SCREENSHOT(
        id = "screenshot",
        titleResId = R.string.vb_screenshot,
        icon = Icons.Rounded.Screenshot,
        keycode = UiAndroidKeycodes.SYSRQ,
        behavior = VirtualButtonBehavior.INJECT_KEYCODE,
    ),
    PASSWORD_INPUT(
        id = "password_input",
        titleResId = R.string.vb_fill_password,
        icon = Icons.Rounded.Password,
        keycode = null,
        behavior = VirtualButtonBehavior.HOST_ACTION,
    ),
    ALL_APPS(
        id = "all_apps",
        titleResId = R.string.vb_all_apps,
        icon = Icons.Rounded.Apps,
        keycode = null,
        behavior = VirtualButtonBehavior.HOST_ACTION,
    ),
    RECENT_TASKS(
        id = "recent_tasks",
        titleResId = R.string.vb_recent_tasks,
        icon = Icons.Rounded.DashboardCustomize,
        keycode = null,
        behavior = VirtualButtonBehavior.HOST_ACTION,
    ),
    TOGGLE_IME(
        id = "toggle_ime",
        titleResId = R.string.vb_toggle_ime,
        icon = Icons.Rounded.Keyboard,
        keycode = null,
        behavior = VirtualButtonBehavior.HOST_ACTION,
    ),
    PASTE_LOCAL_CLIPBOARD(
        id = "paste_local_clipboard",
        titleResId = R.string.vb_paste_clipboard,
        icon = Icons.Rounded.ContentPaste,
        keycode = null,
        behavior = VirtualButtonBehavior.HOST_ACTION,
    ),
    EXIT_FULLSCREEN(
        id = "exit_fullscreen",
        titleResId = R.string.vb_exit_fullscreen,
        icon = Icons.Rounded.FullscreenExit,
        keycode = null,
        behavior = VirtualButtonBehavior.HOST_ACTION,
        fullscreenOnly = true,
    );
}

data class VirtualButtonItem(
    val action: VirtualButtonAction,
    val showOutside: Boolean,
)

/**
 * 宿主动作回调: 由承载虚拟按钮的界面实现, 只有界面自己知道这些动作该落到什么状态上
 *
 * 新增一个宿主动作时, 在这里加一个方法即可, 不需要再去每个界面补一个 when 分支
 */
interface VirtualButtonHost {
    // 关闭流媒体全屏页, 回到设备页
    fun handleExitFullscreen() = Unit

    // 打开最近任务面板
    fun handleShowRecentTasks() = Unit

    // 打开应用列表面板
    fun handleShowAllApps() = Unit

    // 拉起设备输入法
    fun handleToggleIme() = Unit

    // 把本机剪贴板内容粘贴到设备
    fun handlePasteLocalClipboard() = Unit
}

object VirtualButtonActions {
    val all = VirtualButtonAction.entries

    private val byId = all.associateBy { it.id }

    private val byKeycode = all.mapNotNull { action ->
        action.keycode?.let { keycode -> keycode to action }
    }.toMap()

    fun byKeycode(keycode: Int): VirtualButtonAction? = byKeycode[keycode]

    // 该界面上可见的动作: 全屏专属动作只在流媒体全屏页提供
    fun visibleOn(surface: VirtualButtonSurface): List<VirtualButtonAction> = all.filter { action ->
        when (surface) {
            VirtualButtonSurface.FULLSCREEN -> true
            VirtualButtonSurface.PREVIEW -> !action.fullscreenOnly
        }
    }

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
        // 新增动作无需迁移存储: 未出现在已存布局里的动作统一追加到更多菜单
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

    fun splitLayout(
        items: List<VirtualButtonItem>,
        surface: VirtualButtonSurface = VirtualButtonSurface.FULLSCREEN,
    ): Pair<List<VirtualButtonAction>, List<VirtualButtonAction>> {
        val visible = visibleOn(surface).toSet()
        val shown = items.filter { it.action in visible }
        val outside = shown.filter { it.showOutside }.map { it.action }
        val more = shown.filter { !it.showOutside }.map { it.action }
        return outside to more
    }

    /**
     * 虚拟按钮动作的唯一分发点
     *
     * 按 [VirtualButtonBehavior] 分类处理: 注入按键的动作直接下发设备, 客户端本地动作交给
     * [host]; 因此新增动作不会再掉进某个界面的 else 兜底分支里被静默忽略
     *
     * [scope] 用一个主线程作用域即可: 界面回调本就在主线程, 设备侧的下发由各回调内部
     * 自行切到 IO, 与重构前逐动作手写时的线程语义保持一致
     */
    fun perform(
        scope: CoroutineScope,
        action: VirtualButtonAction,
        onInjectKeycode: suspend (Int) -> Unit,
        host: VirtualButtonHost,
    ) {
        when (action.behavior) {
            VirtualButtonBehavior.INJECT_KEYCODE -> {
                val keycode = action.keycode ?: return
                scope.launch { onInjectKeycode(keycode) }
            }

            // 宿主动作同步执行, 不额外启动协程, 保证在调用线程 (主线程) 上落地
            VirtualButtonBehavior.HOST_ACTION -> when (action) {
                VirtualButtonAction.MORE -> Unit
                VirtualButtonAction.PASSWORD_INPUT -> Unit
                VirtualButtonAction.EXIT_FULLSCREEN -> host.handleExitFullscreen()
                VirtualButtonAction.RECENT_TASKS -> host.handleShowRecentTasks()
                VirtualButtonAction.ALL_APPS -> host.handleShowAllApps()
                VirtualButtonAction.TOGGLE_IME -> host.handleToggleIme()
                VirtualButtonAction.PASTE_LOCAL_CLIPBOARD -> host.handlePasteLocalClipboard()
                // 该动作标了 HOST_ACTION 却没有分发目标: 属于接错线, 直接暴露而不是静默吞掉
                else -> error("unhandled host action: ${action.id}")
            }
        }
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
        // 预览卡只渲染该界面可见的动作, 全屏专属动作在这里整体隐藏
        val previewVisible = remember { VirtualButtonActions.visibleOn(VirtualButtonSurface.PREVIEW).toSet() }
        val visibleActions = outsideActions.filter { it in previewVisible }

        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
        ) {
            visibleActions.forEach { action ->
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
                        PreviewActionButtonContent(
                            action = action,
                            showText = showText,
                            contentColor = contentColor,
                        )
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
    private fun PreviewActionButtonContent(
        action: VirtualButtonAction,
        showText: Boolean,
        contentColor: Color,
    ) {
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

        // 纵向与横向只有容器与按钮尺寸不同, 按钮本体与弹层共用同一套渲染
        // weight 是 RowScope / ColumnScope 的作用域扩展, 因此由调用方算好等分修饰符传进来
        @Composable
        fun renderButton(action: VirtualButtonAction, itemModifier: Modifier) {
            Box(modifier = itemModifier) {
                FullscreenActionButton(
                    action = action,
                    thickness = thickness,
                    modifier = buttonModifier,
                    onClick = {
                        haptic.contextClick()
                        when (action) {
                            // 更多菜单与密码输入由本组件自行展开弹层, 其余动作上抛
                            VirtualButtonAction.MORE -> showMorePopup = true
                            VirtualButtonAction.PASSWORD_INPUT
                                if passwordPopupContent != null -> showPasswordPopup = true

                            else -> scope.launch { onAction(action) }
                        }
                    },
                )

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

        if (isVertical) Column(
            modifier = containerModifier,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            val itemModifier = Modifier.weight(1f)
            visibleActions.forEach { renderButton(it, itemModifier) }
        }
        else Row(
            modifier = containerModifier,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            val itemModifier = Modifier.weight(1f)
            visibleActions.forEach { renderButton(it, itemModifier) }
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
    private fun FullscreenActionButton(
        action: VirtualButtonAction,
        thickness: Dp,
        modifier: Modifier,
        onClick: () -> Unit,
    ) {
        Button(
            onClick = onClick,
            modifier = modifier,
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
                tint = Color.White,
            )
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
                            ),
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
                                },
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
            DropdownItem(
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
                            spinnerColors = DropdownDefaults.dropdownColors(),
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
    ): PopupPositionProvider {
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
