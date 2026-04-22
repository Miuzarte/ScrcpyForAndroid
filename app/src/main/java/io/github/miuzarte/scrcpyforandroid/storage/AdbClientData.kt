package io.github.miuzarte.scrcpyforandroid.storage

import android.content.Context
import android.os.Parcelable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.StateFlow
import kotlinx.parcelize.Parcelize

class AdbClientData(context: Context) : Settings(context, "AdbClient") {
    companion object {
        val RSA_PRIVATE_KEY = Pair(
            stringPreferencesKey("rsa_private_key"),
            "",
        )
        val RSA_PUBLIC_KEY_X509 = Pair(
            stringPreferencesKey("rsa_public_key_x509"),
            "",
        )
    }

    val rsaPrivateKey by setting(RSA_PRIVATE_KEY)
    val rsaPublicKeyX509 by setting(RSA_PUBLIC_KEY_X509)

    @Parcelize
    data class Bundle(
        val rsaPrivateKey: String,
        val rsaPublicKeyX509: String,
    ) : Parcelable {
    }

    private val bundleFields = arrayOf<BundleField<Bundle>>(
        bundleField(RSA_PRIVATE_KEY) { it.rsaPrivateKey },
        bundleField(RSA_PUBLIC_KEY_X509) { it.rsaPublicKeyX509 },
    )

    val bundleState: StateFlow<Bundle> = createBundleState(::bundleFromPreferences)

    private fun bundleFromPreferences(preferences: Preferences) = Bundle(
        rsaPrivateKey = preferences.read(RSA_PRIVATE_KEY),
        rsaPublicKeyX509 = preferences.read(RSA_PUBLIC_KEY_X509),
    )

    suspend fun loadBundle() = loadBundle(::bundleFromPreferences)

    suspend fun saveBundle(new: Bundle) = saveBundle(bundleState.value, new, bundleFields)

    suspend fun updateBundle(transform: (Bundle) -> Bundle) {
        saveBundle(transform(bundleState.value))
    }
}
