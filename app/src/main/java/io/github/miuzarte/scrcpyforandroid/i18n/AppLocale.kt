package io.github.miuzarte.scrcpyforandroid.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/**
 * 应用内语言的唯一入口
 *
 * 分两条路径:
 * - API 33+: 交给系统的「按应用设定语言」, 由框架持久化并应用到全进程
 *   (Activity / Service / Application / 系统设置), 框架会下发 locale 配置变更并重建组件
 * - API 26-32: 自己持久化到 SharedPreferences, 由 [LocalizedActivity] 包装 base context 生效
 *
 * [SUPPORTED_TAGS] 必须与 res/xml/locales_config.xml 及 values-xx 资源目录一致
 * (`:app:preBuild` 的 verifyAppLocaleWiring 会校验)
 */
object AppLocale {
    /** 跟随系统 */
    const val FOLLOW_SYSTEM = ""

    /** 支持的应用内语言, 与 values/ (en) 和 values-zh 对应 */
    val SUPPORTED_TAGS = listOf("en", "zh")

    private const val PREFS = "locale_cache"
    private const val KEY_LANGUAGE_TAG = "language_tag"
    private const val KEY_FRAMEWORK_UNAVAILABLE = "framework_locale_unavailable"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun storedTag(context: Context) =
        prefs(context).getString(KEY_LANGUAGE_TAG, FOLLOW_SYSTEM) ?: FOLLOW_SYSTEM

    /**
     * API 33+ 可读的框架 per-app locale
     *
     * 返回 null 表示框架不可用 (低于 33, 或 OEM 实现异常), 此时回退到 SharedPreferences
     */
    private fun frameworkLocales(context: Context): LocaleList? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_FRAMEWORK_UNAVAILABLE, false)) return null
        return runCatching {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales
        }.getOrNull()
    }

    private fun localeListOf(tag: String) =
        if (tag.isEmpty()) LocaleList.getEmptyLocaleList()
        else LocaleList.forLanguageTags(tag)

    /**
     * 当前应用内语言, 空字符串表示跟随系统
     *
     * API 33+ 且框架可用时只认框架的值, 本地镜像仅作降级兜底, 避免覆盖用户在系统设置里的选择
     */
    fun currentTag(context: Context): String =
        frameworkLocales(context)?.toLanguageTags() ?: storedTag(context)

    /**
     * 设置应用内语言, 空字符串表示跟随系统
     */
    fun setTag(context: Context, tag: String) {
        // 始终写本地镜像: 供低于 33 使用, 也供框架异常时降级
        prefs(context).edit { putString(KEY_LANGUAGE_TAG, tag) }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        runCatching {
            context.getSystemService(LocaleManager::class.java)
                ?.setApplicationLocales(localeListOf(tag))
                ?: error("LocaleManager unavailable")
        }.onFailure {
            // 框架调用失败时固定走包装路径, 避免出现「设置了但不生效」的死状态
            prefs(context).edit { putBoolean(KEY_FRAMEWORK_UNAVAILABLE, true) }
        }
    }

    /**
     * 按应用内语言包装 context
     *
     * API 33+ 框架可用时原样返回: 框架已把 per-app locale 应用到该包的所有 context
     */
    fun localizedContext(context: Context): Context {
        if (frameworkLocales(context) != null) return context

        val tag = storedTag(context)
        if (tag.isEmpty()) return context

        return context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            },
        )
    }
}
