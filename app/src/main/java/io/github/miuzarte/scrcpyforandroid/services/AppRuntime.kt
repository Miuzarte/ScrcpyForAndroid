package io.github.miuzarte.scrcpyforandroid.services

import android.content.Context
import androidx.annotation.StringRes
import io.github.miuzarte.scrcpyforandroid.i18n.AppLocale
import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.nativecore.AdbMdnsDiscoverer
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
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
        private set
    var currentConnectionTarget: ConnectionTarget? = null
    var currentConnectedDevice: ConnectedDeviceInfo? = null

    // 当前设备使用的 profile ID (session 级, 脱离快捷设备独立运作)
    val currentConnectionProfileId = MutableStateFlow("global")

    private val sessionLock = Any()
    private var sessionServices: DeviceConnectionServices? = null

    /**
     * 进程级会话: [Scrcpy] 与连接服务只创建一次, Activity 重建 (切语言 / 深浅色 / 系统回收) 时复用
     *
     * 配置变化不要重建实例, 改 [Scrcpy.sessionConfig] 即可, 下一次 start() 生效
     */
    internal fun obtainSession(sessionConfig: Scrcpy.SessionConfig): Session {
        synchronized(sessionLock) {
            val existingScrcpy = scrcpy
            val existingServices = sessionServices
            if (existingScrcpy != null && existingServices != null) {
                return Session(existingScrcpy, existingServices)
            }

            val createdScrcpy = Scrcpy(
                appContext = appContext,
                initialSessionConfig = sessionConfig,
            )
            val adbCoordinator = DeviceAdbConnectionCoordinator()
            val connectionStateStore = ConnectionStateStore()
            val connectionController = ConnectionController(
                scrcpy = createdScrcpy,
                stateStore = connectionStateStore,
                adbCoordinator = adbCoordinator,
            )
            val autoReconnectManager = DeviceAdbAutoReconnectManager(
                controller = connectionController,
                stateStore = connectionStateStore,
            )
            val services = DeviceConnectionServices(
                adbCoordinator = adbCoordinator,
                connectionStateStore = connectionStateStore,
                connectionController = connectionController,
                autoReconnectManager = autoReconnectManager,
            )
            scrcpy = createdScrcpy
            sessionServices = services
            return Session(createdScrcpy, services)
        }
    }

    /**
     * 收尾会话
     *
     * 只应在 MainActivity 真正退出 (isFinishing) 时调用;
     * Activity 重建 (配置变更) 不能走这里, 否则连接状态与自动重连会被重置
     */
    internal fun releaseSession() {
        synchronized(sessionLock) {
            sessionServices?.autoReconnectManager?.close()
            AppScreenOn.release()
            sessionServices = null
            scrcpy = null
        }
    }

    internal class Session(
        val scrcpy: Scrcpy,
        val services: DeviceConnectionServices,
    )

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

    // application context 不经过 Activity 的 base context,
    // 这里按当前应用内语言包装, 使 snackbar 等全局文案跟随应用内语言
    fun stringResource(@StringRes resId: Int) = AppLocale.localizedContext(appContext).getString(resId)
    fun stringResource(@StringRes resId: Int, vararg args: Any) =
        AppLocale.localizedContext(appContext).getString(resId, *args)
}
