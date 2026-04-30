package io.github.miuzarte.scrcpyforandroid.services

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface EventLogMessage {
    data class Raw(val text: String) : EventLogMessage
    data class Resource(
        @param:StringRes val resId: Int,
        val args: List<Any> = emptyList(),
    ) : EventLogMessage
}

data class EventLogEntry(
    val timestamp: String,
    val message: EventLogMessage,
) {
    fun render(context: Context): String {
        return "[$timestamp] ${message.render(context)}"
    }
}

fun EventLogMessage.render(context: Context): String {
    return when (this) {
        is EventLogMessage.Raw -> text
        is EventLogMessage.Resource -> context.getString(
            resId,
            *args.map { arg ->
                if (arg is EventLogMessage) arg.render(context) else arg
            }.toTypedArray(),
        )
    }
}

/**
 * Global singleton for event logging.
 * 
 * Manages event logs with timestamp formatting and automatic log rotation.
 * Logs are stored in a thread-safe SnapshotStateList for Compose integration.
 */
object EventLogger {
    private const val LOG_TAG = "EventLogger"

    const val MAX_LINES = 512

    private val _eventLog: SnapshotStateList<EventLogEntry> = mutableStateListOf()

    /**
     * Read-only access to the event log list.
     */
    val eventLog: List<EventLogEntry> get() = _eventLog

    /**
     * Log an event with timestamp and optional error.
     * 
     * @param message The log message
     * @param level Log level (Log.INFO, Log.ERROR, Log.WARN, Log.DEBUG)
     * @param error Optional throwable for error logging
     */
    fun logEvent(message: String, level: Int = Log.INFO, error: Throwable? = null) {
        logEvent(EventLogMessage.Raw(message), level, error)
    }

    fun logEvent(
        @StringRes messageResId: Int,
        vararg args: Any,
        level: Int = Log.INFO,
        error: Throwable? = null,
    ) {
        logEvent(EventLogMessage.Resource(messageResId, args.toList()), level, error)
    }

    fun logEvent(message: EventLogMessage, level: Int = Log.INFO, error: Throwable? = null) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _eventLog.add(0, EventLogEntry(timestamp, message))

        // Rotate logs if exceeds max size
        if (_eventLog.size > MAX_LINES) {
            _eventLog.removeRange(MAX_LINES, _eventLog.size)
        }

        val logcatMessage = message.render(AppRuntime.context)

        // Log to Android logcat
        when (level) {
            Log.ERROR -> if (error != null) Log.e(LOG_TAG, logcatMessage, error)
            else Log.e(LOG_TAG, logcatMessage)

            Log.WARN -> if (error != null) Log.w(LOG_TAG, logcatMessage, error)
            else Log.w(LOG_TAG, logcatMessage)

            Log.DEBUG -> if (error != null) Log.d(LOG_TAG, logcatMessage, error)
            else Log.d(LOG_TAG, logcatMessage)

            else -> if (error != null) Log.i(LOG_TAG, logcatMessage, error)
            else Log.i(LOG_TAG, logcatMessage)
        }
    }

    /**
     * Clear all event logs.
     */
    fun clearLogs() {
        _eventLog.clear()
    }

    /**
     * Check if there are any logs.
     */
    fun hasLogs(): Boolean = _eventLog.isNotEmpty()
}
