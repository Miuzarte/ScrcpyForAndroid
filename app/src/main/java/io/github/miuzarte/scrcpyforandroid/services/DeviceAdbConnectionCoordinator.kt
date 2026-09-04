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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.parcelize.Parcelize
import kotlin.time.Duration.Companion.milliseconds
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
            // 不再使用 withTimeout 包裹, 因为 Java 阻塞 Socket 无法被协程取消中断
            // 超时由 socket.connect(address, timeoutMs) 自身控制, 取消由 NativeAdbService.cancelPendingConnect() 处理
            adbService.connect(resolved, port, timeout = timeoutMs.milliseconds)
        }
    }

    /**
     * 通过 USB 连接 ADB 设备
     *
     * @param usbDevice USB 设备
     * @param inputStream USB 输入流
     * @param outputStream USB 输出流
     * @return ConnectionTarget 连接目标
     */
    suspend fun connectUsb(
        usbDevice: UsbDevice,
        inputStream: InputStream,
        outputStream: OutputStream,
        abortHandshake: (() -> Unit)? = null,
    ): ConnectionTarget {
        return withContext(Dispatchers.IO) {
            // 创建 USB 连接目标
            val target = ConnectionTarget(
                host = String.format("0x%04X/0x%04X", usbDevice.vendorId, usbDevice.productId),
                port = 0,
                deviceId = usbDevice.deviceId,
                connectionType = DeviceConnectionType.USB,
            )

            // 通过 USB 流连接
            adbService.connectUsb(inputStream, outputStream, usbDevice.deviceId, abortHandshake)

            target
        }
    }

    fun cancelPendingConnect() {
        adbService.cancelPendingConnect()
    }

    /**
     * 连接第一个可达的地址
     *
     * 支持 TCP 和 USB 连接:
     * - TCP 连接: 先探测可达性, 再建立连接
     * - USB 连接: 直接使用 USB 隧道连接 (需要在调用前建立 USB 隧道)
     *
     * @param addresses 地址列表
     * @param connectTimeoutMs TCP 连接超时时间
     * @param probeTimeoutMs TCP 探测超时时间
     * @return ConnectionTarget 连接目标
     */
    suspend fun connectFirstReachable(
        addresses: List<String>,
        connectTimeoutMs: Long,
        probeTimeoutMs: Int,
    ): ConnectionTarget {
        val targets = addresses.mapNotNull { ConnectionTarget.unmarshalFrom(it) }

        // USB 走独立连接入口 (connectUsbDevice), 快捷方式中不会也不应包含 usb: 地址;
        // 若因历史残留数据混入会被过滤跳过, 最终报 No reachable address
        val tcpTargets = targets.filter { it.connectionType == DeviceConnectionType.LAN }

        // 尝试 TCP 连接
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

    /**
     * 真实链路探测: 限时执行一次 no-op shell 往返
     * 标志位查询无法发现 TCP 半开 (对端掉电不发 FIN) 导致的假死连接;
     * 探测超时/失败即判定断开, 由 keepAlive 循环走既有断开+自动重连链路
     */
    suspend fun probeConnection(timeoutMs: Long): Boolean {
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs) {
                runCatching { adbService.shell(":") }.isSuccess
            } ?: false
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
