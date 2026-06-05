package io.github.miuzarte.scrcpyforandroid.storage

import android.content.Context
import android.os.Parcelable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.StateFlow
import kotlinx.parcelize.Parcelize

class MtlsData(context: Context): Settings(context, "MtlsData") {
    companion object {
        val CA_CERTIFICATE = Pair(
            stringPreferencesKey("ca_certificate"),
            "",
        )
        val CA_CERTIFICATE_FILE_NAME = Pair(
            stringPreferencesKey("ca_certificate_file_name"),
            "",
        )
        val CLIENT_CERTIFICATE = Pair(
            stringPreferencesKey("client_certificate"),
            "",
        )
        val CLIENT_PRIVATE_KEY = Pair(
            stringPreferencesKey("client_private_key"),
            "",
        )
        val CLIENT_CERT_FILE_NAME = Pair(
            stringPreferencesKey("client_cert_file_name"),
            "",
        )
        val CLIENT_KEY_FILE_NAME = Pair(
            stringPreferencesKey("client_key_file_name"),
            "",
        )
    }

    val caCertificate by setting(CA_CERTIFICATE)
    val caCertificateFileName by setting(CA_CERTIFICATE_FILE_NAME)
    val clientCertificate by setting(CLIENT_CERTIFICATE)
    val clientPrivateKey by setting(CLIENT_PRIVATE_KEY)
    val clientCertFileName by setting(CLIENT_CERT_FILE_NAME)
    val clientKeyFileName by setting(CLIENT_KEY_FILE_NAME)

    @Parcelize
    data class Bundle(
        val caCertificate: String,
        val caCertificateFileName: String,
        val clientCertificate: String,
        val clientPrivateKey: String,
        val clientCertFileName: String,
        val clientKeyFileName: String,
    ): Parcelable {
    }

    private val bundleFields = arrayOf<BundleField<Bundle>>(
        bundleField(CA_CERTIFICATE) { it.caCertificate },
        bundleField(CA_CERTIFICATE_FILE_NAME) { it.caCertificateFileName },
        bundleField(CLIENT_CERTIFICATE) { it.clientCertificate },
        bundleField(CLIENT_PRIVATE_KEY) { it.clientPrivateKey },
        bundleField(CLIENT_CERT_FILE_NAME) { it.clientCertFileName },
        bundleField(CLIENT_KEY_FILE_NAME) { it.clientKeyFileName },
    )

    val bundleState: StateFlow<Bundle> = createBundleState(::bundleFromPreferences)

    private fun bundleFromPreferences(preferences: Preferences) = Bundle(
        caCertificate = preferences.read(CA_CERTIFICATE),
        caCertificateFileName = preferences.read(CA_CERTIFICATE_FILE_NAME),
        clientCertificate = preferences.read(CLIENT_CERTIFICATE),
        clientPrivateKey = preferences.read(CLIENT_PRIVATE_KEY),
        clientCertFileName = preferences.read(CLIENT_CERT_FILE_NAME),
        clientKeyFileName = preferences.read(CLIENT_KEY_FILE_NAME),
    )

    suspend fun loadBundle() = loadBundle(::bundleFromPreferences)

    suspend fun saveBundle(new: Bundle) = saveBundle(bundleState.value, new, bundleFields)

    suspend fun updateBundle(transform: (Bundle) -> Bundle) {
        saveBundle(transform(bundleState.value))
    }
}
