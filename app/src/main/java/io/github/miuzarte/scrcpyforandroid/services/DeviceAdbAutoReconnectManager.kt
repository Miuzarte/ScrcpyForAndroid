package io.github.miuzarte.scrcpyforandroid.services

import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcut
import java.io.Closeable

internal class DeviceAdbAutoReconnectManager(
    private val controller: ConnectionController,
    private val stateStore: ConnectionStateStore,
    private val backgroundRunner: DeviceAdbBackgroundRunner = DeviceAdbBackgroundRunner(),
) : Closeable {
    suspend fun runKeepAliveLoop(
        isForeground: () -> Boolean,
        intervalMs: Long,
        connectTimeoutMs: Long,
        keepAliveTimeoutMs: Long,
        onReconnectSuccess: suspend (host: String, port: Int) -> Unit,
        onReconnectFailure: suspend (Throwable) -> Unit,
    ) {
        backgroundRunner.runKeepAliveLoop(
            sessionState = { stateStore.state.value.adbSession },
            isForeground = isForeground,
            intervalMs = intervalMs,
            keepAliveCheck = { _, _ -> controller.keepAliveCheck(keepAliveTimeoutMs) },
            reconnect = { host, port ->
                controller.connectWithTimeout(host, port, connectTimeoutMs)
            },
            onReconnectSuccess = { host, port ->
                controller.markKeepAliveReconnectSuccess(host, port)
                onReconnectSuccess(host, port)
            },
            onReconnectFailure = onReconnectFailure,
        )
    }

    suspend fun runAutoReconnectLoop(
        isForeground: () -> Boolean,
        isAutoReconnectEnabled: () -> Boolean,
        isBusy: () -> Boolean,
        isAdbConnecting: () -> Boolean,
        hasActiveSession: () -> Boolean,
        savedShortcuts: () -> List<DeviceShortcut>,
        isBlacklisted: (String) -> Boolean,
        connectTimeoutMs: Long,
        probeTimeoutMs: Int,
        discoverConnectService: suspend () -> Pair<String, Int>?,
        onMdnsPortChanged: suspend (host: String, oldPort: Int, newPort: Int) -> Unit,
        onKnownDeviceReconnected: suspend (DeviceShortcut) -> Unit,
        onDiscoveredDeviceReconnected: suspend (host: String, port: Int, knownDevice: DeviceShortcut) -> Unit,
        retryIntervalMs: Long,
    ) {
        backgroundRunner.runAutoReconnectLoop(
            isConnected = { stateStore.state.value.adbSession.isConnected },
            isForeground = isForeground,
            isAutoReconnectEnabled = {
                isAutoReconnectEnabled() &&
                        stateStore.state.value.disconnectCause != DisconnectCause.User &&
                        stateStore.state.value.disconnectCause != DisconnectCause.KillAdbOnClose
            },
            isBusy = isBusy,
            isAdbConnecting = isAdbConnecting,
            hasActiveSession = hasActiveSession,
            savedShortcuts = savedShortcuts,
            isBlacklisted = isBlacklisted,
            probeTcpReachable = { host, port ->
                controller.probeTcpReachable(host, port, probeTimeoutMs)
            },
            discoverConnectService = discoverConnectService,
            onMdnsPortChanged = onMdnsPortChanged,
            connectKnownShortcut = { target ->
                if (!controller.runAutoAdbConnect(target.host, target.port, connectTimeoutMs)) {
                    false
                } else {
                    controller.handleAdbConnected(
                        host = target.host,
                        port = target.port,
                        scrcpyProfileId = target.scrcpyProfileId,
                    )
                    onKnownDeviceReconnected(target)
                    true
                }
            },
            connectDiscoveredShortcut = { host, port, knownDevice ->
                if (!controller.runAutoAdbConnect(host, port, connectTimeoutMs)) {
                    false
                } else {
                    controller.handleAdbConnected(
                        host = host,
                        port = port,
                        scrcpyProfileId = knownDevice.scrcpyProfileId,
                    )
                    onDiscoveredDeviceReconnected(host, port, knownDevice)
                    true
                }
            },
            retryIntervalMs = retryIntervalMs,
        )
    }

    override fun close() {
        backgroundRunner.close()
    }
}
