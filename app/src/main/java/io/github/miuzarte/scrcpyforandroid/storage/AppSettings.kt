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
    enum class FullscreenVirtualButtonDock(
        val rawValue: String,
        val isFixed: Boolean,
        val directionLabel: String,
    ) {
        FOLLOW_TOP("FOLLOW_TOP", false, "上方"),
        FOLLOW_BOTTOM("FOLLOW_BOTTOM", false, "下方"),
        FOLLOW_LEFT("FOLLOW_LEFT", false, "左侧"),
        FOLLOW_RIGHT("FOLLOW_RIGHT", false, "右侧"),
        FIXED_TOP("FIXED_TOP", true, "上方"),
        FIXED_BOTTOM("FIXED_BOTTOM", true, "下方"),
        FIXED_LEFT("FIXED_LEFT", true, "左侧"),
        FIXED_RIGHT("FIXED_RIGHT", true, "右侧");

        fun toStoredValue(): String = rawValue

        val summary: String
            get() = "${if (!isFixed) "跟随" else "固定"}显示在屏幕$directionLabel"

        val modeIndex: Int
            get() = if (!isFixed) 0 else 1

        val directionIndex: Int
            get() = when (this) {
                FOLLOW_TOP, FIXED_TOP -> 0
                FOLLOW_BOTTOM, FIXED_BOTTOM -> 1
                FOLLOW_LEFT, FIXED_LEFT -> 2
                FOLLOW_RIGHT, FIXED_RIGHT -> 3
            }

        companion object {
            val modeItems = listOf("跟随", "固定")
            val directionItems = listOf("上方", "下方", "左侧", "右侧")

            fun fromBundle(bundle: Bundle) =
                entries.firstOrNull { it.rawValue == bundle.fullscreenVirtualButtonDock }
                    ?: FOLLOW_BOTTOM

            fun fromStoredValue(value: String) =
                entries.firstOrNull { it.rawValue == value }
                    ?: FOLLOW_BOTTOM

            fun fromModeAndDirection(modeIndex: Int, directionIndex: Int) =
                when (directionIndex) {
                    0 -> if (modeIndex == 0) FOLLOW_TOP else FIXED_TOP
                    1 -> if (modeIndex == 0) FOLLOW_BOTTOM else FIXED_BOTTOM
                    2 -> if (modeIndex == 0) FOLLOW_LEFT else FIXED_LEFT
                    3 -> if (modeIndex == 0) FOLLOW_RIGHT else FIXED_RIGHT
                    else -> if (modeIndex == 0) FOLLOW_BOTTOM else FIXED_BOTTOM
                }
        }
    }

    companion object {
        // Theme
        val THEME_BASE_INDEX = Pair(
            intPreferencesKey("theme_base_index"),
            0,
        )
        val MONET = Pair(
            booleanPreferencesKey("monet"),
            false,
        )
        val MONET_SEED_INDEX = Pair(
            intPreferencesKey("monet_seed_index"),
            0,
        )
        val MONET_PALETTE_STYLE = Pair(
            intPreferencesKey("monet_palette_style"),
            0,
        )
        val MONET_COLOR_SPEC = Pair(
            intPreferencesKey("monet_color_spec"),
            0,
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

        // Scrcpy
        val LOW_LATENCY = Pair(
            booleanPreferencesKey("low_latency"),
            false,
        )
        val FULLSCREEN_DEBUG_INFO = Pair(
            booleanPreferencesKey("fullscreen_debug_info"),
            false,
        )
        val HIDE_SIMPLE_CONFIG_ITEMS = Pair(
            booleanPreferencesKey("hide_simple_config_items"),
            false,
        )
        val DEVICE_PREVIEW_CARD_HEIGHT_DP = Pair(
            intPreferencesKey("device_preview_card_height_dp"),
            1080 / 3,
        )
        val REALTIME_CLIPBOARD_SYNC_TO_DEVICE = Pair(
            booleanPreferencesKey("realtime_clipboard_sync_to_device"),
            true,
        )

        // Fullscreen
        val FULLSCREEN_CONTROL_IGNORE_SYSTEM_ROTATION_LOCK = Pair(
            booleanPreferencesKey("fullscreen_control_ignore_system_rotation_lock"),
            true,
        )
        val FULLSCREEN_CONTROL_BACK_TO_DEVICE = Pair(
            booleanPreferencesKey("fullscreen_control_back_to_device"),
            false,
        )
        val SHOW_FULLSCREEN_VIRTUAL_BUTTONS = Pair(
            booleanPreferencesKey("show_fullscreen_virtual_buttons"),
            true,
        )
        val FULLSCREEN_VIRTUAL_BUTTON_HEIGHT_DP = Pair(
            intPreferencesKey("fullscreen_virtual_button_height_dp"),
            16,
        )
        val FULLSCREEN_VIRTUAL_BUTTON_DOCK = Pair(
            stringPreferencesKey("fullscreen_virtual_button_dock"),
            FullscreenVirtualButtonDock.FIXED_BOTTOM.toStoredValue(),
        )
        val SHOW_FULLSCREEN_FLOATING_BUTTON = Pair(
            booleanPreferencesKey("show_fullscreen_floating_button"),
            true,
        )
        val FULLSCREEN_FLOATING_BUTTON_SIZE_DP = Pair(
            intPreferencesKey("fullscreen_floating_button_size_dp"),
            48,
        )
        val FULLSCREEN_FLOATING_BUTTON_BACKGROUND_ALPHA_PERCENT = Pair(
            intPreferencesKey("fullscreen_floating_button_background_alpha_percent"),
            25,
        )
        val FULLSCREEN_FLOATING_BUTTON_RING_ALPHA_PERCENT = Pair(
            intPreferencesKey("fullscreen_floating_button_ring_alpha_percent"),
            100,
        )
        val FULLSCREEN_COMPATIBILITY_MODE = Pair(
            booleanPreferencesKey("fullscreen_compatibility_mode"),
            false,
        )

        val FULLSCREEN_FLOATING_BUTTON_X_FRACTION = Pair(
            floatPreferencesKey("fullscreen_floating_button_x_fraction"),
            0.84f,
        )
        val FULLSCREEN_FLOATING_BUTTON_Y_FRACTION = Pair(
            floatPreferencesKey("fullscreen_floating_button_y_fraction"),
            0.72f,
        )
        val PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT = Pair(
            booleanPreferencesKey("preview_virtual_button_show_text"),
            true,
        )
        val VIRTUAL_BUTTONS_LAYOUT = Pair(
            stringPreferencesKey("virtual_buttons_layout"),
            "more:1" +

                    ",app_switch:1,home:0,back:1" +

                    ",password_input:0" +
                    ",all_apps:0" +
                    ",recent_tasks:0" +
                    ",toggle_ime:0" +
                    ",paste_local_clipboard:0" +

                    ",menu:0,notification:0" +
                    ",volume_up:0,volume_down:0,volume_mute:0" +
                    ",power:0,screenshot:0" +

                    "",
        )
        val DEVICE_TWO_PANE_CONFIG_ON_RIGHT = Pair(
            booleanPreferencesKey("device_two_pane_config_on_right"),
            false,
        )

        // Scrcpy Server
        val CUSTOM_SERVER_URI = Pair(
            stringPreferencesKey("custom_server_uri"),
            "",
        )
        val CUSTOM_SERVER_VERSION = Pair(
            stringPreferencesKey("custom_server_version"),
            "",
        )
        val SERVER_REMOTE_PATH = Pair(
            stringPreferencesKey("server_remote_path"),
            Scrcpy.DEFAULT_REMOTE_PATH,
        )

        // ADB
        val ADB_KEY_NAME = Pair(
            stringPreferencesKey("adb_key_name"),
            "scrcpy",
        )
        val ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN = Pair(
            booleanPreferencesKey("adb_pairing_auto_discover_on_dialog_open"),
            true,
        )
        val ADB_AUTO_RECONNECT_PAIRED_DEVICE = Pair(
            booleanPreferencesKey("adb_auto_reconnect_paired_device"),
            true,
        )
        val ADB_MDNS_LAN_DISCOVERY = Pair(
            // 没必要加开关, 保持启用
            booleanPreferencesKey("adb_mdns_lan_discovery"),
            true,
        )
        val ADB_AUTO_LOAD_APP_LIST_ON_CONNECT = Pair(
            booleanPreferencesKey("adb_auto_load_app_list_on_connect"),
            false,
        )

        // Terminal
        val TERMINAL_FONT_SIZE_SP = Pair(
            floatPreferencesKey("terminal_font_size_sp"),
            12f,
        )
        val TERMINAL_FONT_DISPLAY_NAME = Pair(
            stringPreferencesKey("terminal_font_display_name"),
            "",
        )

        val PASSWORD_REQUIRE_AUTH = Pair(
            booleanPreferencesKey("password_require_auth"),
            true,
        )

        val FILE_MANAGER_SORT_BY = Pair(
            stringPreferencesKey("file_manager_sort_by"),
            "NAME",
        )
        val FILE_MANAGER_SORT_DESCENDING = Pair(
            booleanPreferencesKey("file_manager_sort_descending"),
            false,
        )
        val LAST_UPDATE_CHECK_AT = Pair(
            longPreferencesKey("last_update_check_at"),
            0L,
        )
        val CLEAR_LOGS_ON_EXIT = Pair(
            booleanPreferencesKey("clear_logs_on_exit"),
            true,
        )
        val HIDE_DEVICE_LOGS = Pair(
            booleanPreferencesKey("hide_device_logs"),
            false,
        )
    }

    @Parcelize
    data class Bundle(
        // Theme
        val themeBaseIndex: Int,
        val monet: Boolean,
        val monetSeedIndex: Int,
        val monetPaletteStyle: Int,
        val monetColorSpec: Int,
        val blur: Boolean,
        val floatingBottomBar: Boolean,
        val floatingBottomBarBlur: Boolean,
        val smoothCorner: Boolean,

        // Scrcpy
        val lowLatency: Boolean,
        val fullscreenDebugInfo: Boolean,
        val hideSimpleConfigItems: Boolean,
        val devicePreviewCardHeightDp: Int,
        val realtimeClipboardSyncToDevice: Boolean,

        // Fullscreen
        val fullscreenControlIgnoreSystemRotationLock: Boolean,
        val fullscreenControlBackToDevice: Boolean,
        val showFullscreenVirtualButtons: Boolean,
        val fullscreenVirtualButtonHeightDp: Int,
        val fullscreenVirtualButtonDock: String,
        val showFullscreenFloatingButton: Boolean,
        val fullscreenFloatingButtonSizeDp: Int,
        val fullscreenFloatingButtonBackgroundAlphaPercent: Int,
        val fullscreenFloatingButtonRingAlphaPercent: Int,
        val fullscreenCompatibilityMode: Boolean,

        val fullscreenFloatingButtonXFraction: Float,
        val fullscreenFloatingButtonYFraction: Float,
        val previewVirtualButtonShowText: Boolean,
        val virtualButtonsLayout: String,
        val deviceTwoPaneConfigOnRight: Boolean,

        // Scrcpy Server
        val customServerUri: String,
        val customServerVersion: String,
        val serverRemotePath: String,

        // ADB
        val adbKeyName: String,
        val adbPairingAutoDiscoverOnDialogOpen: Boolean,
        val adbAutoReconnectPairedDevice: Boolean,
        val adbMdnsLanDiscovery: Boolean,
        val adbAutoLoadAppListOnConnect: Boolean,

        // Terminal
        val terminalFontSizeSp: Float,
        val terminalFontDisplayName: String,

        val passwordRequireAuth: Boolean,

        val fileManagerSortBy: String,
        val fileManagerSortDescending: Boolean,
        val lastUpdateCheckAt: Long,
        val clearLogsOnExit: Boolean,
        val hideDeviceLogs: Boolean,
    ) : Parcelable {
    }

    private val bundleFields = arrayOf<BundleField<Bundle>>(
        // Theme
        bundleField(THEME_BASE_INDEX) { it.themeBaseIndex },
        bundleField(MONET) { it.monet },
        bundleField(MONET_SEED_INDEX) { it.monetSeedIndex },
        bundleField(MONET_PALETTE_STYLE) { it.monetPaletteStyle },
        bundleField(MONET_COLOR_SPEC) { it.monetColorSpec },
        bundleField(BLUR) { it.blur },
        bundleField(FLOATING_BOTTOM_BAR) { it.floatingBottomBar },
        bundleField(FLOATING_BOTTOM_BAR_BLUR) { it.floatingBottomBarBlur },
        bundleField(SMOOTH_CORNER) { it.smoothCorner },

        // Scrcpy
        bundleField(LOW_LATENCY) { it.lowLatency },
        bundleField(FULLSCREEN_DEBUG_INFO) { it.fullscreenDebugInfo },
        bundleField(HIDE_SIMPLE_CONFIG_ITEMS) { it.hideSimpleConfigItems },
        bundleField(DEVICE_PREVIEW_CARD_HEIGHT_DP) { it.devicePreviewCardHeightDp },
        bundleField(REALTIME_CLIPBOARD_SYNC_TO_DEVICE) { it.realtimeClipboardSyncToDevice },

        // Fullscreen
        bundleField(FULLSCREEN_CONTROL_IGNORE_SYSTEM_ROTATION_LOCK) { it.fullscreenControlIgnoreSystemRotationLock },
        bundleField(FULLSCREEN_CONTROL_BACK_TO_DEVICE) { it.fullscreenControlBackToDevice },
        bundleField(SHOW_FULLSCREEN_VIRTUAL_BUTTONS) { it.showFullscreenVirtualButtons },
        bundleField(FULLSCREEN_VIRTUAL_BUTTON_HEIGHT_DP) { it.fullscreenVirtualButtonHeightDp },
        bundleField(FULLSCREEN_VIRTUAL_BUTTON_DOCK) { it.fullscreenVirtualButtonDock },
        bundleField(SHOW_FULLSCREEN_FLOATING_BUTTON) { it.showFullscreenFloatingButton },
        bundleField(FULLSCREEN_FLOATING_BUTTON_SIZE_DP) { it.fullscreenFloatingButtonSizeDp },
        bundleField(FULLSCREEN_FLOATING_BUTTON_BACKGROUND_ALPHA_PERCENT) { it.fullscreenFloatingButtonBackgroundAlphaPercent },
        bundleField(FULLSCREEN_FLOATING_BUTTON_RING_ALPHA_PERCENT) { it.fullscreenFloatingButtonRingAlphaPercent },
        bundleField(FULLSCREEN_COMPATIBILITY_MODE) { it.fullscreenCompatibilityMode },

        bundleField(FULLSCREEN_FLOATING_BUTTON_X_FRACTION) { it.fullscreenFloatingButtonXFraction },
        bundleField(FULLSCREEN_FLOATING_BUTTON_Y_FRACTION) { it.fullscreenFloatingButtonYFraction },
        bundleField(PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT) { it.previewVirtualButtonShowText },
        bundleField(VIRTUAL_BUTTONS_LAYOUT) { it.virtualButtonsLayout },
        bundleField(DEVICE_TWO_PANE_CONFIG_ON_RIGHT) { it.deviceTwoPaneConfigOnRight },

        // Scrcpy Server
        bundleField(CUSTOM_SERVER_URI) { it.customServerUri },
        bundleField(CUSTOM_SERVER_VERSION) { it.customServerVersion },
        bundleField(SERVER_REMOTE_PATH) { it.serverRemotePath },

        // ADB
        bundleField(ADB_KEY_NAME) { it.adbKeyName },
        bundleField(ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN) { it.adbPairingAutoDiscoverOnDialogOpen },
        bundleField(ADB_AUTO_RECONNECT_PAIRED_DEVICE) { it.adbAutoReconnectPairedDevice },
        bundleField(ADB_MDNS_LAN_DISCOVERY) { it.adbMdnsLanDiscovery },
        bundleField(ADB_AUTO_LOAD_APP_LIST_ON_CONNECT) { it.adbAutoLoadAppListOnConnect },

        // Terminal
        bundleField(TERMINAL_FONT_SIZE_SP) { it.terminalFontSizeSp },
        bundleField(TERMINAL_FONT_DISPLAY_NAME) { it.terminalFontDisplayName },

        bundleField(PASSWORD_REQUIRE_AUTH) { it.passwordRequireAuth },

        bundleField(FILE_MANAGER_SORT_BY) { it.fileManagerSortBy },
        bundleField(FILE_MANAGER_SORT_DESCENDING) { it.fileManagerSortDescending },
        bundleField(LAST_UPDATE_CHECK_AT) { it.lastUpdateCheckAt },
        bundleField(CLEAR_LOGS_ON_EXIT) { it.clearLogsOnExit },
        bundleField(HIDE_DEVICE_LOGS) { it.hideDeviceLogs },
    )

    val bundleState: StateFlow<Bundle> = createBundleState(::bundleFromPreferences)

    private fun bundleFromPreferences(preferences: Preferences) = Bundle(
        // Theme
        themeBaseIndex = preferences.read(THEME_BASE_INDEX),
        monet = preferences.read(MONET),
        monetSeedIndex = preferences.read(MONET_SEED_INDEX),
        monetPaletteStyle = preferences.read(MONET_PALETTE_STYLE),
        monetColorSpec = preferences.read(MONET_COLOR_SPEC),
        blur = preferences.read(BLUR),
        floatingBottomBar = preferences.read(FLOATING_BOTTOM_BAR),
        floatingBottomBarBlur = preferences.read(FLOATING_BOTTOM_BAR_BLUR),
        smoothCorner = preferences.read(SMOOTH_CORNER),

        // Scrcpy
        lowLatency = preferences.read(LOW_LATENCY),
        fullscreenDebugInfo = preferences.read(FULLSCREEN_DEBUG_INFO),
        hideSimpleConfigItems = preferences.read(HIDE_SIMPLE_CONFIG_ITEMS),
        devicePreviewCardHeightDp = preferences.read(DEVICE_PREVIEW_CARD_HEIGHT_DP),
        realtimeClipboardSyncToDevice = preferences.read(REALTIME_CLIPBOARD_SYNC_TO_DEVICE),

        // Fullscreen
        fullscreenControlIgnoreSystemRotationLock =
            preferences.read(FULLSCREEN_CONTROL_IGNORE_SYSTEM_ROTATION_LOCK),
        fullscreenControlBackToDevice = preferences.read(FULLSCREEN_CONTROL_BACK_TO_DEVICE),
        showFullscreenVirtualButtons = preferences.read(SHOW_FULLSCREEN_VIRTUAL_BUTTONS),
        fullscreenVirtualButtonHeightDp = preferences.read(FULLSCREEN_VIRTUAL_BUTTON_HEIGHT_DP),
        fullscreenVirtualButtonDock = preferences.read(FULLSCREEN_VIRTUAL_BUTTON_DOCK),
        showFullscreenFloatingButton = preferences.read(SHOW_FULLSCREEN_FLOATING_BUTTON),
        fullscreenFloatingButtonSizeDp = preferences.read(FULLSCREEN_FLOATING_BUTTON_SIZE_DP),
        fullscreenFloatingButtonBackgroundAlphaPercent =
            preferences.read(FULLSCREEN_FLOATING_BUTTON_BACKGROUND_ALPHA_PERCENT),
        fullscreenFloatingButtonRingAlphaPercent =
            preferences.read(FULLSCREEN_FLOATING_BUTTON_RING_ALPHA_PERCENT),
        fullscreenCompatibilityMode = preferences.read(FULLSCREEN_COMPATIBILITY_MODE),

        fullscreenFloatingButtonXFraction = preferences.read(FULLSCREEN_FLOATING_BUTTON_X_FRACTION),
        fullscreenFloatingButtonYFraction = preferences.read(FULLSCREEN_FLOATING_BUTTON_Y_FRACTION),
        previewVirtualButtonShowText = preferences.read(PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT),
        virtualButtonsLayout = preferences.read(VIRTUAL_BUTTONS_LAYOUT),
        deviceTwoPaneConfigOnRight = preferences.read(DEVICE_TWO_PANE_CONFIG_ON_RIGHT),

        // Scrcpy Server
        customServerUri = preferences.read(CUSTOM_SERVER_URI),
        customServerVersion = preferences.read(CUSTOM_SERVER_VERSION),
        serverRemotePath = preferences.read(SERVER_REMOTE_PATH),

        // ADB
        adbKeyName = preferences.read(ADB_KEY_NAME),
        adbPairingAutoDiscoverOnDialogOpen =
            preferences.read(ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN),
        adbAutoReconnectPairedDevice = preferences.read(ADB_AUTO_RECONNECT_PAIRED_DEVICE),
        adbMdnsLanDiscovery = preferences.read(ADB_MDNS_LAN_DISCOVERY),
        adbAutoLoadAppListOnConnect = preferences.read(ADB_AUTO_LOAD_APP_LIST_ON_CONNECT),

        // Terminal
        terminalFontSizeSp = preferences.read(TERMINAL_FONT_SIZE_SP),
        terminalFontDisplayName = preferences.read(TERMINAL_FONT_DISPLAY_NAME),

        passwordRequireAuth = preferences.read(PASSWORD_REQUIRE_AUTH),
        fileManagerSortBy = preferences.read(FILE_MANAGER_SORT_BY),
        fileManagerSortDescending = preferences.read(FILE_MANAGER_SORT_DESCENDING),
        lastUpdateCheckAt = preferences.read(LAST_UPDATE_CHECK_AT),
        clearLogsOnExit = preferences.read(CLEAR_LOGS_ON_EXIT),
        hideDeviceLogs = preferences.read(HIDE_DEVICE_LOGS),
    )

    suspend fun loadBundle() = loadBundle(::bundleFromPreferences)

    suspend fun saveBundle(new: Bundle) = saveBundle(bundleState.value, new, bundleFields)

    suspend fun updateBundle(transform: (Bundle) -> Bundle) {
        saveBundle(transform(bundleState.value))
    }

    // TODO?
    // fun validate(): Boolean = true
}
