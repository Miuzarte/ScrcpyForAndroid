package io.github.miuzarte.scrcpyforandroid.services

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import io.github.miuzarte.scrcpyforandroid.MainActivity
import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.nativecore.AdbMdnsDiscoverer
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult

// 用于不同 activity 之间传递实例
object AppRuntime {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        AdbMdnsDiscoverer.init(appContext)
    }

    val context: Context
        get() = appContext

    var scrcpy: Scrcpy? = null
    var currentConnectionTarget: ConnectionTarget? = null
    var currentConnectedDevice: ConnectedDeviceInfo? = null

    // 当前设备使用的 profile ID (session 级, 脱离快捷设备独立运作)
    val currentConnectionProfileId = MutableStateFlow("global")

    private val snackbarHostStateLock = Any()
    private val snackbarHostStateStack = mutableListOf<SnackbarHostState>()

    var snackbarHostState: SnackbarHostState?
        get() = synchronized(snackbarHostStateLock) {
            snackbarHostStateStack.lastOrNull()
        }
        set(value) {
            synchronized(snackbarHostStateLock) {
                snackbarHostStateStack.clear()
                if (value != null) snackbarHostStateStack.add(value)
            }
        }

    private val snackbarScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun registerSnackbarHostState(hostState: SnackbarHostState): () -> Unit {
        synchronized(snackbarHostStateLock) {
            snackbarHostStateStack.add(hostState)
        }
        return {
            synchronized(snackbarHostStateLock) {
                snackbarHostStateStack.remove(hostState)
            }
        }
    }

    suspend fun snackbarDismissNewest() = snackbarHostState?.newestSnackbarData()?.dismiss()

    fun snackbar(
        message: String,
        actionLabel: String? = null,
        withDismissAction: Boolean = true,
        duration: SnackbarDuration = SnackbarDuration.Short,
        onResult: ((SnackbarResult) -> Unit)? = null,
        dismissNewest: Boolean = false,
    ) = snackbarHostState?.let {
        snackbarScope.launch {
            if (dismissNewest) snackbarDismissNewest()
            it.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = withDismissAction,
                duration = duration,
            ).let { result -> onResult?.invoke(result) }
        }
    }

    fun snackbar(
        @StringRes messageResId: Int,
        @StringRes actionLabelResId: Int? = null,
        withDismissAction: Boolean = true,
        duration: SnackbarDuration = SnackbarDuration.Short,
        onResult: ((SnackbarResult) -> Unit)? = null,
        dismissNewest: Boolean = false,
    ) = snackbar(
        message = stringResource(messageResId),
        actionLabel = actionLabelResId?.let(::stringResource),
        withDismissAction = withDismissAction,
        duration = duration,
        onResult = onResult,
        dismissNewest = dismissNewest,
    )

    fun snackbar(
        @StringRes messageResId: Int,
        vararg args: Any,
        @StringRes actionLabelResId: Int? = null,
        withDismissAction: Boolean = true,
        duration: SnackbarDuration = SnackbarDuration.Short,
        onResult: ((SnackbarResult) -> Unit)? = null,
        dismissNewest: Boolean = false,
    ) = snackbar(
        message = stringResource(messageResId, *args),
        actionLabel = actionLabelResId?.let(::stringResource),
        withDismissAction = withDismissAction,
        duration = duration,
        onResult = onResult,
        dismissNewest = dismissNewest,
    )

    // 应用内语言设置只包裹了 Activity 的 base context,
    // 这里用同样的方式包装 application context, 使 snackbar 等全局文案跟随应用内语言
    private fun localizedContext(): Context {
        val languageTag = MainActivity.getAppLanguageTag(appContext)
        return if (languageTag.isEmpty()) appContext
        else appContext.createConfigurationContext(
            Configuration(appContext.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(languageTag))
            },
        )
    }

    fun stringResource(@StringRes resId: Int) = localizedContext().getString(resId)
    fun stringResource(@StringRes resId: Int, vararg args: Any) = localizedContext().getString(resId, *args)
}
