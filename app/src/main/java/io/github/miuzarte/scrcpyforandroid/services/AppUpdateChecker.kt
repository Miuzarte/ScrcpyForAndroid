package io.github.miuzarte.scrcpyforandroid.services

import android.util.Log
import io.github.miuzarte.scrcpyforandroid.BuildConfig
import io.github.miuzarte.scrcpyforandroid.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object AppUpdateChecker {
    private const val TAG = "AppUpdateChecker"
    const val RELEASES_API_URL =
        "https://api.github.com/repos/Miuzarte/ScrcpyForAndroid/releases?per_page=1"
    const val RELEASES_URL =
        "https://github.com/Miuzarte/ScrcpyForAndroid/releases"
    const val REPO_URL =
        "https://github.com/Miuzarte/ScrcpyForAndroid"
    const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L

    data class ReleaseInfo(
        val currentVersion: String,
        val latestVersion: String,
        val hasUpdate: Boolean,
        val htmlUrl: String,
    )

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object Error : State
        data class Ready(val release: ReleaseInfo) : State
    }

    private val checkMutex = Mutex()
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun ensureChecked(currentVersion: String = BuildConfig.VERSION_NAME) {
        checkMutex.withLock {
            if (_state.value is State.Ready || _state.value is State.Checking) return
            _state.value = State.Checking
            runCatching { fetchLatestRelease(currentVersion) }
                .onSuccess { _state.value = State.Ready(it) }
                .onFailure { error ->
                    EventLogger.logEvent(
                        R.string.main_update_check_failed,
                        error.message ?: error.javaClass.simpleName,
                        level = Log.WARN,
                        error = error,
                    )
                    _state.value = State.Error
                }
        }
    }

    private suspend fun fetchLatestRelease(currentVersion: String): ReleaseInfo =
        withContext(Dispatchers.IO) {
            val connection = (URL(RELEASES_API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "ScrcpyForAndroid/$currentVersion")
            }
            try {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val release = JSONArray(body).optJSONObject(0)
                    ?: error("GitHub releases response is empty")
                val latestVersion = release.optString("tag_name")
                    .ifBlank { release.optString("name") }
                    .ifBlank { error("GitHub release has no tag name") }
                ReleaseInfo(
                    currentVersion = currentVersion,
                    latestVersion = latestVersion.removePrefix("v").removePrefix("V"),
                    hasUpdate = compareVersions(currentVersion, latestVersion) < 0,
                    htmlUrl = release.optString("html_url").ifBlank { RELEASES_URL },
                )
            } finally {
                connection.disconnect()
            }
        }

    private fun compareVersions(current: String, latest: String): Int {
        val currentParts = normalizeVersion(current)
        val latestParts = normalizeVersion(latest)
        val maxSize = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxSize) {
            val currentPart = currentParts.getOrElse(i) { 0 }
            val latestPart = latestParts.getOrElse(i) { 0 }
            if (currentPart != latestPart) {
                return currentPart.compareTo(latestPart)
            }
        }
        return 0
    }

    private fun normalizeVersion(value: String) =
        value
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .split(Regex("[^0-9]+"))
            .filter { it.isNotBlank() }
            .mapNotNull { it.toIntOrNull() }
            .ifEmpty { listOf(0) }
}
