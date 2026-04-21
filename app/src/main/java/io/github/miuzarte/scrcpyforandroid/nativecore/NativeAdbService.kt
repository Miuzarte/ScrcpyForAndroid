package io.github.miuzarte.scrcpyforandroid.nativecore

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
 * The mutex protects connection replacement and lifecycle transitions.
 * Once a live connection reference is obtained, stream I/O is performed outside
 * the mutex so long-running operations do not block disconnect or other calls.
 * 
 * All network operations are executed on Dispatchers.IO.
 */
object NativeAdbService {
    private val transport = DirectAdbTransport
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
        includeLanDevices: Boolean = true,
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
        includeLanDevices: Boolean = true,
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
    suspend fun shell(command: String): String {
        val conn = snapshotConnection()
        val response = conn.shell(command)
        Log.d(TAG, "command: $command, response: $response")
        return response
    }

    suspend fun startApp(
        packageName: String,
        displayId: Int? = null,
        forceStop: Boolean = false,
    ): String {
        val conn = snapshotConnection()
        val normalizedPackageName = packageName.trim()
        require(normalizedPackageName.isNotBlank()) { "package name is blank" }

        if (forceStop) {
            conn.shell(
                "am force-stop ${quoteShellArg(normalizedPackageName)}"
            )
        }

        val resolveOutput = conn.shell(
            "cmd package resolve-activity --brief ${quoteShellArg(normalizedPackageName)}"
        )
        val componentName = resolveOutput
            .lineSequence()
            .map(String::trim)
            .lastOrNull { '/' in it }
            ?: throw IllegalStateException(
                "Cannot resolve launch activity for $normalizedPackageName"
            )

        val displayArg = displayId
            ?.takeIf { it >= 0 }
            ?.let { " --display $it" }
            .orEmpty()
        val command = "am start-activity$displayArg -n ${quoteShellArg(componentName)}"
        val response = conn.shell(command)
        Log.d(TAG, "startApp(): package=$normalizedPackageName component=$componentName")
        return response
    }

    suspend fun openShellStream(command: String): AdbSocketStream {
        return snapshotConnection().openStream("shell:$command")
    }

    suspend fun push(localPath: Path, remotePath: String) {
        snapshotConnection().push(localPath.toFile().readBytes(), remotePath)
    }

    suspend fun openAbstractSocket(name: String): AdbSocketStream {
        return snapshotConnection().openStream("localabstract:$name")
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

    private suspend fun snapshotConnection(): DirectAdbConnection = mutex.withLock {
        requireConnection()
    }

    private fun quoteShellArg(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }

    private const val TAG = "NativeAdbService"
}
