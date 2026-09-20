package io.github.miuzarte.scrcpyforandroid.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 虚拟按钮动作的布局契约测试
 *
 * 覆盖新增动作 (如退出全屏) 的存储兼容与按界面过滤, 这两处一旦回归会直接表现为
 * 按钮消失或出现在不该出现的界面上
 */
class VirtualButtonActionsTest {
    private val exitFullscreen = VirtualButtonAction.EXIT_FULLSCREEN

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
}
