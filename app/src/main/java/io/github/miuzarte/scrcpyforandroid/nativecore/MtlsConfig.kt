package io.github.miuzarte.scrcpyforandroid.nativecore

import android.annotation.SuppressLint
import android.util.Base64
import android.util.Log
import org.conscrypt.Conscrypt
import java.io.ByteArrayInputStream
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateCrtKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager

/**
 * mTLS configuration that holds CA certificate, client certificate chain,
 * and client private key. Builds an [SSLContext] for mutual TLS authentication.
 */
class MtlsConfig private constructor(
    private val caCertificate: X509Certificate,
    private val clientCertificate: X509Certificate,
    private val clientPrivateKey: PrivateKey,
) {

    val sslContext: SSLContext by lazy {
        val conscryptProvider: Provider = Conscrypt.newProviderBuilder().build()
        if (Security.getProvider(conscryptProvider.name) == null) {
            Security.insertProviderAt(conscryptProvider, 1)
        }
        val context = SSLContext.getInstance("TLSv1.3", conscryptProvider)
        context.init(arrayOf(keyManager), arrayOf(trustManager), SecureRandom())
        context
    }

    private val keyManager: X509ExtendedKeyManager
        get() = object: X509ExtendedKeyManager() {
            private val keyAlias = "mtls-client"

            override fun chooseClientAlias(
                keyType: Array<out String>?,
                issuers: Array<out java.security.Principal>?,
                socket: java.net.Socket?,
            ): String = keyAlias

            override fun getCertificateChain(alias: String?): Array<X509Certificate>? {
                return if (alias == keyAlias) arrayOf(clientCertificate) else null
            }

            override fun getPrivateKey(alias: String?): PrivateKey? {
                return if (alias == keyAlias) clientPrivateKey else null
            }

            override fun getClientAliases(
                keyType: String?,
                issuers: Array<out java.security.Principal>?,
            ): Array<String>? = null

            override fun getServerAliases(
                keyType: String?,
                issuers: Array<out java.security.Principal>?,
            ): Array<String>? = null

            override fun chooseServerAlias(
                keyType: String?,
                issuers: Array<out java.security.Principal>?,
                socket: java.net.Socket?,
            ): String? = null
        }

    @get:SuppressLint("CustomX509TrustManager")
    private val trustManager: X509ExtendedTrustManager
        get() = object: X509ExtendedTrustManager() {

            private fun validateCertificate(chain: Array<out X509Certificate>?) {
                if (chain.isNullOrEmpty()) {
                    throw java.security.cert.CertificateException("Empty certificate chain")
                }
                val cert = chain[0]
                cert.checkValidity()
                if (cert.issuerX500Principal != caCertificate.subjectX500Principal) {
                    throw java.security.cert.CertificateException(
                        "Certificate issuer mismatch: got ${cert.issuerX500Principal}, expected ${caCertificate.subjectX500Principal}"
                    )
                }
            }

            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: java.net.Socket?) {
                validateCertificate(chain)
            }

            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
                validateCertificate(chain)
            }

            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                validateCertificate(chain)
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: java.net.Socket?) {
                validateCertificate(chain)
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
                validateCertificate(chain)
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                validateCertificate(chain)
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf(caCertificate)
        }

    companion object {
        private const val TAG = "MtlsConfig"

        /**
         * Build [MtlsConfig] from PEM-encoded strings.
         */
        fun fromPem(
            caCertPem: String,
            clientCertPem: String,
            clientKeyPem: String,
        ): MtlsConfig {
            val caCert = parseX509Certificate(caCertPem)
            val clientCert = parseX509Certificate(clientCertPem)
            val clientKey = parsePrivateKey(clientKeyPem)
            Log.i(TAG, "fromPem(): ca=${fingerprint(caCert.encoded)}, client=${fingerprint(clientCert.encoded)}")
            return MtlsConfig(caCert, clientCert, clientKey)
        }

        /**
         * Build [MtlsConfig] from a PKCS12 keystore and a CA certificate PEM.
         */
        fun fromPkcs12(
            caCertPem: String,
            pkcs12Bytes: ByteArray,
            password: String = "",
        ): MtlsConfig {
            val caCert = parseX509Certificate(caCertPem)
            val ks = java.security.KeyStore.getInstance("PKCS12")
            ks.load(ByteArrayInputStream(pkcs12Bytes), password.toCharArray())
            val alias = ks.aliases().asSequence().firstOrNull()
                ?: throw IllegalArgumentException("PKCS12 contains no entries")
            val clientCert = ks.getCertificate(alias) as? X509Certificate
                ?: throw IllegalArgumentException("PKCS12 entry is not an X509 certificate")
            val clientKey = ks.getKey(alias, password.toCharArray()) as? PrivateKey
                ?: throw IllegalArgumentException("PKCS12 entry has no private key")
            Log.i(TAG, "fromPkcs12(): ca=${fingerprint(caCert.encoded)}, client=${fingerprint(clientCert.encoded)}")
            return MtlsConfig(caCert, clientCert, clientKey)
        }

        /**
         * Extract client certificate and private key PEM from a PKCS12 keystore.
         * Returns (certPem, keyPem).
         */
        fun extractPemFromPkcs12(
            pkcs12Bytes: ByteArray,
            password: String = "",
        ): Pair<String, String> {
            val ks = java.security.KeyStore.getInstance("PKCS12")
            ks.load(ByteArrayInputStream(pkcs12Bytes), password.toCharArray())
            val alias = ks.aliases().asSequence().firstOrNull()
                ?: throw IllegalArgumentException("PKCS12 contains no entries")
            val cert = ks.getCertificate(alias) as? X509Certificate
                ?: throw IllegalArgumentException("PKCS12 entry is not an X509 certificate")
            val key = ks.getKey(alias, password.toCharArray()) as? PrivateKey
                ?: throw IllegalArgumentException("PKCS12 entry has no private key")
            val certPem = "-----BEGIN CERTIFICATE-----\n" +
                android.util.Base64.encodeToString(cert.encoded, android.util.Base64.NO_WRAP) +
                "\n-----END CERTIFICATE-----"
            val keyPem = "-----BEGIN PRIVATE KEY-----\n" +
                android.util.Base64.encodeToString(key.encoded, android.util.Base64.NO_WRAP) +
                "\n-----END PRIVATE KEY-----"
            return Pair(certPem, keyPem)
        }

        private fun parseX509Certificate(pem: String): X509Certificate {
            val der = pemToDer(pem)
            val cf = CertificateFactory.getInstance("X.509")
            return cf.generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        }

        private fun parsePrivateKey(pem: String): PrivateKey {
            val kf = KeyFactory.getInstance("RSA")
            val pemBlock = readPem(pem)
            return when (pemBlock?.label) {
                "RSA PRIVATE KEY" -> {
                    val spec = parsePkcs1PrivateKey(pemBlock.bytes)
                    kf.generatePrivate(spec)
                }
                "PRIVATE KEY", null -> {
                    val der = pemBlock?.bytes ?: Base64.decode(
                        pem.filterNot(Char::isWhitespace), Base64.DEFAULT
                    )
                    kf.generatePrivate(PKCS8EncodedKeySpec(der))
                }
                else -> throw IllegalArgumentException("Unsupported private key format: ${pemBlock.label}")
            }
        }

        private fun pemToDer(pem: String): ByteArray {
            val pemBlock = readPem(pem) ?: return Base64.decode(
                pem.filterNot(Char::isWhitespace), Base64.DEFAULT
            )
            return pemBlock.bytes
        }

        private data class PemBlock(val label: String, val bytes: ByteArray)

        private fun readPem(content: String): PemBlock? {
            val begin = Regex("-----BEGIN ([A-Z0-9 ]+)-----").find(content) ?: return null
            val label = begin.groupValues[1]
            val endMarker = "-----END $label-----"
            val end = content.indexOf(endMarker, begin.range.last + 1)
            require(end >= 0) { "Invalid PEM: missing END $label" }
            val body = content.substring(begin.range.last + 1, end)
            return PemBlock(label, Base64.decode(body.filterNot(Char::isWhitespace), Base64.DEFAULT))
        }

        private fun parsePkcs1PrivateKey(der: ByteArray): RSAPrivateCrtKeySpec {
            val reader = DerReader(der).readSequence()
            reader.readInteger() // version
            val modulus = reader.readInteger()
            val publicExponent = reader.readInteger()
            val privateExponent = reader.readInteger()
            val primeP = reader.readInteger()
            val primeQ = reader.readInteger()
            val primeExponentP = reader.readInteger()
            val primeExponentQ = reader.readInteger()
            val crtCoefficient = reader.readInteger()
            return RSAPrivateCrtKeySpec(
                modulus, publicExponent, privateExponent,
                primeP, primeQ, primeExponentP, primeExponentQ, crtCoefficient,
            )
        }

        private fun fingerprint(certDer: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(certDer)
            return digest.joinToString(":") { b -> "%02x".format(b) }
        }
    }
}

private class DerReader(private val bytes: ByteArray) {
    private var offset = 0

    fun readSequence(): DerReader {
        val content = readTag(0x30)
        return DerReader(content)
    }

    fun readInteger(): java.math.BigInteger =
        java.math.BigInteger(readTag(0x02))

    private fun readTag(expectedTag: Int): ByteArray {
        require(offset < bytes.size) { "Invalid ASN.1 DER" }
        val tag = bytes[offset++].toInt() and 0xff
        require(tag == expectedTag) { "Unsupported ASN.1 DER key format" }
        val length = readLength()
        require(offset + length <= bytes.size) { "Invalid ASN.1 DER length" }
        return bytes.copyOfRange(offset, offset + length).also {
            offset += length
        }
    }

    private fun readLength(): Int {
        val first = bytes[offset++].toInt() and 0xff
        if (first < 0x80) return first
        val lengthBytes = first and 0x7f
        require(lengthBytes in 1..4) { "Unsupported ASN.1 DER length" }
        var length = 0
        repeat(lengthBytes) {
            length = (length shl 8) or (bytes[offset++].toInt() and 0xff)
        }
        return length
    }
}
