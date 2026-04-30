package io.github.miuzarte.scrcpyforandroid.services

import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class DisconnectCause {
    User,
    KeepAliveFailed,
    SwitchTarget,
    KillAdbOnClose,
    ConnectFailed,
    AutoReconnectFailed,
    Unknown,
}

internal data class ConnectionState(
    val adbSession: DeviceAdbSessionState = DeviceAdbSessionState(),
    val disconnectCause: DisconnectCause? = null,
    val lastError: String? = null,
)

internal class ConnectionStateStore {
    private val _state = MutableStateFlow(ConnectionState())
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    fun update(transform: (ConnectionState) -> ConnectionState) {
        _state.update(transform)
    }

    fun updateSession(transform: (DeviceAdbSessionState) -> DeviceAdbSessionState) {
        _state.update { current ->
            current.copy(adbSession = transform(current.adbSession))
        }
    }

    fun markConnected(
        target: ConnectionTarget,
        scrcpyProfileId: String = ScrcpyOptions.GLOBAL_PROFILE_ID,
    ) {
        update {
            it.copy(
                adbSession = it.adbSession.copy(
                    isConnected = true,
                    currentTarget = target,
                    connectedScrcpyProfileId = scrcpyProfileId,
                    statusLine = "${target.host}:${target.port}",
                ),
                disconnectCause = null,
                lastError = null,
            )
        }
    }

    fun markDisconnected(
        cause: DisconnectCause,
        statusLine: String = "Disconnected",
        connectedDeviceLabel: String = "Disconnected",
    ) {
        update {
            it.copy(
                adbSession = DeviceAdbSessionState(
                    statusLine = statusLine,
                    connectedDeviceLabel = connectedDeviceLabel,
                ),
                disconnectCause = cause,
                lastError = null,
            )
        }
    }

    fun markConnectionFailed(message: String?) {
        update {
            it.copy(
                adbSession = it.adbSession.copy(statusLine = "ADB connection failed"),
                disconnectCause = DisconnectCause.ConnectFailed,
                lastError = message,
            )
        }
    }
}
