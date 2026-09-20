package io.github.miuzarte.scrcpyforandroid.widgets

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记录落到宿主上的动作, 用来验证 [VirtualButtonActions.perform] 的分发
 */
private class RecordingHost: VirtualButtonHost {
    val calls = mutableListOf<String>()

    override fun handleExitFullscreen() {
        calls += "exitFullscreen"
    }

    override fun handleShowRecentTasks() {
        calls += "recentTasks"
    }

    override fun handleShowAllApps() {
        calls += "allApps"
    }

    override fun handleToggleIme() {
        calls += "toggleIme"
    }

    override fun handlePasteLocalClipboard() {
        calls += "pasteClipboard"
    }
}

/**
 * 虚拟按钮动作的布局契约测试
 *
 * 覆盖新增动作 (如退出全屏) 的存储兼容与按界面过滤, 这两处一旦回归会直接表现为
 * 按钮消失或出现在不该出现的界面上; 另外覆盖 [VirtualButtonActions.perform] 的分发,
 * 它是唯一的动作出口, 漏接一个宿主动作会让该按钮点下去毫无反应
 */
class VirtualButtonActionsTest {
    private val exitFullscreen = VirtualButtonAction.EXIT_FULLSCREEN

    // perform 只负责启动分发, 用 Unconfined 让它在本线程内跑完, 断言不需要等待
    private fun dispatch(
        action: VirtualButtonAction,
        host: VirtualButtonHost,
        onInjectKeycode: suspend (Int) -> Unit = {},
    ) = VirtualButtonActions.perform(
        scope = CoroutineScope(Dispatchers.Unconfined),
        action = action,
        onInjectKeycode = onInjectKeycode,
        host = host,
    )

    // 旧版本存下的布局, 其中没有退出全屏这一项
    private val legacyStoredLayout = "more:1,app_switch:1,home:0,back:1,password_input:0," +
        "all_apps:0,recent_tasks:0,toggle_ime:0,paste_local_clipboard:0," +
        "menu:0,notification:0,volume_up:0,volume_down:0,volume_mute:0,power:0,screenshot:0"

    @Test
    fun fullscreenOnlyActionOnlyVisibleOnFullscreenSurface() {
        assertTrue(exitFullscreen in VirtualButtonActions.visibleOn(VirtualButtonSurface.FULLSCREEN))
        assertFalse(exitFullscreen in VirtualButtonActions.visibleOn(VirtualButtonSurface.PREVIEW))
    }

    @Test
    fun fullscreenOnlyActionHiddenFromPreviewSurface() {
        val items = VirtualButtonActions.parseStoredLayout("${exitFullscreen.id}:1")

        val (previewOutside, previewMore) =
            VirtualButtonActions.splitLayout(items, VirtualButtonSurface.PREVIEW)
        assertFalse(exitFullscreen in previewOutside)
        assertFalse(exitFullscreen in previewMore)

        val (fullscreenOutside, _) =
            VirtualButtonActions.splitLayout(items, VirtualButtonSurface.FULLSCREEN)
        assertTrue(exitFullscreen in fullscreenOutside)
    }

    @Test
    fun olderStoredLayoutGetsNewActionsInMoreMenu() {
        // 旧布局里没有新动作, 解析时应追加到更多菜单, 而不是占用外部按钮位
        val items = VirtualButtonActions.parseStoredLayout(legacyStoredLayout)
        val restored = items.firstOrNull { it.action == exitFullscreen }
        assertEquals(false, restored?.showOutside)

        val (outside, more) = VirtualButtonActions.splitLayout(items)
        assertFalse(exitFullscreen in outside)
        assertTrue(exitFullscreen in more)
    }

    @Test
    fun storedLayoutRoundTripKeepsPlacement() {
        val items = VirtualButtonActions.parseStoredLayout("")
            .map { if (it.action == exitFullscreen) it.copy(showOutside = true) else it }

        val decoded = VirtualButtonActions.parseStoredLayout(
            VirtualButtonActions.encodeStoredLayout(items),
        )
        val restored = decoded.firstOrNull { it.action == exitFullscreen }
        assertEquals(true, restored?.showOutside)
    }

    @Test
    fun mergedOrderFollowsUserLayoutInsteadOfEnumOrder() {
        // 存储顺序与枚举顺序刻意不同: 悬浮球这类"一个菜单装下全部动作"的入口必须跟着用户排序
        val stored = "power:0,home:0,back:0"

        val merged = VirtualButtonActions.mergedOrder(
            VirtualButtonActions.parseStoredLayout(stored),
            excluded = setOf(VirtualButtonAction.MORE),
        )

        assertEquals(
            listOf(VirtualButtonAction.POWER, VirtualButtonAction.HOME, VirtualButtonAction.BACK),
            merged.take(3),
        )
    }

    @Test
    fun mergedOrderExcludesRequestedActionsAndKeepsEveryOtherOne() {
        val items = VirtualButtonActions.parseStoredLayout("")

        val merged = VirtualButtonActions.mergedOrder(
            items,
            excluded = setOf(VirtualButtonAction.MORE),
        )

        assertFalse(VirtualButtonAction.MORE in merged)
        // 除被剔除的以外, 菜单式入口不该漏掉任何动作
        assertEquals(
            VirtualButtonAction.entries.filterNot { it == VirtualButtonAction.MORE }.toSet(),
            merged.toSet(),
        )
    }

    @Test
    fun behaviorMatchesKeycodePresence() {
        VirtualButtonAction.entries.forEach { action ->
            when (action.behavior) {
                VirtualButtonBehavior.INJECT_KEYCODE -> assertTrue(
                    "${action.id} 标了注入按键却没有 keycode",
                    action.keycode != null,
                )

                VirtualButtonBehavior.HOST_ACTION -> assertNull(
                    "${action.id} 是宿主动作, 不该带 keycode",
                    action.keycode,
                )
            }
        }
    }

    @Test
    fun keycodeLookupResolvesInjectedActions() {
        VirtualButtonAction.entries
            .filter { it.behavior == VirtualButtonBehavior.INJECT_KEYCODE }
            .forEach { action ->
                val keycode = action.keycode ?: return@forEach
                assertEquals(action, VirtualButtonActions.byKeycode(keycode))
            }
    }

    @Test
    fun performInjectsKeycodeForKeycodeActions() {
        val injecting = VirtualButtonAction.entries
            .filter { it.behavior == VirtualButtonBehavior.INJECT_KEYCODE }
        val injected = mutableListOf<Int>()
        val host = RecordingHost()

        injecting.forEach { action ->
            dispatch(action, host, onInjectKeycode = { injected += it })
        }

        assertEquals(injecting.mapNotNull { it.keycode }, injected)
        assertEquals(emptyList<String>(), host.calls)
    }

    @Test
    fun performRoutesHostActionsToTheirCallback() {
        val expected = mapOf(
            VirtualButtonAction.EXIT_FULLSCREEN to "exitFullscreen",
            VirtualButtonAction.RECENT_TASKS to "recentTasks",
            VirtualButtonAction.ALL_APPS to "allApps",
            VirtualButtonAction.TOGGLE_IME to "toggleIme",
            VirtualButtonAction.PASTE_LOCAL_CLIPBOARD to "pasteClipboard",
        )

        expected.forEach { (action, callback) ->
            val host = RecordingHost()
            dispatch(action, host)
            assertEquals("$action 应该落到 $callback", listOf(callback), host.calls)
        }
    }

    @Test
    fun everyHostActionIsEitherDispatchedOrMenuOwned() {
        // 「更多」与「填充锁屏密码」由菜单自己消化, 其余宿主动作必须有唯一的分发目标
        // (漏接时 perform 会抛错, 这条同时保证没有动作被静默吞掉)
        val menuOwned = setOf(VirtualButtonAction.MORE, VirtualButtonAction.PASSWORD_INPUT)

        VirtualButtonAction.entries
            .filter { it.behavior == VirtualButtonBehavior.HOST_ACTION }
            .forEach { action ->
                val host = RecordingHost()
                val injected = mutableListOf<Int>()
                dispatch(action, host, onInjectKeycode = { injected += it })

                assertEquals(
                    "$action 的宿主动作分发次数不对",
                    if (action in menuOwned) 0 else 1,
                    host.calls.size,
                )
                assertEquals("$action 不该注入按键", emptyList<Int>(), injected)
            }
    }
}
