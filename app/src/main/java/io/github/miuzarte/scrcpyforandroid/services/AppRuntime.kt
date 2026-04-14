package io.github.miuzarte.scrcpyforandroid.services

import android.content.Context
import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.nativecore.AdbMdnsDiscoverer
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy

// 用于不同 activity 之间传递实例
object AppRuntime {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        AdbMdnsDiscoverer.init(appContext)
    }

    val context: Context
        get() = appContext

    var scrcpy: Scrcpy? = null
    var currentConnectionTarget: ConnectionTarget? = null
}
