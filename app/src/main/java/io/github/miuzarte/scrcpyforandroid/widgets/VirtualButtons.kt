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
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
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

// 动作列表中「展开密码列表」的那一项: 它是级联菜单的触发项, 本身不是可执行动作
private val PasswordMenuAction = VirtualButtonAction.PASSWORD_INPUT

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
     * 不分「栏上 / 更多」的动作顺序, 用于把动作收进单个入口的形态 (悬浮球)
     *
     * 与 [splitLayout] 同一份用户排序, 因此悬浮球的菜单顺序与停靠栏一致;
     * 不需要的项 (例如"更多"这个开关本身) 由调用方剔除
     */
    fun mergedOrder(
        items: List<VirtualButtonItem>,
        excluded: Set<VirtualButtonAction> = emptySet(),
    ): List<VirtualButtonAction> {
        val visible = visibleOn(VirtualButtonSurface.FULLSCREEN).toSet()
        return items.map { it.action }
            .filter { it in visible && it !in excluded }
            .distinct()
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
                // 密码列表由菜单自己做二级展开, 选中密码才是填充动作
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

/**
 * 动作列表转成级联菜单的一级条目: [PasswordMenuAction] 展开后就是二级的密码列表
 *
 * [passwordChildren] 为 null 表示宿主没有提供密码列表, 此时该动作仍然展示, 但禁用且不展开,
 * 避免用户点了没有任何反馈
 */
@Composable
private fun rememberMenuEntries(
    actions: List<VirtualButtonAction>,
    passwordChildren: List<DropdownItem>?,
    dispatch: (VirtualButtonAction) -> Unit,
): List<DropdownEntry> {
    val titles = actions.map { stringResource(it.titleResId) }
    // 回调每个组合都是新实例, 所以不进 key: 条目在菜单展开期间必须保持同一份实例,
    // 否则级联菜单会把已展开的二级菜单收起
    val currentDispatch by rememberUpdatedState(dispatch)
    return remember(actions, titles, passwordChildren) {
        menuEntries(actions, titles, passwordChildren) { action ->
            currentDispatch(action)
        }
    }
}

private fun menuEntries(
    actions: List<VirtualButtonAction>,
    titles: List<String>,
    passwordChildren: List<DropdownItem>?,
    dispatch: (VirtualButtonAction) -> Unit,
): List<DropdownEntry> {
    val items = actions.mapIndexed { index, action ->
        val title = titles[index]
        val icon: @Composable (Modifier) -> Unit = { iconModifier ->
            Icon(
                imageVector = action.icon,
                contentDescription = title,
                modifier = iconModifier,
            )
        }
        if (action == PasswordMenuAction) {
            DropdownItem(
                text = title,
                enabled = !passwordChildren.isNullOrEmpty(),
                icon = icon,
                children = passwordChildren,
            )
        } else {
            DropdownItem(
                text = title,
                icon = icon,
                onClick = { dispatch(action) },
            )
        }
    }
    return listOf(DropdownEntry(items = items))
}

// 密码列表是一层平铺的条目, 作为二级菜单时不需要再分组
private fun passwordEntry(items: List<DropdownItem>): List<DropdownEntry> =
    listOf(DropdownEntry(items = items))

/**
 * 虚拟按钮所有弹层的统一状态
 *
 * 预览卡、全屏停靠栏、悬浮球共用这一份结构: 三条路径的差别只剩下面那几个可点的按钮画成
 * 什么样, 弹层与动作分发完全同源, 不会再出现某一条路径漏改的情况
 *
 * 槽位由调用方传进来, 因为预览卡与停靠栏可以同时存在, 两侧的菜单是互不影响的两份状态
 */
private class ActionPopups<S>(
    val menuSlot: S,
    val entries: List<DropdownEntry>,
    // 动作分发: 注入按键的在回调内部切到 IO, 与重构前的线程语义一致
    private val dispatch: (VirtualButtonAction) -> Unit,
    val passwordSlot: S? = null,
    val passwordChildren: List<DropdownItem>? = null,
) {
    var openSlot: S? by mutableStateOf(null)
        private set

    fun isOpen(slot: S): Boolean = openSlot == slot

    fun open(slot: S) {
        openSlot = slot
    }

    fun close() {
        openSlot = null
    }

    /** 点了某个动作按钮: 「更多」开菜单, 密码项开二级列表, 其余按行为分发 */
    fun trigger(action: VirtualButtonAction) {
        when {
            action == VirtualButtonAction.MORE -> open(menuSlot)

            // 没有密码列表可展开 (例如没配密码) 时仍然弹出菜单, 而不是点了没反应
            action == PasswordMenuAction ->
                if (passwordChildren.isNullOrEmpty() || passwordSlot == null) open(menuSlot)
                else open(passwordSlot)

            else -> dispatch(action)
        }
    }
}

class VirtualButtonBar(
    private val outside: List<VirtualButtonAction>,
    private val more: List<VirtualButtonAction>,
) {
    enum class FullscreenDock {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT,
    }

    /**
     * 弹层槽位: 每个"锚点"各自持有独立的状态, 因此预览卡、停靠栏、悬浮球各自的菜单互不影响
     *
     * 菜单与密码二级列表共用一个锚点 (二级菜单本来就挂在它的锚点上), 所以要分成两套槽位
     */
    private enum class PopupSlot {
        PreviewMore,
        PreviewPassword,
        FullscreenMore,
        FullscreenPassword,
        Ball,
    }

    // 每条渲染路径各持一份状态 (预览卡与停靠栏会同时存在), 但结构、条目与分发完全同源
    @Composable
    private fun rememberActionPopups(
        passwordChildren: List<DropdownItem>?,
        onAction: suspend (VirtualButtonAction) -> Unit,
        menuSlot: PopupSlot = PopupSlot.FullscreenMore,
        passwordSlot: PopupSlot? = PopupSlot.FullscreenPassword,
    ): ActionPopups<PopupSlot> {
        val scope = rememberCoroutineScope()
        fun dispatch(action: VirtualButtonAction) {
            scope.launch { onAction(action) }
        }
        val entries = rememberMenuEntries(more, passwordChildren) { action -> dispatch(action) }
        return remember(this) {
            ActionPopups(
                menuSlot = menuSlot,
                entries = entries,
                dispatch = { action -> dispatch(action) },
                passwordSlot = passwordSlot,
                passwordChildren = passwordChildren,
            )
        }
    }

    // 预览卡只渲染该界面可见的动作, 全屏专属动作在这里整体隐藏
    @Composable
    fun Preview(
        enabled: Boolean,
        showText: Boolean,
        onAction: suspend (VirtualButtonAction) -> Unit,
        modifier: Modifier = Modifier,
        passwordChildren: List<DropdownItem>? = null,
        popupBottomPadding: Dp = 0.dp,
    ) {
        val popups = rememberActionPopups(
            passwordChildren = passwordChildren,
            onAction = onAction,
            menuSlot = PopupSlot.PreviewMore,
            passwordSlot = PopupSlot.PreviewPassword,
        )
        val previewVisible = remember { VirtualButtonActions.visibleOn(VirtualButtonSurface.PREVIEW).toSet() }
        val visibleActions = outside.filter { it in previewVisible }

        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
        ) {
            visibleActions.forEach { action ->
                Box(modifier = Modifier.weight(1f)) {
                    PreviewActionButton(
                        action = action,
                        enabled = enabled,
                        showText = showText,
                        onClick = { popups.trigger(action) },
                    )
                    PreviewActionPopups(
                        state = popups,
                        visibleAction = action,
                        popupBottomPadding = popupBottomPadding,
                    )
                }
            }
        }
    }

    @Composable
    private fun PreviewActionButton(
        action: VirtualButtonAction,
        enabled: Boolean,
        showText: Boolean,
        onClick: () -> Unit,
    ) {
        val haptic = LocalHapticFeedback.current
        Button(
            onClick = {
                haptic.contextClick()
                onClick()
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                color = colorScheme.primary,
                disabledColor = colorScheme.primary.copy(alpha = 0.35f),
            ),
            insideMargin = PaddingValues(0.dp),
        ) {
            val contentColor =
                if (enabled) colorScheme.onPrimary
                else colorScheme.onPrimary.copy(alpha = 0.45f)
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
    }

    @Composable
    private fun PreviewActionPopups(
        state: ActionPopups<PopupSlot>,
        visibleAction: VirtualButtonAction,
        popupBottomPadding: Dp,
    ) {
        if (visibleAction == VirtualButtonAction.MORE) {
            ActionCascadingPopup(
                show = state.isOpen(state.menuSlot),
                entries = state.entries,
                onDismissRequest = state::close,
                alignment = PopupPositionProvider.Align.TopEnd,
                renderInRootScaffold = false,
                popupBottomPadding = popupBottomPadding,
            )
        }
        val passwordSlot = state.passwordSlot
        val passwordChildren = state.passwordChildren
        if (visibleAction == PasswordMenuAction && passwordSlot != null && !passwordChildren.isNullOrEmpty()) {
            ActionCascadingPopup(
                show = state.isOpen(passwordSlot),
                entries = passwordEntry(passwordChildren),
                onDismissRequest = state::close,
                alignment = PopupPositionProvider.Align.TopEnd,
                renderInRootScaffold = false,
                popupBottomPadding = popupBottomPadding,
            )
        }
    }

    @Composable
    fun Fullscreen(
        onAction: suspend (VirtualButtonAction) -> Unit,
        showOutsideButtons: Boolean = true,
        modifier: Modifier = Modifier,
        dock: FullscreenDock = FullscreenDock.BOTTOM,
        reverseOrder: Boolean = false,
        thickness: Dp = 16.dp,
        passwordChildren: List<DropdownItem>? = null,
    ) {
        val popups = rememberActionPopups(passwordChildren, onAction)
        // 悬浮球形态下停靠栏一个按钮都不画, 只保留共享状态 (showOutsideButtons = false)
        if (!showOutsideButtons) return

        val isVertical = dock == FullscreenDock.LEFT || dock == FullscreenDock.RIGHT
        val visibleActions =
            if (reverseOrder) outside.asReversed()
            else outside
        val containerModifier =
            if (isVertical) modifier
                .width(thickness)
                .fillMaxHeight()
            else modifier
                .fillMaxWidth()
                .height(thickness)

        if (isVertical) Column(
            modifier = containerModifier,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            val itemModifier = Modifier.weight(1f)
            visibleActions.forEach { FullscreenAction(it, thickness, itemModifier, popups) }
        }
        else Row(
            modifier = containerModifier,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            val itemModifier = Modifier.weight(1f)
            visibleActions.forEach { FullscreenAction(it, thickness, itemModifier, popups) }
        }
    }

    // 纵向与横向只有容器与按钮尺寸不同, 按钮本体与弹层共用同一套渲染
    // weight 是 RowScope / ColumnScope 的作用域扩展, 因此由调用方算好等分修饰符传进来
    @Composable
    private fun FullscreenAction(
        action: VirtualButtonAction,
        thickness: Dp,
        itemModifier: Modifier,
        popups: ActionPopups<PopupSlot>,
    ) {
        val haptic = LocalHapticFeedback.current
        Box(modifier = itemModifier) {
            Button(
                onClick = {
                    haptic.contextClick()
                    popups.trigger(action)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(thickness),
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

            if (action == VirtualButtonAction.MORE) {
                ActionCascadingPopup(
                    show = popups.isOpen(popups.menuSlot),
                    entries = popups.entries,
                    onDismissRequest = popups::close,
                )
            }
            val passwordSlot = popups.passwordSlot
            val passwordChildren = popups.passwordChildren
            if (action == PasswordMenuAction && passwordSlot != null && !passwordChildren.isNullOrEmpty()) {
                ActionCascadingPopup(
                    show = popups.isOpen(passwordSlot),
                    entries = passwordEntry(passwordChildren),
                    onDismissRequest = popups::close,
                )
            }
        }
    }

    /**
     * 悬浮球: 与停靠栏共用同一套弹层与分发, 只是把可点的东西收成一个球
     *
     * 调用方构造时把 more 传成完整动作列表 (走 [VirtualButtonActions.mergedOrder]), outside 传空;
     * 两边读的是同一份用户排序, 因此球的菜单顺序与停靠栏一致
     */
    @Composable
    fun FloatingBall(
        onAction: suspend (VirtualButtonAction) -> Unit,
        modifier: Modifier = Modifier,
        passwordChildren: List<DropdownItem>? = null,
    ) {
        val popups = rememberActionPopups(
            passwordChildren = passwordChildren,
            onAction = onAction,
            menuSlot = PopupSlot.Ball,
            passwordSlot = null,
        )
        // 没有任何动作可放进菜单时, 退回停靠栏形态 (画全部动作按钮), 避免出现一颗点不出东西的球
        if (more.isEmpty()) {
            Fullscreen(
                onAction = onAction,
                modifier = modifier,
                passwordChildren = passwordChildren,
            )
            return
        }

        val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
        // 悬浮球的位置与外观设置
        val asBundleShared by appSettings.bundleState.collectAsState()
        var offsetXFraction by rememberSaveable(asBundleShared.fullscreenFloatingButtonXFraction) {
            mutableFloatStateOf(asBundleShared.fullscreenFloatingButtonXFraction)
        }
        var offsetYFraction by rememberSaveable(asBundleShared.fullscreenFloatingButtonYFraction) {
            mutableFloatStateOf(asBundleShared.fullscreenFloatingButtonYFraction)
        }
        val asBundleSharedLatest by rememberUpdatedState(asBundleShared)
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

        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val ballSize = asBundleShared.fullscreenFloatingButtonSizeDp.dp
            val maxX = (maxWidth - ballSize).coerceAtLeast(0.dp)
            val maxY = (maxHeight - ballSize).coerceAtLeast(0.dp)
            val currentX = maxX * offsetXFraction.coerceIn(0f, 1f)
            val currentY = maxY * offsetYFraction.coerceIn(0f, 1f)
            val popupAlignment =
                if (offsetXFraction > 0.5f) PopupPositionProvider.Align.TopEnd
                else PopupPositionProvider.Align.TopStart

            Box(
                modifier = Modifier
                    .offset { IntOffset(currentX.roundToPx(), currentY.roundToPx()) }
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
                FloatingBallButton(
                    ballSize = ballSize,
                    backgroundAlpha =
                        (asBundleShared.fullscreenFloatingButtonBackgroundAlphaPercent / 100f)
                            .coerceIn(0.1f, 1f),
                    ringAlpha =
                        (asBundleShared.fullscreenFloatingButtonRingAlphaPercent / 100f)
                            .coerceIn(0f, 1f),
                    // 「更多」不在球的条目里, 用它当"打开菜单"的触发动作
                    onClick = { popups.trigger(VirtualButtonAction.MORE) },
                )

                ActionCascadingPopup(
                    show = popups.isOpen(popups.menuSlot),
                    entries = popups.entries,
                    onDismissRequest = popups::close,
                    alignment = popupAlignment,
                )
            }
        }
    }

    @Composable
    private fun FloatingBallButton(
        ballSize: Dp,
        backgroundAlpha: Float,
        ringAlpha: Float,
        onClick: () -> Unit,
    ) {
        val haptic = LocalHapticFeedback.current
        val ringSize = ballSize / 2
        val ringWidth = ballSize / 24
        Button(
            modifier = Modifier.fillMaxSize(),
            onClick = {
                haptic.contextClick()
                onClick()
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
    }

    /**
     * 级联菜单弹层: 一级是动作列表, 「填充锁屏密码」展开后是二级的密码列表
     */
    @Composable
    private fun ActionCascadingPopup(
        show: Boolean,
        entries: List<DropdownEntry>,
        onDismissRequest: () -> Unit,
        alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.TopEnd,
        renderInRootScaffold: Boolean = true,
        popupBottomPadding: Dp = 0.dp,
    ) {
        OverlayCascadingListPopup(
            show = show,
            entries = entries,
            onDismissRequest = onDismissRequest,
            popupPositionProvider =
                if (popupBottomPadding > 0.dp) {
                    rememberBottomSafeContextMenuPositionProvider(popupBottomPadding)
                } else {
                    ListPopupDefaults.ContextMenuPositionProvider
                },
            alignment = alignment,
            renderInRootScaffold = renderInRootScaffold,
            enableWindowDim = false,
        )
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
