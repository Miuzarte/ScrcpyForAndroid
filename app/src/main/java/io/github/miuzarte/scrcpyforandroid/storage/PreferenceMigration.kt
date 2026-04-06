package io.github.miuzarte.scrcpyforandroid.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import io.github.miuzarte.scrcpyforandroid.constants.AppDefaults
import io.github.miuzarte.scrcpyforandroid.constants.AppPreferenceKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PreferenceMigration"

/**
 * 从旧的 SharedPreferences 迁移到新的 DataStore
 */
class PreferenceMigration(private val appContext: Context) {
    private val appSharedPrefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val sharedPrefs: SharedPreferences by lazy {
        appContext.getSharedPreferences("nativecore_adb_rsa", Context.MODE_PRIVATE)
    }

    /**
     * 检查是否需要迁移（SharedPreferences 是否包含数据）
     */
    suspend fun needsMigration(): Boolean = withContext(Dispatchers.IO) {
        appSharedPrefs.all.isNotEmpty() || sharedPrefs.all.isNotEmpty()
    }

    /**
     * 执行完整迁移
     */
    suspend fun migrate(
        clearSharedPrefs: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        if (!needsMigration()) {
            Log.i(TAG, "No data to migrate, skipping")
            return@withContext
        } else {
            val appList = appSharedPrefs.all.entries.joinToString { (k, v) -> "\"$k\" to $v" }
            val adbList = sharedPrefs.all.entries.joinToString { (k, v) -> "\"$k\" to $v" }
            Log.d(TAG, "Migrating appSharedPrefs ($appList)")
        }

        Log.i(TAG, "Starting migration from SharedPreferences to DataStore")

        // 迁移 AppSettings
        migrateAppSettings()

        // 迁移 ScrcpyOptions
        migrateScrcpyOptions()

        // 迁移 QuickDevices
        migrateQuickDevices()

        // 迁移 ADB 密钥
        migrateAdbClientData()

        // 清空 SharedPreferences
        if (clearSharedPrefs) {
            appSharedPrefs.edit { clear() }
            sharedPrefs.edit { clear() }
            Log.d(TAG, "SharedPreferences cleared")
        }

        Log.i(
            TAG, "Migration completed successfully" +
                    " and SharedPreferences ${if (clearSharedPrefs) "" else "is not "}cleared"
        )
    }

    /**
     * 迁移应用设置
     */
    private suspend fun migrateAppSettings() {
        val appSettings = Storage.appSettings

        // Theme Settings
        migrateInt(
            AppPreferenceKeys.THEME_BASE_INDEX,
            AppDefaults.THEME_BASE_INDEX,
            appSettings.themeBaseIndex
        )
        migrateBoolean(
            AppPreferenceKeys.MONET,
            AppDefaults.MONET,
            appSettings.monet
        )

        // Scrcpy Settings
        migrateBoolean(
            AppPreferenceKeys.FULLSCREEN_DEBUG_INFO,
            AppDefaults.FULLSCREEN_DEBUG_INFO,
            appSettings.fullscreenDebugInfo
        )
        migrateBoolean(
            AppPreferenceKeys.SHOW_FULLSCREEN_VIRTUAL_BUTTONS,
            AppDefaults.SHOW_FULLSCREEN_VIRTUAL_BUTTONS,
            appSettings.showFullscreenVirtualButtons
        )
        migrateBoolean(
            AppPreferenceKeys.KEEP_SCREEN_ON_WHEN_STREAMING,
            AppDefaults.KEEP_SCREEN_ON_WHEN_STREAMING,
            appSettings.keepScreenOnWhenStreaming
        )
        migrateInt(
            AppPreferenceKeys.DEVICE_PREVIEW_CARD_HEIGHT_DP,
            AppDefaults.DEVICE_PREVIEW_CARD_HEIGHT_DP,
            appSettings.devicePreviewCardHeightDp
        )
        migrateBoolean(
            AppPreferenceKeys.PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT,
            AppDefaults.PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT,
            appSettings.previewVirtualButtonShowText
        )
        migrateString(
            AppPreferenceKeys.VIRTUAL_BUTTONS_LAYOUT,
            AppDefaults.VIRTUAL_BUTTONS_LAYOUT,
            appSettings.virtualButtonsLayout
        )

        // Scrcpy Server Settings
        migrateString(
            AppPreferenceKeys.CUSTOM_SERVER_URI,
            AppDefaults.CUSTOM_SERVER_URI ?: "",
            appSettings.customServerUri
        )
        migrateString(
            AppPreferenceKeys.SERVER_REMOTE_PATH,
            AppDefaults.SERVER_REMOTE_PATH_INPUT,
            appSettings.serverRemotePath
        )

        // ADB Settings
        migrateString(
            AppPreferenceKeys.ADB_KEY_NAME,
            AppDefaults.ADB_KEY_NAME_INPUT,
            appSettings.adbKeyName
        )
        migrateBoolean(
            AppPreferenceKeys.ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN,
            AppDefaults.ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN,
            appSettings.adbPairingAutoDiscoverOnDialogOpen
        )
        migrateBoolean(
            AppPreferenceKeys.ADB_AUTO_RECONNECT_PAIRED_DEVICE,
            AppDefaults.ADB_AUTO_RECONNECT_PAIRED_DEVICE,
            appSettings.adbAutoReconnectPairedDevice
        )
        migrateBoolean(
            AppPreferenceKeys.ADB_MDNS_LAN_DISCOVERY,
            AppDefaults.ADB_MDNS_LAN_DISCOVERY,
            appSettings.adbMdnsLanDiscovery
        )

        Log.d(TAG, "AppSettings migration completed")
    }

    /**
     * 迁移 Scrcpy 选项
     */
    private suspend fun migrateScrcpyOptions() {
        val scrcpyOptions = Storage.scrcpyOptions

        // Audio & Video Codecs
        migrateString(
            AppPreferenceKeys.AUDIO_CODEC,
            AppDefaults.AUDIO_CODEC,
            scrcpyOptions.audioCodec
        )
        migrateString(
            AppPreferenceKeys.VIDEO_CODEC,
            AppDefaults.VIDEO_CODEC,
            scrcpyOptions.videoCodec
        )

        // Bit Rates
        val audioBitRateKbps = sharedPrefs.getInt(
            AppPreferenceKeys.AUDIO_BIT_RATE_KBPS,
            AppDefaults.AUDIO_BIT_RATE_KBPS
        )
        scrcpyOptions.audioBitRate.set(audioBitRateKbps * 1000) // Convert to bps

        val videoBitRateMbps = sharedPrefs.getFloat(
            AppPreferenceKeys.VIDEO_BIT_RATE_MBPS,
            AppDefaults.VIDEO_BIT_RATE_MBPS
        )
        scrcpyOptions.videoBitRate.set((videoBitRateMbps * 1_000_000).toInt())

        // Control Options
        migrateBoolean(
            AppPreferenceKeys.TURN_SCREEN_OFF,
            AppDefaults.TURN_SCREEN_OFF,
            scrcpyOptions.turnScreenOff
        )
        migrateBoolean(
            AppPreferenceKeys.NO_CONTROL,
            AppDefaults.NO_CONTROL
        ) { value ->
            scrcpyOptions.control.set(!value) // Invert logic
        }
        migrateBoolean(
            AppPreferenceKeys.NO_VIDEO,
            AppDefaults.NO_VIDEO
        ) { value ->
            scrcpyOptions.video.set(!value) // Invert logic
        }

        // Video Source
        val videoSourcePreset = sharedPrefs.getString(
            AppPreferenceKeys.VIDEO_SOURCE_PRESET,
            AppDefaults.VIDEO_SOURCE_PRESET
        ).orEmpty().ifBlank { AppDefaults.VIDEO_SOURCE_PRESET }
        scrcpyOptions.videoSource.set(videoSourcePreset)

        migrateString(
            AppPreferenceKeys.DISPLAY_ID,
            AppDefaults.DISPLAY_ID
        ) { value ->
            value.toIntOrNull()?.let { scrcpyOptions.displayId.set(it) }
        }

        // Camera Settings
        migrateString(
            AppPreferenceKeys.CAMERA_ID,
            AppDefaults.CAMERA_ID,
            scrcpyOptions.cameraId
        )
        migrateString(
            AppPreferenceKeys.CAMERA_FACING_PRESET,
            AppDefaults.CAMERA_FACING_PRESET,
            scrcpyOptions.cameraFacing
        )
        migrateString(
            AppPreferenceKeys.CAMERA_SIZE_PRESET,
            AppDefaults.CAMERA_SIZE_PRESET
        ) { value ->
            if (value == "custom") {
                val customSize = sharedPrefs.getString(
                    AppPreferenceKeys.CAMERA_SIZE_CUSTOM,
                    AppDefaults.CAMERA_SIZE_CUSTOM
                ).orEmpty()
                scrcpyOptions.cameraSize.set(customSize)
            } else {
                scrcpyOptions.cameraSize.set(value)
            }
        }
        migrateString(
            AppPreferenceKeys.CAMERA_AR,
            AppDefaults.CAMERA_AR,
            scrcpyOptions.cameraAr
        )
        migrateString(
            AppPreferenceKeys.CAMERA_FPS,
            AppDefaults.CAMERA_FPS
        ) { value ->
            value.toIntOrNull()?.let { scrcpyOptions.cameraFps.set(it) }
        }
        migrateBoolean(
            AppPreferenceKeys.CAMERA_HIGH_SPEED,
            AppDefaults.CAMERA_HIGH_SPEED,
            scrcpyOptions.cameraHighSpeed
        )

        // Audio Source
        val audioSourcePreset = sharedPrefs.getString(
            AppPreferenceKeys.AUDIO_SOURCE_PRESET,
            AppDefaults.AUDIO_SOURCE_PRESET
        ).orEmpty().ifBlank { AppDefaults.AUDIO_SOURCE_PRESET }

        if (audioSourcePreset == "custom") {
            val customSource = sharedPrefs.getString(
                AppPreferenceKeys.AUDIO_SOURCE_CUSTOM,
                AppDefaults.AUDIO_SOURCE_CUSTOM
            ).orEmpty()
            scrcpyOptions.audioSource.set(customSource)
        } else {
            scrcpyOptions.audioSource.set(audioSourcePreset)
        }

        migrateBoolean(
            AppPreferenceKeys.AUDIO_DUP,
            AppDefaults.AUDIO_DUP,
            scrcpyOptions.audioDup
        )
        migrateBoolean(
            AppPreferenceKeys.NO_AUDIO_PLAYBACK,
            AppDefaults.NO_AUDIO_PLAYBACK
        ) { value ->
            scrcpyOptions.audioPlayback.set(!value) // Invert logic
        }
        migrateBoolean(
            AppPreferenceKeys.REQUIRE_AUDIO,
            AppDefaults.REQUIRE_AUDIO,
            scrcpyOptions.requireAudio
        )

        // Max Size & FPS
        migrateString(
            AppPreferenceKeys.MAX_SIZE_INPUT,
            AppDefaults.MAX_SIZE_INPUT
        ) { value ->
            value.toIntOrNull()?.let { scrcpyOptions.maxSize.set(it) }
        }
        migrateString(
            AppPreferenceKeys.MAX_FPS_INPUT,
            AppDefaults.MAX_FPS_INPUT,
            scrcpyOptions.maxFps
        )

        // Encoders & Codec Options
        migrateString(
            AppPreferenceKeys.VIDEO_ENCODER,
            AppDefaults.VIDEO_ENCODER,
            scrcpyOptions.videoEncoder
        )
        migrateString(
            AppPreferenceKeys.VIDEO_CODEC_OPTION,
            AppDefaults.VIDEO_CODEC_OPTION,
            scrcpyOptions.videoCodecOptions
        )
        migrateString(
            AppPreferenceKeys.AUDIO_ENCODER,
            AppDefaults.AUDIO_ENCODER,
            scrcpyOptions.audioEncoder
        )
        migrateString(
            AppPreferenceKeys.AUDIO_CODEC_OPTION,
            AppDefaults.AUDIO_CODEC_OPTION,
            scrcpyOptions.audioCodecOptions
        )

        // New Display
        val newDisplayWidth = sharedPrefs.getString(
            AppPreferenceKeys.NEW_DISPLAY_WIDTH,
            AppDefaults.NEW_DISPLAY_WIDTH
        ).orEmpty()
        val newDisplayHeight = sharedPrefs.getString(
            AppPreferenceKeys.NEW_DISPLAY_HEIGHT,
            AppDefaults.NEW_DISPLAY_HEIGHT
        ).orEmpty()
        val newDisplayDpi = sharedPrefs.getString(
            AppPreferenceKeys.NEW_DISPLAY_DPI,
            AppDefaults.NEW_DISPLAY_DPI
        ).orEmpty()

        if (newDisplayWidth.isNotBlank() && newDisplayHeight.isNotBlank()) {
            val newDisplay = if (newDisplayDpi.isNotBlank()) {
                "${newDisplayWidth}x${newDisplayHeight}/${newDisplayDpi}"
            } else {
                "${newDisplayWidth}x${newDisplayHeight}"
            }
            scrcpyOptions.newDisplay.set(newDisplay)
        }

        // Crop
        val cropWidth = sharedPrefs.getString(
            AppPreferenceKeys.CROP_WIDTH,
            AppDefaults.CROP_WIDTH
        ).orEmpty()
        val cropHeight = sharedPrefs.getString(
            AppPreferenceKeys.CROP_HEIGHT,
            AppDefaults.CROP_HEIGHT
        ).orEmpty()
        val cropX = sharedPrefs.getString(
            AppPreferenceKeys.CROP_X,
            AppDefaults.CROP_X
        ).orEmpty()
        val cropY = sharedPrefs.getString(
            AppPreferenceKeys.CROP_Y,
            AppDefaults.CROP_Y
        ).orEmpty()

        if (cropWidth.isNotBlank() && cropHeight.isNotBlank() &&
            cropX.isNotBlank() && cropY.isNotBlank()
        ) {
            scrcpyOptions.crop.set("${cropWidth}:${cropHeight}:${cropX}:${cropY}")
        }

        // Audio enabled flag (convert to audio option)
        val audioEnabled = sharedPrefs.getBoolean(
            AppPreferenceKeys.AUDIO_ENABLED,
            AppDefaults.AUDIO_ENABLED
        )
        scrcpyOptions.audio.set(audioEnabled)

        Log.d(TAG, "ScrcpyOptions migration completed")
    }

    /**
     * 迁移快速设备列表
     */
    private suspend fun migrateQuickDevices() {
        val quickDevices = Storage.quickDevices

        // Migrate quick devices list
        val quickDevicesRaw = appSharedPrefs.getString(
            AppPreferenceKeys.QUICK_DEVICES,
            ""
        ).orEmpty()
        if (quickDevicesRaw.isNotBlank()) {
            quickDevices.quickDevicesList.set(quickDevicesRaw)
        }

        // Migrate quick connect input
        migrateString(
            AppPreferenceKeys.QUICK_CONNECT_INPUT,
            AppDefaults.QUICK_CONNECT_INPUT,
            quickDevices.quickConnectInput
        )

        Log.d(TAG, "QuickDevices migration completed")
    }
    
    /**
     * 迁移 ADB 客户端数据（RSA 密钥）
     */
    private suspend fun migrateAdbClientData() {
        val adbClientData = Storage.adbClientData
        
        // 迁移 RSA 私钥
        val privKey = sharedPrefs.getString("priv", null)
        if (privKey != null) {
            adbClientData.rsaPrivateKey.set(privKey)
            Log.d(TAG, "ADB RSA private key migrated")
        }
        
        Log.d(TAG, "AdbClientData migration completed")
    }

    // Helper methods for different data types

    private suspend fun migrateString(
        key: String,
        defaultValue: String,
        settingProperty: Settings.SettingProperty<String>
    ) {
        val value = appSharedPrefs.getString(key, defaultValue)
            .orEmpty()
            .ifBlank { defaultValue }
        settingProperty.set(value)
    }

    private suspend fun migrateString(
        key: String,
        defaultValue: String,
        action: suspend (String) -> Unit
    ) {
        val value = appSharedPrefs.getString(key, defaultValue)
            .orEmpty()
            .ifBlank { defaultValue }
        action(value)
    }

    private suspend fun migrateInt(
        key: String,
        defaultValue: Int,
        settingProperty: Settings.SettingProperty<Int>
    ) {
        val value = appSharedPrefs.getInt(key, defaultValue)
        settingProperty.set(value)
    }

    private suspend fun migrateBoolean(
        key: String,
        defaultValue: Boolean,
        settingProperty: Settings.SettingProperty<Boolean>
    ) {
        val value = appSharedPrefs.getBoolean(key, defaultValue)
        settingProperty.set(value)
    }

    private suspend fun migrateBoolean(
        key: String,
        defaultValue: Boolean,
        action: suspend (Boolean) -> Unit
    ) {
        val value = appSharedPrefs.getBoolean(key, defaultValue)
        action(value)
    }
}
