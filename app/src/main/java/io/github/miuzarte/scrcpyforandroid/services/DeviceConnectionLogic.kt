package io.github.miuzarte.scrcpyforandroid.services

import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ConnectedDeviceInfo(
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
 * - This function issues multiple `getprop` commands over a single reusable shell session
 *   to avoid reopening a new adb shell stream for every property.
 * - Returns a lightweight [ConnectedDeviceInfo] structure with commonly-used properties.
 */
internal suspend fun fetchConnectedDeviceInfo(
    adbService: NativeAdbService,
    host: String,
    port: Int,
): ConnectedDeviceInfo = withContext(Dispatchers.IO) {
    val values = runCatching {
        adbService.shellBatch {
            command("getprop ro.product.model")
            command("getprop ro.serialno")
            command("getprop ro.product.manufacturer")
            command("getprop ro.product.brand")
            command("getprop ro.product.device")
            command("getprop ro.build.version.release")
            command("getprop ro.build.version.sdk")
        }
    }.getOrDefault(emptyList())

    ConnectedDeviceInfo(
        model = values.getOrNull(0).orEmpty().trim().ifBlank { "$host:$port" },
        serial = values.getOrNull(1).orEmpty().trim(),
        manufacturer = values.getOrNull(2).orEmpty().trim(),
        brand = values.getOrNull(3).orEmpty().trim(),
        device = values.getOrNull(4).orEmpty().trim(),
        androidRelease = values.getOrNull(5).orEmpty().trim(),
        sdkInt = values.getOrNull(6).orEmpty().trim().toIntOrNull() ?: -1,
    )
}
