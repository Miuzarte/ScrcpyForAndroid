package io.github.miuzarte.scrcpyforandroid.nativecore

import android.util.Base64
import android.util.Log
import io.github.miuzarte.scrcpyforandroid.storage.AdbClientData
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import kotlinx.coroutines.runBlocking
import java.io.*
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.*
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateCrtKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

/**
 * Low-level transport helper that manages local RSA keys and creates
 * `DirectAdbConnection` instances for direct TCP/TLS ADB connections.
 *
 * This type is responsible for persisting the private key and performing
 * pairing/connect discovery helpers.
 */
internal object DirectAdbTransport {

    private val keyLock = Any()

    @Volatile
    private var cachedKeys: Pair<PrivateKey, ByteArray>? = null

    private fun keys(): Pair<PrivateKey, ByteArray> =
        cachedKeys ?: synchronized(keyLock) {
            cachedKeys ?: runBlocking { loadOrCreate() }.also { cachedKeys = it }
        }

    val privateKey: PrivateKey get() = keys().first
    val publicKeyX509: ByteArray get() = keys().second

    fun getPrivateKeyPem(): String? = cachedKeys?.let { (priv, _) ->
        "-----BEGIN PRIVATE KEY-----\n" +
                Base64.encodeToString(priv.encoded, Base64.DEFAULT) +
                "-----END PRIVATE KEY-----"
    }

    fun getPublicKeyPem(): String? = cachedKeys?.let { (_, pub) ->
        "-----BEGIN PUBLIC KEY-----\n" +
                Base64.encodeToString(pub, Base64.DEFAULT) +
                "-----END PUBLIC KEY-----"
    }

    @Volatile
    var keyName: String = AppSettings.ADB_KEY_NAME.defaultValue

    /**
     * 通过TCP连接ADB设备
     */
    fun connect(host: String, port: Int, timeoutMs: Int = 10_000): DirectAdbConnection {
        Log.i(TAG, "connect(): opening direct adbd transport to $host:$port (timeout=${timeoutMs}ms)")
        val conn = DirectAdbConnection(
            host,
            port,
            privateKey,
            publicKeyX509,
            keyName.ifBlank { AppSettings.ADB_KEY_NAME.defaultValue },
            tcpMarker = true
        )
        conn.handshake(timeoutMs)
        Log.i(TAG, "connect(): handshake success for $host:$port")
        return conn
    }

    /**
     * 通过USB流连接ADB设备
     *
     * @param inputStream USB输入流
     * @param outputStream USB输出流
     * @param deviceId USB设备ID（可选）
     * @return DirectAdbConnection 连接实例
     */
    fun connectUsb(
        inputStream: InputStream,
        outputStream: OutputStream,
        deviceId: Int? = null
    ): DirectAdbConnection {
        Log.i(TAG, "connectUsb(): opening USB ADB transport (deviceId=$deviceId)")
        val conn = DirectAdbConnection(
            inputStream,
            outputStream,
            privateKey,
            publicKeyX509,
            keyName.ifBlank { AppSettings.ADB_KEY_NAME.defaultValue },
            deviceId
        )
        conn.handshake()
        Log.i(TAG, "connectUsb(): handshake success for USB device $deviceId")
        return conn
    }

    fun pair(host: String, port: Int, pairingCode: String): Boolean {
        val targetHost = host.trim()
        val targetCode = pairingCode.trim()
        require(targetHost.isNotBlank()) { "host is blank" }
        require(targetCode.isNotBlank()) { "pairing code is blank" }

        val pairingKey = AdbPairingKey(
            privateKey = privateKey,
            alias = keyName.ifBlank { AppSettings.ADB_KEY_NAME.defaultValue },
        )
        return DirectAdbPairingClient(targetHost, port, targetCode, pairingKey).use {
            it.start()
        }
    }

    fun discoverPairingService(
        timeoutMs: Long = 12_000,
        includeLanDevices: Boolean = true,
    ): Pair<String, Int>? =
        AdbMdnsDiscoverer.discoverPairingService(timeoutMs, includeLanDevices)

    fun discoverConnectService(
        timeoutMs: Long = 12_000,
        includeLanDevices: Boolean = true,
    ): Pair<String, Int>? =
        AdbMdnsDiscoverer.discoverConnectService(timeoutMs, includeLanDevices)

    data class ImportedKeyInfo(
        val fingerprint: String,
    )

    data class KeyResetInfo(
        val fingerprint: String,
        val removedImportedKey: Boolean,
    )

    suspend fun importPrivateKey(content: String, fileName: String): ImportedKeyInfo {
        val privateKey = parsePrivateKey(content)
        validatePrivateKey(privateKey)
        val publicKeyX509 = derivePublicX509(privateKey)
        validatePublicKey(publicKeyX509)

        Storage.adbClientData.saveBundle(
            Storage.adbClientData.bundleState.value.copy(
                importedPrivateKey = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP),
                importedPrivateKeyFileName = fileName,
                importedPublicKeyX509 = Base64.encodeToString(publicKeyX509, Base64.NO_WRAP),
                importedPublicKeyFileName = "",
            ),
        )
        synchronized(keyLock) {
            cachedKeys = Pair(privateKey, publicKeyX509)
        }
        return ImportedKeyInfo(fingerprint(publicKeyX509))
    }

    suspend fun importPublicKey(content: String, fileName: String): ImportedKeyInfo {
        val privateKey = loadActivePrivateKey()
            ?: throw IllegalArgumentException("Import private key first")
        val importedPublicKey = parsePublicKey(content)
        val importedRsa = importedPublicKey as? RSAPublicKey
            ?: throw IllegalArgumentException("Public key is not RSA")
        val privateRsa = privateKey as? RSAPrivateCrtKey
            ?: throw IllegalArgumentException("Stored private key cannot derive an RSA public key")

        require(importedRsa.modulus == privateRsa.modulus) { "Public key does not match private key" }
        require(importedRsa.publicExponent == privateRsa.publicExponent) {
            "Public key does not match private key"
        }

        val publicKeyX509 = importedPublicKey.encoded
        validatePublicKey(publicKeyX509)
        Storage.adbClientData.saveBundle(
            Storage.adbClientData.bundleState.value.copy(
                importedPublicKeyX509 = Base64.encodeToString(publicKeyX509, Base64.NO_WRAP),
                importedPublicKeyFileName = fileName,
            ),
        )
        synchronized(keyLock) {
            cachedKeys = Pair(privateKey, publicKeyX509)
        }
        return ImportedKeyInfo(fingerprint(publicKeyX509))
    }

    fun reloadKeys() {
        synchronized(keyLock) {
            cachedKeys = runBlocking { loadOrCreate() }
        }
    }

    fun resetKeys(): KeyResetInfo {
        val data = Storage.adbClientData.bundleState.value
        val hasImportedKey = data.importedPrivateKey.isNotBlank() || data.importedPublicKeyX509.isNotBlank()
        val keys = if (hasImportedKey) {
            synchronized(keyLock) {
                runBlocking {
                    Storage.adbClientData.saveBundle(
                        data.copy(
                            importedPrivateKey = "",
                            importedPrivateKeyFileName = "",
                            importedPublicKeyX509 = "",
                            importedPublicKeyFileName = "",
                        ),
                    )
                    loadOrCreate()
                }.also { cachedKeys = it }
            }
        } else {
            synchronized(keyLock) {
                runBlocking { loadOrCreate(forceNew = true) }.also { cachedKeys = it }
            }
        }
        return KeyResetInfo(fingerprint(keys.second), removedImportedKey = hasImportedKey)
    }

    /**
     * Load persisted RSA keypair from DataStore, or generate a new one.
     * Returns (privateKey, publicX509Bytes).
     */
    private suspend fun loadOrCreate(
        forceNew: Boolean = false,
    ): Pair<PrivateKey, ByteArray> {
        val adbClientData = Storage.adbClientData

        val importedPrivB64 = adbClientData.importedPrivateKey.get()
        val generatedPrivB64 = adbClientData.rsaPrivateKey.get()
        val privB64 = importedPrivB64.ifBlank { generatedPrivB64 }

        if (privB64.isNotBlank() && !forceNew) {
            try {
                val priv = generatePkcs8PrivateKey(Base64.decode(privB64, Base64.DEFAULT))
                val pubB64 = adbClientData.importedPublicKeyX509.get()
                    .ifBlank { adbClientData.rsaPublicKeyX509.get() }
                val pub =
                    if (pubB64.isNotBlank()) Base64.decode(pubB64, Base64.DEFAULT)
                    else derivePublicX509(priv).also { derivedPublicKey ->
                        val encoded = Base64.encodeToString(derivedPublicKey, Base64.NO_WRAP)
                        val current = adbClientData.bundleState.value
                        adbClientData.saveBundle(
                            if (importedPrivB64.isNotBlank()) {
                                current.copy(
                                    importedPublicKeyX509 = encoded,
                                    importedPublicKeyFileName = "",
                                )
                            } else {
                                current.copy(
                                    rsaPublicKeyX509 = encoded,
                                )
                            },
                        )
                    }

                Log.i(
                    TAG,
                    "loadOrCreate(): loaded persisted RSA key pair from DataStore, " +
                            "fp=${fingerprint(pub)}",
                )
                return Pair(priv, pub)
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "loadOrCreate(): failed to load persisted key from DataStore, regenerating",
                    e,
                )
            }
        }
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        val privateKeyB64 = Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP)
        val publicKeyB64 = Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
        adbClientData.saveBundle(
            AdbClientData.Bundle(
                rsaPrivateKey = privateKeyB64,
                rsaPublicKeyX509 = publicKeyB64,
                importedPrivateKey = "",
                importedPrivateKeyFileName = "",
                importedPublicKeyX509 = "",
                importedPublicKeyFileName = "",
            ),
        )

        Log.i(
            TAG,
            "loadOrCreate(): generated new RSA key pair, fp=${fingerprint(kp.public.encoded)}",
        )
        return Pair(kp.private, kp.public.encoded)
    }

    private suspend fun loadActivePrivateKey(): PrivateKey? {
        val data = Storage.adbClientData
        val privateKeyB64 = data.importedPrivateKey.get().ifBlank {
            data.rsaPrivateKey.get()
        }
        if (privateKeyB64.isBlank()) return null
        return generatePkcs8PrivateKey(Base64.decode(privateKeyB64, Base64.DEFAULT))
    }

    private fun parsePrivateKey(content: String): PrivateKey {
        val pem = readPem(content)
        val der = when {
            pem != null -> {
                require(pem.label != "ENCRYPTED PRIVATE KEY") {
                    "Encrypted private keys are not supported"
                }
                pem.bytes
            }

            else -> Base64.decode(content.filterNot(Char::isWhitespace), Base64.DEFAULT)
        }
        val kf = KeyFactory.getInstance("RSA")
        return when (pem?.label) {
            "RSA PRIVATE KEY" -> kf.generatePrivate(parsePkcs1PrivateKey(der))
            "PRIVATE KEY", null -> generatePkcs8PrivateKey(der)
            else -> throw IllegalArgumentException("Unsupported private key format: ${pem.label}")
        }
    }

    private fun generatePkcs8PrivateKey(der: ByteArray): PrivateKey {
        val kf = KeyFactory.getInstance("RSA")
        val key = runCatching { kf.generatePrivate(PKCS8EncodedKeySpec(der)) }.getOrNull()
        return key as? RSAPrivateCrtKey ?: kf.generatePrivate(parsePkcs8PrivateKey(der))
    }

    private fun parsePublicKey(content: String): PublicKey {
        val pem = readPem(content)
        val normalized = content.trim()
        val kf = KeyFactory.getInstance("RSA")
        val der = when {
            pem != null -> pem.bytes
            normalized.contains(" ") -> Base64.decode(normalized.substringBefore(' '), Base64.DEFAULT)
            else -> Base64.decode(normalized.filterNot(Char::isWhitespace), Base64.DEFAULT)
        }
        return when {
            pem?.label == "RSA PUBLIC KEY" -> kf.generatePublic(parsePkcs1PublicKey(der))
            pem?.label == "PUBLIC KEY" || pem == null && !looksLikeAdbPublicKey(der) ->
                kf.generatePublic(X509EncodedKeySpec(der))

            pem == null -> parseAdbPublicKey(der)
            else -> throw IllegalArgumentException("Unsupported public key format: ${pem.label}")
        }
    }

    private fun validatePrivateKey(privateKey: PrivateKey) {
        val rsa = privateKey as? RSAPrivateCrtKey
            ?: throw IllegalArgumentException("Private key must be an RSA CRT key")
        require(rsa.modulus.bitLength() == 2048) { "ADB RSA key must be 2048-bit" }
        require(rsa.publicExponent.signum() > 0 && rsa.publicExponent.bitLength() <= 31) {
            "Unsupported RSA public exponent"
        }
        Signature.getInstance("SHA1withRSA").apply {
            initSign(privateKey)
            update("scrcpy-adb-key-check".toByteArray())
            sign()
        }
    }

    private fun validatePublicKey(publicX509: ByteArray) {
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicX509))
        val rsa = publicKey as? RSAPublicKey
            ?: throw IllegalArgumentException("Public key must be RSA")
        require(rsa.modulus.bitLength() == 2048) { "ADB RSA key must be 2048-bit" }
        require(rsa.publicExponent.signum() > 0 && rsa.publicExponent.bitLength() <= 31) {
            "Unsupported RSA public exponent"
        }
    }

    private fun derivePublicX509(privateKey: PrivateKey): ByteArray {
        val rsa = privateKey as? RSAPrivateCrtKey
            ?: throw IllegalStateException("Expected RSAPrivateCrtKey but was ${privateKey.javaClass.name}")
        val kf = KeyFactory.getInstance("RSA")
        val public = kf.generatePublic(RSAPublicKeySpec(rsa.modulus, rsa.publicExponent))
        return public.encoded
    }

    private data class PemBlock(
        val label: String,
        val bytes: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PemBlock

            if (label != other.label) return false
            if (!bytes.contentEquals(other.bytes)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = label.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }

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
            modulus,
            publicExponent,
            privateExponent,
            primeP,
            primeQ,
            primeExponentP,
            primeExponentQ,
            crtCoefficient,
        )
    }

    private fun parsePkcs8PrivateKey(der: ByteArray): RSAPrivateCrtKeySpec {
        val reader = DerReader(der).readSequence()
        reader.readInteger() // version
        reader.readElement() // privateKeyAlgorithm
        return parsePkcs1PrivateKey(reader.readOctetString())
    }

    private fun parsePkcs1PublicKey(der: ByteArray): RSAPublicKeySpec {
        val reader = DerReader(der).readSequence()
        return RSAPublicKeySpec(reader.readInteger(), reader.readInteger())
    }

    private fun looksLikeAdbPublicKey(bytes: ByteArray): Boolean =
        bytes.size == ADB_PUBLIC_KEY_BYTES &&
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int == ADB_PUBLIC_KEY_WORDS

    private fun parseAdbPublicKey(bytes: ByteArray): PublicKey {
        require(looksLikeAdbPublicKey(bytes)) { "Invalid ADB public key" }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val words = buf.int
        buf.int // n0inv
        val modulusLE = ByteArray(words * 4)
        buf.get(modulusLE)
        val rrLE = ByteArray(words * 4)
        buf.get(rrLE)
        val exponent = buf.int.toLong() and 0xffffffffL
        val modulus = BigInteger(1, modulusLE.reversedArray())
        return KeyFactory.getInstance("RSA").generatePublic(
            RSAPublicKeySpec(modulus, BigInteger.valueOf(exponent)),
        )
    }

    private class DerReader(
        private val bytes: ByteArray,
    ) {
        private var offset = 0

        fun readSequence(): DerReader {
            val content = readTag(0x30)
            return DerReader(content)
        }

        fun readInteger(): BigInteger =
            BigInteger(readTag(0x02))

        fun readOctetString(): ByteArray =
            readTag(0x04)

        fun readElement(): ByteArray {
            require(offset < bytes.size) { "Invalid ASN.1 DER" }
            val start = offset
            offset++
            val length = readLength()
            require(offset + length <= bytes.size) { "Invalid ASN.1 DER length" }
            offset += length
            return bytes.copyOfRange(start, offset)
        }

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

    private fun fingerprint(publicX509: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicX509)
        return digest.joinToString(":") { b -> "%02x".format(b) }
    }

    private fun encodeAdbPublicKey(modulus: BigInteger, exponent: Int): ByteArray {
        val two32 = BigInteger.ONE.shiftLeft(32)
        val mask32 = two32.subtract(BigInteger.ONE)

        fun toBigEndianPadded(n: BigInteger): ByteArray {
            val raw = n.toByteArray()
            val arr = ByteArray(ADB_PUBLIC_KEY_MODULUS_BYTES)
            val src = if (raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
            src.copyInto(arr, destinationOffset = ADB_PUBLIC_KEY_MODULUS_BYTES - src.size)
            return arr
        }

        val modBE = toBigEndianPadded(modulus)
        val n0 = modulus.and(mask32)
        val n0inv = n0.modInverse(two32).negate().mod(two32).toInt()
        val r = BigInteger.ONE.shiftLeft(ADB_PUBLIC_KEY_MODULUS_BYTES * 8)
        val rrBE = toBigEndianPadded(r.multiply(r).mod(modulus))

        val buf = ByteBuffer.allocate(ADB_PUBLIC_KEY_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(ADB_PUBLIC_KEY_WORDS)
        buf.putInt(n0inv)
        for (i in ADB_PUBLIC_KEY_WORDS - 1 downTo 0) {
            val o = i * 4
            buf.put(modBE[o + 3]); buf.put(modBE[o + 2]); buf.put(modBE[o + 1]); buf.put(modBE[o])
        }
        for (i in ADB_PUBLIC_KEY_WORDS - 1 downTo 0) {
            val o = i * 4
            buf.put(rrBE[o + 3]); buf.put(rrBE[o + 2]); buf.put(rrBE[o + 1]); buf.put(rrBE[o])
        }
        buf.putInt(exponent)
        return buf.array()
    }

    private const val TAG = "DirectAdbTransport"
    private const val ADB_PUBLIC_KEY_WORDS = 64
    private const val ADB_PUBLIC_KEY_MODULUS_BYTES = 256
    private const val ADB_PUBLIC_KEY_BYTES = 4 + 4 + ADB_PUBLIC_KEY_MODULUS_BYTES +
            ADB_PUBLIC_KEY_MODULUS_BYTES + 4
}

/**
 * Represents a single direct ADB connection over TCP (optionally upgraded to TLS)
 * or over injected streams (e.g., USB).
 *
 * Exposes framed ADB streams via `openStream` and handles the protocol handshake,
 * reader thread and stream routing.
 *
 * 支持两种连接方式：
 * 1. TCP连接：通过host:port建立Socket连接
 * 2. 流式连接：通过注入的InputStream/OutputStream建立连接（用于USB等非TCP场景）
 */
internal class DirectAdbConnection(
    val host: String,
    val port: Int,
    private val privateKey: PrivateKey,
    private val publicKeyX509: ByteArray,
    private val keyName: String = AppSettings.ADB_KEY_NAME.defaultValue,
): AutoCloseable {

    private val sha1DigestInfoPrefix = byteArrayOf(
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2B, 0x0E,
        0x03, 0x02, 0x1A, 0x05, 0x00, 0x04, 0x14,
    )

    // 连接类型标识
    enum class ConnectionType {
        TCP,    // TCP Socket连接
        STREAM  // 流式连接（USB等）
    }

    var connectionType: ConnectionType = ConnectionType.TCP
        private set
    
    // TCP连接相关
    var socket: Socket? = null
        private set
    
    // 流式连接相关（USB等）
    private var injectedInputStream: InputStream? = null
    private var injectedOutputStream: OutputStream? = null
    
    private lateinit var rawIn: BufferedInputStream
    private lateinit var rawOut: OutputStream
    private var tlsSocket: SSLSocket? = null
    private val nextLocalId = AtomicInteger(1)
    private val streams = ConcurrentHashMap<Int, AdbSocketStream>()

    @Volatile
    private var closed = false
    private var readerThread: Thread? = null

    /**
     * TCP连接构造函数
     */
    constructor(
        host: String,
        port: Int,
        privateKey: PrivateKey,
        publicKeyX509: ByteArray,
        keyName: String,
        @Suppress("UNUSED_PARAMETER") tcpMarker: Boolean = true  // 用于区分构造函数
    ) : this(host, port, privateKey, publicKeyX509, keyName) {
        this.connectionType = ConnectionType.TCP
        this.socket = Socket()
    }

    /**
     * 流式连接构造函数（用于USB等非TCP场景）
     * 通过注入的InputStream/OutputStream建立ADB连接
     */
    constructor(
        inputStream: InputStream,
        outputStream: OutputStream,
        privateKey: PrivateKey,
        publicKeyX509: ByteArray,
        keyName: String = AppSettings.ADB_KEY_NAME.defaultValue,
        deviceId: Int? = null
    ) : this(
        host = "usb:${deviceId ?: "unknown"}",  // 使用USB标识作为host
        port = 0,  // USB连接不使用端口
        privateKey = privateKey,
        publicKeyX509 = publicKeyX509,
        keyName = keyName
    ) {
        this.connectionType = ConnectionType.STREAM
        this.socket = null
        this.injectedInputStream = inputStream
        this.injectedOutputStream = outputStream
    }

    companion object {
        private const val TAG = "DirectAdbConnection"
        private const val A_CNXN = 0x4e584e43
        private const val A_AUTH = 0x48545541
        private const val A_STLS = 0x534c5453
        private const val A_OPEN = 0x4e45504f
        private const val A_OKAY = 0x59414b4f
        private const val A_CLSE = 0x45534c43
        private const val A_WRTE = 0x45545257
        private const val STLS_VERSION = 0x01000000
        private const val AUTH_TOKEN = 1
        private const val AUTH_SIGNATURE = 2
        private const val AUTH_RSAPUBLICKEY = 3
        private const val VERSION = 0x01000001
        private const val MAX_PAYLOAD = 256 * 1024
    }

    /**
     * 执行ADB协议握手
     *
     * 支持两种连接方式：
     * 1. TCP连接：建立Socket连接，支持TLS升级
     * 2. 流式连接：使用注入的InputStream/OutputStream（用于USB等场景）
     *
     * 握手流程：
     * - 发送CNXN消息
     * - 处理可选的STLS（TLS升级）
     * - 处理AUTH认证（TOKEN -> SIGNATURE 或 PUBKEY 流程）
     * - 成功后启动读取线程
     */
    fun handshake(timeoutMs: Int = 10_000) {
        when (connectionType) {
            ConnectionType.TCP -> handshakeTcp(timeoutMs)
            ConnectionType.STREAM -> handshakeStream()
        }
    }

    /**
     * TCP连接握手
     */
    private fun handshakeTcp(timeoutMs: Int) {
        Log.i(TAG, "handshakeTcp(): tcp connect -> $host:$port (timeout=${timeoutMs}ms)")
        val tcpSocket = socket ?: throw IllegalStateException("TCP socket is null")
        
        tcpSocket.connect(InetSocketAddress(host, port), timeoutMs)
        tcpSocket.tcpNoDelay = true
        tcpSocket.keepAlive = true
        tcpSocket.soTimeout = 60_000
        // 增大Socket缓冲区，减少高码率视频传输时的阻塞和卡顿
        tcpSocket.receiveBufferSize = 1_048_576  // 1MB
        tcpSocket.sendBufferSize = 1_048_576     // 1MB
        rawIn = BufferedInputStream(tcpSocket.getInputStream(), 262_144)
        rawOut = tcpSocket.getOutputStream()

        performAdbHandshake()

        tcpSocket.soTimeout = 0
        readerThread = thread(isDaemon = true, name = "adb-reader-$host:$port") { readLoop() }
    }

    /**
     * 流式连接握手（用于USB等非TCP场景）
     */
    private fun handshakeStream() {
        Log.i(TAG, "handshakeStream(): stream connect -> $host")
        
        val inputStream = injectedInputStream 
            ?: throw IllegalStateException("Injected InputStream is null")
        val outputStream = injectedOutputStream 
            ?: throw IllegalStateException("Injected OutputStream is null")
        
        rawIn = BufferedInputStream(inputStream, 262_144)
        rawOut = outputStream

        performAdbHandshake()

        readerThread = thread(isDaemon = true, name = "adb-reader-$host") { readLoop() }
    }

    /**
     * 执行ADB协议握手（TCP和流式连接共用）
     *
     * 处理CNXN、STLS、AUTH等消息
     */
    private fun performAdbHandshake() {
        sendMsg(A_CNXN, VERSION, MAX_PAYLOAD, "host::\u0000".toByteArray(Charsets.UTF_8))

        var first = recvMsg()
        if (first.command == A_STLS) {
            sendMsg(A_STLS, STLS_VERSION, 0)
            upgradeToTls()
            first = recvMsg()
        }

        when (first.command) {
            A_CNXN -> Unit
            A_AUTH -> {
                if (first.arg0 != AUTH_TOKEN) {
                    throw IOException("ADB: expected AUTH_TOKEN, got type=${first.arg0}")
                }
                sendMsg(A_AUTH, AUTH_SIGNATURE, 0, signToken(first.data))
                val afterSign = recvMsg()
                when (afterSign.command) {
                    A_CNXN -> Unit
                    A_AUTH -> {
                        if (afterSign.arg0 != AUTH_TOKEN) {
                            throw IOException("ADB: expected AUTH_TOKEN after rejected signature, got type=${afterSign.arg0}")
                        }
                        sendMsg(A_AUTH, AUTH_RSAPUBLICKEY, 0, buildAdbPubKey())
                        val cnxn = recvMsg()
                        if (cnxn.command != A_CNXN) {
                            throw IOException("ADB: connection rejected. Please accept the authorisation dialog on the target device.")
                        }
                    }

                    else -> throw IOException(
                        "ADB: unexpected message 0x${
                            afterSign.command.toString(
                                16,
                            )
                        } after AUTH_SIGNATURE",
                    )
                }
            }

            else -> throw IOException("ADB: unexpected initial message 0x${first.command.toString(16)}")
        }
    }

    /**
     * 升级到TLS连接
     *
     * 注意：USB连接不支持TLS升级，因为USB隧道已经提供了安全传输
     */
    private fun upgradeToTls() {
        when (connectionType) {
            ConnectionType.TCP -> {
                val tcpSocket = socket ?: throw IllegalStateException("TCP socket is null")
                val pairingKey = AdbPairingKey(
                    privateKey = privateKey,
                    alias = keyName,
                )
                val sslSocket = pairingKey.sslContext.socketFactory
                    .createSocket(tcpSocket, host, port, true) as SSLSocket
                sslSocket.startHandshake()
                tlsSocket = sslSocket
                rawIn = BufferedInputStream(sslSocket.inputStream, 65_536)
                rawOut = sslSocket.outputStream
            }
            ConnectionType.STREAM -> {
                // USB连接不支持TLS升级，跳过
                Log.w(TAG, "upgradeToTls(): TLS upgrade not supported for stream connections")
            }
        }
    }

    /**
     * Open a logical ADB stream for `service` and return an [AdbSocketStream].
     *
     * - Sends an A_OPEN message and waits for remote acknowledgment. The returned
     *   stream exposes blocking InputStream/OutputStream wrappers usable by callers.
     */
    fun openStream(service: String): AdbSocketStream {
        val localId = nextLocalId.getAndIncrement()
        val flowControlWindow = Storage.appSettings.bundleState.value.adbFlowControlWindow
        val stream = AdbSocketStream(
            localId,
            { command, arg0, arg1, data ->
                sendMsg(command, arg0, arg1, data)
            },
            flowControlWindow,
        )
        streams[localId] = stream
        sendMsg(A_OPEN, localId, 0, (service + "\u0000").toByteArray(Charsets.UTF_8))
        try {
            stream.awaitOpen(15_000)
        } catch (e: Exception) {
            streams.remove(localId)
            throw e
        }
        return stream
    }

    fun shell(command: String): String =
        openStream("shell:$command")
            .use { it.inputStream.readBytes().toString(Charsets.UTF_8) }

    /**
     * 通过ADB协议层启用TCP/IP模式
     *
     * 直接发送 `tcpip:PORT` 服务命令，不依赖shell，不需要root权限。
     * 这是ADB原生支持的命令，比通过shell执行 `setprop service.adb.tcp.port` 更可靠。
     *
     * @param port TCP端口号，通常为5555
     * @return 服务端响应
     */
    fun tcpip(port: Int): String =
        openStream("tcpip:$port")
            .use { it.inputStream.readBytes().toString(Charsets.UTF_8) }

    /**
     * Push raw bytes to a remote path using the minimal ADB "sync" protocol.
     *
     * - Implements SEND/DATA/DONE/OKAY sequence and throws IOException on failure.
     */
    fun push(data: ByteArray, remotePath: String, unixMode: Int = 420) {
        data.inputStream().use { input ->
            push(input, remotePath, unixMode)
        }
    }

    fun push(input: InputStream, remotePath: String, unixMode: Int = 420) {
        openStream("sync:")
            .use { stream ->
                val out = stream.outputStream
                val inp = stream.inputStream
                val pathMode = "$remotePath,$unixMode".toByteArray(Charsets.UTF_8)

                out.write("SEND".toByteArray(Charsets.US_ASCII))
                out.writeIntLE(pathMode.size)
                out.write(pathMode)

                val chunkBuf = ByteArray(64 * 1024)
                while (true) {
                    val len = input.read(chunkBuf)
                    if (len <= 0) break

                    out.write("DATA".toByteArray(Charsets.US_ASCII))
                    out.writeIntLE(len)
                    out.write(chunkBuf, 0, len)
                }

                out.write("DONE".toByteArray(Charsets.US_ASCII))
                out.writeIntLE((System.currentTimeMillis() / 1000).toInt())
                out.flush()

                val idBuf = ByteArray(4).also { inp.readExact(it) }
                val msgLen = inp.readIntLE()
                val id = String(idBuf, Charsets.US_ASCII)
                if (id != "OKAY") {
                    val msg = if (msgLen > 0) ByteArray(msgLen).also { inp.readExact(it) }
                        .toString(Charsets.UTF_8) else id
                    throw IOException("ADB push failed: $msg")
                } else if (msgLen > 0) {
                    inp.skip(msgLen.toLong())
                }
            }
    }

    fun pull(remotePath: String): ByteArray {
        val output = ByteArrayOutputStream()
        pull(remotePath, output)
        return output.toByteArray()
    }

    fun pull(remotePath: String, output: OutputStream) {
        openStream("sync:")
            .use { stream ->
                val out = stream.outputStream
                val inp = stream.inputStream
                val pathBytes = remotePath.toByteArray(Charsets.UTF_8)

                out.write("RECV".toByteArray(Charsets.US_ASCII))
                out.writeIntLE(pathBytes.size)
                out.write(pathBytes)
                out.flush()

                while (true) {
                    val idBuf = ByteArray(4).also { inp.readExact(it) }
                    val msgLen = inp.readIntLE()
                    when (val id = String(idBuf, Charsets.US_ASCII)) {
                        "DATA" -> {
                            val chunk = ByteArray(msgLen)
                            inp.readExact(chunk)
                            output.write(chunk)
                        }

                        "DONE" -> {
                            if (msgLen > 0) {
                                inp.skip(msgLen.toLong())
                            }
                            return
                        }

                        "FAIL" -> {
                            val msg = if (msgLen > 0) {
                                ByteArray(msgLen).also { inp.readExact(it) }
                                    .toString(Charsets.UTF_8)
                            } else {
                                "unknown error"
                            }
                            throw IOException("ADB pull failed: $msg")
                        }

                        else -> {
                            if (msgLen > 0) {
                                inp.skip(msgLen.toLong())
                            }
                            throw IOException("ADB pull failed: unexpected sync id $id")
                        }
                    }
                }
            }
    }

    /**
     * 检查连接是否存活
     *
     * TCP连接：检查socket状态
     * 流式连接：检查closed标志和流是否可用
     */
    fun isAlive(): Boolean {
        if (closed) return false
        
        return when (connectionType) {
            ConnectionType.TCP -> {
                val tcpSocket = socket ?: return false
                val isClosed = tcpSocket.isClosed
                val isConnected = tcpSocket.isConnected
                Log.d(TAG, "isAlive() TCP: isClosed=$isClosed, isConnected=$isConnected")
                !isClosed && isConnected
            }
            ConnectionType.STREAM -> {
                // 流式连接：检查流是否仍然可用
                val inputStream = injectedInputStream
                val outputStream = injectedOutputStream
                val alive = inputStream != null && outputStream != null
                Log.d(TAG, "isAlive() STREAM: alive=$alive")
                alive
            }
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            streams.values.forEach { runCatching { it.forceClose() } }
            streams.clear()
            runCatching { tlsSocket?.close() }
            
            // 根据连接类型关闭相应的资源
            when (connectionType) {
                ConnectionType.TCP -> {
                    runCatching { socket?.close() }
                }
                ConnectionType.STREAM -> {
                    // 流式连接：关闭注入的流（但不关闭原始流，因为它们可能由调用者管理）
                    // 注入的流通常由USB隧道管理，这里只清除引用
                    injectedInputStream = null
                    injectedOutputStream = null
                }
            }
            
            runCatching { readerThread?.interrupt() }
        }
    }

    private fun readLoop() {
        try {
            while (!closed) {
                val msg = recvMsg()
                when (msg.command) {
                    A_OKAY -> streams[msg.arg1]?.onRemoteOkay(msg.arg0)
                    A_WRTE -> {
                        val s = streams[msg.arg1]
                        if (s != null) {
                            s.onData(msg.data)
                            sendMsg(A_OKAY, msg.arg1, msg.arg0)
                        } else {
                            sendMsg(A_CLSE, 0, msg.arg0)
                        }
                    }

                    A_CLSE -> streams.remove(msg.arg1)?.forceClose()
                    A_OPEN -> sendMsg(A_CLSE, 0, msg.arg0)
                }
            }
        } catch (_: Exception) {
            if (!closed) {
                closed = true
                streams.values.forEach { runCatching { it.forceClose() } }
            }
        }
    }

    /**
     * Send a framed ADB message (header + optional payload).
     *
     * - Header fields are little-endian as required by the ADB protocol.
     */
    @Synchronized
    private fun sendMsg(
        command: Int,
        arg0: Int = 0,
        arg1: Int = 0,
        data: ByteArray = ByteArray(0),
    ) {
        val crc = data.fold(0L) { acc, b -> acc + (b.toLong() and 0xFF) }.toInt()
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command).putInt(arg0).putInt(arg1)
            .putInt(data.size).putInt(crc).putInt(command xor -1)
            .array()
        rawOut.write(header)
        if (data.isNotEmpty()) rawOut.write(data)
        rawOut.flush()
    }

    /**
     * Receive and parse a single ADB framed message from the socket.
     *
     * - Blocks until the full 24-byte header is read and then reads the payload.
     */
    private fun recvMsg(): AdbMsg {
        val h = ByteArray(24)
        rawIn.readExact(h)
        val buf = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        val command = buf.int
        val arg0 = buf.int
        val arg1 = buf.int
        val dataLen = buf.int
        buf.int
        buf.int
        // ADB协议限制：MAX_PAYLOAD = 256KB，拒绝异常大的数据长度
        if (dataLen < 0 || dataLen > MAX_PAYLOAD) {
            throw IOException("ADB: corrupted message, invalid dataLen=$dataLen (max=$MAX_PAYLOAD)")
        }
        val data =
            if (dataLen > 0) ByteArray(dataLen).also { rawIn.readExact(it) } else ByteArray(0)
        return AdbMsg(command, arg0, arg1, data)
    }

    private data class AdbMsg(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as AdbMsg

            if (command != other.command) return false
            if (arg0 != other.arg0) return false
            if (arg1 != other.arg1) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = command
            result = 31 * result + arg0
            result = 31 * result + arg1
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    private fun signToken(token: ByteArray): ByteArray {
        // adbd expects RSA signature over SHA-1 digest info where token is the digest payload.
        val payload = ByteArray(sha1DigestInfoPrefix.size + token.size)
        sha1DigestInfoPrefix.copyInto(payload, destinationOffset = 0)
        token.copyInto(payload, destinationOffset = sha1DigestInfoPrefix.size)
        return Signature.getInstance("NONEwithRSA").apply {
            initSign(privateKey)
            update(payload)
        }.sign()
    }

    private fun buildAdbPubKey(): ByteArray {
        val kf = KeyFactory.getInstance("RSA")
        val pub = kf.generatePublic(X509EncodedKeySpec(publicKeyX509))
        val spec = kf.getKeySpec(pub, RSAPublicKeySpec::class.java)
        val adbKeyBytes = encodeAdbPublicKey(spec.modulus, spec.publicExponent.toInt())
        return "${Base64.encodeToString(adbKeyBytes, Base64.NO_WRAP)} $keyName\u0000"
            .toByteArray(Charsets.UTF_8)
    }

    private fun encodeAdbPublicKey(modulus: BigInteger, exponent: Int): ByteArray {
        val words = 64
        val bytes = 256
        val two32 = BigInteger.ONE.shiftLeft(32)
        val mask32 = two32.subtract(BigInteger.ONE)

        fun toBigEndianPadded(n: BigInteger): ByteArray {
            val raw = n.toByteArray()
            val arr = ByteArray(bytes)
            val src = if (raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
            src.copyInto(arr, destinationOffset = bytes - src.size)
            return arr
        }

        val modBE = toBigEndianPadded(modulus)
        // n0 is the least-significant 32 bits of modulus; for RSA modulus this must be odd.
        val n0 = modulus.and(mask32)
        val n0inv = n0.modInverse(two32).negate().mod(two32).toInt()
        val r = BigInteger.ONE.shiftLeft(bytes * 8)
        val rrBE = toBigEndianPadded(r.multiply(r).mod(modulus))

        val buf = ByteBuffer.allocate(4 + 4 + bytes + bytes + 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(words)
        buf.putInt(n0inv)
        for (i in words - 1 downTo 0) {
            val o = i * 4
            buf.put(modBE[o + 3]); buf.put(modBE[o + 2]); buf.put(modBE[o + 1]); buf.put(modBE[o])
        }
        for (i in words - 1 downTo 0) {
            val o = i * 4
            buf.put(rrBE[o + 3]); buf.put(rrBE[o + 2]); buf.put(rrBE[o + 1]); buf.put(rrBE[o])
        }
        buf.putInt(exponent)
        return buf.array()
    }
}

/**
 * Logical ADB stream abstraction mapped to a local id. Provides blocking
 * `InputStream`/`OutputStream` implementations and lifecycle helpers used by callers.
 */
class AdbSocketStream(
    val localId: Int,
    private val sender: (cmd: Int, arg0: Int, arg1: Int, `data`: ByteArray) -> Unit,
    private val flowControlWindow: Int = 0,
): Closeable {

    companion object {
        private const val A_WRTE = 0x45545257
        private const val A_CLSE = 0x45534c43
    }

    @Volatile
    var remoteId: Int = 0

    @Volatile
    var closed: Boolean = false

    private val latch = CountDownLatch(1)
    private val latchOk = AtomicBoolean(false)
    private val queue = LinkedBlockingQueue<Any>()

    // need notifyAll() / wait()
    private val writeLock = Object()

    @Volatile
    private var inflightWrites = 0

    private object EndOfStreamMarker

    val inputStream: InputStream = InStream()
    val outputStream: OutputStream = OutStream()

    internal fun onRemoteOkay(remote: Int) {
        if (remoteId == 0) {
            remoteId = remote
            latchOk.set(true)
            latch.countDown()
        }
        if (flowControlWindow > 0) {
            synchronized(writeLock) {
                if (inflightWrites > 0) inflightWrites--
                writeLock.notifyAll()
            }
        }
    }

    internal fun onData(data: ByteArray) {
        if (!closed) queue.offer(data)
    }

    internal fun forceClose() {
        closed = true
        queue.offer(EndOfStreamMarker)
        latch.countDown()
        if (flowControlWindow > 0) {
            synchronized(writeLock) { writeLock.notifyAll() }
        }
    }

    fun awaitOpen(timeoutMs: Long) {
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw IOException("ADB stream open timed out (localId=$localId)")
        }
        if (!latchOk.get()) {
            throw IOException("ADB stream rejected by device (localId=$localId)")
        }
    }

    override fun close() {
        if (closed) return

        closed = true
        if (remoteId != 0) runCatching {
            sender(A_CLSE, localId, remoteId, ByteArray(0))
        }
        queue.offer(EndOfStreamMarker)
        if (flowControlWindow > 0) {
            synchronized(writeLock) { writeLock.notifyAll() }
        }
    }

    private inner class InStream: InputStream() {
        private var chunk: ByteArray? = null
        private var off = 0

        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) == -1) -1 else (b[0].toInt() and 0xFF)
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            while (true) {
                val c = chunk
                if (c != null && this.off < c.size) {
                    val n = minOf(len, c.size - this.off)
                    c.copyInto(b, off, this.off, this.off + n)
                    this.off += n
                    return n
                }
                chunk = null
                this.off = 0
                val next = queue.take()
                if (next === EndOfStreamMarker) {
                    return -1
                }
                chunk = next as ByteArray
            }
        }

        override fun available(): Int = chunk?.let { it.size - off } ?: 0
    }

    private inner class OutStream: OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()))
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed) throw IOException("ADB stream closed")
            if (len == 0) return
            if (flowControlWindow > 0) {
                synchronized(writeLock) {
                    while (inflightWrites >= flowControlWindow && !closed) {
                        writeLock.wait()
                    }
                    if (closed) throw IOException("ADB stream closed")
                    inflightWrites++
                }
            }
            sender(A_WRTE, localId, remoteId, b.copyOfRange(off, off + len))
        }

        override fun flush() {}
    }
}

private fun InputStream.readExact(buf: ByteArray) {
    var off = 0
    while (off < buf.size) {
        val n = read(buf, off, buf.size - off)
        if (n < 0) throw EOFException("readExact: expected ${buf.size} bytes, got $off")
        off += n
    }
}

private fun InputStream.readIntLE(): Int {
    val b0 = read()
    val b1 = read()
    val b2 = read()
    val b3 = read()
    if (b3 < 0) throw EOFException("readIntLE: EOF")
    return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
}

private fun OutputStream.writeIntLE(v: Int) {
    write(v and 0xFF)
    write(v shr 8 and 0xFF)
    write(v shr 16 and 0xFF)
    write(v shr 24 and 0xFF)
}
