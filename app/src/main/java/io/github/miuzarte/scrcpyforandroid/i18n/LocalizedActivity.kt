package io.github.miuzarte.scrcpyforandroid.i18n

import android.content.Context
import androidx.fragment.app.FragmentActivity

/**
 * 所有 Activity 的基类, 统一在 attachBaseContext 应用应用内语言
 *
 * API 33+ 由系统按应用语言设置接管, [AppLocale.localizedContext] 会原样返回, 这里的包装是空操作
 * 新增 Activity 必须继承本类 (`:app:preBuild` 的 verifyAppLocaleWiring 会校验)
 */
abstract class LocalizedActivity: FragmentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.localizedContext(newBase))
    }
}
