package io.github.miuzarte.scrcpyforandroid.storage

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ScrcpyProfiles(context: Context) : Settings(context, "ScrcpyProfiles") {
    companion object {
        private val PROFILES_JSON = Pair(
            stringPreferencesKey("profiles_json"),
            "",
        )
    }

    data class Profile(
        val id: String,
        val name: String,
        val bundle: ScrcpyOptions.Bundle,
        val isBuiltinGlobal: Boolean = false,
    )

    data class State(
        val profiles: List<Profile>,
    ) {
        val globalProfile: Profile get() = profiles.first()
    }

    private val profilesJson by setting(PROFILES_JSON)

    val state: StateFlow<State> = createBundleState(::stateFromPreferences)

    private fun stateFromPreferences(preferences: androidx.datastore.preferences.core.Preferences): State {
        val raw = preferences.read(PROFILES_JSON)
        return normalizeState(
            runCatching { decodeState(raw) }.getOrNull() ?: State(emptyList())
        )
    }

    suspend fun loadState() = loadBundle(::stateFromPreferences)

    suspend fun saveState(newState: State) {
        profilesJson.set(encodeState(normalizeState(newState)))
    }

    suspend fun getProfiles(): List<Profile> = loadState().profiles

    suspend fun getProfile(id: String): Profile? = loadState().profiles.firstOrNull { it.id == id }

    suspend fun getProfileOrGlobal(id: String?): Profile {
        val state = loadState()
        return state.profiles.firstOrNull { it.id == id } ?: state.globalProfile
    }

    suspend fun getBundleOrGlobal(id: String?): ScrcpyOptions.Bundle =
        getProfileOrGlobal(id).bundle

    suspend fun updateBundle(id: String, bundle: ScrcpyOptions.Bundle): State {
        val current = loadState()
        val profiles = current.profiles.map {
            if (it.id == id) it.copy(bundle = bundle)
            else it
        }
        val next = normalizeState(current.copy(profiles = profiles))
        saveState(next)
        return next
    }

    suspend fun createProfile(
        requestedName: String,
        bundle: ScrcpyOptions.Bundle = ScrcpyOptions.defaultBundle(),
    ): Profile {
        val current = loadState()
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = ensureUniqueName(current, requestedName),
            bundle = bundle,
        )
        val next = normalizeState(current.copy(profiles = current.profiles + profile))
        saveState(next)
        return next.profiles.first { it.id == profile.id }
    }

    suspend fun duplicateProfile(
        sourceId: String,
        requestedName: String = "",
    ): Profile? {
        val current = loadState()
        val source = current.profiles.firstOrNull { it.id == sourceId } ?: return null
        return createProfile(
            requestedName = requestedName.ifBlank { source.name },
            bundle = source.bundle,
        )
    }

    suspend fun renameProfile(id: String, requestedName: String): Profile? {
        if (id == ScrcpyOptions.GLOBAL_PROFILE_ID) return null
        val current = loadState()
        val existing = current.profiles.firstOrNull { it.id == id } ?: return null
        val updated = existing.copy(
            name = ensureUniqueName(current, requestedName, excludeId = id),
        )
        val next = normalizeState(
            current.copy(
                profiles = current.profiles.map {
                    if (it.id == id) updated else it
                }
            )
        )
        saveState(next)
        return next.profiles.firstOrNull { it.id == id }
    }

    suspend fun deleteProfile(id: String): Boolean {
        if (id == ScrcpyOptions.GLOBAL_PROFILE_ID) return false
        val current = loadState()
        if (current.profiles.none { it.id == id }) return false
        val next = normalizeState(
            current.copy(
                profiles = current.profiles.filterNot { it.id == id }
            )
        )
        saveState(next)
        return true
    }

    suspend fun moveProfile(fromIndex: Int, toIndex: Int): State {
        val current = loadState()
        if (fromIndex !in current.profiles.indices || toIndex !in current.profiles.indices) {
            return current
        }
        if (fromIndex == toIndex || fromIndex == 0 || toIndex == 0) {
            return current
        }
        val mutable = current.profiles.toMutableList()
        val item = mutable.removeAt(fromIndex)
        val target = if (toIndex > fromIndex) toIndex - 1 else toIndex
        mutable.add(target, item)
        val next = normalizeState(current.copy(profiles = mutable))
        saveState(next)
        return next
    }

    fun ensureUniqueName(
        state: State,
        requestedName: String,
        excludeId: String? = null,
    ): String {
        val textGlobal = AppRuntime.stringResource(ScrcpyOptions.GLOBAL_PROFILE_NAME_RES_ID)
        val textNewProfile = AppRuntime.stringResource(ScrcpyOptions.NEW_PROFILE_NAME_RES_ID)
        val baseName = requestedName.trim().ifBlank { textNewProfile }
        if (baseName == textGlobal)
            return ensureUniqueName(state, textNewProfile, excludeId)
        val existingNames = state.profiles
            .filterNot { it.id == excludeId }
            .map { it.name }
            .toSet()
        if (baseName !in existingNames) return baseName
        var suffix = 1
        while (true) {
            val candidate = "$baseName ($suffix)"
            if (candidate !in existingNames) return candidate
            suffix++
        }
    }

    private fun normalizeState(state: State): State {
        val textGlobal = AppRuntime.stringResource(ScrcpyOptions.GLOBAL_PROFILE_NAME_RES_ID)
        val textNewProfile = AppRuntime.stringResource(ScrcpyOptions.NEW_PROFILE_NAME_RES_ID)
        val global = state.profiles.firstOrNull { it.id == ScrcpyOptions.GLOBAL_PROFILE_ID }
            ?.copy(name = textGlobal, isBuiltinGlobal = true)
            ?: Profile(
                id = ScrcpyOptions.GLOBAL_PROFILE_ID,
                name = textGlobal,
                bundle = ScrcpyOptions.defaultBundle(),
                isBuiltinGlobal = true,
            )
        val usedNames = linkedSetOf(global.name)
        val others = buildList {
            state.profiles
                .asSequence()
                .filterNot { it.id == ScrcpyOptions.GLOBAL_PROFILE_ID }
                .forEach { profile ->
                    val baseName = profile.name.trim().ifBlank { textNewProfile }
                        .takeUnless { it == textGlobal }
                        ?: textNewProfile
                    var normalizedName = baseName
                    if (normalizedName in usedNames) {
                        var suffix = 1
                        while (true) {
                            val candidate = "$baseName ($suffix)"
                            if (candidate !in usedNames) {
                                normalizedName = candidate
                                break
                            }
                            suffix++
                        }
                    }
                    usedNames += normalizedName
                    add(profile.copy(name = normalizedName, isBuiltinGlobal = false))
                }
        }
        return State(listOf(global) + others)
    }

    private fun encodeState(state: State): String {
        val array = JSONArray()
        for (profile in state.profiles) {
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("bundle", encodeBundle(profile.bundle))
            )
        }
        return array.toString()
    }

    private fun decodeState(raw: String): State {
        val textNewProfile = AppRuntime.stringResource(ScrcpyOptions.NEW_PROFILE_NAME_RES_ID)
        if (raw.isBlank()) return State(emptyList())
        val array = JSONArray(raw)
        val profiles = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val name = item.optString("name").trim()
                val bundleRaw = item.optString("bundle")
                if (id.isBlank()) continue
                add(
                    Profile(
                        id = id,
                        name = name.ifBlank { textNewProfile },
                        bundle = decodeBundle(item.optJSONObject("bundle")),
                        isBuiltinGlobal = id == ScrcpyOptions.GLOBAL_PROFILE_ID,
                    )
                )
            }
        }
        return State(profiles)
    }

    private fun encodeBundle(bundle: ScrcpyOptions.Bundle): JSONObject =
        JSONObject()
            .put("crop", bundle.crop)
            .put("recordFilename", bundle.recordFilename)
            .put("videoCodecOptions", bundle.videoCodecOptions)
            .put("audioCodecOptions", bundle.audioCodecOptions)
            .put("videoEncoder", bundle.videoEncoder)
            .put("audioEncoder", bundle.audioEncoder)
            .put("cameraId", bundle.cameraId)
            .put("cameraSize", bundle.cameraSize)
            .put("cameraSizeCustom", bundle.cameraSizeCustom)
            .put("cameraSizeUseCustom", bundle.cameraSizeUseCustom)
            .put("cameraAr", bundle.cameraAr)
            .put("cameraFps", bundle.cameraFps)
            .put("logLevel", bundle.logLevel)
            .put("videoCodec", bundle.videoCodec)
            .put("audioCodec", bundle.audioCodec)
            .put("videoSource", bundle.videoSource)
            .put("audioSource", bundle.audioSource)
            .put("recordFormat", bundle.recordFormat)
            .put("cameraFacing", bundle.cameraFacing)
            .put("maxSize", bundle.maxSize)
            .put("videoBitRate", bundle.videoBitRate)
            .put("audioBitRate", bundle.audioBitRate)
            .put("maxFps", bundle.maxFps)
            .put("angle", bundle.angle)
            .put("captureOrientation", bundle.captureOrientation)
            .put("captureOrientationLock", bundle.captureOrientationLock)
            .put("displayOrientation", bundle.displayOrientation)
            .put("recordOrientation", bundle.recordOrientation)
            .put("displayImePolicy", bundle.displayImePolicy)
            .put("displayId", bundle.displayId)
            .put("screenOffTimeout", bundle.screenOffTimeout)
            .put("showTouches", bundle.showTouches)
            .put("fullscreen", bundle.fullscreen)
            .put("control", bundle.control)
            .put("videoPlayback", bundle.videoPlayback)
            .put("audioPlayback", bundle.audioPlayback)
            .put("turnScreenOff", bundle.turnScreenOff)
            .put("keyInjectMode", bundle.keyInjectMode)
            .put("forwardKeyRepeat", bundle.forwardKeyRepeat)
            .put("stayAwake", bundle.stayAwake)
            .put("disableScreensaver", bundle.disableScreensaver)
            .put("powerOffOnClose", bundle.powerOffOnClose)
            .put("legacyPaste", bundle.legacyPaste)
            .put("clipboardAutosync", bundle.clipboardAutosync)
            .put("downsizeOnError", bundle.downsizeOnError)
            .put("mouseHover", bundle.mouseHover)
            .put("cleanup", bundle.cleanup)
            .put("powerOn", bundle.powerOn)
            .put("video", bundle.video)
            .put("audio", bundle.audio)
            .put("requireAudio", bundle.requireAudio)
            .put("killAdbOnClose", bundle.killAdbOnClose)
            .put("cameraHighSpeed", bundle.cameraHighSpeed)
            .put("list", bundle.list)
            .put("audioDup", bundle.audioDup)
            .put("newDisplay", bundle.newDisplay)
            .put("startApp", bundle.startApp)
            .put("startAppCustom", bundle.startAppCustom)
            .put("startAppUseCustom", bundle.startAppUseCustom)
            .put("vdDestroyContent", bundle.vdDestroyContent)
            .put("vdSystemDecorations", bundle.vdSystemDecorations)

    private fun decodeBundle(bundleJson: JSONObject?): ScrcpyOptions.Bundle {
        val defaults = ScrcpyOptions.defaultBundle()
        val json = bundleJson ?: return defaults
        return defaults.copy(
            crop = json.optStringOrDefault(
                "crop",
                defaults.crop,
            ),
            recordFilename = json.optStringOrDefault(
                "recordFilename",
                defaults.recordFilename,
            ),
            videoCodecOptions = json.optStringOrDefault(
                "videoCodecOptions",
                defaults.videoCodecOptions,
            ),
            audioCodecOptions = json.optStringOrDefault(
                "audioCodecOptions",
                defaults.audioCodecOptions,
            ),
            videoEncoder = json.optStringOrDefault(
                "videoEncoder",
                defaults.videoEncoder,
            ),
            audioEncoder = json.optStringOrDefault(
                "audioEncoder",
                defaults.audioEncoder,
            ),
            cameraId = json.optStringOrDefault(
                "cameraId",
                defaults.cameraId,
            ),
            cameraSize = json.optStringOrDefault(
                "cameraSize",
                defaults.cameraSize,
            ),
            cameraSizeCustom = json.optStringOrDefault(
                "cameraSizeCustom",
                defaults.cameraSizeCustom,
            ),
            cameraSizeUseCustom = json.optBooleanOrDefault(
                "cameraSizeUseCustom",
                defaults.cameraSizeUseCustom,
            ),
            cameraAr = json.optStringOrDefault(
                "cameraAr",
                defaults.cameraAr,
            ),
            cameraFps = json.optIntOrDefault(
                "cameraFps",
                defaults.cameraFps,
            ),
            logLevel = json.optStringOrDefault(
                "logLevel",
                defaults.logLevel,
            ),
            videoCodec = json.optStringOrDefault(
                "videoCodec",
                defaults.videoCodec,
            ),
            audioCodec = json.optStringOrDefault(
                "audioCodec",
                defaults.audioCodec,
            ),
            videoSource = json.optStringOrDefault(
                "videoSource",
                defaults.videoSource,
            ),
            audioSource = json.optStringOrDefault(
                "audioSource",
                defaults.audioSource,
            ),
            recordFormat = json.optStringOrDefault(
                "recordFormat",
                defaults.recordFormat,
            ),
            cameraFacing = json.optStringOrDefault(
                "cameraFacing",
                defaults.cameraFacing,
            ),
            maxSize = json.optIntOrDefault(
                "maxSize",
                defaults.maxSize,
            ),
            videoBitRate = json.optIntOrDefault(
                "videoBitRate",
                defaults.videoBitRate,
            ),
            audioBitRate = json.optIntOrDefault(
                "audioBitRate",
                defaults.audioBitRate,
            ),
            maxFps = json.optStringOrDefault(
                "maxFps",
                defaults.maxFps,
            ),
            angle = json.optStringOrDefault(
                "angle",
                defaults.angle,
            ),
            captureOrientation = json.optIntOrDefault(
                "captureOrientation",
                defaults.captureOrientation,
            ),
            captureOrientationLock = json.optStringOrDefault(
                "captureOrientationLock",
                defaults.captureOrientationLock,
            ),
            displayOrientation = json.optIntOrDefault(
                "displayOrientation",
                defaults.displayOrientation,
            ),
            recordOrientation = json.optIntOrDefault(
                "recordOrientation",
                defaults.recordOrientation,
            ),
            displayImePolicy = json.optStringOrDefault(
                "displayImePolicy",
                defaults.displayImePolicy,
            ),
            displayId = json.optIntOrDefault(
                "displayId",
                defaults.displayId,
            ),
            screenOffTimeout = json.optLongOrDefault(
                "screenOffTimeout",
                defaults.screenOffTimeout,
            ),
            showTouches = json.optBooleanOrDefault(
                "showTouches",
                defaults.showTouches,
            ),
            fullscreen = json.optBooleanOrDefault(
                "fullscreen",
                defaults.fullscreen,
            ),
            control = json.optBooleanOrDefault(
                "control",
                defaults.control,
            ),
            videoPlayback = json.optBooleanOrDefault(
                "videoPlayback",
                defaults.videoPlayback,
            ),
            audioPlayback = json.optBooleanOrDefault(
                "audioPlayback",
                defaults.audioPlayback,
            ),
            turnScreenOff = json.optBooleanOrDefault(
                "turnScreenOff",
                defaults.turnScreenOff,
            ),
            keyInjectMode = json.optStringOrDefault(
                "keyInjectMode",
                defaults.keyInjectMode,
            ),
            forwardKeyRepeat = json.optBooleanOrDefault(
                "forwardKeyRepeat",
                defaults.forwardKeyRepeat,
            ),
            stayAwake = json.optBooleanOrDefault(
                "stayAwake",
                defaults.stayAwake,
            ),
            disableScreensaver = json.optBooleanOrDefault(
                "disableScreensaver",
                defaults.disableScreensaver,
            ),
            powerOffOnClose = json.optBooleanOrDefault(
                "powerOffOnClose",
                defaults.powerOffOnClose,
            ),
            legacyPaste = json.optBooleanOrDefault(
                "legacyPaste",
                defaults.legacyPaste,
            ),
            clipboardAutosync = json.optBooleanOrDefault(
                "clipboardAutosync",
                defaults.clipboardAutosync,
            ),
            downsizeOnError = json.optBooleanOrDefault(
                "downsizeOnError",
                defaults.downsizeOnError,
            ),
            mouseHover = json.optBooleanOrDefault(
                "mouseHover",
                defaults.mouseHover,
            ),
            cleanup = json.optBooleanOrDefault(
                "cleanup",
                defaults.cleanup,
            ),
            powerOn = json.optBooleanOrDefault(
                "powerOn",
                defaults.powerOn,
            ),
            video = json.optBooleanOrDefault(
                "video",
                defaults.video,
            ),
            audio = json.optBooleanOrDefault(
                "audio",
                defaults.audio,
            ),
            requireAudio = json.optBooleanOrDefault(
                "requireAudio",
                defaults.requireAudio,
            ),
            killAdbOnClose = json.optBooleanOrDefault(
                "killAdbOnClose",
                defaults.killAdbOnClose,
            ),
            cameraHighSpeed = json.optBooleanOrDefault(
                "cameraHighSpeed",
                defaults.cameraHighSpeed,
            ),
            list = json.optStringOrDefault(
                "list",
                defaults.list,
            ),
            audioDup = json.optBooleanOrDefault(
                "audioDup",
                defaults.audioDup,
            ),
            newDisplay = json.optStringOrDefault(
                "newDisplay",
                defaults.newDisplay,
            ),
            startApp = json.optStringOrDefault(
                "startApp",
                defaults.startApp,
            ),
            startAppCustom = json.optStringOrDefault(
                "startAppCustom",
                defaults.startAppCustom,
            ),
            startAppUseCustom = json.optBooleanOrDefault(
                "startAppUseCustom",
                defaults.startAppUseCustom,
            ),
            vdDestroyContent = json.optBooleanOrDefault(
                "vdDestroyContent",
                defaults.vdDestroyContent,
            ),
            vdSystemDecorations = json.optBooleanOrDefault(
                "vdSystemDecorations",
                defaults.vdSystemDecorations,
            ),
        )
    }

    private fun JSONObject.optStringOrDefault(key: String, defaultValue: String): String =
        if (has(key) && !isNull(key)) optString(key, defaultValue) else defaultValue

    private fun JSONObject.optBooleanOrDefault(key: String, defaultValue: Boolean): Boolean =
        if (has(key) && !isNull(key)) optBoolean(key, defaultValue) else defaultValue

    private fun JSONObject.optIntOrDefault(key: String, defaultValue: Int): Int =
        if (has(key) && !isNull(key)) optInt(key, defaultValue) else defaultValue

    private fun JSONObject.optLongOrDefault(key: String, defaultValue: Long): Long =
        if (has(key) && !isNull(key)) optLong(key, defaultValue) else defaultValue
}
