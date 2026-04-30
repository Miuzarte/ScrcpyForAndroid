package io.github.miuzarte.scrcpyforandroid.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.RecordFilenameTemplate
import io.github.miuzarte.scrcpyforandroid.services.RecordingFileResolver
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyOptions
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyProfiles
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.scrcpyOptions
import io.github.miuzarte.scrcpyforandroid.storage.Storage.scrcpyProfiles
import io.github.miuzarte.scrcpyforandroid.ui.BlurredBar
import io.github.miuzarte.scrcpyforandroid.ui.LocalEnableBlur
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles

@Composable
internal fun RecordPreferencesScreen(
    scrollBehavior: ScrollBehavior,
    profileId: String,
    scrcpy: Scrcpy,
) {
    val navigator = LocalRootNavigator.current
    val blurBackdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = blurBackdrop != null

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { AppRuntime.snackbarHostState?.let { SnackbarHost(it) } },
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                TopAppBar(
                    title = stringResource(R.string.record_title),
                    color =
                        if (blurActive) Color.Transparent
                        else colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = navigator.pop) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { pagePadding ->
        Box(modifier = if (blurActive) Modifier.layerBackdrop(blurBackdrop) else Modifier) {
            RecordPreferencesPage(
                contentPadding = pagePadding,
                scrollBehavior = scrollBehavior,
                profileId = profileId,
                scrcpy = scrcpy,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordPreferencesPage(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    profileId: String,
    scrcpy: Scrcpy,
) {
    val focusManager = LocalFocusManager.current
    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val currentSession by scrcpy.currentSessionState.collectAsState()

    val soBundleShared by scrcpyOptions.bundleState.collectAsState()
    val scrcpyProfilesState by scrcpyProfiles.state.collectAsState()
    val sourceBundle = remember(profileId, soBundleShared, scrcpyProfilesState) {
        recordBundleForProfile(
            profileId = profileId,
            globalBundle = soBundleShared,
            profilesState = scrcpyProfilesState,
        )
    }
    val sourceBundleLatest by rememberUpdatedState(sourceBundle)
    var soBundle by rememberSaveable(profileId, sourceBundle) { mutableStateOf(sourceBundle) }
    val soBundleLatest by rememberUpdatedState(soBundle)

    LaunchedEffect(sourceBundle) {
        if (soBundle != sourceBundle) {
            soBundle = sourceBundle
        }
    }
    LaunchedEffect(soBundle) {
        delay(Settings.BUNDLE_SAVE_DELAY)
        if (soBundle != sourceBundleLatest) {
            saveRecordBundleForProfile(profileId, soBundle)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            taskScope.launch {
                saveRecordBundleForProfile(profileId, soBundleLatest)
            }
        }
    }

    var draftTemplate by rememberSaveable(
        profileId,
        stateSaver = TextFieldValue.Saver,
    ) {
        mutableStateOf(
            TextFieldValue(
                text = soBundle.recordFilename,
                selection = TextRange(soBundle.recordFilename.length),
            )
        )
    }

    LaunchedEffect(sourceBundle.recordFilename) {
        if (draftTemplate.text != sourceBundle.recordFilename) {
            draftTemplate = TextFieldValue(
                text = sourceBundle.recordFilename,
                selection = TextRange(sourceBundle.recordFilename.length),
            )
        }
    }
    LaunchedEffect(draftTemplate.text, profileId) {
        delay(Settings.BUNDLE_SAVE_DELAY)
        val trimmed = draftTemplate.text.trim()
        if (trimmed != sourceBundleLatest.recordFilename) {
            saveRecordBundleForProfile(
                profileId = profileId,
                bundle = soBundleLatest.copy(recordFilename = trimmed),
            )
        }
    }
    val draftTemplateLatest by rememberUpdatedState(draftTemplate)
    DisposableEffect(Unit) {
        onDispose {
            val trimmed = draftTemplateLatest.text.trim()
            if (trimmed != soBundleLatest.recordFilename) {
                taskScope.launch {
                    saveRecordBundleForProfile(
                        profileId = profileId,
                        bundle = soBundleLatest.copy(recordFilename = trimmed),
                    )
                }
            }
        }
    }

    val entries = remember { RecordFilenameTemplate.entries }
    val previewSessionInfo = remember(currentSession) {
        currentSession ?: Scrcpy.Session.SessionInfo(
            deviceName = AppRuntime.currentConnectedDevice?.model ?: "Unknown",
            codecId = 0,
            codec = Shared.Codec.H264,
            width = 1920,
            height = 1080,
            audioCodecId = 0,
            audioCodec = Shared.Codec.OPUS,
            controlEnabled = true,
            host = AppRuntime.currentConnectionTarget?.host ?: "192.168.1.100",
            port = AppRuntime.currentConnectionTarget?.port ?: 5555,
        )
    }
    val previewFilename = remember(draftTemplate.text) {
        RecordingFileResolver.sanitizeFileName(
            RecordFilenameTemplate.resolve(
                template = draftTemplate.text,
                sessionInfo = previewSessionInfo,
            )
        )
    }
    val listState = rememberSaveable(
        profileId,
        saver = LazyListState.Saver,
    ) {
        LazyListState()
    }

    LazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
        state = listState,
        bottomInnerPadding = UiSpacing.PageBottom,
    ) {
        item {
            TextField(
                value = draftTemplate,
                onValueChange = { draftTemplate = it.sanitizeRecordFilenameInput() },
                label = stringResource(R.string.record_filename_hint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
        }

        if (draftTemplate.text.isNotEmpty()) item {
            Text(
                text = stringResource(R.string.record_preview) + "\n$previewFilename",
                color = colorScheme.onSurfaceVariantSummary,
                fontSize = textStyles.body2.fontSize,
                modifier = Modifier.padding(horizontal = UiSpacing.Large),
            )
        }

        item {
            Card {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiSpacing.Large),
                    horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                ) {
                    entries.forEach { entry ->
                        TextButton(
                            text = entry.value,
                            onClick = {
                                draftTemplate = draftTemplate.replaceSelection(entry.value)
                            },
                            insideMargin = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(vertical = UiSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                ) {
                    entries.filter { it.isTemplate }.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider()
                        Column(
                            modifier = Modifier.padding(horizontal = UiSpacing.Large),
                            verticalArrangement = Arrangement.spacedBy(UiSpacing.Small)
                        ) {
                            Text(
                                text = entry.value,
                                color = if (entry.isTemplate) colorScheme.primary else colorScheme.onSurface,
                                fontSize = textStyles.body1.fontSize,
                            )
                            Text(
                                text = stringResource(
                                    entry.descriptionResId ?: R.string.record_plain_text
                                ),
                                color = colorScheme.onSurfaceVariantSummary,
                                fontSize = textStyles.body2.fontSize,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun recordBundleForProfile(
    profileId: String,
    globalBundle: ScrcpyOptions.Bundle,
    profilesState: ScrcpyProfiles.State,
): ScrcpyOptions.Bundle {
    return if (profileId == ScrcpyOptions.GLOBAL_PROFILE_ID) {
        globalBundle
    } else {
        profilesState.profiles
            .firstOrNull { it.id == profileId }
            ?.bundle
            ?: globalBundle
    }
}

private suspend fun saveRecordBundleForProfile(
    profileId: String,
    bundle: ScrcpyOptions.Bundle,
) {
    val normalizedBundle = bundle.copy(recordFilename = bundle.recordFilename.trim())
    if (profileId == ScrcpyOptions.GLOBAL_PROFILE_ID)
        scrcpyOptions.saveBundle(normalizedBundle)
    else
        scrcpyProfiles.updateBundle(profileId, normalizedBundle)
}

private fun TextFieldValue.replaceSelection(replacement: String): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val updatedText = buildString {
        append(text.substring(0, start))
        append(replacement)
        append(text.substring(end))
    }
    val cursor = start + replacement.length
    return copy(
        text = updatedText,
        selection = TextRange(cursor),
    ).sanitizeRecordFilenameInput()
}

private fun TextFieldValue.sanitizeRecordFilenameInput(): TextFieldValue {
    val sanitizedText = RecordingFileResolver.sanitizeFileName(text)
    if (sanitizedText == text) return this
    val sanitizedSelection = TextRange(
        start = selection.start.coerceIn(0, sanitizedText.length),
        end = selection.end.coerceIn(0, sanitizedText.length),
    )
    return copy(
        text = sanitizedText,
        selection = sanitizedSelection,
    )
}
