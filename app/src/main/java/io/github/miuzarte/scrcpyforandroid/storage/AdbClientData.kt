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
        val IMPORTED_PRIVATE_KEY = Pair(
            stringPreferencesKey("imported_private_key"),
            "",
        )
        val IMPORTED_PRIVATE_KEY_FILE_NAME = Pair(
            stringPreferencesKey("imported_private_key_file_name"),
            "",
        )
        val IMPORTED_PUBLIC_KEY_X509 = Pair(
            stringPreferencesKey("imported_public_key_x509"),
            "",
        )
        val IMPORTED_PUBLIC_KEY_FILE_NAME = Pair(
            stringPreferencesKey("imported_public_key_file_name"),
            "",
        )
    }

    val rsaPrivateKey by setting(RSA_PRIVATE_KEY)
    val rsaPublicKeyX509 by setting(RSA_PUBLIC_KEY_X509)
    val importedPrivateKey by setting(IMPORTED_PRIVATE_KEY)
    val importedPrivateKeyFileName by setting(IMPORTED_PRIVATE_KEY_FILE_NAME)
    val importedPublicKeyX509 by setting(IMPORTED_PUBLIC_KEY_X509)
    val importedPublicKeyFileName by setting(IMPORTED_PUBLIC_KEY_FILE_NAME)

    @Parcelize
    data class Bundle(
        val rsaPrivateKey: String,
        val rsaPublicKeyX509: String,
        val importedPrivateKey: String,
        val importedPrivateKeyFileName: String,
        val importedPublicKeyX509: String,
        val importedPublicKeyFileName: String,
    ) : Parcelable {
    }

    private val bundleFields = arrayOf<BundleField<Bundle>>(
        bundleField(RSA_PRIVATE_KEY) { it.rsaPrivateKey },
        bundleField(RSA_PUBLIC_KEY_X509) { it.rsaPublicKeyX509 },
        bundleField(IMPORTED_PRIVATE_KEY) { it.importedPrivateKey },
        bundleField(IMPORTED_PRIVATE_KEY_FILE_NAME) { it.importedPrivateKeyFileName },
        bundleField(IMPORTED_PUBLIC_KEY_X509) { it.importedPublicKeyX509 },
        bundleField(IMPORTED_PUBLIC_KEY_FILE_NAME) { it.importedPublicKeyFileName },
    )

    val bundleState: StateFlow<Bundle> = createBundleState(::bundleFromPreferences)

    private fun bundleFromPreferences(preferences: Preferences) = Bundle(
        rsaPrivateKey = preferences.read(RSA_PRIVATE_KEY),
        rsaPublicKeyX509 = preferences.read(RSA_PUBLIC_KEY_X509),
        importedPrivateKey = preferences.read(IMPORTED_PRIVATE_KEY),
        importedPrivateKeyFileName = preferences.read(IMPORTED_PRIVATE_KEY_FILE_NAME),
        importedPublicKeyX509 = preferences.read(IMPORTED_PUBLIC_KEY_X509),
        importedPublicKeyFileName = preferences.read(IMPORTED_PUBLIC_KEY_FILE_NAME),
    )

    suspend fun loadBundle() = loadBundle(::bundleFromPreferences)

    suspend fun saveBundle(new: Bundle) = saveBundle(bundleState.value, new, bundleFields)

    suspend fun updateBundle(transform: (Bundle) -> Bundle) {
        saveBundle(transform(bundleState.value))
    }
}
