package io.github.miuzarte.scrcpyforandroid.nativecore

import android.content.Context
import android.util.Log
import java.nio.file.Path

/**
 * Higher-level ADB service that wraps `DirectAdbTransport` and provides
 * synchronized connect/disconnect/shell helpers for callers.
 *
 * Methods are synchronized because the underlying transport is single-connection
 * and accessed from the app's serialized IO executor.
 */
class NativeAdbService(appContext: Context) {

    private val transport = DirectAdbTransport(appContext)

    @Volatile
    private var connection: DirectAdbConnection? = null

    @Volatile
    private var connectedHost: String? = null

    @Volatile
    private var connectedPort: Int? = null

    var keyName: String
        get() = transport.keyName
        set(value) {
            transport.keyName = value
        }

    @Synchronized
    fun pair(host: String, port: Int, pairingCode: String): Boolean {
        val h = host.trim()
        val code = pairingCode.trim()
        require(h.isNotBlank()) { "host is blank" }
        require(code.isNotBlank()) { "pairing code is blank" }
        Log.i(TAG, "pair(): host=$h port=$port")
        return try {
            transport.pair(h, port, code)
        } catch (e: Exception) {
            Log.e(TAG, "pair(): failed host=$h port=$port", e)
            val detail = e.message ?: "${e.javaClass.simpleName} (no message)"
            throw IllegalStateException("ADB pair failed for $h:$port -> $detail", e)
        }
    }

    @Synchronized
    fun discoverPairingService(
        timeoutMs: Long = 12_000,
        includeLanDevices: Boolean = true
    ): Pair<String, Int>? {
        return try {
            transport.discoverPairingService(timeoutMs, includeLanDevices)
        } catch (e: Exception) {
            Log.w(TAG, "discoverPairingService(): failed", e)
            null
        }
    }

    @Synchronized
    fun discoverConnectService(
        timeoutMs: Long = 12_000,
        includeLanDevices: Boolean = true
    ): Pair<String, Int>? {
        return try {
            transport.discoverConnectService(timeoutMs, includeLanDevices)
        } catch (e: Exception) {
            Log.w(TAG, "discoverConnectService(): failed", e)
            null
        }
    }

    /**
     * Connect to a remote ADB endpoint. If an existing connection points to the
     * same host:port it is reused; otherwise the previous connection is closed
     * before attempting the new connect.
     */
    @Synchronized
    fun connect(host: String, port: Int): Boolean {
        Log.i(TAG, "connect(): host=$host port=$port")
        val existing = connection
        if (existing != null && existing.isAlive() && connectedHost == host && connectedPort == port) {
            return true
        }
        disconnect()
        try {
            val conn = transport.connect(host, port)
            connection = conn
            connectedHost = host
            connectedPort = port
            return true
        } catch (e: Exception) {
            Log.e(TAG, "connect(): failed host=$host port=$port", e)
            val detail = e.message ?: "${e.javaClass.simpleName} (no message)"
            throw IllegalStateException("ADB connect failed to $host:$port -> $detail", e)
        }
    }

    /**
     * Close the current ADB connection immediately.
     */
    @Synchronized
    fun disconnect() {
        runCatching { connection?.close() }
        connection = null
        connectedHost = null
        connectedPort = null
    }

    @Synchronized
    fun isConnected(): Boolean = connection?.isAlive() == true

    /**
     * Execute a shell command on the connected device and return stdout text.
     */
    @Synchronized
    fun shell(command: String): String = requireConnection().shell(command)

    @Synchronized
    internal fun openShellStream(command: String): AdbSocketStream =
        requireConnection().openStream("shell:$command")

    @Synchronized
    fun push(localPath: Path, remotePath: String) {
        requireConnection().push(localPath.toFile().readBytes(), remotePath)
    }

    @Synchronized
    internal fun openAbstractSocket(name: String): AdbSocketStream =
        requireConnection().openStream("localabstract:$name")

    @Synchronized
    fun close() = disconnect()

    private fun requireConnection(): DirectAdbConnection {
        return connection?.takeIf { it.isAlive() }
            ?: throw IllegalStateException("ADB not connected")
    }

    companion object {
        private const val TAG = "NativeAdbService"
    }
}
