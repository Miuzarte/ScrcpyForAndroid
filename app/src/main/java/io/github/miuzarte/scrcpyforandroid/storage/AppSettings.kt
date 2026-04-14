package io.github.miuzarte.scrcpyforandroid.storage

import android.content.Context
import android.os.Parcelable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
        val BLUR = Pair(
            booleanPreferencesKey("blur"),
            true,
        )
        val FLOATING_BOTTOM_BAR = Pair(
            booleanPreferencesKey("floating_bottom_bar"),
            false,
        )
        val FLOATING_BOTTOM_BAR_BLUR = Pair(
            booleanPreferencesKey("floating_bottom_bar_blur"),
            false,
        )
        val SMOOTH_CORNER = Pair(
            booleanPreferencesKey("smooth_corner"),
            false,
        )
        val FULLSCREEN_DEBUG_INFO = Pair(
            booleanPreferencesKey("fullscreen_debug_info"),
            false
        )
        val SHOW_FULLSCREEN_VIRTUAL_BUTTONS = Pair(
            booleanPreferencesKey("show_fullscreen_virtual_buttons"),
            false
        )
        val SHOW_FULLSCREEN_FLOATING_BUTTON = Pair(
            booleanPreferencesKey("show_fullscreen_floating_button"),
            true
        )
        val FULLSCREEN_FLOATING_BUTTON_X_FRACTION = Pair(
            floatPreferencesKey("fullscreen_floating_button_x_fraction"),
            0.84f
        )
        val FULLSCREEN_FLOATING_BUTTON_Y_FRACTION = Pair(
            floatPreferencesKey("fullscreen_floating_button_y_fraction"),
            0.72f
        )
        val DEVICE_PREVIEW_CARD_HEIGHT_DP = Pair(
            intPreferencesKey("device_preview_card_height_dp"),
            1080 / 3
        )
        val PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT = Pair(
            booleanPreferencesKey("preview_virtual_button_show_text"),
            true
        )
        val VIRTUAL_BUTTONS_LAYOUT = Pair(
            stringPreferencesKey("virtual_buttons_layout"),
            "more:1,password_input:0,app_switch:1,home:0,back:1,menu:0,notification:0,volume_up:0,volume_down:0,volume_mute:0,power:0,screenshot:0"
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
            // 没必要加开关, 保持启用
            booleanPreferencesKey("adb_mdns_lan_discovery"),
            true
        )
        val ADB_AUTO_LOAD_APP_LIST_ON_CONNECT = Pair(
            booleanPreferencesKey("adb_auto_load_app_list_on_connect"),
            false
        )
        val PASSWORD_REQUIRE_AUTH = Pair(
            booleanPreferencesKey("password_require_auth"),
            true
        )
        val LAST_UPDATE_CHECK_AT = Pair(
            longPreferencesKey("last_update_check_at"),
            0L
        )
    }

    // Theme Settings
    val themeBaseIndex by setting(THEME_BASE_INDEX)
    val monet by setting(MONET)
    val blur by setting(BLUR)
    val floatingBottomBar by setting(FLOATING_BOTTOM_BAR)
    val floatingBottomBarBlur by setting(FLOATING_BOTTOM_BAR_BLUR)
    val smoothCorner by setting(SMOOTH_CORNER)

    // Scrcpy Settings
    val fullscreenDebugInfo by setting(FULLSCREEN_DEBUG_INFO)
    val showFullscreenVirtualButtons by setting(SHOW_FULLSCREEN_VIRTUAL_BUTTONS)
    val showFullscreenFloatingButton by setting(SHOW_FULLSCREEN_FLOATING_BUTTON)
    val fullscreenFloatingButtonXFraction by setting(FULLSCREEN_FLOATING_BUTTON_X_FRACTION)
    val fullscreenFloatingButtonYFraction by setting(FULLSCREEN_FLOATING_BUTTON_Y_FRACTION)
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
    val adbAutoLoadAppListOnConnect by setting(ADB_AUTO_LOAD_APP_LIST_ON_CONNECT)
    val passwordRequireAuth by setting(PASSWORD_REQUIRE_AUTH)
    val lastUpdateCheckAt by setting(LAST_UPDATE_CHECK_AT)

    @Parcelize
    data class Bundle(
        val themeBaseIndex: Int,
        val monet: Boolean,
        val blur: Boolean,
        val floatingBottomBar: Boolean,
        val floatingBottomBarBlur: Boolean,
        val smoothCorner: Boolean,
        val fullscreenDebugInfo: Boolean,
        val showFullscreenVirtualButtons: Boolean,
        val showFullscreenFloatingButton: Boolean,
        val fullscreenFloatingButtonXFraction: Float,
        val fullscreenFloatingButtonYFraction: Float,
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
        val adbAutoLoadAppListOnConnect: Boolean,
        val passwordRequireAuth: Boolean,
        val lastUpdateCheckAt: Long,
    ) : Parcelable {
    }

    private val bundleFields = arrayOf(
        bundleField(THEME_BASE_INDEX) { bundle: Bundle -> bundle.themeBaseIndex },
        bundleField(MONET) { bundle: Bundle -> bundle.monet },
        bundleField(BLUR) { bundle: Bundle -> bundle.blur },
        bundleField(FLOATING_BOTTOM_BAR) { bundle: Bundle -> bundle.floatingBottomBar },
        bundleField(FLOATING_BOTTOM_BAR_BLUR) { bundle: Bundle -> bundle.floatingBottomBarBlur },
        bundleField(SMOOTH_CORNER) { bundle: Bundle -> bundle.smoothCorner },
        bundleField(FULLSCREEN_DEBUG_INFO) { bundle: Bundle -> bundle.fullscreenDebugInfo },
        bundleField(SHOW_FULLSCREEN_VIRTUAL_BUTTONS) { bundle: Bundle -> bundle.showFullscreenVirtualButtons },
        bundleField(SHOW_FULLSCREEN_FLOATING_BUTTON) { bundle: Bundle -> bundle.showFullscreenFloatingButton },
        bundleField(FULLSCREEN_FLOATING_BUTTON_X_FRACTION) { bundle: Bundle -> bundle.fullscreenFloatingButtonXFraction },
        bundleField(FULLSCREEN_FLOATING_BUTTON_Y_FRACTION) { bundle: Bundle -> bundle.fullscreenFloatingButtonYFraction },
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
        bundleField(ADB_AUTO_LOAD_APP_LIST_ON_CONNECT) { bundle: Bundle -> bundle.adbAutoLoadAppListOnConnect },
        bundleField(PASSWORD_REQUIRE_AUTH) { bundle: Bundle -> bundle.passwordRequireAuth },
        bundleField(LAST_UPDATE_CHECK_AT) { bundle: Bundle -> bundle.lastUpdateCheckAt },
    )

    val bundleState: StateFlow<Bundle> = createBundleState(::bundleFromPreferences)

    private fun bundleFromPreferences(preferences: Preferences) = Bundle(
        themeBaseIndex = preferences.read(THEME_BASE_INDEX),
        monet = preferences.read(MONET),
        blur = preferences.read(BLUR),
        floatingBottomBar = preferences.read(FLOATING_BOTTOM_BAR),
        floatingBottomBarBlur = preferences.read(FLOATING_BOTTOM_BAR_BLUR),
        smoothCorner = preferences.read(SMOOTH_CORNER),
        fullscreenDebugInfo = preferences.read(FULLSCREEN_DEBUG_INFO),
        showFullscreenVirtualButtons = preferences.read(SHOW_FULLSCREEN_VIRTUAL_BUTTONS),
        showFullscreenFloatingButton = preferences.read(SHOW_FULLSCREEN_FLOATING_BUTTON),
        fullscreenFloatingButtonXFraction = preferences.read(FULLSCREEN_FLOATING_BUTTON_X_FRACTION),
        fullscreenFloatingButtonYFraction = preferences.read(FULLSCREEN_FLOATING_BUTTON_Y_FRACTION),
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
        adbAutoLoadAppListOnConnect = preferences.read(ADB_AUTO_LOAD_APP_LIST_ON_CONNECT),
        passwordRequireAuth = preferences.read(PASSWORD_REQUIRE_AUTH),
        lastUpdateCheckAt = preferences.read(LAST_UPDATE_CHECK_AT),
    )

    suspend fun loadBundle() = loadBundle(::bundleFromPreferences)

    suspend fun saveBundle(new: Bundle) = saveBundle(bundleState.value, new, bundleFields)

    suspend fun updateBundle(transform: (Bundle) -> Bundle) {
        saveBundle(transform(bundleState.value))
    }

    // TODO?
    // fun validate(): Boolean = true
}
