package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.pages.LocalRootNavigator
import io.github.miuzarte.scrcpyforandroid.pages.RootScreen
import io.github.miuzarte.scrcpyforandroid.scrcpy.ClientOptions.RecordFormat
import io.github.miuzarte.scrcpyforandroid.services.NativeRecordingSupport
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles

@Composable
fun RecordPreferences(
    profileId: String,
    recordFilenameTemplate: String,
    recordFormat: String,
    enabled: Boolean,
    onRecordFormatChange: (String) -> Unit,
) {
    val navigator = LocalRootNavigator.current
    val supportedFormats = remember { NativeRecordingSupport.supportedFormats }

    val formatItems = supportedFormats.map {
        if (it == RecordFormat.AUTO) stringResource(R.string.text_auto)
        else it.string
    }

    val formatIndex = remember(recordFormat) {
        supportedFormats.indexOfFirst { it.string == recordFormat }
            .coerceAtLeast(0)
    }
    val currentTemplateSummary = recordFilenameTemplate
        .ifBlank { stringResource(R.string.text_off) }

    ArrowPreference(
        title = stringResource(R.string.record_title),
        summary = "--record",
        enabled = enabled,
        onClick = {
            navigator.push(RootScreen.ScrcpyOptionRecord(profileId))
        },
        endActions = {
            Text(
                text = currentTemplateSummary,
                color = colorScheme.onSurfaceVariantActions,
                fontSize = textStyles.body2.fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )

    OverlayDropdownPreference(
        title = stringResource(R.string.record_format),
        summary = "--record-format",
        items = formatItems,
        selectedIndex = formatIndex,
        enabled = enabled,
        onSelectedIndexChange = { index ->
            onRecordFormatChange(supportedFormats[index].string)
        },
    )
}
