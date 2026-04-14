package io.github.miuzarte.scrcpyforandroid.nativecore

import android.util.Log
import org.conscrypt.Conscrypt
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocket

private const val TAG = "DirectAdbPairing"

private const val CURRENT_KEY_HEADER_VERSION = 1.toByte()
private const val MIN_SUPPORTED_KEY_HEADER_VERSION = 1.toByte()
private const val MAX_SUPPORTED_KEY_HEADER_VERSION = 1.toByte()
private const val MAX_PEER_INFO_SIZE = 8192
private const val MAX_PAYLOAD_SIZE = MAX_PEER_INFO_SIZE * 2

private const val EXPORTED_KEY_LABEL = "adb-label\u0000"
private const val EXPORTED_KEY_SIZE = 64
private const val PAIRING_PACKET_HEADER_SIZE = 6

/**
 * PeerInfo container used to pack peer metadata for the pairing exchange.
 */
private class PeerInfo(val type: Byte, rawData: ByteArray) {
    val data = ByteArray(MAX_PEER_INFO_SIZE - 1)

    init {
        rawData.copyInto(data, endIndex = rawData.size.coerceAtMost(MAX_PEER_INFO_SIZE - 1))
    }

    fun writeTo(buffer: ByteBuffer) {
        buffer.put(type)
        buffer.put(data)
    }

    companion object {
        const val ADB_RSA_PUB_KEY: Byte = 0

        fun readFrom(buffer: ByteBuffer): PeerInfo {
            val type = buffer.get()
            val data = ByteArray(MAX_PEER_INFO_SIZE - 1)
            buffer.get(data)
            return PeerInfo(type, data)
        }
    }
}

/**
 * Small header framing structure used by the ADB pairing packet format.
 */
private class PairingPacketHeader(val version: Byte, val type: Byte, val payload: Int) {
    object Type {
        const val SPAKE2_MSG: Byte = 0
        const val PEER_INFO: Byte = 1
    }

    fun writeTo(buffer: ByteBuffer) {
        buffer.put(version)
        buffer.put(type)
        buffer.putInt(payload)
    }

    companion object {
        fun readFrom(buffer: ByteBuffer): PairingPacketHeader? {
            val version = buffer.get()
            val type = buffer.get()
            val payload = buffer.int

            if (version !in MIN_SUPPORTED_KEY_HEADER_VERSION..MAX_SUPPORTED_KEY_HEADER_VERSION) {
                Log.e(TAG, "header version mismatch: $version")
                return null
            }
            if (type != Type.SPAKE2_MSG && type != Type.PEER_INFO) {
                Log.e(TAG, "unknown packet type: $type")
                return null
            }
            if (payload !in 1..MAX_PAYLOAD_SIZE) {
                Log.e(TAG, "unsafe payload size: $payload")
                return null
            }
            return PairingPacketHeader(version, type, payload)
        }
    }
}

/**
 * Wrapper around native SPAKE2 pairing context used to perform the cryptographic
 * exchanges. The native implementation is provided by the `adbpairing` library.
 */
private class PairingContext private constructor(private val nativePtr: Long) {

    val msg: ByteArray = nativeMsg(nativePtr)

    fun initCipher(theirMsg: ByteArray): Boolean = nativeInitCipher(nativePtr, theirMsg)

    fun encrypt(input: ByteArray): ByteArray? = nativeEncrypt(nativePtr, input)

    fun decrypt(input: ByteArray): ByteArray? = nativeDecrypt(nativePtr, input)

    fun destroy() {
        nativeDestroy(nativePtr)
    }

    private external fun nativeMsg(nativePtr: Long): ByteArray
    private external fun nativeInitCipher(nativePtr: Long, theirMsg: ByteArray): Boolean
    private external fun nativeEncrypt(nativePtr: Long, inbuf: ByteArray): ByteArray?
    private external fun nativeDecrypt(nativePtr: Long, inbuf: ByteArray): ByteArray?
    private external fun nativeDestroy(nativePtr: Long)

    companion object {
        fun create(password: ByteArray): PairingContext? {
            val ptr = nativeConstructor(true, password)
            return if (ptr != 0L) PairingContext(ptr) else null
        }

        @JvmStatic
        private external fun nativeConstructor(isClient: Boolean, password: ByteArray): Long
    }
}

/**
 * Client-side implementation of the ADB pairing protocol.
 *
 * Connects to the device's pairing port, performs the TLS handshake, derives
 * keying material using the user-supplied pairing code, and exchanges peer
 * information to complete pairing.
 */
internal class DirectAdbPairingClient(
    private val host: String,
    private val port: Int,
    private val pairingCode: String,
    private val key: AdbPairingKey,
) : Closeable {

    private enum class State {
        READY,
        EXCHANGING_MSGS,
        EXCHANGING_PEER_INFO,
        STOPPED;
    }

    private lateinit var socket: Socket
    private lateinit var inputStream: DataInputStream
    private lateinit var outputStream: DataOutputStream

    private val peerInfo = PeerInfo(PeerInfo.ADB_RSA_PUB_KEY, key.adbPublicKey)
    private lateinit var pairingContext: PairingContext
    private var state: State = State.READY

    fun start(): Boolean {
        check(isLibraryLoaded) { "Pairing native bridge is unavailable" }

        setupTlsConnection()
        state = State.EXCHANGING_MSGS
        if (!doExchangeMsgs()) {
            state = State.STOPPED
            return false
        }

        state = State.EXCHANGING_PEER_INFO
        if (!doExchangePeerInfo()) {
            state = State.STOPPED
            return false
        }

        state = State.STOPPED
        return true
    }

    private fun setupTlsConnection() {
        socket = Socket(host, port)
        socket.tcpNoDelay = true

        val sslSocket = key.sslContext.socketFactory
            .createSocket(socket, host, port, true) as SSLSocket
        sslSocket.startHandshake()

        inputStream = DataInputStream(sslSocket.inputStream)
        outputStream = DataOutputStream(sslSocket.outputStream)

        val codeBytes = pairingCode.toByteArray(Charsets.UTF_8)
        val keyMaterial = exportTlsKeyingMaterial(sslSocket)
        val password = ByteArray(codeBytes.size + keyMaterial.size)
        codeBytes.copyInto(password)
        keyMaterial.copyInto(password, destinationOffset = codeBytes.size)

        pairingContext = checkNotNull(PairingContext.create(password)) {
            "Unable to create pairing context"
        }
    }

    private fun exportTlsKeyingMaterial(sslSocket: SSLSocket): ByteArray {
        if (Conscrypt.isConscrypt(sslSocket)) {
            return Conscrypt.exportKeyingMaterial(
                sslSocket,
                EXPORTED_KEY_LABEL,
                null,
                EXPORTED_KEY_SIZE,
            )
        }

        throw IllegalStateException(
            "TLS socket is not backed by bundled Conscrypt: ${sslSocket.javaClass.name}",
        )
    }

    private fun createHeader(type: Byte, payloadSize: Int): PairingPacketHeader {
        return PairingPacketHeader(CURRENT_KEY_HEADER_VERSION, type, payloadSize)
    }

    private fun readHeader(): PairingPacketHeader? {
        val bytes = ByteArray(PAIRING_PACKET_HEADER_SIZE)
        inputStream.readFully(bytes)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        return PairingPacketHeader.readFrom(buffer)
    }

    private fun writeHeader(header: PairingPacketHeader, payload: ByteArray) {
        val buffer = ByteBuffer.allocate(PAIRING_PACKET_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        header.writeTo(buffer)
        outputStream.write(buffer.array())
        outputStream.write(payload)
    }

    private fun doExchangeMsgs(): Boolean {
        val msg = pairingContext.msg
        writeHeader(createHeader(PairingPacketHeader.Type.SPAKE2_MSG, msg.size), msg)

        val theirHeader = readHeader() ?: return false
        if (theirHeader.type != PairingPacketHeader.Type.SPAKE2_MSG) return false

        val theirMessage = ByteArray(theirHeader.payload)
        inputStream.readFully(theirMessage)
        return pairingContext.initCipher(theirMessage)
    }

    private fun doExchangePeerInfo(): Boolean {
        val plain = ByteBuffer.allocate(MAX_PEER_INFO_SIZE).order(ByteOrder.BIG_ENDIAN)
        peerInfo.writeTo(plain)

        val encrypted = pairingContext.encrypt(plain.array()) ?: return false
        writeHeader(createHeader(PairingPacketHeader.Type.PEER_INFO, encrypted.size), encrypted)

        val theirHeader = readHeader() ?: return false
        if (theirHeader.type != PairingPacketHeader.Type.PEER_INFO) return false

        val peerMessage = ByteArray(theirHeader.payload)
        inputStream.readFully(peerMessage)

        val decrypted =
            pairingContext.decrypt(peerMessage) ?: throw AdbInvalidPairingCodeException()
        if (decrypted.size != MAX_PEER_INFO_SIZE) {
            Log.e(TAG, "invalid peer info size: ${decrypted.size}")
            return false
        }
        PeerInfo.readFrom(ByteBuffer.wrap(decrypted).order(ByteOrder.BIG_ENDIAN))
        return true
    }

    override fun close() {
        runCatching { inputStream.close() }
        runCatching { outputStream.close() }
        runCatching { socket.close() }
        if (state != State.READY) {
            runCatching { pairingContext.destroy() }
        }
    }

    companion object {
        private val isLibraryLoaded: Boolean = runCatching {
            System.loadLibrary("adbpairing")
            true
        }.onFailure {
            Log.e(TAG, "loadLibrary(adbpairing) failed", it)
        }.getOrDefault(false)
    }
}
