package io.github.miuzarte.scrcpyforandroid.services

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.runBlocking

// not working in MIUI
class PictureInPictureActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_STOP_SCRCPY) return
        val pendingResult = goAsync()
        Thread {
            try {
                val appContext = context.applicationContext
                AppRuntime.init(appContext)
                AppWakeLocks.init(appContext)
                runBlocking {
                    AppRuntime.scrcpy?.stop()
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_STOP_SCRCPY =
            "io.github.miuzarte.scrcpyforandroid.action.STOP_SCRCPY_FROM_PIP"
        private const val REQUEST_CODE_STOP_SCRCPY = 1

        fun createIntentFilter(): IntentFilter = IntentFilter(ACTION_STOP_SCRCPY)

        fun createPendingIntent(context: Context): PendingIntent {
            val intent = Intent(ACTION_STOP_SCRCPY).setPackage(context.packageName)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_STOP_SCRCPY,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
