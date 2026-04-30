package io.github.miuzarte.scrcpyforandroid.services

import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.Executors

internal class DeviceAdbBackgroundRunner : Closeable {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "device-adb-monitor").apply { isDaemon = true }
    }
    private val dispatcher: ExecutorCoroutineDispatcher = executor.asCoroutineDispatcher()

    suspend fun runKeepAliveLoop(
        sessionState: () -> DeviceAdbSessionState,
        isForeground: () -> Boolean,
        intervalMs: Long,
        keepAliveCheck: suspend (host: String, port: Int) -> Boolean,
        reconnect: suspend (host: String, port: Int) -> Unit,
        onReconnectSuccess: suspend (host: String, port: Int) -> Unit,
        onReconnectFailure: suspend (Throwable) -> Unit,
        shouldAutoReconnect: () -> Boolean = { true },
    ) = withContext(dispatcher) {
        val target = sessionState().currentTarget ?: return@withContext
        val host = target.host
        val port = target.port

        while (sessionState().isConnected && sessionState().currentTarget == target) {
            if (!isForeground()) {
                delay(intervalMs)
                continue
            }

            delay(intervalMs)
            val alive = runCatching {
                keepAliveCheck(host, port)
            }.getOrElse { false }
            if (alive) continue
            if (!shouldAutoReconnect()) break

            try {
                reconnect(host, port)
                withContext(Dispatchers.Main) {
                    onReconnectSuccess(host, port)
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    onReconnectFailure(error)
                }
                break
            }
        }
    }

    suspend fun runAutoReconnectLoop(
        isConnected: () -> Boolean,
        isForeground: () -> Boolean,
        isAutoReconnectEnabled: () -> Boolean,
        isBusy: () -> Boolean,
        isAdbConnecting: () -> Boolean,
        hasActiveSession: () -> Boolean,
        savedShortcuts: () -> List<DeviceShortcut>,
        isBlacklisted: (String) -> Boolean,
        probeTcpReachable: suspend (host: String, port: Int) -> Boolean,
        discoverConnectService: suspend () -> Pair<String, Int>?,
        onMdnsPortChanged: suspend (host: String, oldPort: Int, newPort: Int) -> Unit,
        connectKnownShortcut: suspend (shortcut: DeviceShortcut) -> Boolean,
        connectDiscoveredShortcut: suspend (
            host: String,
            port: Int,
            shortcut: DeviceShortcut,
        ) -> Boolean,
        retryIntervalMs: Long,
    ) = withContext(dispatcher) {
        val quickConnectTriedOnce = mutableSetOf<String>()
        while (!isConnected() && isAutoReconnectEnabled()) {
            if (!isForeground() || isBusy() || isAdbConnecting() || hasActiveSession()) {
                delay(retryIntervalMs)
                continue
            }

            val quickCandidates = savedShortcuts()
            if (quickCandidates.isNotEmpty()) {
                for (target in quickCandidates) {
                    if (isConnected() || isAdbConnecting()) break
                    if (isBlacklisted(target.host)) continue

                    val targetKey = "${target.host}:${target.port}"
                    if (quickConnectTriedOnce.contains(targetKey)) continue

                    if (!probeTcpReachable(target.host, target.port)) continue
                    quickConnectTriedOnce += targetKey

                    if (connectKnownShortcut(target)) {
                        break
                    }
                }
                if (isConnected()) break
            }

            val discovered = discoverConnectService()
            if (discovered == null) {
                delay(retryIntervalMs)
                continue
            }

            val (discoveredHost, discoveredPort) = discovered
            if (isBlacklisted(discoveredHost)) {
                delay(retryIntervalMs)
                continue
            }

            val knownDevice = savedShortcuts().firstOrNull { it.host == discoveredHost }
            if (knownDevice == null) {
                delay(retryIntervalMs)
                continue
            }

            val portToReplace = savedShortcuts().firstOrNull {
                it.host == discoveredHost &&
                        it.port != knownDevice.port &&
                        it.port != discoveredPort
            }?.port
            if (portToReplace != null) {
                withContext(Dispatchers.Main) {
                    onMdnsPortChanged(discoveredHost, portToReplace, discoveredPort)
                }
            }

            if (isConnected() || isAdbConnecting()) {
                delay(retryIntervalMs)
                continue
            }

            connectDiscoveredShortcut(discoveredHost, discoveredPort, knownDevice)
            delay(retryIntervalMs)
        }
    }

    override fun close() {
        dispatcher.close()
        executor.shutdownNow()
    }
}
