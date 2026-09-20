package io.github.miuzarte.scrcpyforandroid.password

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 虚拟按钮二级菜单 (密码列表) 的映射契约
 *
 * 这里最容易回归的是"哪些密码可以点": 失效密码与无法认证的页面都必须给出不可点击的条目,
 * 否则用户点下去只会收到一个填充失败提示
 */
class PasswordPickerEntriesTest {
    private val texts = PasswordStatusTexts(
        invalidated = "已失效",
        authenticated = "创建时已验证",
        unauthenticated = "创建时未经验证",
        burned = "创建时已验证 (熔断)",
        none = "无可用密码",
        cannotAuthHere = "当前页面无法拉起验证",
    )

    private val validEntry = PasswordEntry(
        id = "a",
        name = "密码 1",
        cipherText = "1234".toCharArray(),
        createdWithAuth = PasswordCreatedState.AuthenticatedCreated,
    )

    private val invalidEntry = PasswordEntry(
        id = "b",
        name = "密码 2",
        cipherText = null,
        createdWithAuth = PasswordCreatedState.AuthenticatedCreated,
    )

    @Test
    fun emptyPasswordListGivesDisabledHint() {
        val items = passwordDropdownItems(emptyList(), texts, canAuthenticate = true, onFill = {})

        assertEquals(1, items.size)
        assertEquals(texts.none, items[0].text)
        assertFalse(items[0].enabled)
    }

    @Test
    fun pageWithoutAuthenticationGivesDisabledHint() {
        val items = passwordDropdownItems(listOf(validEntry), texts, canAuthenticate = false, onFill = {})

        assertEquals(1, items.size)
        assertEquals(texts.cannotAuthHere, items[0].text)
        assertFalse(items[0].enabled)
    }

    @Test
    fun validPasswordIsClickableAndReportsItsIndex() {
        val filled = mutableListOf<Int>()
        val items = passwordDropdownItems(
            entries = listOf(validEntry, validEntry.copy(id = "c", name = "密码 3")),
            texts = texts,
            canAuthenticate = true,
            onFill = { filled += it },
        )

        assertEquals(2, items.size)
        assertTrue(items[0].enabled)
        assertEquals(texts.authenticated, items[0].summary)
        items[1].onClick?.invoke()
        assertEquals(listOf(1), filled)
    }

    @Test
    fun invalidatedPasswordStaysVisibleButDisabled() {
        val filled = mutableListOf<Int>()
        val items = passwordDropdownItems(
            entries = listOf(validEntry, invalidEntry),
            texts = texts,
            canAuthenticate = true,
            onFill = { filled += it },
        )

        assertEquals(2, items.size)
        assertEquals(texts.invalidated, items[1].summary)
        assertFalse(items[1].enabled)
        items[1].onClick?.invoke()
        assertEquals(emptyList<Int>(), filled)
    }

    @Test
    fun burnedPasswordKeepsItsOwnSummary() {
        val items = passwordDropdownItems(
            entries = listOf(validEntry.copy(createdWithAuth = PasswordCreatedState.AuthenticatedCreatedModified)),
            texts = texts,
            canAuthenticate = true,
            onFill = {},
        )

        assertEquals(texts.burned, items[0].summary)
    }
}
