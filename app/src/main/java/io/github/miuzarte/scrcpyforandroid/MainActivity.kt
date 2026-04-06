package io.github.miuzarte.scrcpyforandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.miuzarte.scrcpyforandroid.pages.MainPage
import io.github.miuzarte.scrcpyforandroid.storage.PreferenceMigration
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // initialize settings singleton
        Storage.init(applicationContext)

        val migration = PreferenceMigration(applicationContext)
        runBlocking {
            if (migration.needsMigration())
                migration.migrate(clearSharedPrefs = true)
        }

        enableEdgeToEdge()

        setContent {
            MainPage()
        }
    }
}
