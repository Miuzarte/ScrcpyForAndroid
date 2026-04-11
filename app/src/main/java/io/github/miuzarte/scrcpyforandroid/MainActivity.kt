package io.github.miuzarte.scrcpyforandroid

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import io.github.miuzarte.scrcpyforandroid.pages.MainScreen
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.storage.PreferenceMigration
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // initialize settings singleton
        Storage.init(applicationContext)
        AppRuntime.init(applicationContext)

        val migration = PreferenceMigration(applicationContext)
        runBlocking {
            if (migration.needsMigration())
                migration.migrate(clearSharedPrefs = true)
        }

        enableEdgeToEdge()

        setContent {
            MainScreen()
        }
    }

    override fun onResume() {
        super.onResume()
        StreamActivity.dismissActivePictureInPicture()
    }
}
