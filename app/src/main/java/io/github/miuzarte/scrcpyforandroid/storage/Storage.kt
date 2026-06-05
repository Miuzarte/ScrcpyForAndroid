package io.github.miuzarte.scrcpyforandroid.storage

import io.github.miuzarte.scrcpyforandroid.services.AppRuntime

// settings singleton
object Storage {
    val appSettings: AppSettings by lazy { AppSettings(AppRuntime.context) }
    val quickDevices: QuickDevices by lazy { QuickDevices(AppRuntime.context) }
    val scrcpyOptions: ScrcpyOptions by lazy { ScrcpyOptions(AppRuntime.context) }
    val scrcpyProfiles: ScrcpyProfiles by lazy { ScrcpyProfiles(AppRuntime.context) }
    val adbClientData: AdbClientData by lazy { AdbClientData(AppRuntime.context) }
    val mtlsData: MtlsData by lazy { MtlsData(AppRuntime.context) }
}
