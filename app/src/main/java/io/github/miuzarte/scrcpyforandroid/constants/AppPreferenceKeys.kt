package io.github.miuzarte.scrcpyforandroid.constants

object AppPreferenceKeys {
    const val PREFS_NAME = "scrcpy_app_prefs"
    const val NATIVE_ADB_KEY_PREFS_NAME = "nativecore_adb_rsa"
    const val NATIVE_ADB_PRIVATE_KEY = "priv"

    // Devices
    const val QUICK_DEVICES = "quick_devices"
    const val QUICK_CONNECT_INPUT = "quick_connect_input"

    const val PAIR_HOST = "pair_host"
    const val PAIR_PORT = "pair_port"
    const val PAIR_CODE = "pair_code"

    const val AUDIO_ENABLED = "audio_enabled"
    const val AUDIO_CODEC = "audio_codec"
    const val AUDIO_BIT_RATE_INPUT = "audio_bit_rate_input"
    const val AUDIO_BIT_RATE_KBPS = "audio_bit_rate_kbps"
    const val VIDEO_CODEC = "video_codec"
    const val VIDEO_BIT_RATE_MBPS = "video_bit_rate_mbps"
    const val VIDEO_BIT_RATE_INPUT = "video_bit_rate_input"

    const val TURN_SCREEN_OFF = "turn_screen_off"
    const val NO_CONTROL = "no_control"
    const val NO_VIDEO = "no_video"

    const val VIDEO_SOURCE_PRESET = "video_source_preset"
    const val DISPLAY_ID = "display_id"

    const val CAMERA_ID = "camera_id"
    const val CAMERA_FACING_PRESET = "camera_facing_preset"
    const val CAMERA_SIZE_PRESET = "camera_size_preset"
    const val CAMERA_SIZE_CUSTOM = "camera_size_custom"
    const val CAMERA_AR = "camera_ar"
    const val CAMERA_FPS = "camera_fps"
    const val CAMERA_HIGH_SPEED = "camera_high_speed"

    const val AUDIO_SOURCE_PRESET = "audio_source_preset"
    const val AUDIO_SOURCE_CUSTOM = "audio_source_custom"
    const val AUDIO_DUP = "audio_dup"
    const val NO_AUDIO_PLAYBACK = "no_audio_playback"
    const val REQUIRE_AUDIO = "require_audio"

    const val MAX_SIZE_INPUT = "max_size_input"
    const val MAX_FPS_INPUT = "max_fps_input"

    const val VIDEO_ENCODER = "video_encoder"
    const val VIDEO_CODEC_OPTION = "video_codec_options"
    const val AUDIO_ENCODER = "audio_encoder"
    const val AUDIO_CODEC_OPTION = "audio_codec_options"

    const val NEW_DISPLAY_WIDTH = "new_display_width"
    const val NEW_DISPLAY_HEIGHT = "new_display_height"
    const val NEW_DISPLAY_DPI = "new_display_dpi"

    const val CROP_WIDTH = "crop_width"
    const val CROP_HEIGHT = "crop_height"
    const val CROP_X = "crop_x"
    const val CROP_Y = "crop_y"

    // Settings
    const val THEME_BASE_INDEX = "theme_base_index"
    const val MONET = "monet"

    const val FULLSCREEN_DEBUG_INFO = "fullscreen_debug_info"
    const val SHOW_FULLSCREEN_VIRTUAL_BUTTONS = "show_fullscreen_virtual_buttons"
    const val KEEP_SCREEN_ON_WHEN_STREAMING = "keep_screen_on_when_streaming"
    const val DEVICE_PREVIEW_CARD_HEIGHT_DP = "device_preview_card_height_dp"
    const val VIRTUAL_BUTTONS_OUTSIDE = "virtual_buttons_outside"
    const val VIRTUAL_BUTTONS_IN_MORE = "virtual_buttons_in_more"

    const val CUSTOM_SERVER_URI = "custom_server_uri"

    const val SERVER_REMOTE_PATH = "server_remote_path"

    const val ADB_KEY_NAME = "adb_key_name"
    const val ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN = "adb_pairing_auto_discover_on_dialog_open"
    const val ADB_AUTO_RECONNECT_PAIRED_DEVICE = "adb_auto_reconnect_paired_device"
    const val ADB_MDNS_LAN_DISCOVERY = "adb_mdns_lan_discovery"
}
