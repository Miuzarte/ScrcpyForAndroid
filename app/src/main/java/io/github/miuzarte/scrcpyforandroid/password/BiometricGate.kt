package io.github.miuzarte.scrcpyforandroid.password

import android.app.KeyguardManager
import android.content.Context
import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

object BiometricGate {

    private const val ALLOWED_AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

    private val sessionIds = AtomicLong(0L)

    fun canAuthenticate(): Boolean {
        return BiometricManager.from(AppRuntime.context)
            .canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isDeviceSecure(): Boolean {
        val manager = AppRuntime.context
            .getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return manager.isDeviceSecure
    }

    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "验证身份",
        subtitle: String = "确认后继续",
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val sessionId = sessionIds.incrementAndGet()
        var resumed = false
        lateinit var prompt: BiometricPrompt
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                prompt.cancelAuthentication()
                if (!resumed && continuation.isActive) {
                    resumed = true
                    continuation.resume(false)
                }
            }
        }
        activity.lifecycle.addObserver(observer)

        fun finish(result: Boolean) {
            if (resumed || !continuation.isActive) return
            if (sessionId != sessionIds.get()) return
            resumed = true
            activity.runOnMain { activity.lifecycle.removeObserver(observer) }
            continuation.resume(result)
        }

        prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    finish(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    finish(false)
                }

                override fun onAuthenticationFailed() = Unit
            }
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                .setTitle(title)
                .setSubtitle(subtitle)
                .build()
        )

        continuation.invokeOnCancellation {
            activity.runOnMain {
                activity.lifecycle.removeObserver(observer)
                prompt.cancelAuthentication()
            }
        }
    }

    private inline fun FragmentActivity.runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            runOnUiThread { block() }
        }
    }
}
