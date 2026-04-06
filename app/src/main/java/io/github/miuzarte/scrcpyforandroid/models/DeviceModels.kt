package io.github.miuzarte.scrcpyforandroid.models

import io.github.miuzarte.scrcpyforandroid.constants.Defaults

// Composable 用, 不可变 List
class DeviceShortcuts(val devices: List<DeviceShortcut>) : List<DeviceShortcut> by devices {
    fun marshalToString(
        separator: String = DEFAULT_SEPARATOR,
    ): String = joinToString(separator) { it.marshalToString() }

    companion object {
        const val DEFAULT_SEPARATOR = "\n"

        fun unmarshalFrom(
            s: String,
            separator: String = DEFAULT_SEPARATOR,
        ): DeviceShortcuts {
            if (s.isBlank()) return DeviceShortcuts(emptyList())
            val list = s.splitToSequence(separator)
                .mapNotNull { DeviceShortcut.unmarshalFrom(it) }
                .toList()
            return DeviceShortcuts(list)
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
        online: Boolean? = null,
        newPort: Int? = null,
        updateNameOnlyWhenEmpty: Boolean = false,
    ): DeviceShortcuts {
        val idx = if (id != null) getIndex(id)
        else if (host != null && port != null) getIndex(host, port)
        else -1

        if (idx < 0) return this
        val old = devices[idx]

        // 确定最终的属性值
        val finalName = when {
            name == null -> old.name
            updateNameOnlyWhenEmpty && old.name.isNotBlank() -> old.name
            else -> name
        }
        val finalPort = newPort ?: old.port
        val finalOnline = online ?: old.online

        // 若无任何变化，返回原实例
        if (finalName == old.name && finalPort == old.port && finalOnline == old.online)
            return this

        val newList = devices.toMutableList().apply {
            this[idx] = DeviceShortcut(
                name = finalName,
                host = old.host,
                port = finalPort,
                online = finalOnline
            )
        }
        return DeviceShortcuts(
            if (newPort != null && newPort != old.port)
                newList.distinctBy { it.id }
            else newList
        )
    }

    fun upsert(
        shortcut: DeviceShortcut,
        index: Int? = null,
    ): DeviceShortcuts {
        val existingIdx = getIndex(shortcut.id)
        val newList = devices.toMutableList()
        if (existingIdx >= 0) {
            newList[existingIdx] = shortcut
        } else {
            if (index != null) newList.add(index, shortcut)
            else newList.add(shortcut)
        }
        return DeviceShortcuts(newList)
    }

    fun move(fromIndex: Int, toIndex: Int): DeviceShortcuts {
        if (fromIndex !in devices.indices || toIndex !in devices.indices) return this
        if (fromIndex == toIndex) return this
        val mutable = devices.toMutableList()
        val item = mutable.removeAt(fromIndex)
        // 如果目标位置在原位置之后，移除后列表长度减1，因此目标索引需减1
        val target = if (toIndex > fromIndex) toIndex - 1 else toIndex
        mutable.add(target, item)
        return DeviceShortcuts(mutable)
    }

    // 删除指定设备
    fun remove(id: String) = DeviceShortcuts(devices.filterNot { it.id == id })

    // 清空所有设备
    fun clear() = DeviceShortcuts(emptyList())

    // 复制当前实例
    fun copy(devices: List<DeviceShortcut> = this.devices): DeviceShortcuts =
        DeviceShortcuts(devices)
}

data class DeviceShortcut(
    val name: String = "",
    val host: String,
    val port: Int = Defaults.ADB_PORT,
    val online: Boolean = false,
) {
    val id: String get() = "$host:$port"

    fun marshalToString(
        separator: String = DEFAULT_SEPARATOR,
    ): String = listOf(
        name.trim(), host.trim(), port.toString()
    ).joinToString(
        separator = separator
    )

    companion object {
        const val DEFAULT_SEPARATOR = "|"
        fun unmarshalFrom(
            s: String,
            separator: String = DEFAULT_SEPARATOR,
        ): DeviceShortcut? {
            val parts = s.split(separator, limit = 3)
            return when (parts.size) {
                3 -> {
                    val name = parts[0].trim()
                    val host = parts[1].trim()
                    val port = parts[2].trim().toIntOrNull() ?: Defaults.ADB_PORT
                    if (host.isNotBlank()) DeviceShortcut(
                        name = name,
                        host = host,
                        port = port,
                    )
                    else null
                }

                else -> null
            }
        }
    }
}

data class ConnectionTarget(
    val host: String,
    val port: Int = Defaults.ADB_PORT,
) {
    override fun toString(): String = "$host:$port"

    companion object {
        fun unmarshalFrom(s: String): ConnectionTarget? {
            val parts = s.split(":", limit = 2)
            return when (parts.size) {
                2 -> ConnectionTarget(
                    host = parts[0].trim(),
                    port = parts[1].trim().toIntOrNull() ?: Defaults.ADB_PORT,
                )

                1 -> ConnectionTarget(
                    host = parts[0].trim(),
                    port = Defaults.ADB_PORT,
                )

                0 -> ConnectionTarget(
                    host = s.trim(),
                    port = Defaults.ADB_PORT,
                )

                else -> null
            }
        }
    }
}
