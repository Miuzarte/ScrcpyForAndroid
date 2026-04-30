package io.github.miuzarte.scrcpyforandroid

import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import io.github.miuzarte.scrcpyforandroid.pages.MainScreen
import io.github.miuzarte.scrcpyforandroid.password.BiometricGate
import io.github.miuzarte.scrcpyforandroid.password.PasswordRepository
import io.github.miuzarte.scrcpyforandroid.password.hasAuthenticatedOrigin
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.AppScreenOn
import kotlinx.coroutines.runBlocking

// 生物认证需要 FragmentActivity
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyMainOrientationPolicy()

        // no logEvent before context init
        AppRuntime.init(applicationContext)
        AppScreenOn.register(window)

        runBlocking {
            PasswordRepository.refresh()
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

    private companion object {
        private const val PHONE_LANDSCAPE_LOCK_ASPECT_RATIO = 16f / 9f
    }
}
