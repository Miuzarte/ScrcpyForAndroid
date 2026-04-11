package io.github.miuzarte.scrcpyforandroid.pages

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import io.github.miuzarte.scrcpyforandroid.BuildConfig
import io.github.miuzarte.scrcpyforandroid.services.AppUpdateChecker
import kotlinx.coroutines.flow.onEach
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.RuntimeShader
import top.yukonga.miuix.kmp.blur.asBrush
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.shapes.SmoothRoundedCornerShape
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode

@Composable
internal fun AboutScreen() {
    val navigator = LocalRootNavigator.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    var logoHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        AppUpdateChecker.ensureChecked(BuildConfig.VERSION_NAME)
    }

    val scrollProgress by remember {
        derivedStateOf {
            if (logoHeightPx <= 0) {
                0f
            } else {
                val index = lazyListState.firstVisibleItemIndex
                val offset = lazyListState.firstVisibleItemScrollOffset
                if (index > 0) 1f else (offset.toFloat() / logoHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "关于",
                scrollBehavior = topAppBarScrollBehavior,
                color = colorScheme.surface.copy(alpha = if (scrollProgress == 1f) 1f else 0f),
                titleColor = colorScheme.onSurface.copy(alpha = scrollProgress),
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    IconButton(onClick = navigator.pop) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        AboutContent(
            padding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding(),
            ),
            lazyListState = lazyListState,
            scrollProgress = scrollProgress,
            onLogoHeightChanged = { logoHeightPx = it },
        )
    }
}

@Composable
private fun AboutContent(
    padding: PaddingValues,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    scrollProgress: Float,
    onLogoHeightChanged: (Int) -> Unit,
) {
    val context = LocalContext.current
    val updateState by AppUpdateChecker.state.collectAsState()
    val backdrop = rememberLayerBackdrop()
    val blurEnabled = remember { isRenderEffectSupported() }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val cardBlendColors = remember(isDark) { aboutCardBlendToken(isDark) }
    var logoAreaY by remember { mutableFloatStateOf(0f) }
    var projectNameY by remember { mutableFloatStateOf(0f) }
    var versionCodeY by remember { mutableFloatStateOf(0f) }
    var projectNameProgress by remember { mutableFloatStateOf(0f) }
    var versionCodeProgress by remember { mutableFloatStateOf(0f) }
    var initialLogoAreaY by remember { mutableFloatStateOf(0f) }

    val (releaseStatusText, releasesUrl) = remember(updateState) {
        when (val state = updateState) {
            AppUpdateChecker.State.Idle -> null to AppUpdateChecker.RELEASES_URL
            AppUpdateChecker.State.Checking -> "..." to AppUpdateChecker.RELEASES_URL
            is AppUpdateChecker.State.Ready -> state.release.latestVersion to state.release.htmlUrl
            is AppUpdateChecker.State.Error -> "错误" to AppUpdateChecker.RELEASES_URL
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .onEach { offset ->
                if (lazyListState.firstVisibleItemIndex > 0) {
                    projectNameProgress = 1f
                    versionCodeProgress = 1f
                    return@onEach
                }
                if (initialLogoAreaY == 0f && logoAreaY > 0f) {
                    initialLogoAreaY = logoAreaY
                }
                val refLogoAreaY = if (initialLogoAreaY > 0f) initialLogoAreaY else logoAreaY
                val stage1TotalLength = refLogoAreaY - versionCodeY
                val stage2TotalLength = versionCodeY - projectNameY
                val versionCodeDelay = stage1TotalLength * 0.5f
                versionCodeProgress =
                    ((offset.toFloat() - versionCodeDelay) / (stage1TotalLength - versionCodeDelay).coerceAtLeast(
                        1f
                    ))
                        .coerceIn(0f, 1f)
                projectNameProgress =
                    ((offset.toFloat() - stage1TotalLength) / stage2TotalLength.coerceAtLeast(1f))
                        .coerceIn(0f, 1f)
            }
            .collect {}
    }

    BgEffectBackground(
        dynamicBackground = true,
        modifier = Modifier.fillMaxSize(),
        bgModifier = Modifier.layerBackdrop(backdrop),
        effectBackground = true,
        alpha = { 1f - scrollProgress },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = padding.calculateTopPadding() + 92.dp,
                    start = 24.dp,
                    end = 24.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current
                val textMeasurer = rememberTextMeasurer()
                val baseTitleFontSize = 35.sp
                val titleLayout = remember(textMeasurer) {
                    textMeasurer.measure(
                        text = "Scrcpy for Android",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = baseTitleFontSize,
                        ),
                        softWrap = false,
                    )
                }
                val titleFontSize = with(density) {
                    val availableWidthPx = maxWidth.roundToPx().toFloat()
                    val measuredWidthPx = titleLayout.size.width.toFloat().coerceAtLeast(1f)
                    val scale = (availableWidthPx / measuredWidthPx).coerceAtMost(1f)
                    (baseTitleFontSize.value * scale).coerceAtLeast(24f).sp
                }
                Text(
                    text = "Scrcpy for Android",
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 5.dp)
                        .onGloballyPositioned { coordinates ->
                            if (projectNameY != 0f) return@onGloballyPositioned
                            val y = coordinates.positionInWindow().y
                            val size = coordinates.size
                            projectNameY = y + size.height
                        }
                        .graphicsLayer {
                            alpha = 1f - projectNameProgress
                            scaleX = 1f - (projectNameProgress * 0.05f)
                            scaleY = 1f - (projectNameProgress * 0.05f)
                        }
                        .textureBlur(
                            backdrop = backdrop,
                            shape = SmoothRoundedCornerShape(16.dp),
                            blurRadius = 150f,
                            noiseCoefficient = BlurDefaults.NoiseCoefficient,
                            colors = BlurColors(
                                blendColors = heroBlendColors(isDark),
                            ),
                            contentBlendMode = ComposeBlendMode.DstIn,
                            enabled = blurEnabled,
                        ),
                    color = MiuixTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = titleFontSize,
                )
            }
            Text(
                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 1f - versionCodeProgress
                        scaleX = 1f - (versionCodeProgress * 0.05f)
                        scaleY = 1f - (versionCodeProgress * 0.05f)
                    }
                    .onGloballyPositioned { coordinates ->
                        if (versionCodeY != 0f) return@onGloballyPositioned
                        val y = coordinates.positionInWindow().y
                        val size = coordinates.size
                        versionCodeY = y + size.height
                    },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = AppUpdateChecker.REPO_URL.removePrefix("https://"),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 1f - versionCodeProgress
                        scaleX = 1f - (versionCodeProgress * 0.05f)
                        scaleY = 1f - (versionCodeProgress * 0.05f)
                    }
                    .onGloballyPositioned { coordinates ->
                        if (versionCodeY != 0f) return@onGloballyPositioned
                        val y = coordinates.positionInWindow().y
                        val size = coordinates.size
                        versionCodeY = y + size.height
                    },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "logoSpacer") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .padding(top = 36.dp)
                        .onSizeChanged { onLogoHeightChanged(it.height) }
                        .onGloballyPositioned { coordinates ->
                            val y = coordinates.positionInWindow().y
                            val size = coordinates.size
                            logoAreaY = y + size.height
                        },
                    contentAlignment = Alignment.TopCenter,
                ) {}
            }
            item(key = "about") {
                Column(
                    modifier = Modifier
                        .fillParentMaxHeight()
                        .padding(bottom = padding.calculateBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AboutCard(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        backdrop = backdrop,
                        blurEnabled = blurEnabled,
                        blendColors = cardBlendColors,
                    ) {
                        ArrowPreference(
                            title = "项目仓库",
                            endActions = {
                                Text(
                                    text = "GitHub",
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = colorScheme.onSurfaceVariantActions,
                                )
                            },
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, AppUpdateChecker.REPO_URL.toUri())
                                )
                            },
                        )
                        ArrowPreference(
                            title = "版本发布",
                            endActions = {
                                releaseStatusText?.let {
                                    Text(
                                        text = it,
                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        color = colorScheme.onSurfaceVariantActions,
                                    )
                                }
                            },
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, releasesUrl.toUri())
                                )
                            },
                        )
                    }
                    AboutCard(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        backdrop = backdrop,
                        blurEnabled = blurEnabled,
                        blendColors = cardBlendColors,
                    ) {
                        ArrowPreference(
                            title = "License",
                            endActions = {
                                Text(
                                    text = "Apache-2.0",
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = colorScheme.onSurfaceVariantActions,
                                )
                            },
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        "https://www.apache.org/licenses/LICENSE-2.0.txt".toUri()
                                    )
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutCard(
    modifier: Modifier,
    backdrop: LayerBackdrop,
    blurEnabled: Boolean,
    blendColors: List<BlendColorEntry>,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.textureBlur(
            backdrop = backdrop,
            shape = SmoothRoundedCornerShape(16.dp),
            blurRadius = 60f,
            noiseCoefficient = BlurDefaults.NoiseCoefficient,
            colors = BlurColors(blendColors = blendColors),
            enabled = blurEnabled,
        ),
        colors = CardDefaults.defaultColors(
            if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
            Color.Transparent,
        ),
    ) {
        content()
    }
}

@Composable
private fun heroBlendColors(isDark: Boolean): List<BlendColorEntry> =
    remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xFF1AF500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xCC4A4A4A), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xFF4F4F4F), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xFF1AF200), BlurBlendMode.Lab),
            )
        }
    }

private fun aboutCardBlendToken(isDark: Boolean): List<BlendColorEntry> =
    if (isDark) {
        listOf(
            BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
            BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
        )
    } else {
        listOf(
            BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
            BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
        )
    }

@Composable
private inline fun BgEffectBackground(
    dynamicBackground: Boolean,
    modifier: Modifier = Modifier,
    bgModifier: Modifier = Modifier,
    effectBackground: Boolean = true,
    crossinline alpha: () -> Float = { 1f },
    content: @Composable BoxScope.() -> Unit,
) {
    val shaderSupported = remember { isRuntimeShaderSupported() }
    if (!shaderSupported) {
        Box(modifier = modifier, content = content)
        return
    }
    Box(modifier = modifier) {
        val surface = colorScheme.surface
        val painter = remember { BgEffectPainter() }
        val animTime = rememberFrameTimeSeconds(dynamicBackground)
        val isDark = colorScheme.background.luminance() < 0.5f

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(bgModifier),
        ) {
            drawRect(surface)
            if (effectBackground) {
                val drawHeight = size.height * 0.78f
                painter.updateResolution(size.width, size.height)
                painter.updatePresetIfNeeded(drawHeight, size.height, size.width, isDark)
                painter.updateAnimTime(animTime())
                drawRect(painter.runtimeShader.asBrush(), alpha = alpha())
            }
        }
        content()
    }
}

@Composable
private fun rememberFrameTimeSeconds(
    playing: Boolean = true,
): () -> Float {
    var time by remember { mutableFloatStateOf(0f) }
    var startOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playing) {
        if (!playing) {
            startOffset = time
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (playing) {
            val now = withFrameNanos { it }
            time = startOffset + (now - start) / 1_000_000_000f
        }
    }
    return { time }
}

// @RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class BgEffectPainter {
    val runtimeShader by lazy {
        RuntimeShader(OS2_BG_FRAG).also { initStaticUniforms(it) }
    }
    private val resolution = FloatArray(2)
    private val bound = FloatArray(4)
    private var animTime = Float.NaN
    private var isDarkCached: Boolean? = null
    private var presetApplied = false

    private fun initStaticUniforms(shader: RuntimeShader) {
        shader.setFloatUniform("uTranslateY", 0f)
        shader.setFloatUniform("uNoiseScale", 1.5f)
        shader.setFloatUniform("uPointOffset", 0.1f)
        shader.setFloatUniform("uPointRadiusMulti", 1f)
        shader.setFloatUniform("uAlphaMulti", 1f)
    }

    fun updateResolution(width: Float, height: Float) {
        if (resolution[0] == width && resolution[1] == height) return
        resolution[0] = width
        resolution[1] = height
        runtimeShader.setFloatUniform("uResolution", resolution)
    }

    fun updateAnimTime(time: Float) {
        if (animTime == time) return
        animTime = time
        runtimeShader.setFloatUniform("uAnimTime", animTime)
    }

    fun updatePresetIfNeeded(
        logoHeight: Float,
        height: Float,
        width: Float,
        isDark: Boolean,
    ) {
        if (presetApplied && isDarkCached == isDark) return
        updateBound(logoHeight, height, width)
        val preset = if (isDark) PHONE_DARK else PHONE_LIGHT
        runtimeShader.setFloatUniform("uPoints", preset.points)
        runtimeShader.setFloatUniform("uColors", preset.colors)
        runtimeShader.setFloatUniform("uLightOffset", preset.lightOffset)
        runtimeShader.setFloatUniform("uSaturateOffset", preset.saturateOffset)
        runtimeShader.setFloatUniform("uBound", bound)
        isDarkCached = isDark
        presetApplied = true
    }

    private fun updateBound(
        logoHeight: Float,
        totalHeight: Float,
        totalWidth: Float,
    ) {
        val heightRatio = logoHeight / totalHeight
        if (totalWidth <= totalHeight) {
            bound[0] = 0f
            bound[1] = 1f - heightRatio
            bound[2] = 1f
            bound[3] = heightRatio
        } else {
            val aspectRatio = totalWidth / totalHeight
            val contentCenterY = 1f - heightRatio / 2f
            bound[0] = 0f
            bound[1] = contentCenterY - aspectRatio / 2f
            bound[2] = 1f
            bound[3] = aspectRatio
        }
    }

    private data class BgEffectPreset(
        val points: FloatArray,
        val colors: FloatArray,
        val lightOffset: Float,
        val saturateOffset: Float,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as BgEffectPreset

            if (lightOffset != other.lightOffset) return false
            if (saturateOffset != other.saturateOffset) return false
            if (!points.contentEquals(other.points)) return false
            if (!colors.contentEquals(other.colors)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = lightOffset.hashCode()
            result = 31 * result + saturateOffset.hashCode()
            result = 31 * result + points.contentHashCode()
            result = 31 * result + colors.contentHashCode()
            return result
        }
    }

    private companion object {
        val PHONE_LIGHT = BgEffectPreset(
            points = floatArrayOf(
                0.67f, 0.42f, 1.0f, 0.69f, 0.75f, 1.0f,
                0.14f, 0.71f, 0.95f, 0.14f, 0.27f, 0.8f,
            ),
            colors = floatArrayOf(
                0.57f, 0.76f, 0.98f, 1.0f,
                0.98f, 0.85f, 0.68f, 1.0f,
                0.98f, 0.75f, 0.93f, 1.0f,
                0.73f, 0.70f, 0.98f, 1.0f,
            ),
            lightOffset = 0.1f,
            saturateOffset = 0.2f,
        )
        val PHONE_DARK = BgEffectPreset(
            points = floatArrayOf(
                0.63f, 0.50f, 0.88f, 0.69f, 0.75f, 0.80f,
                0.17f, 0.66f, 0.81f, 0.14f, 0.24f, 0.72f,
            ),
            colors = floatArrayOf(
                0.0f, 0.31f, 0.58f, 1.0f,
                0.53f, 0.29f, 0.15f, 1.0f,
                0.46f, 0.06f, 0.27f, 1.0f,
                0.16f, 0.12f, 0.45f, 1.0f,
            ),
            lightOffset = -0.1f,
            saturateOffset = 0.2f,
        )
    }
}

private const val OS2_BG_FRAG = """
    uniform vec2 uResolution;
    uniform shader uTex;
    uniform float uAnimTime;
    uniform vec4 uBound;
    uniform float uTranslateY;
    uniform vec3 uPoints[4];
    uniform vec4 uColors[4];
    uniform float uAlphaMulti;
    uniform float uNoiseScale;
    uniform float uPointOffset;
    uniform float uPointRadiusMulti;
    uniform float uSaturateOffset;
    uniform float uLightOffset;

    vec3 rgb2hsv(vec3 c)
    {
        vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
        vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
        vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
        float d = q.x - min(q.w, q.y);
        float e = 1.0e-10;
        return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
    }

    vec3 hsv2rgb(vec3 c)
    {
        vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
        vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
        return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
    }

    float hash(vec2 p) {
        vec3 p3 = fract(vec3(p.xyx) * 0.13);
        p3 += dot(p3, p3.yzx + 3.333);
        return fract((p3.x + p3.y) * p3.z);
    }

    float perlin(vec2 x) {
        vec2 i = floor(x);
        vec2 f = fract(x);
        float a = hash(i);
        float b = hash(i + vec2(1.0, 0.0));
        float c = hash(i + vec2(0.0, 1.0));
        float d = hash(i + vec2(1.0, 1.0));
        vec2 u = f * f * (3.0 - 2.0 * f);
        return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
    }

    float gradientNoise(in vec2 uv)
    {
        return fract(52.9829189 * fract(dot(uv, vec2(0.06711056, 0.00583715))));
    }

    vec4 main(vec2 fragCoord){
        vec2 vUv = fragCoord/uResolution;
        vUv.y = 1.0-vUv.y;
        vec2 uv = vUv;
        uv -= vec2(0., uTranslateY);
        uv.xy -= uBound.xy;
        uv.xy /= uBound.zw;
        vec3 hsv;
        vec4 color = vec4(0.0);
        float noiseValue = perlin(vUv * uNoiseScale + vec2(-uAnimTime, -uAnimTime));

        for (int i = 0; i < 4; i++){
            vec4 pointColor = uColors[i];
            pointColor.rgb *= pointColor.a;
            vec2 point = uPoints[i].xy;
            float rad = uPoints[i].z * uPointRadiusMulti;
            point.x += sin(uAnimTime + point.y) * uPointOffset;
            point.y += cos(uAnimTime + point.x) * uPointOffset;
            float d = distance(uv, point);
            float pct = smoothstep(rad, 0., d);
            color.rgb = mix(color.rgb, pointColor.rgb, pct);
            color.a = mix(color.a, pointColor.a, pct);
        }

        float oppositeNoise = smoothstep(0., 1., noiseValue);
        if (color.a > 0.001) {
            color.rgb /= color.a;
        } else {
            color.rgb = vec3(0.0);
        }
        hsv = rgb2hsv(color.rgb);
        hsv.y = mix(hsv.y, 0.0, oppositeNoise * uSaturateOffset);
        color.rgb = hsv2rgb(hsv);
        color.rgb += oppositeNoise * uLightOffset;
        color.a = clamp(color.a, 0., 1.);
        color.a *= uAlphaMulti;

        vec4 uiColor = uTex.eval(vec2(vUv.x, 1.0 - vUv.y)*uResolution);
        vec4 fragColor;
        color.rgb += (10.0 / 255.0) * gradientNoise(fragCoord.xy) - (5.0 / 255.0);
        if (uiColor.a < 0.01) {
            fragColor = color;
        } else {
            fragColor = uiColor;
        }
        return vec4(fragColor.rgb*fragColor.a, fragColor.a);
    }
"""
