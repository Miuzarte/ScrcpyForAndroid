package io.github.miuzarte.scrcpyforandroid.models

import android.os.Parcelable
import io.github.miuzarte.scrcpyforandroid.constants.Defaults
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyOptions
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 设备连接类型枚举
 */
enum class DeviceConnectionType {
    /** 无线LAN连接 */
    LAN,
    /** 有线USB连接 */
    USB
}

class DeviceShortcuts(val devices: List<DeviceShortcut>): List<DeviceShortcut> by devices {
    fun marshalToString(): String = DeviceShortcut.json.encodeToString(devices)

    companion object {
        fun unmarshalFrom(s: String): DeviceShortcuts {
            if (s.isBlank()) return DeviceShortcuts(emptyList())
            val trimmed = s.trim()
            if (trimmed.startsWith("[")) {
                return runCatching {
                    val list = DeviceShortcut.json.decodeFromString<List<DeviceShortcut>>(trimmed)
                        .map {
                            if (it.addresses.isEmpty()) it.copy(addresses = listOf(""))
                            else it
                        }
                    DeviceShortcuts(list)
                }.getOrDefault(DeviceShortcuts(emptyList()))
            }
            return unmarshalFromPipe(s)
        }

        private fun unmarshalFromPipe(s: String): DeviceShortcuts {
            var nextLegacyId = 1
            val list = s.lines()
                .mapNotNull { raw ->
                    val firstValue = raw.split("|", limit = 2)
                        .firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (firstValue.toIntOrNull() != null) {
                        unmarshalDeviceFromPipe(raw)
                    } else {
                        unmarshalDeviceFromPipe(
                            raw,
                            fallbackId = nextLegacyId.toString(),
                        ).also {
                            if (it != null) nextLegacyId++
                        }
                    }
                }
            return DeviceShortcuts(list)
        }

        private fun unmarshalDeviceFromPipe(
            s: String,
            fallbackId: String? = null,
        ): DeviceShortcut? {
            val parts = s.split("|")
            val idInData = parts.firstOrNull()
                ?.trim()
                ?.takeIf { it.toIntOrNull() != null }

            return if (idInData != null) {
                if (parts.size == 7) {
                    val name = parts[1].trim()
                    val host = parts[2].trim()
                    val port = parts[3].trim().toIntOrNull() ?: Defaults.ADB_PORT
                    val startScrcpyOnConnect = parts.getOrNull(4)?.trim() == "1"
                    val openFullscreenOnStart = startScrcpyOnConnect
                            && parts.getOrNull(5)?.trim() == "1"
                    val scrcpyProfileId = parts.getOrNull(6)
                        ?.trim()
                        ?.takeUnless { it.isNullOrBlank() }
                        ?: ScrcpyOptions.GLOBAL_PROFILE_ID

                    if (host.isNotBlank()) DeviceShortcut(
                        id = idInData,
                        name = name,
                        addresses = listOf("$host:$port"),
                        startScrcpyOnConnect = startScrcpyOnConnect,
                        openFullscreenOnStart = openFullscreenOnStart,
                        scrcpyProfileId = scrcpyProfileId,
                    )
                    else null
                } else null
            } else if (fallbackId != null && parts.size == 6) {
                val name = parts[0].trim()
                val host = parts[1].trim()
                val port = parts[2].trim().toIntOrNull() ?: Defaults.ADB_PORT
                val startScrcpyOnConnect = parts.getOrNull(3)?.trim() == "1"
                val openFullscreenOnStart = startScrcpyOnConnect
                        && parts.getOrNull(4)?.trim() == "1"
                val scrcpyProfileId = parts.getOrNull(5)
                    ?.trim()
                    ?.takeUnless { it.isNullOrBlank() }
                    ?: ScrcpyOptions.GLOBAL_PROFILE_ID

                if (host.isNotBlank()) DeviceShortcut(
                    id = fallbackId,
                    name = name,
                    addresses = listOf("$host:$port"),
                    startScrcpyOnConnect = startScrcpyOnConnect,
                    openFullscreenOnStart = openFullscreenOnStart,
                    scrcpyProfileId = scrcpyProfileId,
                )
                else null
            } else null
        }
    }

    private fun getIndex(id: String) = devices.indexOfFirst { it.id == id }
    private fun getIndex(host: String, port: Int) = devices.indexOfFirst {
        it.host == host && it.port == port
    }

    fun get(id: String) = devices.firstOrNull { it.id == id }
    fun get(host: String, port: Int) = devices.firstOrNull {
        it.host == host && it.port == port
    }

    fun update(
        id: String? = null,
        host: String? = null,
        port: Int? = null,
        name: String? = null,
        startScrcpyOnConnect: Boolean? = null,
        openFullscreenOnStart: Boolean? = null,
        scrcpyProfileId: String? = null,
        newPort: Int? = null,
        updateNameOnlyWhenEmpty: Boolean = false,
    ): DeviceShortcuts {
        val idx = if (id != null) getIndex(id)
        else if (host != null && port != null) getIndex(host, port)
        else -1

        if (idx < 0) return this
        val old = devices[idx]
        val updateById = id != null

        val updatedAddresses = when {
            updateById && host != null -> {
                val p = port ?: old.port
                listOf("$host:$p")
            }

            updateById -> old.addresses

            newPort != null -> {
                old.addresses.map { addr ->
                    val parsed = ConnectionTarget.unmarshalFrom(addr)
                    if (parsed != null && parsed.host == host && parsed.port == port) {
                        // 根据连接类型生成正确的地址格式
                        when (parsed.connectionType) {
                            DeviceConnectionType.LAN -> "${parsed.host}:$newPort"
                            DeviceConnectionType.USB -> parsed.toString()  // USB地址保持不变
                        }
                    } else addr
                }
            }

            else -> old.addresses
        }

        val updated = DeviceShortcut(
            id = old.id,
            name = when {
                name == null -> old.name
                updateNameOnlyWhenEmpty && old.name.isNotBlank() -> old.name
                else -> name
            },
            addresses = updatedAddresses,
            startScrcpyOnConnect = startScrcpyOnConnect ?: old.startScrcpyOnConnect,
            openFullscreenOnStart = openFullscreenOnStart ?: old.openFullscreenOnStart,
            scrcpyProfileId = scrcpyProfileId ?: old.scrcpyProfileId,
        )

        if (updated == old) return this

        val newList = devices.toMutableList()
            .apply {
                this[idx] = updated
            }
        return DeviceShortcuts(
            if ((updateById && (updated.addresses != old.addresses))
                || (newPort != null && newPort != old.port)
            )
                newList.distinctBy { it.id }
            else newList,
        )
    }

    fun upsert(
        shortcut: DeviceShortcut,
        index: Int? = null,
    ): DeviceShortcuts {
        val normalizedShortcut = normalizeId(shortcut)
        val existingById = getIndex(normalizedShortcut.id)
        val existingIdx = if (existingById >= 0) {
            existingById
        } else {
            getIndex(normalizedShortcut.host, normalizedShortcut.port)
        }
        val newList = devices.toMutableList()
        if (existingIdx >= 0) {
            val existingId = devices[existingIdx].id
            newList[existingIdx] = normalizedShortcut.copy(id = existingId)
        } else {
            if (index != null) newList.add(index, normalizedShortcut)
            else newList.add(normalizedShortcut)
        }
        return DeviceShortcuts(newList)
    }

    private fun normalizeId(shortcut: DeviceShortcut): DeviceShortcut {
        if (shortcut.id.toIntOrNull() != null) return shortcut
        return shortcut.copy(id = nextId())
    }

    private fun nextId(): String {
        val maxId = devices.maxOfOrNull { it.id.toIntOrNull() ?: 0 } ?: 0
        return (maxId + 1).toString()
    }

    fun move(fromIndex: Int, toIndex: Int): DeviceShortcuts {
        if (fromIndex !in devices.indices || toIndex !in devices.indices) return this
        if (fromIndex == toIndex) return this
        val mutable = devices.toMutableList()
        val item = mutable.removeAt(fromIndex)
        val target = if (toIndex > fromIndex) toIndex - 1 else toIndex
        mutable.add(target, item)
        return DeviceShortcuts(mutable)
    }

    fun remove(id: String) = DeviceShortcuts(devices.filterNot { it.id == id })

    fun clear() = DeviceShortcuts(emptyList())

    fun copy(devices: List<DeviceShortcut> = this.devices): DeviceShortcuts =
        DeviceShortcuts(devices)
}

@Serializable
data class DeviceShortcut(
    val id: String = "",
    val name: String = "",
    val addresses: List<String> = listOf(""),
    val startScrcpyOnConnect: Boolean = false,
    val openFullscreenOnStart: Boolean = false,
    val scrcpyProfileId: String = ScrcpyOptions.GLOBAL_PROFILE_ID,
) {
    val host: String
        get() {
            val first = addresses.firstOrNull() ?: return ""
            return ConnectionTarget.unmarshalFrom(first)?.host ?: ""
        }

    val port: Int
        get() {
            val first = addresses.firstOrNull() ?: return Defaults.ADB_PORT
            return ConnectionTarget.unmarshalFrom(first)?.port ?: Defaults.ADB_PORT
        }

    fun matchesAddress(target: ConnectionTarget) = addresses.any { addr ->
        val ct = ConnectionTarget.unmarshalFrom(addr)
        ct != null && ct.host == target.host && ct.port == target.port
    }

    fun matchesHost(host: String) = addresses.any { addr ->
        ConnectionTarget.unmarshalFrom(addr)?.host == host
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Parcelize
data class ConnectionTarget(
    val host: String,
    val port: Int = Defaults.ADB_PORT,
    val deviceId: Int? = null,  // USB设备ID，LAN连接时为null
    val connectionType: DeviceConnectionType = DeviceConnectionType.LAN
): Parcelable {
    /**
     * 输出连接目标的字符串表示
     * LAN格式：host:port 或 [host]:port
     * USB格式：usb:0x{VID}/0x{PID}#{deviceId}
     */
    override fun toString(): String = when (connectionType) {
        DeviceConnectionType.LAN -> {
            if (':' in host) "[$host]:$port"
            else "$host:$port"
        }
        DeviceConnectionType.USB -> {
            // USB格式：usb:0x{VID}/0x{PID}#{deviceId}
            // host存储VID:PID格式，deviceId单独存储
            val devicePart = if (deviceId != null) "#$deviceId" else ""
            "usb:$host$devicePart"
        }
    }

    companion object {
        /**
         * 从字符串解析连接目标
         * 支持格式：
         * - LAN: host:port, [host]:port
         * - USB: usb:0x{VID}/0x{PID}#{deviceId}
         */
        fun unmarshalFrom(s: String): ConnectionTarget? {
            val input = s.trim()
            
            // 检测USB格式
            if (input.startsWith("usb:", ignoreCase = true)) {
                return parseUsbTarget(input)
            }
            
            // 解析LAN格式
            return parseLanTarget(input)
        }
        
        /**
         * 解析USB连接目标
         * 格式：usb:0x{VID}/0x{PID}#{deviceId}
         */
        private fun parseUsbTarget(input: String): ConnectionTarget? {
            // 移除 "usb:" 前缀
            val usbPart = input.substring(4)
            
            // 分离 deviceId 部分（#后面的部分）
            val hashIndex = usbPart.indexOf('#')
            val vidPidPart: String
            val deviceId: Int?
            
            if (hashIndex >= 0) {
                vidPidPart = usbPart.substring(0, hashIndex)
                deviceId = usbPart.substring(hashIndex + 1).toIntOrNull()
            } else {
                vidPidPart = usbPart
                deviceId = null
            }
            
            // 验证VID/PID格式（0x{VID}/0x{PID}）
            if (!vidPidPart.matches(Regex("^0x[0-9A-Fa-f]{4}/0x[0-9A-Fa-f]{4}$"))) {
                return null
            }
            
            return ConnectionTarget(
                host = vidPidPart,  // 存储VID:PID格式
                port = 0,  // USB连接不使用端口
                deviceId = deviceId,
                connectionType = DeviceConnectionType.USB
            )
        }
        
        /**
         * 解析LAN连接目标
         * 格式：host:port, [host]:port
         */
        private fun parseLanTarget(input: String): ConnectionTarget? {
            val host: String
            val port: Int
            
            if (input.startsWith('[')) {
                val closeBracket = input.indexOf(']')
                if (closeBracket < 1) return null
                host = input.substring(1, closeBracket)
                port = if (input.length > closeBracket + 1 && input[closeBracket + 1] == ':')
                    input.substring(closeBracket + 2).toIntOrNull() ?: Defaults.ADB_PORT
                else
                    Defaults.ADB_PORT
            } else {
                val colonCount = input.count { it == ':' }
                if (colonCount > 1) {
                    host = input
                    port = Defaults.ADB_PORT
                } else {
                    val parts = input.split(":", limit = 2)
                    host = parts[0].trim()
                    port = if (parts.size >= 2)
                        parts[1].trim().toIntOrNull() ?: Defaults.ADB_PORT
                    else
                        Defaults.ADB_PORT
                }
            }
            
            return ConnectionTarget(
                host = host,
                port = port,
                deviceId = null,
                connectionType = DeviceConnectionType.LAN
            )
        }
    }
}
