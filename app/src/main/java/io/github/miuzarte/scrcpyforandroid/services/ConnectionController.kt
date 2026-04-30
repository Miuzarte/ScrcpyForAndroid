package io.github.miuzarte.scrcpyforandroid.services

import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyOptions

internal data class ConnectionDisconnectResult(
    val clearedTarget: ConnectionTarget?,
)

internal data class ConnectedAdbResult(
    val target: ConnectionTarget,
    val info: ConnectedDeviceInfo,
    val scrcpyProfileId: String,
)

internal data class StopScrcpyResult(
    val disconnectedAdb: Boolean,
    val clearedTarget: ConnectionTarget?,
)

internal class ConnectionController(
    private val scrcpy: Scrcpy,
    private val stateStore: ConnectionStateStore,
    private val adbCoordinator: DeviceAdbConnectionCoordinator = DeviceAdbConnectionCoordinator(),
) {
    val state: ConnectionState
        get() = stateStore.state.value

    suspend fun disconnectAdbConnection(
        clearQuickOnlineForTarget: ConnectionTarget? = state.adbSession.currentTarget,
        cause: DisconnectCause = DisconnectCause.Unknown,
        statusLine: String = "Disconnected",
    ): ConnectionDisconnectResult {
        stateStore.markDisconnected(cause = cause, statusLine = statusLine)
        AppRuntime.currentConnectionTarget = null
        AppRuntime.currentConnectedDevice = null
        runCatching { scrcpy.stop() }
        runCatching { adbCoordinator.disconnect() }
        AppScreenOn.release()
        return ConnectionDisconnectResult(clearedTarget = clearQuickOnlineForTarget)
    }

    suspend fun disconnectCurrentTargetBeforeConnecting(
        newHost: String,
        newPort: Int,
    ): ConnectionTarget? {
        val current = state.adbSession.currentTarget ?: return null
        if (!state.adbSession.isConnected) return null
        if (current.host == newHost && current.port == newPort) return null

        disconnectAdbConnection(
            clearQuickOnlineForTarget = current,
            cause = DisconnectCause.SwitchTarget,
        )
        return current
    }

    fun applyConnectedDeviceCapabilities(sdkInt: Int) {
        stateStore.updateSession {
            it.copy(
                audioForwardingSupported = sdkInt !in 0..<30,
                cameraMirroringSupported = sdkInt !in 0..<31,
            )
        }
    }

    suspend fun connectWithTimeout(host: String, port: Int, timeoutMs: Long) {
        adbCoordinator.connectWithTimeout(host, port, timeoutMs)
    }

    suspend fun keepAliveCheck(timeoutMs: Long): Boolean {
        return adbCoordinator.isConnected(timeoutMs)
    }

    suspend fun probeTcpReachable(host: String, port: Int, timeoutMs: Int): Boolean {
        return adbCoordinator.probeTcpReachable(host, port, timeoutMs)
    }

    suspend fun runAutoAdbConnect(
        host: String,
        port: Int,
        timeoutMs: Long,
    ): Boolean {
        return runCatching {
            connectWithTimeout(host, port, timeoutMs)
            true
        }.getOrElse { error ->
            stateStore.update {
                it.copy(
                    disconnectCause = DisconnectCause.AutoReconnectFailed,
                    lastError = error.message,
                )
            }
            false
        }
    }

    suspend fun handleAdbConnected(
        host: String,
        port: Int,
        scrcpyProfileId: String = ScrcpyOptions.GLOBAL_PROFILE_ID,
    ): ConnectedAdbResult {
        val target = ConnectionTarget(host, port)
        stateStore.markConnected(target = target, scrcpyProfileId = scrcpyProfileId)
        AppRuntime.currentConnectionTarget = target

        val info = adbCoordinator.fetchConnectedDeviceInfo(host, port)
        stateStore.updateSession {
            it.copy(connectedDeviceLabel = info.model)
        }
        AppRuntime.currentConnectedDevice = info
        applyConnectedDeviceCapabilities(info.sdkInt)
        return ConnectedAdbResult(
            target = target,
            info = info,
            scrcpyProfileId = scrcpyProfileId,
        )
    }

    fun syncConnectedScrcpyProfileId(profileId: String) {
        stateStore.updateSession {
            it.copy(connectedScrcpyProfileId = profileId)
        }
    }

    fun updateQuickConnected(quickConnected: Boolean) {
        stateStore.updateSession {
            it.copy(isQuickConnected = quickConnected)
        }
    }

    fun updateStatusLine(statusLine: String) {
        stateStore.updateSession {
            it.copy(statusLine = statusLine)
        }
    }

    fun markConnectionFailed(error: Throwable) {
        stateStore.markConnectionFailed(error.message)
    }

    fun markKeepAliveReconnectSuccess(host: String, port: Int) {
        stateStore.update {
            it.copy(
                adbSession = it.adbSession.copy(
                    isConnected = true,
                    statusLine = "$host:$port",
                ),
                disconnectCause = null,
                lastError = null,
            )
        }
    }

    fun markScrcpyStarted() {
        stateStore.updateSession {
            it.copy(statusLine = "scrcpy running")
        }
    }

    suspend fun stopScrcpySession(killAdbOnClose: Boolean): StopScrcpyResult {
        scrcpy.stop()
        if (killAdbOnClose) {
            val disconnected = disconnectAdbConnection(
                clearQuickOnlineForTarget = state.adbSession.currentTarget,
                cause = DisconnectCause.KillAdbOnClose,
            )
            return StopScrcpyResult(
                disconnectedAdb = true,
                clearedTarget = disconnected.clearedTarget,
            )
        }

        AppScreenOn.release()
        val target = state.adbSession.currentTarget
        if (target != null) {
            updateStatusLine("${target.host}:${target.port}")
        }
        return StopScrcpyResult(
            disconnectedAdb = false,
            clearedTarget = null,
        )
    }
}
