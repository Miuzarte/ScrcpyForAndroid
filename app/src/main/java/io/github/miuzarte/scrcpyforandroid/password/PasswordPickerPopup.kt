package io.github.miuzarte.scrcpyforandroid.password

import android.annotation.SuppressLint
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.fragment.app.FragmentActivity
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
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

    val textInvalidated = stringResource(R.string.password_status_invalidated)
    val textAuthenticated = stringResource(R.string.password_status_authenticated)
    val textUnauthenticated = stringResource(R.string.password_status_unauthenticated)
    val textBurned = stringResource(R.string.password_status_burned)
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
                    if (entry.cipherText == null) textInvalidated
                    else when (entry.createdWithAuth) {
                        PasswordCreatedState.AuthenticatedCreated -> textAuthenticated
                        PasswordCreatedState.UnauthenticatedCreated -> textUnauthenticated
                        PasswordCreatedState.AuthenticatedCreatedModified -> textBurned
                    },
            )
        }
    }

    val textAuthFillTitle = stringResource(R.string.password_auth_fill_title)
    fun fillPassword(index: Int) {
        val entry = entries[index]
        scope.launch {
            passwordUseCase.preparePassword(
                activity = fragActivity!!,
                entry = entry,
                globalRequiresAuth = appSettingsBundle.passwordRequireAuth,
                authTitle = textAuthFillTitle,
            ).onSuccess { password ->
                InjectionController.inject(password)
                onDismissRequest()
            }.onFailure { e ->
                AppRuntime.snackbar(
                    R.string.password_fill_failed,
                    e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    ListPopupColumn {
        if (spinnerEntries.isEmpty()) {
            Text(
                text = stringResource(R.string.password_no_available),
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
                text = stringResource(R.string.password_cannot_auth_here),
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
