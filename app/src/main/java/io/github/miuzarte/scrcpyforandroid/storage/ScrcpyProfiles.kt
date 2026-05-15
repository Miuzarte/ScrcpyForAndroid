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
                    .put("bundle", encodeBundleToJson(profile.bundle))
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
                        bundle = decodeBundleFromJson(item.optJSONObject("bundle")),
                        isBuiltinGlobal = id == ScrcpyOptions.GLOBAL_PROFILE_ID,
                    )
                )
            }
        }
        return State(profiles)
    }
}