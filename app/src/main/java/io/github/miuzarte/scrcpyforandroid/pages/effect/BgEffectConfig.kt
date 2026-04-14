package io.github.miuzarte.scrcpyforandroid.pages.effect

internal enum class DeviceType {
    PHONE,
    PAD,
}

internal object BgEffectConfig {

    internal class Config(
        val points: FloatArray,
        val colors1: FloatArray,
        val colors2: FloatArray,
        val colors3: FloatArray,
        val colorInterpPeriod: Float,
        val lightOffset: Float,
        val saturateOffset: Float,
        val pointOffset: Float,
        // OS3 specific
        val shadowColorMulti: Float = 0.3f,
        val shadowColorOffset: Float = 0.3f,
        val shadowNoiseScale: Float = 5.0f,
    )

    internal fun get(
        deviceType: DeviceType,
        isDark: Boolean,
        isOs3: Boolean,
    ): Config {
        return if (isOs3) {
            getOs3Config(deviceType, isDark)
        } else {
            getOs2Config(deviceType, isDark)
        }
    }
}
