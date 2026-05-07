package io.github.miuzarte.scrcpyforandroid

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import io.github.miuzarte.scrcpyforandroid.pages.MainScreen
import io.github.miuzarte.scrcpyforandroid.password.BiometricGate
import io.github.miuzarte.scrcpyforandroid.password.PasswordRepository
import io.github.miuzarte.scrcpyforandroid.password.hasAuthenticatedOrigin
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.AppScreenOn
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import kotlinx.coroutines.runBlocking
import java.util.Locale

// 生物认证需要 FragmentActivity
class MainActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val languageTag = getAppLanguageTag(newBase)
        val wrappedContext =
            if (languageTag.isNotEmpty()) {
                val config = Configuration(newBase.resources.configuration)
                config.setLocale(Locale.forLanguageTag(languageTag))
                newBase.createConfigurationContext(config)
            } else {
                newBase
            }
        super.attachBaseContext(wrappedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyMainOrientationPolicy()

        // no logEvent before context init
        AppRuntime.init(applicationContext)
        AppScreenOn.register(window)

        runBlocking {
            PasswordRepository.refresh()
            val cached = getAppLanguageTag(applicationContext)
            if (cached.isNotEmpty()) {
                val bundle = appSettings.loadBundle()
                if (bundle.languageTag != cached) {
                    appSettings.updateBundle { it.copy(languageTag = cached) }
                }
            }
            // 认证不可用时, 清除经认证创建的密码
            if (!BiometricGate.canAuthenticate()) {
                PasswordRepository.getAll()
                    .filter { it.createdWithAuth.hasAuthenticatedOrigin && it.cipherText != null }
                    .forEach { PasswordRepository.markInvalid(it.id) }
            }
        }

        enableEdgeToEdge()

        setContent {
            MainScreen()
        }
    }

    override fun onResume() {
        super.onResume()
        applyMainOrientationPolicy()
        StreamActivity.dismissActivePictureInPicture()
    }

    override fun onDestroy() {
        AppScreenOn.unregister(window)
        super.onDestroy()
    }

    private fun applyMainOrientationPolicy() {
        val aspectRatio = currentDisplayAspectRatio()
        requestedOrientation =
            if (aspectRatio > PHONE_LANDSCAPE_LOCK_ASPECT_RATIO)
                ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            else
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun currentDisplayAspectRatio(): Float {
        val bounds =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                windowManager.maximumWindowMetrics.bounds
            else resources.displayMetrics.let { metrics ->
                Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
            }

        val width = bounds.width().coerceAtLeast(1)
        val height = bounds.height().coerceAtLeast(1)
        return maxOf(width, height).toFloat() / minOf(width, height).toFloat()
    }

    internal companion object {
        private const val PHONE_LANDSCAPE_LOCK_ASPECT_RATIO = 16f / 9f

        private const val LOCALE_PREFS = "locale_cache"
        private const val KEY_LANGUAGE_TAG = "language_tag"

        fun getAppLanguageTag(context: Context) =
            context.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
                .getString(KEY_LANGUAGE_TAG, "") ?: ""

        fun setAppLanguageTag(context: Context, languageTag: String) =
            context.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
                .edit { putString(KEY_LANGUAGE_TAG, languageTag) }
    }
}
