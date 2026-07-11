package io.github.miuzarte.scrcpyforandroid.services

import android.hardware.usb.UsbDevice
import android.os.Parcelable
import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.models.DeviceConnectionType
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.nativecore.UsbAdbTunnel
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.parcelize.Parcelize
import java.io.InputStream
import java.io.OutputStream
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
): Parcelable

internal class DeviceAdbConnectionCoordinator(
    private val adbService: NativeAdbService = NativeAdbService,
) {
    suspend fun connectWithTimeout(host: String, port: Int, timeoutMs: Long) {
        withContext(Dispatchers.IO) {
            val resolved = resolveHost(host)
            // 不再使用withTimeout包裹，因为Java阻塞Socket无法被协程取消中断
            // 超时由socket.connect(address, timeoutMs)自身控制，取消由NativeAdbService.cancelPendingConnect()处理
            adbService.connect(resolved, port)
        }
    }

    /**
     * 通过USB连接ADB设备
     *
     * @param usbDevice USB设备
     * @param inputStream USB输入流
     * @param outputStream USB输出流
     * @return ConnectionTarget 连接目标
     */
    suspend fun connectUsb(
        usbDevice: UsbDevice,
        inputStream: InputStream,
        outputStream: OutputStream
    ): ConnectionTarget {
        return withContext(Dispatchers.IO) {
            // 创建USB连接目标
            val target = ConnectionTarget(
                host = String.format("0x%04X/0x%04X", usbDevice.vendorId, usbDevice.productId),
                port = 0,
                deviceId = usbDevice.deviceId,
                connectionType = DeviceConnectionType.USB
            )
            
            // 通过USB流连接
            adbService.connectUsb(inputStream, outputStream, usbDevice.deviceId)
            
            target
        }
    }

    fun cancelPendingConnect() {
        adbService.cancelPendingConnect()
    }

    /**
     * 连接第一个可达的地址
     *
     * 支持TCP和USB连接：
     * - TCP连接：先探测可达性，再建立连接
     * - USB连接：直接使用USB隧道连接（需要在调用前建立USB隧道）
     *
     * @param addresses 地址列表
     * @param connectTimeoutMs TCP连接超时时间
     * @param probeTimeoutMs TCP探测超时时间
     * @return ConnectionTarget 连接目标
     */
    suspend fun connectFirstReachable(
        addresses: List<String>,
        connectTimeoutMs: Long,
        probeTimeoutMs: Int,
    ): ConnectionTarget {
        val targets = addresses.mapNotNull { ConnectionTarget.unmarshalFrom(it) }
        
        // 分离TCP和USB地址
        val tcpTargets = targets.filter { it.connectionType == DeviceConnectionType.LAN }
        val usbTargets = targets.filter { it.connectionType == DeviceConnectionType.USB }
        
        // 优先尝试USB连接（如果有的话）
        for (target in usbTargets) {
            try {
                // USB连接需要在调用前建立隧道，这里只返回目标
                // 实际的USB连接由调用者通过connectUsb方法建立
                return target
            } catch (e: Exception) {
                // USB连接失败，继续尝试下一个
                continue
            }
        }
        
        // 尝试TCP连接
        for (target in tcpTargets) {
            if (probeTcpReachable(target.host, target.port, probeTimeoutMs)) {
                connectWithTimeout(target.host, target.port, connectTimeoutMs)
                return target
            }
        }
        
        throw NoSuchElementException("No reachable address found among: $addresses")
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

    suspend fun executeShell(command: String): String {
        return withContext(Dispatchers.IO) {
            adbService.shell(command)
        }
    }
}
