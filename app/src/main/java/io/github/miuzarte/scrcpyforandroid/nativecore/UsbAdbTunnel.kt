package io.github.miuzarte.scrcpyforandroid.nativecore

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * USB ADB隧道类
 *
 * 负责USB设备的连接管理和数据传输：
 * 1. 打开USB设备连接
 * 2. 查找ADB接口（接口类0xFF）
 * 3. 配置Bulk IN/OUT端点
 * 4. 封装为InputStream/OutputStream
 * 5. 处理USB权限申请
 *
 * 注意：此类不做ADB协议握手，握手由DirectAdbConnection处理
 */
class UsbAdbTunnel(
    private val context: Context,
    private val usbDevice: UsbDevice
) : AutoCloseable {

    companion object {
        private const val TAG = "UsbAdbTunnel"
        
        // ADB接口类（Android Debug Bridge）
        private const val ADB_INTERFACE_CLASS = 0xFF
        
        // USB传输超时（毫秒）
        private const val USB_TRANSFER_TIMEOUT_MS = 5000
        
        // 最大USB包大小（字节）
        // 注意：受Linux USB驱动限制，最大payload为16KB
        private const val MAX_USB_PACKET_SIZE = 16384
        
        // USB权限Action
        private const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
    }

    // USB管理器
    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    // USB设备连接
    private var usbConnection: UsbDeviceConnection? = null
    
    // ADB接口
    private var adbInterface: UsbInterface? = null
    
    // Bulk IN端点（设备->主机）
    private var bulkInEndpoint: UsbEndpoint? = null
    
    // Bulk OUT端点（主机->设备）
    private var bulkOutEndpoint: UsbEndpoint? = null
    
    // 连接状态
    private val isConnected = AtomicBoolean(false)
    
    // 关闭标志
    @Volatile
    private var closed = false
    
    // USB权限接收器
    private var permissionReceiver: BroadcastReceiver? = null
    
    // USB拔出接收器（物理拔出检测）
    private var detachedReceiver: BroadcastReceiver? = null
    
    // 权限等待队列
    private val permissionQueue = LinkedBlockingQueue<Boolean>()

    /**
     * 打开USB隧道
     *
     * @return Pair<InputStream, OutputStream> 输入输出流
     * @throws IOException 如果连接失败
     */
    fun open(): Pair<InputStream, OutputStream> {
        if (closed) throw IOException("Tunnel is closed")
        if (isConnected.get()) throw IOException("Tunnel is already connected")
        
        Log.i(TAG, "open(): opening USB tunnel for device ${usbDevice.deviceName}")
        
        // 1. 检查USB权限
        checkUsbPermission()
        
        // 2. 打开USB设备连接
        usbConnection = usbManager.openDevice(usbDevice)
            ?: throw IOException("Failed to open USB device: ${usbDevice.deviceName}")
        
        // 3. 查找ADB接口
        adbInterface = findAdbInterface()
            ?: throw IOException("No ADB interface found on device ${usbDevice.deviceName}")
        
        // 4. 声明接口
        if (!usbConnection!!.claimInterface(adbInterface, true)) {
            throw IOException("Failed to claim ADB interface")
        }
        
        // 5. 查找Bulk端点
        findBulkEndpoints()
        
        // 6. 创建输入输出流
        val inputStream = UsbInputStream()
        val outputStream = UsbOutputStream()
        
        isConnected.set(true)
        
        // 注册物理拔出监听（拔下OTG线时自动断开连接）
        registerDetachedReceiver()
        
        Log.i(TAG, "open(): USB tunnel opened successfully")
        
        return Pair(inputStream, outputStream)
    }

    /**
     * 检查USB权限
     */
    private fun checkUsbPermission() {
        if (usbManager.hasPermission(usbDevice)) {
            Log.d(TAG, "checkUsbPermission(): already have permission")
            return
        }
        
        Log.i(TAG, "checkUsbPermission(): requesting USB permission")
        
        // 注册权限接收器
        registerPermissionReceiver()
        
        // 请求权限
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION), flags
        )
        usbManager.requestPermission(usbDevice, permissionIntent)
        
        // 等待权限结果
        val granted = permissionQueue.poll(10, TimeUnit.SECONDS)
            ?: throw IOException("USB permission request timed out")
        
        if (!granted) {
            throw IOException("USB permission denied")
        }
        
        Log.d(TAG, "checkUsbPermission(): permission granted")
    }

    /**
     * 注册权限接收器
     */
    private fun registerPermissionReceiver() {
        if (permissionReceiver != null) return
        
        permissionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    synchronized(this@UsbAdbTunnel) {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        
                        if (device?.deviceId == usbDevice.deviceId) {
                            Log.d(TAG, "onReceive(): permission ${if (granted) "granted" else "denied"}")
                            permissionQueue.offer(granted)
                        }
                    }
                }
            }
        }
        
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(permissionReceiver, filter)
        }
    }

    /**
     * 注册USB物理拔出接收器
     *
     * 当用户拔下OTG线时，系统发送ACTION_USB_DEVICE_DETACHED广播。
     * 此接收器检测到后立即设置closed = true，使read循环抛出异常，
     * 从而触发完整的ADB断连流程。
     */
    private fun registerDetachedReceiver() {
        if (detachedReceiver != null) return
        
        detachedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device?.deviceName == usbDevice.deviceName) {
                        Log.i(TAG, "USB device detached: ${usbDevice.deviceName}, closing tunnel")
                        closed = true
                        isConnected.set(false)
                    }
                }
            }
        }
        
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(detachedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(detachedReceiver, filter)
        }
    }

    /**
     * 查找ADB接口
     *
     * ADB接口的class为0xFF（Vendor Specific）
     */
    private fun findAdbInterface(): UsbInterface? {
        for (i in 0 until usbDevice.interfaceCount) {
            val iface = usbDevice.getInterface(i)
            if (iface.interfaceClass == ADB_INTERFACE_CLASS) {
                Log.d(TAG, "findAdbInterface(): found ADB interface at index $i")
                return iface
            }
        }
        return null
    }

    /**
     * 查找Bulk IN/OUT端点
     */
    private fun findBulkEndpoints() {
        val iface = adbInterface ?: throw IllegalStateException("ADB interface not found")
        
        for (i in 0 until iface.endpointCount) {
            val endpoint = iface.getEndpoint(i)
            
            if (endpoint.type == android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (endpoint.direction == android.hardware.usb.UsbConstants.USB_DIR_IN) {
                    bulkInEndpoint = endpoint
                    Log.d(TAG, "findBulkEndpoints(): found Bulk IN endpoint at index $i")
                } else if (endpoint.direction == android.hardware.usb.UsbConstants.USB_DIR_OUT) {
                    bulkOutEndpoint = endpoint
                    Log.d(TAG, "findBulkEndpoints(): found Bulk OUT endpoint at index $i")
                }
            }
        }
        
        if (bulkInEndpoint == null || bulkOutEndpoint == null) {
            throw IOException("Failed to find Bulk IN/OUT endpoints")
        }
    }

    /**
     * 关闭USB隧道
     */
    override fun close() {
        if (closed) return
        
        closed = true
        isConnected.set(false)
        
        Log.i(TAG, "close(): closing USB tunnel")
        
        // 注销权限接收器
        permissionReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "close(): failed to unregister permission receiver", e)
            }
        }
        permissionReceiver = null
        
        // 注销USB拔出接收器
        detachedReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "close(): failed to unregister detached receiver", e)
            }
        }
        detachedReceiver = null
        
        // 释放接口
        adbInterface?.let {
            usbConnection?.releaseInterface(it)
        }
        adbInterface = null
        
        // 关闭连接
        usbConnection?.close()
        usbConnection = null
        
        bulkInEndpoint = null
        bulkOutEndpoint = null
    }

    /**
     * 检查隧道是否已连接
     */
    fun isConnected(): Boolean = isConnected.get() && !closed

    /**
     * USB输入流实现
     *
     * 从USB Bulk IN端点读取数据
     */
    private inner class UsbInputStream : InputStream() {
        private val buffer = ByteArray(MAX_USB_PACKET_SIZE)
        private var bufferPos = 0
        private var bufferLen = 0

        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) == -1) -1 else (b[0].toInt() and 0xFF)
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            // 如果缓冲区有数据，直接返回（不需要检查连接状态，缓冲数据仍有效）
            if (bufferPos < bufferLen) {
                val copyLen = minOf(len, bufferLen - bufferPos)
                System.arraycopy(buffer, bufferPos, b, off, copyLen)
                bufferPos += copyLen
                return copyLen
            }

            // 循环读取，处理bulkTransfer超时（-1）
            // TCP Socket的read()可以无限期阻塞等待数据，而USB bulkTransfer有固定超时
            // 空闲时bulkTransfer超时返回-1，不代表连接断开，应继续等待
            while (true) {
                if (closed) throw IOException("Tunnel is closed")
                if (!isConnected.get()) throw IOException("Tunnel is not connected")

                val endpoint = bulkInEndpoint ?: throw IOException("Bulk IN endpoint not available")
                val connection = usbConnection ?: throw IOException("USB connection not available")

                val readLen = connection.bulkTransfer(
                    endpoint,
                    buffer,
                    minOf(len, buffer.size),
                    USB_TRANSFER_TIMEOUT_MS
                )

                when {
                    readLen > 0 -> {
                        // 正常读取到数据
                        val copyLen = minOf(len, readLen)
                        System.arraycopy(buffer, 0, b, off, copyLen)
                        // 如果读取的数据比请求的多，保存剩余部分
                        if (readLen > copyLen) {
                            bufferPos = copyLen
                            bufferLen = readLen
                        }
                        return copyLen
                    }
                    readLen == 0 -> return 0
                    else -> {
                        // bulkTransfer超时返回-1，检查隧道是否仍然存活
                        if (closed || !isConnected.get()) {
                            throw IOException("USB read failed: tunnel disconnected")
                        }
                        // 隧道仍存活，超时只是因为空闲无数据，继续等待（模拟TCP无限阻塞行为）
                        continue
                    }
                }
            }
        }

        override fun available(): Int = bufferLen - bufferPos

        override fun close() {
            // 不关闭隧道，只关闭流
        }
    }

    /**
     * USB输出流实现
     *
     * 向USB Bulk OUT端点写入数据
     */
    private inner class UsbOutputStream : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()))

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed) throw IOException("Tunnel is closed")
            if (!isConnected.get()) throw IOException("Tunnel is not connected")
            
            val endpoint = bulkOutEndpoint ?: throw IOException("Bulk OUT endpoint not available")
            val connection = usbConnection ?: throw IOException("USB connection not available")
            
            // 分块写入（USB包大小限制）
            var offset = off
            var remaining = len
            
            while (remaining > 0) {
                val chunkSize = minOf(remaining, MAX_USB_PACKET_SIZE)
                
                val written = connection.bulkTransfer(
                    endpoint,
                    b,
                    offset,
                    chunkSize,
                    USB_TRANSFER_TIMEOUT_MS
                )
                
                if (written < 0) {
                    throw IOException("USB write failed")
                }
                
                offset += written
                remaining -= written
            }
        }

        override fun flush() {
            // USB传输是同步的，不需要flush
        }

        override fun close() {
            // 不关闭隧道，只关闭流
        }
    }
}

/**
 * USB设备信息
 *
 * 用于UI显示和设备管理
 */
data class UsbDeviceInfo(
    val device: UsbDevice,
    val hasPermission: Boolean
) {
    /**
     * 获取设备显示名称
     */
    fun getDisplayName(): String {
        val manufacturer = device.manufacturerName ?: "Unknown"
        val product = device.productName ?: "Device"
        return "$manufacturer $product"
    }

    /**
     * 获取设备VID
     */
    fun getVendorId(): Int = device.vendorId

    /**
     * 获取设备PID
     */
    fun getProductId(): Int = device.productId

    /**
     * 获取设备ID
     */
    fun getDeviceId(): Int = device.deviceId

    /**
     * 获取USB地址字符串
     * 格式：usb:0x{VID}/0x{PID}#{deviceId}
     */
    fun getUsbAddress(): String {
        val vid = String.format("0x%04X", device.vendorId)
        val pid = String.format("0x%04X", device.productId)
        return "usb:$vid/$pid#${device.deviceId}"
    }
}
