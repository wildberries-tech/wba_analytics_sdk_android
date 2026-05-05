package ru.wildberries.splitter.api

import android.content.Context
import kotlinx.coroutines.flow.Flow
import ru.wildberries.splitter.impl.NoOpSplitterLogger
import ru.wildberries.splitter.impl.SystemSplitterLogger
import ru.wildberries.splitter.impl.WBSplitterImpl
import ru.wildberries.splitter.impl.data.SplitterDatabase
import ru.wildberries.splitter.impl.data.SplitterPropertiesRepositoryImpl
import ru.wildberries.splitter.impl.data.SplitterRemoteDataSource

/**
 * The main interface for interacting with the Splitter service, which is part of the A/B testing functionality.
 * Its main task is to divide users into experimental groups and provide them with corresponding attributes (properties).
 * @see [WBSplitter.Factory.create] for creating an instance.
 */
public interface WBSplitter {

    /**
     * Returns all properties (attributes) of a specific type from the local cache, waiting for its initialization.
     * Data fetching may occur after this cache is initialized. If you need up-to-date data, it is better to use [observePropertiesForType].
     *
     * @param type The type of properties to retrieve.
     */
    public suspend fun getProperties(type: String): Map<String, String>?

    /**
     * Returns the value of a specific property from the local cache by key, waiting for its initialization.
     * Data fetching may occur after this cache is initialized. If you need up-to-date data, it is better to use [observeProperty].
     *
     * @param type The type to which the property belongs.
     * @param key The key (name) of the requested property.
     */
    public suspend fun getProperty(type: String, key: String): String?

    /**
     * Same as [getProperty], but does not wait for cache initialization.
     *
     * @param type The type to which the property belongs.
     * @param key The key (name) of the requested property.
     */
    public fun getPropertyFromMemoryCache(type: String, key: String): String?

    /**
     * Returns a [Flow] that emits updates for all properties of the specified type.
     * The flow will send new values as they change.
     *
     * @param type The type of properties to observe.
     */
    public fun observePropertiesForType(type: String): Flow<Map<String, String>>

    /**
     * Returns a [Flow] that emits updates for all properties of all types.
     * The flow will send new values as they change.
     */
    public fun observeProperties(): Flow<Map<String, Map<String, String>>>

    /**
     * Returns a [Flow] that emits updates for the value of a specific property.
     * The flow will send new values as they change.
     *
     * @param type The type to which the property belongs.
     * @param key The key (name) of the property to observe.
     */
    public fun observeProperty(type: String, key: String): Flow<String?>

    public fun updateConfig(update: WBSplitterConfig.() -> WBSplitterConfig)

    /**
     * Request experiments fetch softly.
     * If there are another fetch for this splitter in progress, than this one not started.
     * Min 1 hour delay between fetches. If faster, fetch will be throttled.
     * */
    public fun requestPropertiesFetch()

    public companion object Factory {

        /**
         * Creates an instance of [WBSplitter].
         *
         * @param withSystemLogs If `true`, logging will be done to the system LogCat.
         * @param getOnConfigChangedFlags A function that determines the required actions after a configuration change.
         * @param systemLogsTag The tag for messages in LogCat (default is "WBSplitter").
         */
        @Synchronized
        public fun create(
            context: Context,
            config: WBSplitterConfig,
            withSystemLogs: Boolean,
            getOnConfigChangedFlags: (old: WBSplitterConfig, new: WBSplitterConfig) -> OnConfigChangedFlags = ::defaultOnConfigChangedFlags,
            systemLogsTag: String? = "WBSplitter",
        ): WBSplitter {
            val log =
                if (withSystemLogs) SystemSplitterLogger(systemLogsTag) else NoOpSplitterLogger()
            val db = SplitterDatabase.getInstance(context)
            val remoteDataSource = SplitterRemoteDataSource(log)
            val repository = SplitterPropertiesRepositoryImpl(
                logger = log,
                localDataSource = db.splitterPropertyDao(),
                remoteDataSource = remoteDataSource,
            )
            return WBSplitterImpl(
                repository = repository,
                config = config,
                logger = log,
                getOnConfigChangedFlags = getOnConfigChangedFlags,
            )
        }
    }
}

private fun defaultOnConfigChangedFlags(
    old: WBSplitterConfig,
    new: WBSplitterConfig,
): OnConfigChangedFlags = OnConfigChangedFlags(
    forceFetch = old != new,
    updateCacheFromLocalSource = old.apiKey != new.apiKey,
)
