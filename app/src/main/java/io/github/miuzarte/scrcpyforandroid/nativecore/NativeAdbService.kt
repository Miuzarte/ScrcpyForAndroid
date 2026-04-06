package io.github.miuzarte.scrcpyforandroid.nativecore

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.file.Path
import kotlin.time.Duration

/**
 * Higher-level ADB service that wraps `DirectAdbTransport` and provides
 * coroutine-based connect/disconnect/shell helpers for callers.
 *
 * Methods use Mutex for thread-safety because the underlying transport is single-connection
 * and may be accessed from multiple coroutines.
 * 
 * All network operations are executed on Dispatchers.IO.
 */
class NativeAdbService(appContext: Context) {
    private val transport = DirectAdbTransport(appContext)
    private val mutex = Mutex()

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

    suspend fun pair(host: String, port: Int, pairingCode: String): Boolean = mutex.withLock {
        val h = host.trim()
        val code = pairingCode.trim()
        require(h.isNotBlank()) { "host is blank" }
        require(code.isNotBlank()) { "pairing code is blank" }
        Log.i(TAG, "pair(): host=$h port=$port")
        return@withLock try {
            transport.pair(h, port, code)
        } catch (e: Exception) {
            Log.e(TAG, "pair(): failed host=$h port=$port", e)
            val detail = e.message ?: "${e.javaClass.simpleName} (no message)"
            throw IllegalStateException("ADB pair failed for $h:$port -> $detail", e)
        }
    }

    suspend fun discoverPairingService(
        timeoutMs: Long = 12_000,
        includeLanDevices: Boolean = true
    ): Pair<String, Int>? = mutex.withLock {
        return@withLock try {
            transport.discoverPairingService(timeoutMs, includeLanDevices)
        } catch (e: Exception) {
            Log.w(TAG, "discoverPairingService(): failed", e)
            null
        }
    }

    suspend fun discoverConnectService(
        timeoutMs: Long = 12_000,
        includeLanDevices: Boolean = true
    ): Pair<String, Int>? = mutex.withLock {
        return@withLock try {
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
    suspend fun connect(
        host: String,
        port: Int,
        timeout: Duration = Duration.INFINITE,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            Log.i(TAG, "connect(): host=$host port=$port")

            if (connection != null
                && connection!!.isAlive()
                && connectedHost == host
                && connectedPort == port
            ) {
                return@withLock
            }
            disconnectInternal()

            try {
                val conn = withTimeout(timeout) { transport.connect(host, port) }
                connection = conn
                connectedHost = host
                connectedPort = port
            } catch (e: Exception) {
                Log.e(TAG, "connect(): failed host=$host port=$port", e)
                val detail = e.message ?: "${e.javaClass.simpleName} (no message)"
                throw IllegalStateException("ADB connect failed to $host:$port -> $detail", e)
            }
        }
    }

    /**
     * Close the current ADB connection immediately.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        mutex.withLock {
            disconnectInternal()
        }
    }

    suspend fun isConnected(): Boolean = mutex.withLock {
        connection?.isAlive() == true
    }

    /**
     * Execute a shell command on the connected device and return stdout text.
     */
    suspend fun shell(command: String): String = mutex.withLock {
        val response = requireConnection().shell(command)
        Log.d(TAG, "command: $command, response: $response")
        response
    }

    suspend fun openShellStream(command: String): AdbSocketStream = mutex.withLock {
        requireConnection().openStream("shell:$command")
    }

    suspend fun push(localPath: Path, remotePath: String) = mutex.withLock {
        requireConnection().push(localPath.toFile().readBytes(), remotePath)
    }

    suspend fun openAbstractSocket(name: String): AdbSocketStream = mutex.withLock {
        requireConnection().openStream("localabstract:$name")
    }

    suspend fun close() {
        disconnect()
    }

    private fun disconnectInternal() {
        runCatching { connection?.close() }
        connection = null
        connectedHost = null
        connectedPort = null
    }

    private fun requireConnection(): DirectAdbConnection {
        return connection?.takeIf { it.isAlive() }
            ?: throw IllegalStateException("ADB not connected")
    }

    companion object {
        private const val TAG = "NativeAdbService"
    }
}
