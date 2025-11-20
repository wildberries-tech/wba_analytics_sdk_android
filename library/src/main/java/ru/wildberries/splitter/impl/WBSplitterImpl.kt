package ru.wildberries.splitter.impl

import kotlinx.coroutines.flow.Flow
import ru.wildberries.splitter.api.OnConfigChangedFlags
import ru.wildberries.splitter.api.WBSplitter
import ru.wildberries.splitter.api.WBSplitterConfig
import ru.wildberries.splitter.impl.domain.SplitterPropertiesRepository

internal class WBSplitterImpl internal constructor(
    private val repository: SplitterPropertiesRepository,
    private var config: WBSplitterConfig,
    private val getOnConfigChangedFlags: (old: WBSplitterConfig, new: WBSplitterConfig) -> OnConfigChangedFlags,
    logger: SplitterLogger
) : WBSplitter {

    init {
        logger.logDebug { "WBSplitter initialized." }
        repository.updateInMemoryCacheFromLocalSource(config.apiKey)
        repository.fetch(config = config)
    }

    override suspend fun getProperties(type: String): Map<String, String>? =
        repository.getProperties(type)

    override suspend fun getProperty(type: String, key: String): String? =
        repository.getProperty(type, key)

    override fun getPropertyFromMemoryCache(type: String, key: String): String? =
        repository.getPropertyFromMemoryCache(type, key)

    override fun observePropertiesForType(type: String): Flow<Map<String, String>> =
        repository.observeProperties(type)

    override fun observeProperties(): Flow<Map<String, Map<String, String>>> =
        repository.observeProperties()

    override fun observeProperty(type: String, key: String): Flow<String?> =
        repository.observeProperty(type, key)

    override fun updateConfig(update: WBSplitterConfig.() -> WBSplitterConfig) {
        val updated = config.update()
        if (config != updated) {
            val flags = getOnConfigChangedFlags(config, updated)
            if (flags.updateCacheFromLocalSource) {
                repository.updateInMemoryCacheFromLocalSource(updated.apiKey)
            }
            if (flags.forceFetch) {
                repository.fetch(config = updated, force = true)
            } else if (flags.softFetch) {
                repository.fetch(config = updated, force = false)
            }
            config = updated
        }
    }

    override fun requestPropertiesFetch() {
        repository.fetch(config)
    }
}
