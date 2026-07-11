package io.github.miuzarte.scrcpyforandroid.nativecore

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * USB ADB设备监听器
 *
 * 功能：
 * 1. 监听USB设备插拔事件
 * 2. 过滤ADB设备（接口类0xFF）
 * 3. 处理USB权限请求
 * 4. 提供设备列表和事件通知
 *
 * 使用方法：
 * 1. 在Activity.onCreate()中调用startWatching()
 * 2. 在Activity.onDestroy()中调用stopWatching()
 * 3. 通过devicesFlow获取设备列表
 * 4. 通过eventsFlow获取设备事件
 */
class UsbAdbDeviceWatcher(
    private val context: Context
) {
    companion object {
        private const val TAG = "UsbAdbDeviceWatcher"
        
        // ADB接口类（Android Debug Bridge）
        private const val ADB_INTERFACE_CLASS = 0xFF
        
        // USB权限Action
        private const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
    }

    // USB管理器
    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    // 设备列表状态流
    private val _devicesFlow = MutableStateFlow<List<UsbDeviceInfo>>(emptyList())
    val devicesFlow: StateFlow<List<UsbDeviceInfo>> = _devicesFlow.asStateFlow()

    // 设备事件状态流
    private val _eventsFlow = MutableStateFlow<UsbDeviceEvent?>(null)
    val eventsFlow: StateFlow<UsbDeviceEvent?> = _eventsFlow.asStateFlow()

    // 广播接收器
    private var receiver: BroadcastReceiver? = null
    
    // USB权限回调
    private val permissionCallback: (UsbDevice, Boolean) -> Unit = { device, granted ->
        handlePermissionResult(device, granted)
    }

    // 监听状态
    @Volatile
    private var watching = false

    /**
     * 开始监听USB设备
     *
     * 注册广播接收器，监听USB设备插拔和权限事件
     */
    fun startWatching() {
        if (watching) return
        
        Log.i(TAG, "startWatching(): starting USB device watcher")
        
        // 注册USB权限回调
        UsbPermissionBus.addListener(permissionCallback)
        
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        if (device != null) {
                            handleDeviceAttached(device)
                        }
                    }
                    
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        if (device != null) {
                            handleDeviceDetached(device)
                        }
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        
        watching = true
        
        // 扫描已连接的设备
        scanConnectedDevices()
    }

    /**
     * 停止监听USB设备
     */
    fun stopWatching() {
        if (!watching) return
        
        Log.i(TAG, "stopWatching(): stopping USB device watcher")
        
        // 注销USB权限回调
        UsbPermissionBus.removeListener(permissionCallback)
        
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "stopWatching(): failed to unregister receiver", e)
            }
        }
        receiver = null
        watching = false
    }

    /**
     * 扫描已连接的USB设备
     *
     * 查找所有已连接的ADB设备并更新设备列表
     */
    fun scanConnectedDevices() {
        Log.d(TAG, "scanConnectedDevices(): scanning for connected USB devices")
        
        val devices = mutableListOf<UsbDeviceInfo>()
        
        for (device in usbManager.deviceList.values) {
            if (isAdbDevice(device)) {
                val hasPermission = usbManager.hasPermission(device)
                devices.add(UsbDeviceInfo(device, hasPermission))
                
                Log.d(TAG, "scanConnectedDevices(): found ADB device ${device.deviceName} " +
                    "(VID=${String.format("0x%04X", device.vendorId)}, " +
                    "PID=${String.format("0x%04X", device.productId)}, " +
                    "permission=$hasPermission)")
            }
        }
        
        _devicesFlow.value = devices
        
        Log.i(TAG, "scanConnectedDevices(): found ${devices.size} ADB devices")
    }

    /**
     * 检查设备是否为ADB设备
     *
     * ADB设备的接口类为0xFF（Vendor Specific）
     */
    private fun isAdbDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == ADB_INTERFACE_CLASS) {
                return true
            }
        }
        return false
    }

    /**
     * 处理设备连接事件
     */
    private fun handleDeviceAttached(device: UsbDevice) {
        Log.i(TAG, "handleDeviceAttached(): device ${device.deviceName} attached")
        
        if (!isAdbDevice(device)) {
            Log.d(TAG, "handleDeviceAttached(): not an ADB device, ignoring")
            return
        }
        
        // 发送设备连接事件
        _eventsFlow.value = UsbDeviceEvent.Attached(device)
        
        // 请求USB权限
        requestUsbPermission(device)
        
        // 更新设备列表
        scanConnectedDevices()
    }

    /**
     * 处理设备断开事件
     */
    private fun handleDeviceDetached(device: UsbDevice) {
        Log.i(TAG, "handleDeviceDetached(): device ${device.deviceName} detached")
        
        // 发送设备断开事件
        _eventsFlow.value = UsbDeviceEvent.Detached(device)
        
        // 更新设备列表
        scanConnectedDevices()
    }

    /**
     * 处理权限结果
     */
    private fun handlePermissionResult(device: UsbDevice, granted: Boolean) {
        Log.i(TAG, "handlePermissionResult(): device ${device.deviceName} " +
            "permission ${if (granted) "granted" else "denied"}")
        
        // 发送权限事件
        _eventsFlow.value = UsbDeviceEvent.PermissionResult(device, granted)
        
        // 更新设备列表
        scanConnectedDevices()
    }

    /**
     * 请求USB权限
     */
    fun requestUsbPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            Log.d(TAG, "requestUsbPermission(): already have permission for ${device.deviceName}")
            return
        }
        
        Log.i(TAG, "requestUsbPermission(): requesting permission for ${device.deviceName}")
        
        // 使用显式Intent避免Android 14+ FLAG_MUTABLE限制
        val intent = Intent(ACTION_USB_PERMISSION).apply {
            component = ComponentName(context.packageName, UsbPermissionReceiver::class.java.name)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, intent, flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    /**
     * 获取ADB设备列表
     */
    fun getAdbDevices(): List<UsbDeviceInfo> {
        return _devicesFlow.value
    }

    /**
     * 获取有权限的ADB设备列表
     */
    fun getAuthorizedDevices(): List<UsbDeviceInfo> {
        return _devicesFlow.value.filter { it.hasPermission }
    }

    /**
     * 根据设备ID获取设备信息
     */
    fun getDeviceById(deviceId: Int): UsbDeviceInfo? {
        return _devicesFlow.value.find { it.device.deviceId == deviceId }
    }

    /**
     * 根据USB地址获取设备信息
     *
     * @param usbAddress USB地址，格式：usb:0x{VID}/0x{PID}#{deviceId}
     */
    fun getDeviceByUsbAddress(usbAddress: String): UsbDeviceInfo? {
        return _devicesFlow.value.find { it.getUsbAddress() == usbAddress }
    }

    /**
     * 检查是否有ADB设备连接
     */
    fun hasAdbDevices(): Boolean = _devicesFlow.value.isNotEmpty()

    /**
     * 检查是否有已授权的ADB设备
     */
    fun hasAuthorizedDevices(): Boolean = _devicesFlow.value.any { it.hasPermission }

    /**
     * 获取设备数量
     */
    fun getDeviceCount(): Int = _devicesFlow.value.size

    /**
     * 获取已授权设备数量
     */
    fun getAuthorizedDeviceCount(): Int = _devicesFlow.value.count { it.hasPermission }
}

/**
 * USB权限广播接收器
 *
 * 用于接收USB权限请求结果，避免隐式Intent + FLAG_MUTABLE的Android 14+限制
 */
class UsbPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_USB_PERMISSION) return
        
        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
        
        if (device != null) {
            Log.i(TAG, "UsbPermissionReceiver: device ${device.deviceName} permission ${if (granted) "granted" else "denied"}")
            // 通知全局事件总线
            UsbPermissionBus.notifyResult(device, granted)
        }
    }
    
    companion object {
        private const val TAG = "UsbAdbDeviceWatcher"
        private const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
    }
}

/**
 * USB权限事件总线
 *
 * 用于将权限结果从静态BroadcastReceiver传递到UsbAdbDeviceWatcher实例
 */
object UsbPermissionBus {
    private val listeners = mutableListOf<(UsbDevice, Boolean) -> Unit>()
    
    fun addListener(listener: (UsbDevice, Boolean) -> Unit) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: (UsbDevice, Boolean) -> Unit) {
        listeners.remove(listener)
    }
    
    fun notifyResult(device: UsbDevice, granted: Boolean) {
        listeners.forEach { it(device, granted) }
    }
}

/**
 * USB设备事件
 *
 * 用于通知UI层设备状态变化
 */
sealed class UsbDeviceEvent {
    /**
     * 设备连接事件
     */
    data class Attached(val device: UsbDevice) : UsbDeviceEvent()
    
    /**
     * 设备断开事件
     */
    data class Detached(val device: UsbDevice) : UsbDeviceEvent()
    
    /**
     * 权限结果事件
     */
    data class PermissionResult(val device: UsbDevice, val granted: Boolean) : UsbDeviceEvent()
}
