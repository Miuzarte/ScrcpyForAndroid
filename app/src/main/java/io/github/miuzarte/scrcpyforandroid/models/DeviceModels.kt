package io.github.miuzarte.scrcpyforandroid.models

import android.os.Parcelable
import io.github.miuzarte.scrcpyforandroid.constants.Defaults
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyOptions
import kotlinx.parcelize.Parcelize

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
            var nextLegacyId = 1
            val list = s.splitToSequence(separator)
                .mapNotNull { raw ->
                    val firstValue = raw.split(DeviceShortcut.DEFAULT_SEPARATOR, limit = 2)
                        .firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (firstValue.toIntOrNull() != null) {
                        DeviceShortcut.unmarshalFrom(raw)
                    } else {
                        DeviceShortcut.unmarshalFrom(
                            s = raw,
                            fallbackId = nextLegacyId.toString(),
                        ).also {
                            if (it != null) nextLegacyId++
                        }
                    }
                }
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

        val updated = DeviceShortcut(
            id = old.id,
            name = when {
                name == null -> old.name
                updateNameOnlyWhenEmpty && old.name.isNotBlank() -> old.name
                else -> name
            },
            host = if (updateById) host ?: old.host else old.host,
            port = if (updateById) port ?: old.port else newPort ?: old.port,
            startScrcpyOnConnect = startScrcpyOnConnect ?: old.startScrcpyOnConnect,
            openFullscreenOnStart = openFullscreenOnStart ?: old.openFullscreenOnStart,
            scrcpyProfileId = scrcpyProfileId ?: old.scrcpyProfileId,
        )

        // 若无任何变化，返回原实例
        if (updated == old) return this

        val newList = devices.toMutableList()
            .apply {
                this[idx] = updated
            }
        return DeviceShortcuts(
            if ((updateById && (updated.host != old.host || updated.port != old.port))
                || (newPort != null && newPort != old.port)
            )
                newList.distinctBy { it.id }
            else newList
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
            // Keep existing id stable when matching by host:port.
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
    val id: String = "",
    val name: String = "",
    val host: String,
    val port: Int = Defaults.ADB_PORT,
    val startScrcpyOnConnect: Boolean = false,
    val openFullscreenOnStart: Boolean = false,
    val scrcpyProfileId: String = ScrcpyOptions.GLOBAL_PROFILE_ID,
) {
    fun marshalToString(
        separator: String = DEFAULT_SEPARATOR,
    ): String = listOf(
        id.trim(),
        name.trim(),
        host.trim(),
        port.toString(),
        if (startScrcpyOnConnect) "1" else "0",
        if (openFullscreenOnStart) "1" else "0",
        scrcpyProfileId.trim(),
    ).joinToString(
        separator = separator
    )

    companion object {
        const val DEFAULT_SEPARATOR = "|"
        fun unmarshalFrom(
            s: String,
            separator: String = DEFAULT_SEPARATOR,
            fallbackId: String? = null,
        ): DeviceShortcut? {
            val parts = s.split(separator)
            val idInData = parts.firstOrNull()
                ?.trim()
                ?.takeIf { it.toIntOrNull() != null }
            return if (idInData != null) when (parts.size) {
                4, 5, 6, 7 -> {
                    val name = parts[1].trim()
                    val host = parts[2].trim()
                    val port = parts[3].trim().toIntOrNull() ?: Defaults.ADB_PORT

                    val startScrcpyOnConnect = parts.getOrNull(4)
                        ?.trim() == "1"
                    val openFullscreenOnStart = startScrcpyOnConnect
                            && parts.getOrNull(5)
                        ?.trim() == "1"
                    val scrcpyProfileId = parts.getOrNull(6)
                        ?.trim()
                        .takeUnless { it.isNullOrBlank() }
                        ?: ScrcpyOptions.GLOBAL_PROFILE_ID

                    if (host.isNotBlank()) DeviceShortcut(
                        id = idInData,
                        name = name,
                        host = host,
                        port = port,
                        startScrcpyOnConnect = startScrcpyOnConnect,
                        openFullscreenOnStart = openFullscreenOnStart,
                        scrcpyProfileId = scrcpyProfileId,
                    )
                    else null
                }

                else -> null
            }
            else when (parts.size) {
                3, 4, 5, 6 -> {
                    val id = fallbackId ?: return null
                    val name = parts[0].trim()
                    val host = parts[1].trim()
                    val port = parts[2].trim().toIntOrNull() ?: Defaults.ADB_PORT

                    val startScrcpyOnConnect = parts.getOrNull(3)
                        ?.trim() == "1"
                    val openFullscreenOnStart = startScrcpyOnConnect
                            && parts.getOrNull(4)
                        ?.trim() == "1"
                    val scrcpyProfileId = parts.getOrNull(5)
                        ?.trim()
                        .takeUnless { it.isNullOrBlank() }
                        ?: ScrcpyOptions.GLOBAL_PROFILE_ID

                    if (host.isNotBlank()) DeviceShortcut(
                        id = id,
                        name = name,
                        host = host,
                        port = port,
                        startScrcpyOnConnect = startScrcpyOnConnect,
                        openFullscreenOnStart = openFullscreenOnStart,
                        scrcpyProfileId = scrcpyProfileId,
                    )
                    else null
                }

                else -> null
            }
        }
    }
}

@Parcelize
data class ConnectionTarget(
    val host: String,
    val port: Int = Defaults.ADB_PORT,
) : Parcelable {
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
