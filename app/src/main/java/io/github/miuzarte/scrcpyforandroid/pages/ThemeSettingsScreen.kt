package io.github.miuzarte.scrcpyforandroid.pages

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.SectionSmallTitle
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.ui.*
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

private val blurModeLabels = listOf(
    R.string.pref_blur_none,
    R.string.pref_blur_gaussian,
    R.string.pref_blur_progressive,
)
private val monetPaletteStyleOptions = ThemePaletteStyle.entries.map { it.name }
private val monetColorSpecOptions = ThemeColorSpec.entries.map { it.name }
private val monetKeyColorOptions = MonetKeyColorOptions
private val navTransitionStyleOptions = listOf(
    R.string.pref_transition_style_miuix,
    R.string.pref_transition_style_aosp,
)

@Composable
internal fun ThemeSettingsScreen() {
    val haptic = LocalHapticFeedback.current
    val navigator = LocalRootNavigator.current
    val localBlurMode = LocalBlurMode.current
    val blurBackdrop = rememberBlurBackdrop(localBlurMode != AppSettings.BlurMode.NONE)
    val blurActive = blurBackdrop != null
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    val asBundleShared by appSettings.bundleState.collectAsState()
    val asBundleSharedLatest by rememberUpdatedState(asBundleShared)
    var asBundle by rememberSaveable(asBundleShared) { mutableStateOf(asBundleShared) }
    val asBundleLatest by rememberUpdatedState(asBundle)
    LaunchedEffect(asBundleShared) {
        if (asBundle != asBundleShared) asBundle = asBundleShared
    }
    LaunchedEffect(asBundle) {
        delay(Settings.BUNDLE_SAVE_DELAY)
        if (asBundle != asBundleSharedLatest) appSettings.saveBundle(asBundle)
    }
    DisposableEffect(Unit) {
        onDispose { taskScope.launch { appSettings.saveBundle(asBundleLatest) } }
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                TopAppBar(
                    title = stringResource(R.string.pref_title_theme_settings),
                    scrollBehavior = scrollBehavior,
                    color =
                        if (blurActive) Color.Transparent
                        else colorScheme.surface,
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                haptic.contextClick()
                                navigator.pop()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                if (blurActive) Modifier.layerBackdrop(blurBackdrop)
                else Modifier,
        ) {
            LazyColumn(
                contentPadding = innerPadding,
                scrollBehavior = scrollBehavior,
                state = listState,
            ) {
                item {
                    SectionSmallTitle(stringResource(R.string.section_appearance))

                    // 外观模式 TabRow, 置于最上方 Card 外部
                    val themeItems = AppSettings.ThemeModes.baseOptions.map { stringResource(it.labelResId) }
                    TabRow(
                        tabs = themeItems,
                        selectedTabIndex = asBundle.themeBaseIndex.coerceIn(0, themeItems.lastIndex),
                        onTabSelected = { index ->
                            asBundle = asBundle.copy(themeBaseIndex = index)
                        },
                    )

                    Spacer(modifier = Modifier.height(UiSpacing.ContentVertical))

                    Card {
                        SwitchPreference(
                            title = stringResource(R.string.pref_title_monet),
                            summary = stringResource(R.string.pref_summary_monet),
                            checked = asBundle.monet,
                            onCheckedChange = {
                                asBundle = asBundle.copy(monet = it)
                            },
                        )
                        AnimatedVisibility(asBundle.monet) {
                            OverlayDropdownPreference(
                                title = stringResource(R.string.pref_title_monet_key_color),
                                summary = stringResource(R.string.pref_summary_monet_key_color),
                                items = monetKeyColorOptions,
                                selectedIndex = asBundle.monetSeedIndex.coerceIn(0, monetKeyColorOptions.lastIndex),
                                onSelectedIndexChange = { idx ->
                                    asBundle = asBundle.copy(monetSeedIndex = idx)
                                },
                            )
                        }
                        AnimatedVisibility(asBundle.monet && asBundle.monetSeedIndex > 0) {
                            Column {
                                OverlayDropdownPreference(
                                    title = stringResource(R.string.pref_title_monet_palette_style),
                                    summary = stringResource(R.string.pref_summary_monet_palette_style),
                                    items = monetPaletteStyleOptions,
                                    selectedIndex = asBundle.monetPaletteStyle.coerceIn(
                                        0,
                                        monetPaletteStyleOptions.lastIndex,
                                    ),
                                    onSelectedIndexChange = { idx ->
                                        asBundle = asBundle.copy(monetPaletteStyle = idx)
                                    },
                                )
                                OverlayDropdownPreference(
                                    title = stringResource(R.string.pref_title_monet_color_spec),
                                    summary = stringResource(R.string.pref_summary_monet_color_spec),
                                    items = monetColorSpecOptions,
                                    selectedIndex = asBundle.monetColorSpec.coerceIn(
                                        0,
                                        monetColorSpecOptions.lastIndex,
                                    ),
                                    onSelectedIndexChange = { idx ->
                                        asBundle = asBundle.copy(monetColorSpec = idx)
                                    },
                                )
                            }
                        }
                        SwitchPreference(
                            title = stringResource(R.string.pref_title_squircle),
                            summary = stringResource(R.string.pref_summary_squircle),
                            checked = asBundle.squircle,
                            onCheckedChange = {
                                asBundle = asBundle.copy(squircle = it)
                            },
                        )
                        OverlayDropdownPreference(
                            title = stringResource(R.string.pref_title_blur),
                            summary = stringResource(R.string.pref_summary_blur),
                            items = blurModeLabels.map { stringResource(it) },
                            selectedIndex = asBundle.blur.coerceIn(0, blurModeLabels.lastIndex),
                            onSelectedIndexChange = { idx ->
                                asBundle = asBundle.copy(blur = idx)
                            },
                        )
                        // 悬浮底栏依赖 InteractiveHighlight, 其内部构造 android.graphics.RuntimeShader (API 33+ 引入),
                        // 低版本不显示该开关; 组合处的版本兜底在 MainScreen
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            SwitchPreference(
                                title = stringResource(R.string.pref_title_floating_bottom_bar),
                                summary = stringResource(R.string.pref_summary_floating_bottom_bar),
                                checked = asBundle.floatingBottomBar,
                                onCheckedChange = {
                                    asBundle = asBundle.copy(floatingBottomBar = it)
                                },
                            )
                            AnimatedVisibility(
                                asBundle.floatingBottomBar && asBundle.blur != AppSettings.BlurMode.NONE,
                            ) {
                                SwitchPreference(
                                    title = stringResource(R.string.pref_title_liquid_glass),
                                    summary = stringResource(R.string.pref_summary_liquid_glass),
                                    checked = asBundle.floatingBottomBar &&
                                            asBundle.blur != AppSettings.BlurMode.NONE &&
                                            asBundle.floatingBottomBarBlur,
                                    onCheckedChange = {
                                        asBundle = asBundle.copy(floatingBottomBarBlur = it)
                                    },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(UiSpacing.ContentVertical))

                    SectionSmallTitle(stringResource(R.string.section_navigation))
                    Card {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.pref_title_transition_style),
                            summary = stringResource(R.string.pref_summary_transition_style),
                            items = navTransitionStyleOptions.map { stringResource(it) },
                            selectedIndex = asBundle.navTransitionStyle.coerceIn(
                                0,
                                navTransitionStyleOptions.lastIndex,
                            ),
                            onSelectedIndexChange = { idx ->
                                asBundle = asBundle.copy(navTransitionStyle = idx)
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.pref_title_enable_swipe_back),
                            summary = stringResource(R.string.pref_summary_enable_swipe_back),
                            checked = asBundle.swipeBack,
                            onCheckedChange = {
                                asBundle = asBundle.copy(swipeBack = it)
                            },
                        )
                    }
                }
            }
        }
    }
}
