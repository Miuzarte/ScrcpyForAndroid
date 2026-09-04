package io.github.miuzarte.scrcpyforandroid.nativecore

import android.content.Context
import android.hardware.usb.UsbDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * USB ADB 会话（App 级单例）
 *
 * 统一管理 [UsbAdbTunnel] 的生命周期：与 [NativeAdbService] 共用连接锁，
 * 保证连接/断开全程互斥且幂等，避免隧道只在下次连接时才被释放，
 * 导致进程存活期间 USB 设备被持续占用。
 *
 * 阻塞的 USB/binder 操作（close/open/权限等待）统一切 IO 线程执行，
 * 调用方无需关心自身调度器——直接在主协程调 openTunnel/disconnect 也安全。
 */
object UsbAdbSession {
    private var tunnel: UsbAdbTunnel? = null

    /**
     * 打开到指定设备的 USB 隧道（若已有旧隧道会先关闭释放）
     *
     * @return 隧道的输入输出流，交给 DirectAdbClient 建立连接
     */
    suspend fun openTunnel(
        context: Context,
        usbDevice: UsbDevice,
    ): Pair<InputStream, OutputStream> = withContext(Dispatchers.IO) {
        NativeAdbService.connectionMutex.withLock {
            runCatching { tunnel?.close() }
            tunnel = null

            val newTunnel = UsbAdbTunnel(context, usbDevice)
            val streams = try {
                newTunnel.open()
            } catch (e: Exception) {
                // open 失败的实例自行回收（open 内部已兜底，此处双保险）
                runCatching { newTunnel.close() }
                throw e
            }
            tunnel = newTunnel
            streams
        }
    }

    /**
     * 关闭当前隧道并释放 USB 资源（接口、端点、receiver）
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        NativeAdbService.connectionMutex.withLock {
            runCatching { tunnel?.close() }
            tunnel = null
        }
    }

    /**
     * 强制中断当前隧道的阻塞读（握手超时守卫调用）。
     * 不取连接锁：调用场景正是持锁线程被 bulkTransfer 阻塞、需要从外部解除阻塞；
     * UsbAdbTunnel.close 幂等（closed 标志防重入），read 循环最多 5s 内退出。
     */
    internal fun abortCurrentTunnel() {
        runCatching { tunnel?.close() }
    }
}
