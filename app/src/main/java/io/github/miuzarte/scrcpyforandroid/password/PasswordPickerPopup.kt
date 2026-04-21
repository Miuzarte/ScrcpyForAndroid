package io.github.miuzarte.scrcpyforandroid.password

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Password
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.fragment.app.FragmentActivity
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.SpinnerDefaults
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.SpinnerItemImpl
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun PasswordPickerPopupContent(onDismissRequest: () -> Unit) {
    val fragActivity = LocalActivity.current as? FragmentActivity
    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val scope = rememberCoroutineScope()
    val passwordUseCase = remember { PasswordUseCase() }
    val entries by PasswordRepository.entriesState.collectAsState()
    val appSettingsBundle by appSettings.bundleState.collectAsState()

    val snackbar = LocalSnackbarController.current

    val spinnerEntries = remember(entries) {
        entries.map { entry ->
            SpinnerEntry(
                icon = {
                    Icon(
                        imageVector =
                            if (entry.cipherText == null) Icons.Rounded.Block
                            else Icons.Rounded.Password,
                        contentDescription = entry.name,
                        modifier = Modifier.padding(end = UiSpacing.ContentVertical),
                    )
                },
                title = entry.name,
                summary =
                    if (entry.cipherText == null) "已失效"
                    else when (entry.createdWithAuth) {
                        PasswordCreatedState.AuthenticatedCreated -> "创建时已验证"
                        PasswordCreatedState.UnauthenticatedCreated -> "创建时未经验证"
                        PasswordCreatedState.AuthenticatedCreatedModified -> "创建时已验证（熔断）"
                    },
            )
        }
    }

    fun fillPassword(index: Int) {
        val entry = entries[index]
        scope.launch {
            passwordUseCase.preparePassword(
                activity = fragActivity!!,
                entry = entry,
                globalRequiresAuth = appSettingsBundle.passwordRequireAuth,
            ).onSuccess { password ->
                InjectionController.inject(password)
                onDismissRequest()
            }.onFailure {
                taskScope.launch { snackbar.show(it.message ?: "密码填充失败") }
            }
        }
    }

    ListPopupColumn {
        if (spinnerEntries.isEmpty()) {
            Text(
                text = "无可用密码",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UiSpacing.PopupHorizontal),
                color = colorScheme.onSurfaceVariantSummary,
                fontWeight = FontWeight.Medium,
            )
            return@ListPopupColumn
        }

        if (fragActivity == null) {
            Text(
                text = "当前页面无法拉起验证",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UiSpacing.PopupHorizontal),
                color = colorScheme.onSurfaceVariantSummary,
                fontWeight = FontWeight.Medium,
            )
            return@ListPopupColumn
        }

        spinnerEntries.forEachIndexed { index, spinnerEntry ->
            SpinnerItemImpl(
                entry = spinnerEntry,
                entryCount = spinnerEntries.size,
                isSelected = false,
                index = index,
                spinnerColors = SpinnerDefaults.spinnerColors(),
                dialogMode = false,
                onSelectedIndexChange = ::fillPassword,
            )
        }
    }
}
