package io.github.miuzarte.scrcpyforandroid.services

import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ConnectedDeviceInfo(
    val model: String,
    val serial: String,
    val manufacturer: String,
    val brand: String,
    val device: String,
    val androidRelease: String,
    val sdkInt: Int,
)

/**
 * Fetch basic device properties from an already-connected device.
 *
 * Notes:
 * - This function issues multiple `shell getprop` commands via [adbService.shell].
 *   Each call may block on native I/O, so it should be called from a coroutine context.
 * - Returns a lightweight [ConnectedDeviceInfo] structure with commonly-used properties.
 */
internal suspend fun fetchConnectedDeviceInfo(
    adbService: NativeAdbService,
    host: String,
    port: Int
): ConnectedDeviceInfo = withContext(Dispatchers.IO) {
    suspend fun prop(name: String): String = runCatching {
        adbService.shell("getprop $name").trim()
    }.getOrDefault("")

    ConnectedDeviceInfo(
        model = prop("ro.product.model").ifBlank { "$host:$port" },
        serial = prop("ro.serialno"),
        manufacturer = prop("ro.product.manufacturer"),
        brand = prop("ro.product.brand"),
        device = prop("ro.product.device"),
        androidRelease = prop("ro.build.version.release"),
        sdkInt = prop("ro.build.version.sdk").toIntOrNull() ?: -1,
    )
}
