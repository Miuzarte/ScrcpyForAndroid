package io.github.miuzarte.scrcpyforandroid.services

/**
 * 一次应用进程内的连接服务集合
 *
 * 与 [io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy] 一起由 [AppRuntime] 持有,
 * 跨 Activity 重建复用, 避免连接状态 / 自动重连被重置
 */
internal data class DeviceConnectionServices(
    val adbCoordinator: DeviceAdbConnectionCoordinator,
    val connectionStateStore: ConnectionStateStore,
    val connectionController: ConnectionController,
    val autoReconnectManager: DeviceAdbAutoReconnectManager,
)
