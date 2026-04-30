package io.github.miuzarte.scrcpyforandroid.pages

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.StreamActivity
import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcut
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcuts
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.AppScreenOn
import io.github.miuzarte.scrcpyforandroid.services.DisconnectCause
import io.github.miuzarte.scrcpyforandroid.services.EventLogMessage
import io.github.miuzarte.scrcpyforandroid.services.EventLogger.logEvent
import io.github.miuzarte.scrcpyforandroid.services.LocalInputService
import io.github.miuzarte.scrcpyforandroid.services.render
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyOptions
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.quickDevices
import io.github.miuzarte.scrcpyforandroid.storage.Storage.scrcpyOptions
import io.github.miuzarte.scrcpyforandroid.storage.Storage.scrcpyProfiles
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonAction
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private const val ADB_CONNECT_TIMEOUT_MS = 12_000L
private const val ADB_KEEPALIVE_INTERVAL_MS = 3_000L
private const val ADB_KEEPALIVE_TIMEOUT_MS = 1_500L
private const val ADB_AUTO_RECONNECT_DISCOVER_TIMEOUT_MS = 2_000L
private const val ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS = 2_000L
private const val ADB_TCP_PROBE_TIMEOUT_MS = 500

@OptIn(FlowPreview::class)
internal class DeviceTabViewModel(
    internal val scrcpy: Scrcpy,
    connectionServices: DeviceConnectionServices,
) : ViewModel() {

    val scrcpyListings: Scrcpy.Listings get() = scrcpy.listings

    private val adbCoordinator = connectionServices.adbCoordinator
    private val connectionStateStore = connectionServices.connectionStateStore
    private val connectionController = connectionServices.connectionController
    private val autoReconnectManager = connectionServices.autoReconnectManager

    private val _asBundle = MutableStateFlow(appSettings.bundleState.value)
    val asBundle: StateFlow<AppSettings.Bundle> = _asBundle.asStateFlow()

    private val _qdBundle = MutableStateFlow(quickDevices.bundleState.value)

    val soBundle: StateFlow<ScrcpyOptions.Bundle> = scrcpyOptions.bundleState
    val scrcpyProfilesState = scrcpyProfiles.state
    val connectionState = connectionStateStore.state
    val sessionInfo: StateFlow<Scrcpy.Session.SessionInfo?> = scrcpy.currentSessionState
    val listingsRefreshBusy: StateFlow<Boolean> = scrcpy.listings.refreshBusyState
    val listingsRefreshVersion: StateFlow<Int> = scrcpy.listings.refreshVersionState

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _adbConnecting = MutableStateFlow(false)
    val adbConnecting: StateFlow<Boolean> = _adbConnecting.asStateFlow()

    private val _editingDeviceId = MutableStateFlow<String?>(null)
    val editingDeviceId: StateFlow<String?> = _editingDeviceId.asStateFlow()

    private val _activeDeviceActionId = MutableStateFlow<String?>(null)
    val activeDeviceActionId: StateFlow<String?> = _activeDeviceActionId.asStateFlow()

    private val _showRecentTasksSheet = MutableStateFlow(false)
    val showRecentTasksSheet: StateFlow<Boolean> = _showRecentTasksSheet.asStateFlow()

    private val _showAllAppsSheet = MutableStateFlow(false)
    val showAllAppsSheet: StateFlow<Boolean> = _showAllAppsSheet.asStateFlow()

    private val _imeRequestToken = MutableStateFlow(0)
    val imeRequestToken: StateFlow<Int> = _imeRequestToken.asStateFlow()

    private val _pendingScrollToPreview = MutableStateFlow(false)
    val pendingScrollToPreview: StateFlow<Boolean> = _pendingScrollToPreview.asStateFlow()

    private val _quickConnectInput = MutableStateFlow(_qdBundle.value.quickConnectInput)
    val quickConnectInput: StateFlow<String> = _quickConnectInput.asStateFlow()

    private val _savedShortcuts = MutableStateFlow(
        DeviceShortcuts.unmarshalFrom(_qdBundle.value.quickDevicesList)
    )
    val savedShortcuts: StateFlow<DeviceShortcuts> = _savedShortcuts.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val sessionReconnectBlacklistHosts = mutableSetOf<String>()

    val adbConnected: StateFlow<Boolean> = connectionState
        .map { it.adbSession.isConnected }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            connectionState.value.adbSession.isConnected,
        )

    val statusLine: StateFlow<String> = connectionState
        .map { it.adbSession.statusLine }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            connectionState.value.adbSession.statusLine,
        )

    val isQuickConnected: StateFlow<Boolean> = connectionState
        .map { it.adbSession.isQuickConnected }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            connectionState.value.adbSession.isQuickConnected,
        )

    val currentTarget: StateFlow<ConnectionTarget?> = connectionState
        .map { it.adbSession.currentTarget }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            connectionState.value.adbSession.currentTarget,
        )

    val connectedDeviceLabel: StateFlow<String> = connectionState
        .map { it.adbSession.connectedDeviceLabel }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            connectionState.value.adbSession.connectedDeviceLabel,
        )

    val connectedScrcpyProfileId: StateFlow<String> = combine(
        connectionState.map { it.adbSession },
        _savedShortcuts,
    ) { session, shortcuts ->
        val target = session.currentTarget
        if (session.isConnected && target != null)
            shortcuts.get(target.host, target.port)?.scrcpyProfileId
                ?: session.connectedScrcpyProfileId
        else
            session.connectedScrcpyProfileId
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        connectionState.value.adbSession.connectedScrcpyProfileId,
    )

    val connectedScrcpyBundle: StateFlow<ScrcpyOptions.Bundle> = combine(
        connectedScrcpyProfileId,
        soBundle,
        scrcpyProfilesState,
    ) { profileId, globalBundle, profiles ->
        if (profileId == ScrcpyOptions.GLOBAL_PROFILE_ID) globalBundle
        else profiles.profiles.firstOrNull { it.id == profileId }?.bundle ?: globalBundle
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        soBundle.value,
    )

    val connectedVideoPlaybackEnabled: StateFlow<Boolean> = connectedScrcpyBundle
        .map { it.video && it.videoPlayback }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false,
        )

    val connectedScrcpyProfileName: StateFlow<String> =
        combine(
            connectedScrcpyProfileId,
            scrcpyProfilesState,
        ) { profileId, profiles ->
            profiles.profiles
                .firstOrNull { it.id == profileId }
                ?.name
                ?: AppRuntime.stringResource(ScrcpyOptions.GLOBAL_PROFILE_NAME_RES_ID)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            AppRuntime.stringResource(ScrcpyOptions.GLOBAL_PROFILE_NAME_RES_ID),
        )

    val canShowPreviewControls: StateFlow<Boolean> = combine(
        adbConnected,
        connectedVideoPlaybackEnabled,
        sessionInfo,
    ) { connected, videoPlayback, info ->
        connected && videoPlayback && info != null && info.width > 0 && info.height > 0
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        false,
    )

    val virtualButtonLayout: StateFlow<Pair<List<VirtualButtonAction>, List<VirtualButtonAction>>> =
        _asBundle.map {
            VirtualButtonActions.splitLayout(
                VirtualButtonActions.parseStoredLayout(it.virtualButtonsLayout)
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            VirtualButtonActions.splitLayout(
                VirtualButtonActions.parseStoredLayout(_asBundle.value.virtualButtonsLayout)
            ),
        )

    private val _fullscreenRequests = Channel<Unit>(Channel.BUFFERED)
    val fullscreenRequests: Flow<Unit> = _fullscreenRequests.receiveAsFlow()

    init {
        // Sync asBundle from storage -> local
        viewModelScope.launch {
            appSettings.bundleState.collectLatest { shared ->
                if (_asBundle.value != shared) {
                    _asBundle.value = shared
                }
            }
        }

        // Debounced save asBundle local -> storage
        viewModelScope.launch {
            _asBundle.debounce(Settings.BUNDLE_SAVE_DELAY).collectLatest { bundle ->
                if (bundle != appSettings.bundleState.value) {
                    appSettings.saveBundle(bundle)
                }
            }
        }

        // Sync qdBundle from storage -> local
        viewModelScope.launch {
            quickDevices.bundleState.collectLatest { shared ->
                if (_qdBundle.value != shared) {
                    _qdBundle.value = shared
                }
            }
        }

        // Debounced save qdBundle local -> storage
        viewModelScope.launch {
            _qdBundle.debounce(Settings.BUNDLE_SAVE_DELAY).collectLatest { bundle ->
                if (bundle != quickDevices.bundleState.value) {
                    quickDevices.saveBundle(bundle)
                }
            }
        }

        // Sync savedShortcuts from qdBundle serialized list
        viewModelScope.launch {
            _qdBundle.collectLatest { bundle ->
                val parsed = DeviceShortcuts.unmarshalFrom(bundle.quickDevicesList)
                if (parsed.marshalToString() != _savedShortcuts.value.marshalToString()) {
                    _savedShortcuts.value = parsed
                }
            }
        }

        // Persist savedShortcuts -> qdBundle (debounced)
        viewModelScope.launch {
            _savedShortcuts.debounce(Settings.BUNDLE_SAVE_DELAY).collectLatest { shortcuts ->
                val serialized = shortcuts.marshalToString()
                if (serialized != _qdBundle.value.quickDevicesList) {
                    _qdBundle.update { it.copy(quickDevicesList = serialized) }
                }
            }
        }
    }

    override fun onCleared() {
        runBlocking(Dispatchers.IO) {
            appSettings.saveBundle(_asBundle.value)
            quickDevices.saveBundle(_qdBundle.value)
        }
    }

    fun resolveScrcpyBundle(profileId: String): ScrcpyOptions.Bundle {
        if (profileId == ScrcpyOptions.GLOBAL_PROFILE_ID) return soBundle.value
        return scrcpyProfilesState.value.profiles
            .firstOrNull { it.id == profileId }
            ?.bundle
            ?: soBundle.value
    }

    fun setQuickConnectInput(value: String) {
        _quickConnectInput.value = value
    }

    fun saveQuickConnectInput() {
        _qdBundle.update { it.copy(quickConnectInput = _quickConnectInput.value) }
    }

    fun setEditingDeviceId(id: String?) {
        _editingDeviceId.value = id
    }

    fun setAppInForeground(foreground: Boolean) {
        _isAppInForeground.value = foreground
    }

    fun showRecentTasks() {
        _showRecentTasksSheet.value = true
    }

    fun hideRecentTasks() {
        _showRecentTasksSheet.value = false
    }

    fun showAllApps() {
        _showAllAppsSheet.value = true
    }

    fun hideAllApps() {
        _showAllAppsSheet.value = false
    }

    fun toggleIme() {
        _imeRequestToken.update { it + 1 }
    }

    fun updateAsBundle(transform: (AppSettings.Bundle) -> AppSettings.Bundle) {
        _asBundle.update(transform)
    }

    fun updateShortcut(
        id: String? = null,
        host: String? = null,
        port: Int? = null,
        name: String? = null,
        startScrcpyOnConnect: Boolean? = null,
        openFullscreenOnStart: Boolean? = null,
        scrcpyProfileId: String? = null,
        newPort: Int? = null,
        updateNameOnlyWhenEmpty: Boolean = false,
    ) {
        _savedShortcuts.update {
            it.update(
                id,
                host,
                port,
                name,
                startScrcpyOnConnect,
                openFullscreenOnStart,
                scrcpyProfileId,
                newPort,
                updateNameOnlyWhenEmpty,
            )
        }
    }

    fun upsertShortcut(shortcut: DeviceShortcut) {
        _savedShortcuts.update { it.upsert(shortcut) }
    }

    fun removeShortcut(id: String) {
        _savedShortcuts.update { it.remove(id) }
    }

    fun handleVirtualButtonAction(action: VirtualButtonAction) {
        when (action) {
            VirtualButtonAction.RECENT_TASKS -> _showRecentTasksSheet.value = true
            VirtualButtonAction.ALL_APPS -> _showAllAppsSheet.value = true
            VirtualButtonAction.TOGGLE_IME -> _imeRequestToken.update { it + 1 }
            VirtualButtonAction.PASTE_LOCAL_CLIPBOARD -> { /* handled by Composable with context */
            }

            else -> {
                val keycode = action.keycode ?: return
                runBusy(
                    EventLogMessage.Resource(
                        R.string.vm_send_action,
                        listOf(EventLogMessage.Resource(action.titleResId)),
                    )
                ) {
                    scrcpy.injectKeycode(0, keycode)
                    scrcpy.injectKeycode(1, keycode)
                }
            }
        }
    }

    fun startScrcpy() = runBusy(EventLogMessage.Resource(R.string.vm_start_scrcpy)) {
        startScrcpySession()
    }

    fun stopScrcpy() = runBusy(EventLogMessage.Resource(R.string.vm_stop_scrcpy)) {
        stopScrcpySession()
    }

    fun startScrcpy(packageName: String) = runBusy(EventLogMessage.Resource(R.string.vm_start_scrcpy)) {
        startScrcpySession(startAppOverride = packageName)
    }

    fun launchAppWithFallback(packageName: String) = runBusy(EventLogMessage.Resource(R.string.vm_launch_app)) {
        runCatching { scrcpy.startApp(packageName) }
            .onSuccess { logEvent(R.string.vm_app_started_on_display, packageName) }
            .onFailure { error ->
                AppRuntime.snackbar(R.string.vm_start_app_fallback_adb)
                logEvent(R.string.vm_start_app_fallback_adb, level = Log.WARN, error = error)
                adbCoordinator.startApp(packageName = packageName)
                logEvent(R.string.vm_app_started_via_adb, packageName)
            }
    }

    private fun runBusy(
        label: EventLogMessage,
        onFinished: (() -> Unit)? = null,
        block: suspend () -> Unit
    ) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (_: TimeoutCancellationException) {
                logEvent(R.string.vm_label_timeout, label, level = Log.WARN)
            } catch (e: IllegalArgumentException) {
                val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                logEvent(R.string.vm_label_param_error, label, detail, level = Log.WARN, error = e)
                AppRuntime.snackbar(R.string.vm_label_param_error, label.render(AppRuntime.context), detail)
            } catch (e: Exception) {
                val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                logEvent(R.string.vm_label_failed, label, detail, level = Log.ERROR, error = e)
            } finally {
                _busy.value = false
                onFinished?.invoke()
            }
        }
    }

    private fun runAdbConnect(
        label: EventLogMessage,
        onStarted: (() -> Unit)? = null,
        onFinished: (() -> Unit)? = null,
        block: suspend () -> Unit,
    ) {
        if (_adbConnecting.value) return
        viewModelScope.launch {
            onStarted?.invoke()
            _adbConnecting.value = true
            try {
                block()
            } catch (_: TimeoutCancellationException) {
                logEvent(R.string.vm_label_timeout, label, level = Log.WARN)
            } catch (e: IllegalArgumentException) {
                val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                logEvent(R.string.vm_label_param_error, label, detail, level = Log.WARN, error = e)
                AppRuntime.snackbar(R.string.vm_label_param_error, label.render(AppRuntime.context), detail)
            } catch (e: Exception) {
                val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                logEvent(R.string.vm_label_failed, label, detail, level = Log.ERROR, error = e)
            } finally {
                _adbConnecting.value = false
                onFinished?.invoke()
            }
        }
    }

    suspend fun disconnectAdbConnection(
        clearQuickOnlineForTarget: ConnectionTarget? = currentTarget.value,
        logMessage: String? = null,
        cause: DisconnectCause = DisconnectCause.User,
        statusLine: String = "Disconnected",
    ) {
        val result = connectionController.disconnectAdbConnection(
            clearQuickOnlineForTarget,
            cause,
            statusLine
        )
        result.clearedTarget?.let { target ->
            if (target.host.isNotBlank())
                _savedShortcuts.update { it.update(host = target.host, port = target.port) }
        }
        logMessage?.let { logEvent(it) }
    }

    suspend fun disconnectCurrentTargetBeforeConnecting(newHost: String, newPort: Int) {
        val disconnected =
            connectionController.disconnectCurrentTargetBeforeConnecting(newHost, newPort) ?: return
        sessionReconnectBlacklistHosts += disconnected.host
        if (disconnected.host.isNotBlank())
            _savedShortcuts.update { it.update(host = disconnected.host, port = disconnected.port) }
    }

    suspend fun connectWithTimeout(host: String, port: Int) {
        connectionController.connectWithTimeout(host, port, ADB_CONNECT_TIMEOUT_MS)
    }

    fun applyConnectedDeviceCapabilities(sdkInt: Int) {
        connectionController.applyConnectedDeviceCapabilities(sdkInt)
    }

    suspend fun handleAdbConnected(
        host: String,
        port: Int,
        autoStartScrcpy: Boolean = false,
        autoEnterFullScreen: Boolean = false,
        scrcpyProfileId: String = ScrcpyOptions.GLOBAL_PROFILE_ID,
    ) {
        val connected = connectionController.handleAdbConnected(host, port, scrcpyProfileId)
        val info = connected.info
        val fullLabel =
            if (info.serial.isNotBlank()) "${info.model} (${info.serial})" else info.model

        applyConnectedDeviceCapabilities(info.sdkInt)
        _savedShortcuts.update {
            it.update(
                host = host,
                port = port,
                name = fullLabel,
                updateNameOnlyWhenEmpty = true
            )
        }

        logEvent(
            "ADB connected: model=${info.model}, serial=${info.serial.ifBlank { "unknown" }}, " +
                    "manufacturer=${info.manufacturer.ifBlank { "unknown" }}, brand=${info.brand.ifBlank { "unknown" }}, " +
                    "device=${info.device.ifBlank { "unknown" }}, android=${info.androidRelease.ifBlank { "unknown" }}, sdk=${info.sdkInt}"
        )
        AppRuntime.snackbar(R.string.vm_adb_connected)

        if (_asBundle.value.adbAutoLoadAppListOnConnect) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { scrcpy.listings.getApps(forceRefresh = true) }
                    .onFailure { error ->
                        logEvent(
                            R.string.vm_failed_app_list_msg,
                            error.message ?: error.javaClass.simpleName,
                            level = Log.WARN,
                            error = error,
                        )
                    }
            }
        }

        if (autoStartScrcpy && sessionInfo.value == null) {
            runBusy(EventLogMessage.Resource(R.string.vm_start_scrcpy)) {
                startScrcpySession(openFullscreen = autoStartScrcpy && autoEnterFullScreen)
            }
        }
    }

    suspend fun startScrcpySession(
        openFullscreen: Boolean = false,
        startAppOverride: String? = null,
    ) {
        val activeBundle = resolveScrcpyBundle(connectedScrcpyProfileId.value)
        val options = scrcpyOptions.toClientOptions(activeBundle).fix()
        val resolvedOptions = startAppOverride
            ?.takeIf { it.isNotBlank() }
            ?.let { options.copy(startApp = it) }
            ?: options
        val session = scrcpy.start(resolvedOptions)
        _pendingScrollToPreview.value = resolvedOptions.video && resolvedOptions.videoPlayback

        if (resolvedOptions.startApp.isNotBlank() && resolvedOptions.control) {
            runCatching { scrcpy.startApp(resolvedOptions.startApp) }
                .onSuccess {
                    logEvent(R.string.vm_scrcpy_requested_app, resolvedOptions.startApp)
                }
                .onFailure { error ->
                    logEvent(
                        R.string.vm_scrcpy_start_app_failed,
                        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName,
                        level = Log.WARN,
                        error = error,
                    )
                }
        }

        if ((resolvedOptions.fullscreen || openFullscreen) &&
            resolvedOptions.video && resolvedOptions.videoPlayback
        ) {
            _fullscreenRequests.trySend(Unit)
        }
        if (resolvedOptions.disableScreensaver) AppScreenOn.acquire()
        connectionController.markScrcpyStarted()

        val videoDetail =
            if (!resolvedOptions.video) "off"
            else if (activeBundle.videoBitRate <= 0) "${session.codec?.string ?: "null"} ${session.width}x${session.height} @default"
            else "${session.codec?.string ?: "null"} ${session.width}x${session.height} " +
                    "@%.1fMbps".format(activeBundle.videoBitRate / 1_000_000f)

        val audioDetail =
            if (!activeBundle.audio) "off"
            else if (activeBundle.audioBitRate <= 0) "${resolvedOptions.audioCodec} default source=${resolvedOptions.audioSource}"
            else "${resolvedOptions.audioCodec} ${activeBundle.audioBitRate / 1_000f}Kbps" +
                    " source=${resolvedOptions.audioSource}" +
                    if (!resolvedOptions.audioPlayback) "(no-playback)" else ""

        logEvent(
            "scrcpy 已启动: device=${session.deviceName}, video=$videoDetail, audio=$audioDetail, " +
                    "control=${resolvedOptions.control}, turnScreenOff=${resolvedOptions.turnScreenOff}, " +
                    "maxSize=${resolvedOptions.maxSize}, maxFps=${resolvedOptions.maxFps}"
        )
        AppRuntime.snackbar(
            if (resolvedOptions.recordFilename.isNotBlank()) R.string.vm_scrcpy_started_recording
            else R.string.vm_scrcpy_started
        )
    }

    suspend fun stopScrcpySession() {
        val activeBundle = resolveScrcpyBundle(connectedScrcpyProfileId.value)
        val options = scrcpyOptions.toClientOptions(activeBundle).fix()
        if (options.killAdbOnClose) {
            currentTarget.value?.host?.let { sessionReconnectBlacklistHosts += it }
            val result = connectionController.stopScrcpySession(killAdbOnClose = true)
            result.clearedTarget?.let { target ->
                if (target.host.isNotBlank())
                    _savedShortcuts.update { it.update(host = target.host, port = target.port) }
            }
            logEvent(R.string.vm_scrcpy_stopped_adb_disconnected_log)
            AppRuntime.snackbar(R.string.vm_scrcpy_stopped_adb_disconnected)
        } else {
            connectionController.stopScrcpySession(killAdbOnClose = false)
            logEvent(R.string.vm_scrcpy_stopped)
            AppRuntime.snackbar(R.string.vm_scrcpy_stopped)
        }
    }

    fun shouldOpenFullscreenCompat(): Boolean = _asBundle.value.fullscreenCompatibilityMode

    fun openStreamActivity(context: Context) {
        context.startActivity(StreamActivity.createIntent(context))
    }

    suspend fun refreshApps() {
        runCatching { scrcpy.listings.getApps(forceRefresh = true) }
            .onFailure { error ->
                val detail = error.message ?: error.javaClass.simpleName
                logEvent(R.string.vm_failed_app_list_msg, detail, level = Log.WARN, error = error)
                withContext(Dispatchers.Main) {
                    AppRuntime.snackbar(
                        R.string.vm_failed_app_list_msg,
                        detail,
                    )
                }
            }
    }

    suspend fun refreshRecentTasks() {
        runCatching { scrcpy.listings.getRecentTasks(forceRefresh = true) }
            .onFailure { error ->
                val detail = error.message ?: error.javaClass.simpleName
                logEvent(R.string.vm_failed_recent_tasks_msg, detail, level = Log.WARN, error = error)
                withContext(Dispatchers.Main) {
                    AppRuntime.snackbar(
                        R.string.vm_failed_recent_tasks_msg,
                        detail,
                    )
                }
            }
    }

    suspend fun pasteLocalClipboard(context: Context) {
        val text = LocalInputService.getClipboardText(context)
            ?.takeIf { it.isNotBlank() }
        if (text == null) {
            AppRuntime.snackbar(R.string.vm_clipboard_paste_failed)
            return
        }
        val useLegacyPaste = connectedScrcpyBundle.value.legacyPaste
        runCatching {
            withContext(Dispatchers.IO) {
                if (useLegacyPaste) scrcpy.injectText(text)
                else scrcpy.setClipboard(text, paste = true)
            }
            logEvent(
                if (useLegacyPaste) R.string.vm_legacy_paste_injected
                else R.string.vm_clipboard_synced_paste
            )
        }.onFailure { error ->
            logEvent(R.string.vm_clipboard_paste_failed, level = Log.WARN, error = error)
            AppRuntime.snackbar(
                if (useLegacyPaste) R.string.fullscreen_legacy_paste_failed
                else R.string.fullscreen_clipboard_sync_failed
            )
        }
    }

    suspend fun commitImeText(text: String) {
        submitImeText(
            scrcpy = scrcpy, text = text,
            keyInjectMode = scrcpyOptions.toClientOptions(connectedScrcpyBundle.value).keyInjectMode,
        ) { error, useClipboardPaste ->
            logEvent(
                R.string.vm_ime_text_failed,
                error.message ?: error.javaClass.simpleName,
                level = Log.WARN,
                error = error,
            )
            AppRuntime.snackbar(
                if (useClipboardPaste) R.string.fullscreen_paste_non_ascii
                else R.string.fullscreen_text_input_failed
            )
        }
    }

    fun onDeviceAction(device: DeviceShortcut) {
        val connected = adbConnected.value
                && currentTarget.value?.host == device.host
                && currentTarget.value?.port == device.port

        if (!connected) {
            runAdbConnect(
                label = EventLogMessage.Resource(R.string.vm_connect_adb),
                onStarted = { _activeDeviceActionId.value = device.id },
                onFinished = { _activeDeviceActionId.value = null },
            ) {
                disconnectCurrentTargetBeforeConnecting(device.host, device.port)
                try {
                    connectWithTimeout(device.host, device.port)
                    handleAdbConnected(
                        host = device.host,
                        port = device.port,
                        autoStartScrcpy = device.startScrcpyOnConnect,
                        autoEnterFullScreen = device.startScrcpyOnConnect && device.openFullscreenOnStart,
                        scrcpyProfileId = device.scrcpyProfileId
                    )
                    connectionController.updateQuickConnected(false)
                } catch (error: Exception) {
                    connectionController.markConnectionFailed(error)
                    logEvent(R.string.vm_adb_connection_failed, level = Log.ERROR, error = error)
                    AppRuntime.snackbar(R.string.vm_adb_connection_failed)
                }
            }
            return
        }

        runAdbConnect(
            label = EventLogMessage.Resource(R.string.vm_disconnect_adb),
            onStarted = { _activeDeviceActionId.value = device.id },
            onFinished = { _activeDeviceActionId.value = null },
        ) {
            sessionReconnectBlacklistHosts += device.host
            disconnectAdbConnection(
                ConnectionTarget(device.host, device.port),
                logMessage = "ADB disconnected: ${device.name}",
            )
        }
    }

    fun onQuickConnect(target: ConnectionTarget) {
        runAdbConnect(
            label = EventLogMessage.Resource(R.string.vm_connect_adb),
            onStarted = { _activeDeviceActionId.value = target.toString() },
            onFinished = { _activeDeviceActionId.value = null },
        ) {
            disconnectCurrentTargetBeforeConnecting(target.host, target.port)
            try {
                connectWithTimeout(target.host, target.port)
                handleAdbConnected(
                    host = target.host,
                    port = target.port,
                    autoStartScrcpy = false,
                    autoEnterFullScreen = false,
                    scrcpyProfileId = ScrcpyOptions.GLOBAL_PROFILE_ID,
                )
                connectionController.updateQuickConnected(true)
            } catch (error: Exception) {
                connectionController.markConnectionFailed(error)
                logEvent(R.string.vm_adb_connection_failed, level = Log.ERROR, error = error)
                AppRuntime.snackbar(R.string.vm_adb_connection_failed)
            }
        }
    }

    fun onDisconnectCurrent(target: ConnectionTarget?) {
        runAdbConnect(EventLogMessage.Resource(R.string.vm_disconnect_adb)) {
            target?.let {
                sessionReconnectBlacklistHosts += it.host
                disconnectAdbConnection(it, logMessage = "ADB disconnected")
            }
        }
    }

    fun onPair(host: String, port: String, code: String) {
        runBusy(EventLogMessage.Resource(R.string.vm_execute_pairing)) {
            val h = host.trim()
            val p = port.trim().toIntOrNull() ?: return@runBusy
            val c = code.trim()
            val ok = adbCoordinator.pair(h, p, c)
            logEvent(
                if (ok) R.string.vm_pairing_succeeded else R.string.vm_pairing_failed,
                level = if (ok) Log.INFO else Log.ERROR,
            )
            AppRuntime.snackbar(
                if (ok) R.string.vm_pairing_succeeded
                else R.string.vm_pairing_failed
            )
        }
    }

    suspend fun onDiscoverPairingTarget(): Pair<String, Int>? {
        return adbCoordinator.discoverPairingService(includeLanDevices = _asBundle.value.adbMdnsLanDiscovery)
    }

    // TODO: unused
    fun blacklistHost(host: String) {
        sessionReconnectBlacklistHosts += host
    }

    fun startKeepAliveLoop() {
        if (_keepAliveLoopStarted) return
        _keepAliveLoopStarted = true
        viewModelScope.launch {
            try {
                autoReconnectManager.runKeepAliveLoop(
                    isForeground = { _isAppInForeground.value },
                    intervalMs = ADB_KEEPALIVE_INTERVAL_MS,
                    connectTimeoutMs = ADB_CONNECT_TIMEOUT_MS,
                    keepAliveTimeoutMs = ADB_KEEPALIVE_TIMEOUT_MS,
                    onReconnectSuccess = { host, port ->
                        logEvent(R.string.vm_quick_probe_success, host, port)
                        AppRuntime.snackbar(R.string.vm_auto_reconnect_succeeded)
                    },
                    onReconnectFailure = { error ->
                        viewModelScope.launch {
                            disconnectAdbConnection(
                                cause = DisconnectCause.KeepAliveFailed,
                                statusLine = "ADB disconnected"
                            )
                        }
                        logEvent(R.string.vm_auto_reconnect_failed, level = Log.ERROR, error = error)
                        AppRuntime.snackbar(R.string.vm_auto_reconnect_failed)
                    },
                )
            } finally {
                _keepAliveLoopStarted = false
            }
        }
    }

    private var _keepAliveLoopStarted = false

    fun startAutoReconnectLoop() {
        if (_autoReconnectLoopStarted) return
        _autoReconnectLoopStarted = true
        viewModelScope.launch {
            autoReconnectManager.runAutoReconnectLoop(
                isForeground = { _isAppInForeground.value },
                isAutoReconnectEnabled = { _asBundle.value.adbAutoReconnectPairedDevice },
                isBusy = { _busy.value },
                isAdbConnecting = { _adbConnecting.value },
                hasActiveSession = { sessionInfo.value != null },
                savedShortcuts = { _savedShortcuts.value.toList() },
                isBlacklisted = { host -> sessionReconnectBlacklistHosts.contains(host) },
                connectTimeoutMs = ADB_CONNECT_TIMEOUT_MS,
                probeTimeoutMs = ADB_TCP_PROBE_TIMEOUT_MS,
                discoverConnectService = {
                    adbCoordinator.discoverConnectService(
                        ADB_AUTO_RECONNECT_DISCOVER_TIMEOUT_MS,
                        _asBundle.value.adbMdnsLanDiscovery,
                    )
                },
                onMdnsPortChanged = { host, oldPort, newPort ->
                    _savedShortcuts.update {
                        it.update(
                            host = host,
                            port = oldPort,
                            newPort = newPort,
                        )
                    }
                    logEvent(R.string.vm_mdns_updated, host, oldPort, newPort)
                },
                onKnownDeviceReconnected = { target ->
                    _savedShortcuts.update { it.update(host = target.host, port = target.port) }
                    logEvent(R.string.vm_quick_probe_success, target.host, target.port)
                },
                onDiscoveredDeviceReconnected = { host, port, _ ->
                    _savedShortcuts.update { it.update(host = host, port = port) }
                    logEvent(R.string.vm_quick_probe_success, host, port)
                },
                retryIntervalMs = ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS,
            )
        }
    }

    private var _autoReconnectLoopStarted = false

    fun startProfileIdSync() {
        if (_profileIdSyncStarted) return
        _profileIdSyncStarted = true
        viewModelScope.launch {
            combine(adbConnected, currentTarget, _savedShortcuts) { connected, target, shortcuts ->
                Triple(connected, target, shortcuts)
            }.collect { (connected, target, shortcuts) ->
                if (!connected || target == null) return@collect
                val boundProfileId = shortcuts.get(target.host, target.port)
                    ?.scrcpyProfileId ?: ScrcpyOptions.GLOBAL_PROFILE_ID
                if (boundProfileId != connectionState.value.adbSession.connectedScrcpyProfileId) {
                    connectionController.syncConnectedScrcpyProfileId(boundProfileId)
                    logEvent(R.string.vm_device_switched_profile, boundProfileId)
                }
            }
        }
    }

    private var _profileIdSyncStarted = false

    fun startRecentTasksAutoRefresh() {
        if (_recentTasksAutoRefreshStarted) return
        _recentTasksAutoRefreshStarted = true
        viewModelScope.launch {
            combine(adbConnected, currentTarget, _isAppInForeground) { connected, _, foreground ->
                connected && foreground
            }.collect { shouldRefresh ->
                if (!shouldRefresh) return@collect
                withContext(Dispatchers.IO) {
                    runCatching { scrcpy.listings.getRecentTasks(forceRefresh = true) }
                        .onFailure { error ->
                            logEvent(
                                R.string.vm_failed_recent_tasks_msg,
                                error.message ?: error.javaClass.simpleName,
                                level = Log.WARN,
                                error = error,
                            )
                        }
                }
            }
        }
    }

    private var _recentTasksAutoRefreshStarted = false

    fun clearPendingScrollToPreview() {
        _pendingScrollToPreview.value = false
    }

    suspend fun injectTouch(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float,
        actionButton: Int = 0,
        buttons: Int = 0,
    ) {
        scrcpy.injectTouch(
            action,
            pointerId,
            x,
            y,
            screenWidth,
            screenHeight,
            pressure,
            actionButton,
            buttons
        )
    }

    suspend fun pressBackOrTurnScreenOn(action: Int) {
        scrcpy.pressBackOrTurnScreenOn(action)
    }

    fun findCachedApp(packageName: String): Scrcpy.AppInfo? =
        scrcpy.listings.findCachedApp(packageName)

    suspend fun startApp(packageName: String) {
        scrcpy.startApp(packageName)
    }

    suspend fun startAppViaAdb(packageName: String) {
        adbCoordinator.startApp(packageName = packageName)
    }

    class Factory(
        private val scrcpy: Scrcpy,
        private val connectionServices: DeviceConnectionServices,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeviceTabViewModel(scrcpy, connectionServices) as T
        }
    }
}
