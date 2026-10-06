package io.github.vandosketch.camgrid.data

import io.github.vandosketch.camgrid.core.CamGridConfig
import io.github.vandosketch.camgrid.core.ConfigCodec
import io.github.vandosketch.camgrid.platform.AppLog
import io.github.vandosketch.camgrid.platform.ConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds the current [CamGridConfig] and persists it through the platform's [ConfigStore]
 * (encrypted at rest there, because stream URLs can contain credentials). A missing or
 * unreadable config yields the default config.
 *
 * @param saveScope where saves run; IO threads by default. Not tied to a screen or ViewModel,
 *   so a save started just before the app closes still finishes.
 */
class ConfigRepository(
    private val store: ConfigStore,
    private val saveScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    // Loaded synchronously: the config is tiny and the grid needs it before the first frame.
    private val _config = MutableStateFlow(load())
    val config: StateFlow<CamGridConfig> = _config.asStateFlow()

    private val saveMutex = Mutex()

    /**
     * Applies [transform] to the current config and saves the result in the background.
     * Exceptions thrown by [transform] propagate to the caller and nothing is changed.
     */
    fun update(transform: (CamGridConfig) -> CamGridConfig) {
        _config.update(transform)
        saveScope.launch {
            // Always write the latest value, so the last save wins even if saves queue up.
            saveMutex.withLock { save(_config.value) }
        }
    }

    private fun load(): CamGridConfig = try {
        ConfigCodec.decodeOrDefault(store.read())
    } catch (e: Exception) {
        // Only the exception type: messages could in theory contain config contents.
        AppLog.w("Could not read config (${e::class.simpleName}), using defaults")
        CamGridConfig()
    }

    private fun save(config: CamGridConfig) {
        try {
            store.write(ConfigCodec.encode(config))
        } catch (e: Exception) {
            AppLog.e("Could not save config (${e::class.simpleName})")
        }
    }
}
