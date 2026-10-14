package io.github.miuzarte.scrcpyforandroid.nativecore

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface

/**
 * ADB 接口识别
 *
 * ADB 接口的描述符是一个三元组: class 0xFF (Vendor Specific) / subclass 0x42 (ADB) / protocol 0x01 (ADB),
 * 只看 class 会把同样带厂商自定义接口的设备 (手柄的私有配置接口, 无线接收器, 打印机等) 也当成 ADB 设备,
 * 表现为插入手柄也弹 USB 权限申请 / 出现在设备列表里; 设备级描述符与接口级共用一个匹配逻辑
 */
internal object UsbAdbMatch {
    const val ADB_INTERFACE_CLASS = 0xFF
    const val ADB_INTERFACE_SUBCLASS = 0x42
    const val ADB_INTERFACE_PROTOCOL = 0x01

    /** 判断接口是否为 ADB 接口 */
    fun isAdbInterface(iface: UsbInterface): Boolean =
        iface.interfaceClass == ADB_INTERFACE_CLASS &&
                iface.interfaceSubclass == ADB_INTERFACE_SUBCLASS &&
                iface.interfaceProtocol == ADB_INTERFACE_PROTOCOL

    /** 取出设备上的 ADB 接口, 没有则返回 null */
    fun findAdbInterface(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (isAdbInterface(iface)) return iface
        }
        return null
    }

    /** 判断设备是否提供 ADB 接口 */
    fun isAdbDevice(device: UsbDevice): Boolean = findAdbInterface(device) != null
}
