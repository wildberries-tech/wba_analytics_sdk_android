package ru.wildanalytics.pub.splitter.impl.domain

import kotlinx.coroutines.flow.Flow
import ru.wildanalytics.pub.splitter.api.WildSplitterConfig

internal interface SplitterPropertiesRepository {

    suspend fun getProperties(type: String): Map<String, String>?
    suspend fun getProperty(type: String, key: String): String?
    fun getPropertyFromMemoryCache(type: String, key: String): String?
    fun observeProperties(type: String): Flow<Map<String, String>>
    fun observeProperties(): Flow<Map<String, Map<String, String>>>
    fun observeProperty(type: String, key: String): Flow<String?>
    fun fetch(
        config: WildSplitterConfig,
        force: Boolean = false,
    )

    fun updateInMemoryCacheFromLocalSource(apiKey: String)
}
