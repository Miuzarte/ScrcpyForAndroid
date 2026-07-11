package io.github.miuzarte.scrcpyforandroid.nativecore

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import java.io.InputStream
import java.io.OutputStream
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

    /**
     * 当前正在连接中的socket，用于取消连接时强制关闭
     */
    @Volatile
    private var pendingSocket: java.net.Socket? = null

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
     *
     * @param timeout 连接超时时间，默认10秒。传入 Duration.INFINITE 表示不超时。
     */
    suspend fun connect(
        host: String,
        port: Int,
        timeout: Duration = Duration.INFINITE,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            Log.i(TAG, "connect(): host=$host port=$port timeout=$timeout")

            if (connection != null
                && connection!!.isAlive()
                && connectedHost == host
                && connectedPort == port
            ) {
                return@withLock
            }
            
            // 保护现有USB连接不被TCP连接请求断开
            if (connection != null 
                && connection!!.isAlive() 
                && connection!!.connectionType == DirectAdbConnection.ConnectionType.STREAM
            ) {
                Log.w(TAG, "connect(): refusing to disconnect active USB connection for TCP request to $host:$port")
                throw IllegalStateException("Cannot establish TCP connection while USB is connected. Disconnect USB first.")
            }
            
            disconnectInternal()

            try {
                val timeoutMs = if (timeout.isInfinite()) 10_000 else timeout.inWholeMilliseconds.toInt()
                // 先创建连接对象获取socket引用，用于后续取消时强制关闭
                val conn = DirectAdbConnection(
                    host,
                    port,
                    transport.privateKey,
                    transport.publicKeyX509,
                    transport.keyName.ifBlank { AppSettings.ADB_KEY_NAME.defaultValue },
                    tcpMarker = true
                )
                pendingSocket = conn.socket
                try {
                    conn.handshake(timeoutMs)
                } finally {
                    pendingSocket = null
                }
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
     * 通过USB流连接ADB设备
     *
     * @param inputStream USB输入流
     * @param outputStream USB输出流
     * @param deviceId USB设备ID
     */
    suspend fun connectUsb(
        inputStream: InputStream,
        outputStream: OutputStream,
        deviceId: Int? = null
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            Log.i(TAG, "connectUsb(): deviceId=$deviceId")
            
            // 断开现有连接
            disconnectInternal()
            
            try {
                // 通过USB流创建连接
                val conn = DirectAdbConnection(
                    inputStream,
                    outputStream,
                    transport.privateKey,
                    transport.publicKeyX509,
                    transport.keyName.ifBlank { AppSettings.ADB_KEY_NAME.defaultValue },
                    deviceId
                )
                conn.handshake()
                
                connection = conn
                connectedHost = "usb:$deviceId"
                connectedPort = 0
            } catch (e: Exception) {
                Log.e(TAG, "connectUsb(): failed deviceId=$deviceId", e)
                val detail = e.message ?: "${e.javaClass.simpleName} (no message)"
                throw IllegalStateException("ADB USB connect failed for device $deviceId -> $detail", e)
            }
        }
    }

    /**
     * 强制中断当前正在进行的连接。
     * 通过关闭pendingSocket来让阻塞中的socket.connect()立即抛出异常。
     */
    fun cancelPendingConnect() {
        val socket = pendingSocket
        if (socket != null) {
            Log.i(TAG, "cancelPendingConnect(): 强制关闭pendingSocket以中断连接")
            runCatching { socket.close() }
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

    /**
     * 通过ADB协议层启用TCP/IP模式
     *
     * 直接发送 `tcpip:PORT` 服务命令，不依赖shell，不需要root权限。
     * 这是ADB原生支持的命令，比通过shell执行 `setprop service.adb.tcp.port` 更可靠。
     *
     * @param port TCP端口号，通常为5555
     * @return 服务端响应
     */
    suspend fun tcpip(port: Int): String {
        val conn = snapshotConnection()
        val response = conn.tcpip(port)
        Log.d(TAG, "tcpip: port=$port, response: $response")
        return response
    }

    suspend fun shellBatch(build: ShellBatchBuilder.() -> Unit): List<String> {
        val builder = ShellBatchBuilder().apply(build)
        if (builder.commands.isEmpty()) {
            return emptyList()
        }
        val markers = List(builder.commands.size) { index ->
            "__SCRCPY_BATCH_${System.nanoTime()}_${index}__"
        }
        val script = buildString {
            builder.commands.forEachIndexed { index, command ->
                append(command)
                append("; printf '\\n")
                append(markers[index])
                append("\\n'")
                if (index != builder.commands.lastIndex) {
                    append("; ")
                }
            }
        }
        val response = shell(script)
        val outputs = ArrayList<String>(builder.commands.size)
        var remaining = response
        markers.forEach { marker ->
            val token = "\n$marker\n"
            val markerIndex = remaining.indexOf(token)
                .takeIf { it >= 0 }
                ?: throw IllegalStateException("Shell batch marker missing: $marker")
            outputs += remaining.substring(0, markerIndex).trimEnd('\r', '\n')
            remaining = remaining.substring(markerIndex + token.length)
        }
        return outputs
    }

    suspend fun startApp(
        packageName: String,
        displayId: Int? = null,
        forceStop: Boolean = false,
    ): String {
        val normalizedPackageName = packageName.trim()
        require(normalizedPackageName.isNotBlank()) { "package name is blank" }
        val resolveCommand =
            "cmd package resolve-activity --brief ${quoteShellArg(normalizedPackageName)}"
        val resolveOutputIndex = if (forceStop) 1 else 0
        val batchResult = shellBatch {
            if (forceStop) command("am force-stop ${quoteShellArg(normalizedPackageName)}")
            command(resolveCommand)
        }
        val resolveOutput = batchResult.getOrElse(resolveOutputIndex) { "" }
        val componentName = resolveOutput
            .lineSequence()
            .map(String::trim)
            .lastOrNull { '/' in it }
            ?: throw IllegalStateException(
                "Cannot resolve launch activity for $normalizedPackageName",
            )

        val displayArg = displayId
            ?.takeIf { it >= 0 }
            ?.let { " --display $it" }
            .orEmpty()
        val command = "am start-activity$displayArg -n ${quoteShellArg(componentName)}"
        val response = shell(command)
        Log.d(TAG, "startApp(): package=$normalizedPackageName component=$componentName")
        return response
    }

    suspend fun openShellStream(command: String): AdbSocketStream {
        return snapshotConnection().openStream("shell:$command")
    }

    suspend fun ensureConnectionResponsive() {
        val conn = snapshotConnection()
        try {
            conn.shell("true")
        } catch (error: Exception) {
            mutex.withLock {
                if (connection === conn) disconnectInternal()
            }
            throw IllegalStateException("ADB connection is no longer available", error)
        }
    }

    suspend fun push(localPath: Path, remotePath: String) {
        snapshotConnection().push(localPath.toFile().readBytes(), remotePath)
    }

    suspend fun push(input: InputStream, remotePath: String, unixMode: Int = 420) {
        snapshotConnection().push(input, remotePath, unixMode)
    }

    suspend fun pull(remotePath: String): ByteArray {
        return snapshotConnection().pull(remotePath)
    }

    suspend fun pull(remotePath: String, output: OutputStream) {
        snapshotConnection().pull(remotePath, output)
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

    class ShellBatchBuilder internal constructor() {
        internal val commands = mutableListOf<String>()

        fun command(command: String) {
            commands += command
        }
    }

    private const val TAG = "NativeAdbService"
}
