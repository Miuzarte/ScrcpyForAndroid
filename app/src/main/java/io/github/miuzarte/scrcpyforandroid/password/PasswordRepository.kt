package io.github.miuzarte.scrcpyforandroid.password

import android.content.SharedPreferences
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.GeneralSecurityException
import java.security.KeyStoreException
import java.util.UUID

object PasswordRepository {
    private const val PREFS_NAME = "LockscreenPasswords"
    private const val KEY_ORDER = "__order"
    private const val ENTRY_PREFIX = "entry."
    private const val NAME_SUFFIX = ".name"
    private const val PASSWORD_SUFFIX = ".password"
    private const val CREATED_WITH_AUTH_SUFFIX = ".created_with_auth"

    class PasswordStorageCorruptedException(cause: Throwable) : Exception(cause)

    private val _entriesState = MutableStateFlow<List<PasswordEntry>>(emptyList())
    val entriesState: StateFlow<List<PasswordEntry>> = _entriesState.asStateFlow()

    private val prefs: SharedPreferences by lazy {
        val context = AppRuntime.context
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun refresh() {
        _entriesState.value = runCatching { getAll() }.getOrElse { emptyList() }
    }

    fun getAll(): List<PasswordEntry> {
        return runCatching {
            val all = prefs.all
            val nameIds = all.keys
                .filter { it.startsWith(ENTRY_PREFIX) && it.endsWith(NAME_SUFFIX) }
                .map { it.removePrefix(ENTRY_PREFIX).removeSuffix(NAME_SUFFIX) }
                .distinct()
            val storedOrder = (all[KEY_ORDER] as? String)
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter(String::isNotBlank)
            val orderedIds = (storedOrder + nameIds).distinct()
            orderedIds.mapNotNull { id ->
                val name = all[nameKey(id)] as? String ?: return@mapNotNull null
                val rawPassword = all[passwordKey(id)] as? String
                PasswordEntry(
                    id = id,
                    name = name,
                    cipherText = rawPassword?.toCharArray(),
                    createdWithAuth = parseCreatedState(all[createdWithAuthKey(id)]),
                )
            }
        }.getOrElse { throwable ->
            throw storageExceptionOf(throwable)
        }
    }

    fun create(
        name: String,
        cipherText: CharArray,
        createdWithAuth: PasswordCreatedState,
    ): PasswordEntry {
        val entry = PasswordEntry(
            id = UUID.randomUUID().toString(),
            name = name,
            cipherText = cipherText.copyOf(),
            createdWithAuth = createdWithAuth,
        )
        add(entry)
        return entry
    }

    fun add(entry: PasswordEntry) {
        editEntry(entry)
    }

    fun update(entry: PasswordEntry) {
        editEntry(entry)
    }

    fun rename(id: String, name: String) {
        prefs.edit {
            putString(nameKey(id), name)
        }
        refresh()
    }

    fun updateOrder(ids: List<String>) {
        prefs.edit {
            putString(KEY_ORDER, ids.joinToString(","))
        }
        refresh()
    }

    fun delete(id: String) {
        prefs.edit {
            remove(nameKey(id))
                .remove(passwordKey(id))
                .remove(createdWithAuthKey(id))
                .putString(KEY_ORDER, getOrderedIds().filterNot { it == id }.joinToString(","))
        }
        refresh()
    }

    fun markInvalid(id: String) {
        prefs.edit {
            remove(passwordKey(id))
        }
        refresh()
    }

    fun clearAllSecretsKeepingEntries() {
        val ids = getOrderedIds()
        prefs.edit {
            ids.forEach { remove(passwordKey(it)) }
        }
        refresh()
    }

    private fun editEntry(entry: PasswordEntry) {
        val password = entry.cipherText
        try {
            prefs.edit {
                putString(nameKey(entry.id), entry.name)
                putString(createdWithAuthKey(entry.id), entry.createdWithAuth.name)
                if (password == null) {
                    remove(passwordKey(entry.id))
                } else {
                    putString(passwordKey(entry.id), String(password))
                }
                val newOrder = (getOrderedIds() + entry.id).distinct()
                putString(KEY_ORDER, newOrder.joinToString(","))
            }
            refresh()
        } finally {
            password?.fill('\u0000')
        }
    }

    private fun getOrderedIds(): List<String> {
        return (prefs.getString(KEY_ORDER, "").orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank) + getAll().map { it.id }).distinct()
    }

    private fun nameKey(id: String) = "$ENTRY_PREFIX$id$NAME_SUFFIX"
    private fun passwordKey(id: String) = "$ENTRY_PREFIX$id$PASSWORD_SUFFIX"
    private fun createdWithAuthKey(id: String) = "$ENTRY_PREFIX$id$CREATED_WITH_AUTH_SUFFIX"

    private fun parseCreatedState(raw: Any?): PasswordCreatedState {
        return when (raw) {
            is String -> PasswordCreatedState.entries
                .firstOrNull { it.name == raw }
                ?: when (raw.lowercase()) {
                    "true" -> PasswordCreatedState.AuthenticatedCreated
                    "false" -> PasswordCreatedState.UnauthenticatedCreated
                    else -> PasswordCreatedState.UnauthenticatedCreated
                }

            is Boolean -> if (raw) {
                PasswordCreatedState.AuthenticatedCreated
            } else {
                PasswordCreatedState.UnauthenticatedCreated
            }

            else -> PasswordCreatedState.UnauthenticatedCreated
        }
    }

    private fun storageExceptionOf(throwable: Throwable): PasswordStorageCorruptedException {
        val cause = when (throwable) {
            is PasswordStorageCorruptedException -> throwable.cause ?: throwable
            is KeyStoreException,
            is GeneralSecurityException,
            is KeyPermanentlyInvalidatedException -> throwable

            else -> throwable
        }
        return PasswordStorageCorruptedException(cause)
    }
}
