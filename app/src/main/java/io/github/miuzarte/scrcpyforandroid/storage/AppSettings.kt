package io.github.miuzarte.scrcpyforandroid.storage

import android.content.Context
import android.os.Parcelable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import kotlinx.coroutines.flow.StateFlow
import kotlinx.parcelize.Parcelize

class AppSettings(context: Context) : Settings(context, "AppSettings") {
    companion object {
        val THEME_BASE_INDEX = Pair(
            intPreferencesKey("theme_base_index"),
            0
        )
        val MONET = Pair(
            booleanPreferencesKey("monet"),
            false
        )
        val FULLSCREEN_DEBUG_INFO = Pair(
            booleanPreferencesKey("fullscreen_debug_info"),
            false
        )
        val SHOW_FULLSCREEN_VIRTUAL_BUTTONS = Pair(
            booleanPreferencesKey("show_fullscreen_virtual_buttons"),
            true
        )
        val KEEP_SCREEN_ON_WHEN_STREAMING = Pair(
            booleanPreferencesKey("keep_screen_on_when_streaming"),
            false
        )
        val DEVICE_PREVIEW_CARD_HEIGHT_DP = Pair(
            intPreferencesKey("device_preview_card_height_dp"),
            320
        )
        val PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT = Pair(
            booleanPreferencesKey("preview_virtual_button_show_text"),
            true
        )
        val VIRTUAL_BUTTONS_LAYOUT = Pair(
            stringPreferencesKey("virtual_buttons_layout"),
            "more:1,app_switch:1,home:0,back:1,menu:0,notification:0,volume_up:0,volume_down:0,volume_mute:0,power:0,screenshot:0"
        )
        val CUSTOM_SERVER_URI = Pair(
            stringPreferencesKey("custom_server_uri"),
            ""
        )
        val CUSTOM_SERVER_VERSION = Pair(
            stringPreferencesKey("custom_server_version"),
            ""
        )
        val SERVER_REMOTE_PATH = Pair(
            stringPreferencesKey("server_remote_path"),
            Scrcpy.DEFAULT_REMOTE_PATH,
        )
        val ADB_KEY_NAME = Pair(
            stringPreferencesKey("adb_key_name"),
            "scrcpy"
        )
        val ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN = Pair(
            booleanPreferencesKey("adb_pairing_auto_discover_on_dialog_open"),
            true
        )
        val ADB_AUTO_RECONNECT_PAIRED_DEVICE = Pair(
            booleanPreferencesKey("adb_auto_reconnect_paired_device"),
            true
        )
        val ADB_MDNS_LAN_DISCOVERY = Pair(
            booleanPreferencesKey("adb_mdns_lan_discovery"),
            true
        )
    }

    // Theme Settings
    val themeBaseIndex by setting(THEME_BASE_INDEX)
    val monet by setting(MONET)

    // Scrcpy Settings
    val fullscreenDebugInfo by setting(FULLSCREEN_DEBUG_INFO)
    val showFullscreenVirtualButtons by setting(SHOW_FULLSCREEN_VIRTUAL_BUTTONS)
    val keepScreenOnWhenStreaming by setting(KEEP_SCREEN_ON_WHEN_STREAMING)
    val devicePreviewCardHeightDp by setting(DEVICE_PREVIEW_CARD_HEIGHT_DP)
    val previewVirtualButtonShowText by setting(PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT)
    val virtualButtonsLayout by setting(VIRTUAL_BUTTONS_LAYOUT)

    // Scrcpy Server Settings
    val customServerUri by setting(CUSTOM_SERVER_URI)
    val customServerVersion by setting(CUSTOM_SERVER_VERSION)
    val serverRemotePath by setting(SERVER_REMOTE_PATH)

    // ADB Settings
    val adbKeyName by setting(ADB_KEY_NAME)
    val adbPairingAutoDiscoverOnDialogOpen by setting(ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN)
    val adbAutoReconnectPairedDevice by setting(ADB_AUTO_RECONNECT_PAIRED_DEVICE)
    val adbMdnsLanDiscovery by setting(ADB_MDNS_LAN_DISCOVERY)

    @Parcelize
    data class Bundle(
        val themeBaseIndex: Int,
        val monet: Boolean,
        val fullscreenDebugInfo: Boolean,
        val showFullscreenVirtualButtons: Boolean,
        val keepScreenOnWhenStreaming: Boolean,
        val devicePreviewCardHeightDp: Int,
        val previewVirtualButtonShowText: Boolean,
        val virtualButtonsLayout: String,
        val customServerUri: String,
        val customServerVersion: String,
        val serverRemotePath: String,
        val adbKeyName: String,
        val adbPairingAutoDiscoverOnDialogOpen: Boolean,
        val adbAutoReconnectPairedDevice: Boolean,
        val adbMdnsLanDiscovery: Boolean,
    ) : Parcelable {
    }

    private val bundleFields = arrayOf(
        bundleField(THEME_BASE_INDEX) { bundle: Bundle -> bundle.themeBaseIndex },
        bundleField(MONET) { bundle: Bundle -> bundle.monet },
        bundleField(FULLSCREEN_DEBUG_INFO) { bundle: Bundle -> bundle.fullscreenDebugInfo },
        bundleField(SHOW_FULLSCREEN_VIRTUAL_BUTTONS) { bundle: Bundle -> bundle.showFullscreenVirtualButtons },
        bundleField(KEEP_SCREEN_ON_WHEN_STREAMING) { bundle: Bundle -> bundle.keepScreenOnWhenStreaming },
        bundleField(DEVICE_PREVIEW_CARD_HEIGHT_DP) { bundle: Bundle -> bundle.devicePreviewCardHeightDp },
        bundleField(PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT) { bundle: Bundle -> bundle.previewVirtualButtonShowText },
        bundleField(VIRTUAL_BUTTONS_LAYOUT) { bundle: Bundle -> bundle.virtualButtonsLayout },
        bundleField(CUSTOM_SERVER_URI) { bundle: Bundle -> bundle.customServerUri },
        bundleField(CUSTOM_SERVER_VERSION) { bundle: Bundle -> bundle.customServerVersion },
        bundleField(SERVER_REMOTE_PATH) { bundle: Bundle -> bundle.serverRemotePath },
        bundleField(ADB_KEY_NAME) { bundle: Bundle -> bundle.adbKeyName },
        bundleField(ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN) { bundle: Bundle -> bundle.adbPairingAutoDiscoverOnDialogOpen },
        bundleField(ADB_AUTO_RECONNECT_PAIRED_DEVICE) { bundle: Bundle -> bundle.adbAutoReconnectPairedDevice },
        bundleField(ADB_MDNS_LAN_DISCOVERY) { bundle: Bundle -> bundle.adbMdnsLanDiscovery },
    )

    val bundleState: StateFlow<Bundle> = createBundleState(::bundleFromPreferences)

    private fun bundleFromPreferences(preferences: Preferences) = Bundle(
        themeBaseIndex = preferences.read(THEME_BASE_INDEX),
        monet = preferences.read(MONET),
        fullscreenDebugInfo = preferences.read(FULLSCREEN_DEBUG_INFO),
        showFullscreenVirtualButtons = preferences.read(SHOW_FULLSCREEN_VIRTUAL_BUTTONS),
        keepScreenOnWhenStreaming = preferences.read(KEEP_SCREEN_ON_WHEN_STREAMING),
        devicePreviewCardHeightDp = preferences.read(DEVICE_PREVIEW_CARD_HEIGHT_DP),
        previewVirtualButtonShowText = preferences.read(PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT),
        virtualButtonsLayout = preferences.read(VIRTUAL_BUTTONS_LAYOUT),
        customServerUri = preferences.read(CUSTOM_SERVER_URI),
        customServerVersion = preferences.read(CUSTOM_SERVER_VERSION),
        serverRemotePath = preferences.read(SERVER_REMOTE_PATH),
        adbKeyName = preferences.read(ADB_KEY_NAME),
        adbPairingAutoDiscoverOnDialogOpen = preferences.read(
            ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN
        ),
        adbAutoReconnectPairedDevice = preferences.read(ADB_AUTO_RECONNECT_PAIRED_DEVICE),
        adbMdnsLanDiscovery = preferences.read(ADB_MDNS_LAN_DISCOVERY),
    )

    suspend fun loadBundle() = loadBundle(::bundleFromPreferences)

    suspend fun saveBundle(new: Bundle) = saveBundle(bundleState.value, new, bundleFields)

    suspend fun updateBundle(transform: (Bundle) -> Bundle) {
        saveBundle(transform(bundleState.value))
    }

    // TODO?
    // fun validate(): Boolean = true
}
