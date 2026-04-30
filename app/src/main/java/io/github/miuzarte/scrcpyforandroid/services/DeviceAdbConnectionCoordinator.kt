package io.github.miuzarte.scrcpyforandroid.services

import android.os.Parcelable
import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.parcelize.Parcelize
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

@Parcelize
internal data class DeviceAdbSessionState(
    val isConnected: Boolean = false,
    val statusLine: String = "Disconnected",
    val currentTarget: ConnectionTarget? = null,
    val connectedDeviceLabel: String = "Disconnected",
    val isQuickConnected: Boolean = false,
    val connectedScrcpyProfileId: String = ScrcpyOptions.GLOBAL_PROFILE_ID,
    val audioForwardingSupported: Boolean = true,
    val cameraMirroringSupported: Boolean = true,
) : Parcelable

internal class DeviceAdbConnectionCoordinator(
    private val adbService: NativeAdbService = NativeAdbService,
) {
    suspend fun connectWithTimeout(host: String, port: Int, timeoutMs: Long) {
        withContext(Dispatchers.IO) {
            val resolved = resolveHost(host)
            withTimeout(timeoutMs) {
                adbService.connect(resolved, port)
            }
        }
    }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            adbService.disconnect()
        }
    }

    private fun resolveHost(host: String): String {
        val bareHost = if (host.startsWith('[') && host.endsWith(']'))
            host.substring(1, host.length - 1)
        else
            host
        return runCatching { InetAddress.getByName(bareHost).hostAddress }
            .getOrDefault(host)
    }

    suspend fun isConnected(timeoutMs: Long): Boolean {
        return withContext(Dispatchers.IO) {
            withTimeout(timeoutMs) {
                adbService.isConnected()
            }
        }
    }

    suspend fun probeTcpReachable(host: String, port: Int, timeoutMs: Int): Boolean {
        return withContext(Dispatchers.IO) {
            val resolved = resolveHost(host)
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(resolved, port), timeoutMs)
                    true
                }
            }.getOrDefault(false)
        }
    }

    suspend fun fetchConnectedDeviceInfo(host: String, port: Int): ConnectedDeviceInfo {
        return fetchConnectedDeviceInfo(adbService, host, port)
    }

    suspend fun discoverPairingService(
        timeoutMs: Long = 12_000,
        includeLanDevices: Boolean = true,
    ): Pair<String, Int>? {
        return withContext(Dispatchers.IO) {
            adbService.discoverPairingService(
                timeoutMs = timeoutMs,
                includeLanDevices = includeLanDevices,
            )
        }
    }

    suspend fun discoverConnectService(
        timeoutMs: Long = 12_000,
        includeLanDevices: Boolean = true,
    ): Pair<String, Int>? {
        return withContext(Dispatchers.IO) {
            adbService.discoverConnectService(
                timeoutMs = timeoutMs,
                includeLanDevices = includeLanDevices,
            )
        }
    }

    suspend fun pair(host: String, port: Int, pairingCode: String): Boolean {
        return withContext(Dispatchers.IO) {
            val resolved = resolveHost(host)
            adbService.pair(resolved, port, pairingCode)
        }
    }

    suspend fun startApp(
        packageName: String,
        displayId: Int? = null,
        forceStop: Boolean = false,
    ): String {
        return withContext(Dispatchers.IO) {
            adbService.startApp(
                packageName = packageName,
                displayId = displayId,
                forceStop = forceStop,
            )
        }
    }
}
