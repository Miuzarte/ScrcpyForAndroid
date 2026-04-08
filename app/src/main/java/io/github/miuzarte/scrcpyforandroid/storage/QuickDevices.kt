package io.github.miuzarte.scrcpyforandroid.storage

import android.content.Context
import android.os.Parcelable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.StateFlow
import kotlinx.parcelize.Parcelize

class QuickDevices(context: Context) : Settings(context, "QuickDevices") {
    companion object {
        val QUICK_DEVICES_LIST = Pair(
            stringPreferencesKey("quick_devices_list"),
            "",
        )
        val QUICK_CONNECT_INPUT = Pair(
            stringPreferencesKey("quick_connect_input"),
            "",
        )
    }

    val quickDevicesList by setting(QUICK_DEVICES_LIST)
    val quickConnectInput by setting(QUICK_CONNECT_INPUT)

    @Parcelize
    data class Bundle(
        val quickDevicesList: String,
        val quickConnectInput: String,
    ) : Parcelable {
    }

    private val bundleFields = arrayOf(
        bundleField(QUICK_DEVICES_LIST) { bundle: Bundle -> bundle.quickDevicesList },
        bundleField(QUICK_CONNECT_INPUT) { bundle: Bundle -> bundle.quickConnectInput },
    )

    val bundleState: StateFlow<Bundle> = createBundleState(::bundleFromPreferences)

    private fun bundleFromPreferences(preferences: Preferences) = Bundle(
        quickDevicesList = preferences.read(QUICK_DEVICES_LIST),
        quickConnectInput = preferences.read(QUICK_CONNECT_INPUT),
    )

    suspend fun loadBundle() = loadBundle(::bundleFromPreferences)

    suspend fun saveBundle(new: Bundle) = saveBundle(bundleState.value, new, bundleFields)

    suspend fun updateBundle(transform: (Bundle) -> Bundle) {
        saveBundle(transform(bundleState.value))
    }
}
