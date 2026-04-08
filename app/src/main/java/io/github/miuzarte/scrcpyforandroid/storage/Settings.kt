package io.github.miuzarte.scrcpyforandroid.storage

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KProperty
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "Settings"

abstract class Settings(
    context: Context,
    private val name: String,
    corruptionHandler: ReplaceFileCorruptionHandler<Preferences>? =
        ReplaceFileCorruptionHandler {
            Log.e(TAG, "Preferences corrupted, resetting.", it)
            emptyPreferences()
        }
) {
    private val settingsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    data class Pair<T>(
        val key: Preferences.Key<T>,
        val defaultValue: T,
    ) {
        val name: String get() = key.name
    }

    protected fun <T> Preferences.read(pair: Pair<T>): T =
        this[pair.key] ?: pair.defaultValue

    protected interface BundleField<B> {
        suspend fun persist(settings: Settings, current: B, new: B)
    }

    protected fun <B, T> bundleField(pair: Pair<T>, selector: (B) -> T): BundleField<B> =
        object : BundleField<B> {
            override suspend fun persist(settings: Settings, current: B, new: B) {
                val currentValue = selector(current)
                val newValue = selector(new)
                if (currentValue != newValue) {
                    settings.setValue(pair, newValue)
                }
            }
        }

    /**
     * 设置项委托类，自动提供 get/set/observe/asState/asMutableState 方法
     */
    inner class SettingProperty<T>(
        val pair: Pair<T>
    ) {
        // 创建时注册自身
        init {
            registerProperty(pair.name, this)
        }

        operator fun getValue(thisRef: Any?, property: KProperty<*>): SettingProperty<T> = this

        suspend fun isDefaultValue() = getValue(pair) == pair.defaultValue

        suspend fun get(): T = getValue(pair)

        suspend fun set(value: T) = setValue(pair, value)

        fun observe(): Flow<T> = this@Settings.observe(pair)

        @Composable
        fun asState(): State<T> = this@Settings.asState(pair)

        @Composable
        fun asMutableState(): MutableState<T> = this@Settings.asMutableState(pair)
    }

    // 注册表, 用于遍历
    private val propertyRegistry = mutableMapOf<String, SettingProperty<*>>()

    private fun <T> registerProperty(name: String, property: SettingProperty<T>) {
        propertyRegistry[name] = property
    }

    protected fun getAllProperties(): Map<String, SettingProperty<*>> = propertyRegistry.toMap()

    // 为 Context 添加扩展委托属性，确保 DataStore 单例
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = this.name,
        corruptionHandler = corruptionHandler,
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    )

    // 对外暴露的 DataStore 实例
    protected val dataStore: DataStore<Preferences> = context.dataStore

    protected fun <T> setting(pair: Pair<T>) = SettingProperty(pair)

    protected fun <B> createBundleState(reader: (Preferences) -> B): StateFlow<B> =
        dataStore.data
            .map(reader)
            .stateIn(
                scope = settingsScope,
                started = SharingStarted.Eagerly,
                initialValue = runBlocking { loadBundle(reader) }
            )

    protected suspend fun <B> loadBundle(reader: (Preferences) -> B): B =
        reader(dataStore.data.first())

    protected suspend fun <B> saveBundle(
        current: B,
        new: B,
        fields: Array<out BundleField<B>>,
    ) {
        for (field in fields) {
            field.persist(this, current, new)
        }
    }

    protected suspend fun <T> getValue(pair: Pair<T>): T =
        dataStore.data.first()[pair.key] ?: pair.defaultValue

    protected suspend fun <T> setValue(pair: Pair<T>, value: T) =
        dataStore.edit { preferences -> preferences[pair.key] = value }

    protected fun <T> observe(pair: Pair<T>): Flow<T> =
        dataStore.data.map { preferences -> preferences[pair.key] ?: pair.defaultValue }

    @Composable
    protected fun <T> asState(pair: Pair<T>): State<T> =
        observe(pair).collectAsState(initial = pair.defaultValue)

    @Composable
    protected fun <T> asMutableState(pair: Pair<T>): MutableState<T> {
        val scope = rememberCoroutineScope()
        val state = asState(pair)

        return remember(state.value) {
            object : MutableState<T> {
                override var value: T
                    get() = state.value
                    set(newValue) {
                        scope.launch {
                            setValue(pair, newValue)
                        }
                    }

                override fun component1(): T = value
                override fun component2(): (T) -> Unit = { value = it }
            }
        }
    }

    companion object {
        val BUNDLE_SAVE_DELAY = 100.milliseconds
    }
}
