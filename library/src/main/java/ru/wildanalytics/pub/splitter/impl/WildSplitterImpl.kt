package ru.wildanalytics.pub.splitter.impl

import kotlinx.coroutines.flow.Flow
import ru.wildanalytics.pub.splitter.api.OnConfigChangedFlags
import ru.wildanalytics.pub.splitter.api.WildSplitter
import ru.wildanalytics.pub.splitter.api.WildSplitterConfig
import ru.wildanalytics.pub.splitter.impl.domain.SplitterPropertiesRepository

internal class WildSplitterImpl internal constructor(
    private val repository: SplitterPropertiesRepository,
    private var config: WildSplitterConfig,
    private val getOnConfigChangedFlags: (old: WildSplitterConfig, new: WildSplitterConfig) -> OnConfigChangedFlags,
    logger: SplitterLogger,
) : WildSplitter {

    init {
        logger.logDebug { "WildSplitter initialized." }
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

    override fun updateConfig(update: WildSplitterConfig.() -> WildSplitterConfig) {
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
