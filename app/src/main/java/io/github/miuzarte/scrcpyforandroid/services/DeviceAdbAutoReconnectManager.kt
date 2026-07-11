package io.github.miuzarte.scrcpyforandroid.services

import android.util.Log
import io.github.miuzarte.scrcpyforandroid.models.DeviceConnectionType
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcut
import java.io.Closeable

private const val TAG = "DeviceAdbAutoReconnectManager"

internal class DeviceAdbAutoReconnectManager(
    private val controller: ConnectionController,
    private val stateStore: ConnectionStateStore,
    private val backgroundRunner: DeviceAdbBackgroundRunner = DeviceAdbBackgroundRunner(),
): Closeable {
    
    /**
     * 判断是否应该自动重连
     *
     * @param connectionType 连接类型
     * @return 是否应该自动重连
     */
    fun shouldAutoReconnect(connectionType: DeviceConnectionType): Boolean {
        return when (connectionType) {
            DeviceConnectionType.LAN -> true  // 无线连接支持自动重连
            DeviceConnectionType.USB -> false  // USB连接跳过自动重连
        }
    }
    
    suspend fun runKeepAliveLoop(
        isForeground: () -> Boolean,
        intervalMs: Long,
        connectTimeoutMs: Long,
        keepAliveTimeoutMs: Long,
        onReconnectSuccess: suspend (host: String, port: Int) -> Unit,
        onReconnectFailure: suspend (Throwable) -> Unit,
    ) {
        // 启动前记录连接类型，后续不再依赖 stateStore
        val initialTarget = stateStore.state.value.adbSession.currentTarget
        val connectionType = initialTarget?.connectionType ?: DeviceConnectionType.LAN

        // USB连接不做keepalive重连，直接返回
        if (connectionType == DeviceConnectionType.USB) {
            Log.i(TAG, "runKeepAliveLoop(): USB connection, skipping keepalive loop")
            return
        }

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
            shouldAutoReconnect = {
                shouldAutoReconnect(connectionType) &&
                stateStore.state.value.disconnectCause != DisconnectCause.User &&
                stateStore.state.value.disconnectCause != DisconnectCause.KillAdbOnClose
            },
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
            connectKnownShortcut = { device, addressTarget ->
                if (!controller.runAutoAdbConnect(addressTarget.host, addressTarget.port, connectTimeoutMs)) {
                    false
                } else {
                    controller.handleAdbConnected(
                        host = addressTarget.host,
                        port = addressTarget.port,
                        scrcpyProfileId = device.scrcpyProfileId,
                    )
                    onKnownDeviceReconnected(device)
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
