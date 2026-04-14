package io.github.miuzarte.scrcpyforandroid.pages.effect

private val OS2_PHONE_LIGHT_COLORS = floatArrayOf(
    0.57f, 0.76f, 0.98f, 1.0f,
    0.98f, 0.85f, 0.68f, 1.0f,
    0.98f, 0.75f, 0.93f, 1.0f,
    0.73f, 0.70f, 0.98f, 1.0f,
)

private val OS2_PHONE_LIGHT = BgEffectConfig.Config(
    points = floatArrayOf(
        0.67f,
        0.42f,
        1.0f,
        0.69f,
        0.75f,
        1.0f,
        0.14f,
        0.71f,
        0.95f,
        0.14f,
        0.27f,
        0.8f
    ),
    colors1 = OS2_PHONE_LIGHT_COLORS,
    colors2 = OS2_PHONE_LIGHT_COLORS,
    colors3 = OS2_PHONE_LIGHT_COLORS,
    colorInterpPeriod = 100f,
    lightOffset = 0.1f,
    saturateOffset = 0.2f,
    pointOffset = 0.1f,
)

private val OS2_PHONE_DARK_COLORS = floatArrayOf(
    0.0f, 0.31f, 0.58f, 1.0f,
    0.53f, 0.29f, 0.15f, 1.0f,
    0.46f, 0.06f, 0.27f, 1.0f,
    0.16f, 0.12f, 0.45f, 1.0f,
)

private val OS2_PHONE_DARK = BgEffectConfig.Config(
    points = floatArrayOf(
        0.63f,
        0.50f,
        0.88f,
        0.69f,
        0.75f,
        0.80f,
        0.17f,
        0.66f,
        0.81f,
        0.14f,
        0.24f,
        0.72f
    ),
    colors1 = OS2_PHONE_DARK_COLORS,
    colors2 = OS2_PHONE_DARK_COLORS,
    colors3 = OS2_PHONE_DARK_COLORS,
    colorInterpPeriod = 100f,
    lightOffset = -0.1f,
    saturateOffset = 0.2f,
    pointOffset = 0.1f,
)

private val OS2_PAD_LIGHT_COLORS = floatArrayOf(
    0.57f, 0.76f, 0.98f, 1.0f,
    0.98f, 0.85f, 0.68f, 1.0f,
    0.98f, 0.75f, 0.93f, 0.95f,
    0.73f, 0.70f, 0.98f, 0.90f,
)

private val OS2_PAD_LIGHT = BgEffectConfig.Config(
    points = floatArrayOf(
        0.67f,
        0.37f,
        0.88f,
        0.54f,
        0.66f,
        1.0f,
        0.37f,
        0.71f,
        0.68f,
        0.28f,
        0.26f,
        0.62f
    ),
    colors1 = OS2_PAD_LIGHT_COLORS,
    colors2 = OS2_PAD_LIGHT_COLORS,
    colors3 = OS2_PAD_LIGHT_COLORS,
    colorInterpPeriod = 100f,
    lightOffset = 0.1f,
    saturateOffset = 0f,
    pointOffset = 0.1f,
)

private val OS2_PAD_DARK_COLORS = floatArrayOf(
    0.0f, 0.31f, 0.58f, 1.0f,
    0.53f, 0.29f, 0.15f, 1.0f,
    0.46f, 0.06f, 0.27f, 1.0f,
    0.16f, 0.12f, 0.45f, 1.0f,
)

private val OS2_PAD_DARK = BgEffectConfig.Config(
    points = floatArrayOf(
        0.55f,
        0.42f,
        1.0f,
        0.56f,
        0.75f,
        1.0f,
        0.40f,
        0.59f,
        0.71f,
        0.43f,
        0.09f,
        0.75f
    ),
    colors1 = OS2_PAD_DARK_COLORS,
    colors2 = OS2_PAD_DARK_COLORS,
    colors3 = OS2_PAD_DARK_COLORS,
    colorInterpPeriod = 100f,
    lightOffset = -0.1f,
    saturateOffset = 0.2f,
    pointOffset = 0.1f,
)

internal fun getOs2Config(deviceType: DeviceType, isDark: Boolean): BgEffectConfig.Config {
    return when (deviceType) {
        DeviceType.PHONE -> if (isDark) OS2_PHONE_DARK else OS2_PHONE_LIGHT
        DeviceType.PAD -> if (isDark) OS2_PAD_DARK else OS2_PAD_LIGHT
    }
}
