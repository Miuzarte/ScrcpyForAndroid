package io.github.miuzarte.scrcpyforandroid.models

internal data class ConnectionTarget(
    val host: String,
    val port: Int,
)

/**
 * A compact shortcut entry for quick-connect lists shown in the UI.
 * `online` indicates whether the device was reachable when last probed.
 */
internal data class DeviceShortcut(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val online: Boolean,
)
