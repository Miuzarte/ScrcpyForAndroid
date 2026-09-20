package io.github.miuzarte.scrcpyforandroid.password

import androidx.activity.compose.LocalActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Password
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * 虚拟按钮级联菜单二级要展示的密码条目
 *
 * 选中任意一条即按需认证并注入密码, 与原 `PasswordPickerPopupContent` 的行为一致
 */
@Composable
fun rememberPasswordPickerEntries(): List<DropdownItem> {
    val fragActivity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    val passwordUseCase = remember { PasswordUseCase() }
    val entries by PasswordRepository.entriesState.collectAsState()
    val appSettingsBundle by appSettings.bundleState.collectAsState()

    val textInvalidated = stringResource(R.string.password_status_invalidated)
    val textAuthenticated = stringResource(R.string.password_status_authenticated)
    val textUnauthenticated = stringResource(R.string.password_status_unauthenticated)
    val textBurned = stringResource(R.string.password_status_burned)
    val textNone = stringResource(R.string.password_no_available)
    val textCannotAuthHere = stringResource(R.string.password_cannot_auth_here)
    val statusTexts = remember(
        textInvalidated,
        textAuthenticated,
        textUnauthenticated,
        textBurned,
        textNone,
        textCannotAuthHere,
    ) {
        PasswordStatusTexts(
            invalidated = textInvalidated,
            authenticated = textAuthenticated,
            unauthenticated = textUnauthenticated,
            burned = textBurned,
            none = textNone,
            cannotAuthHere = textCannotAuthHere,
        )
    }

    val textAuthFillTitle = stringResource(R.string.password_auth_fill_title)
    // 条目在菜单展开期间必须保持同一份实例, 否则级联菜单会把已展开的二级菜单收起;
    // scope / passwordUseCase / stringResource 都不随组合变化, 可以安全地留在闭包里
    return remember(entries, statusTexts, fragActivity, appSettingsBundle.passwordRequireAuth) {
        val activity = fragActivity
        val requireAuth = appSettingsBundle.passwordRequireAuth
        fun fillPassword(index: Int) {
            val entry = entries.getOrNull(index) ?: return
            if (activity == null) return
            scope.launch {
                passwordUseCase.preparePassword(
                    activity = activity,
                    entry = entry,
                    globalRequiresAuth = requireAuth,
                    authTitle = textAuthFillTitle,
                ).onSuccess { password ->
                    InjectionController.inject(password)
                }.onFailure { e ->
                    AppRuntime.snackbar(
                        R.string.password_fill_failed,
                        e.message ?: e.javaClass.simpleName,
                    )
                }
            }
        }
        passwordDropdownItems(
            entries = entries,
            texts = statusTexts,
            canAuthenticate = activity != null,
            onFill = ::fillPassword,
        )
    }
}

internal data class PasswordStatusTexts(
    val invalidated: String,
    val authenticated: String,
    val unauthenticated: String,
    val burned: String,
    val none: String,
    val cannotAuthHere: String,
)

internal fun passwordDropdownItems(
    entries: List<PasswordEntry>,
    texts: PasswordStatusTexts,
    canAuthenticate: Boolean,
    onFill: (Int) -> Unit,
): List<DropdownItem> {
    if (entries.isEmpty()) return listOf(DropdownItem(text = texts.none, enabled = false))
    // 当前页面拉不起生物验证时列表仍然展示, 只是不可点击, 免得用户点进二级菜单才发现用不了
    if (!canAuthenticate) return listOf(DropdownItem(text = texts.cannotAuthHere, enabled = false))
    return entries.mapIndexed { index, entry ->
        val usable = entry.cipherText != null
        DropdownItem(
            text = entry.name,
            enabled = usable,
            summary = when {
                !usable -> texts.invalidated
                entry.createdWithAuth == PasswordCreatedState.AuthenticatedCreated -> texts.authenticated
                entry.createdWithAuth == PasswordCreatedState.UnauthenticatedCreated -> texts.unauthenticated
                else -> texts.burned
            },
            // 失效密码不给回调: 界面上本来就点不动, 不给回调可以避免别处误触发填充
            onClick = if (usable) ({ onFill(index) }) else null,
            icon = { iconModifier ->
                Icon(
                    imageVector = if (usable) Icons.Rounded.Password else Icons.Rounded.Block,
                    contentDescription = entry.name,
                    modifier = iconModifier,
                    tint = colorScheme.onSurfaceVariantSummary,
                )
            },
        )
    }
}
