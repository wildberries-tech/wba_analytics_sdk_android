package ru.wildanalytics.pub.splitter.impl.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.VisibleForTesting
import ru.wildanalytics.pub.splitter.api.WildSplitterConfig
import ru.wildanalytics.pub.splitter.impl.SplitterLogger
import ru.wildanalytics.pub.splitter.impl.domain.SplitterPropertiesRepository

internal class SplitterPropertiesRepositoryImpl(
    private val logger: SplitterLogger,
    private val localDataSource: SplitterPropertyDao,
    private val remoteDataSource: SplitterRemoteDataSource,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : SplitterPropertiesRepository {

    private var updateCacheFromDbJob: Job? = null

    @Volatile
    private var fetchStateJob: Job? = null
    private val propertiesCacheState: MutableStateFlow<Map<String, Map<String, String>>> =
        MutableStateFlow(emptyMap())
    private val propertiesCache get() = propertiesCacheState.value

    override fun fetch(
        config: WildSplitterConfig,
        force: Boolean,
    ) {
        val apiKey = config.apiKey
        if (force) {
            fetchStateJob?.cancel()
            fetchStateJob = null
        }

        if (fetchStateJob?.isActive == true) {
            return
        }

        fetchStateJob = scope.launch {
            if (force) {
                updateFetchTimestampForApiKey(apiKey, null)
            }

            if (!isFetchAvailable(apiKey, System.currentTimeMillis())) {
                logger.logDebug { "Fetch throttled. Last fetch was recent." }
                return@launch
            }

            try {
                logger.logDebug { "Fetch started." }
                val experiments = remoteDataSource.getExperiments(config)
                val timestamp = System.currentTimeMillis()
                awaitInit()
                updateCacheFromRemoteModels(experiments)
                saveToDb(apiKey, experiments)
                updateFetchTimestampForApiKey(apiKey, timestamp)
            } catch (e: Exception) {
                logger.logError(e) { "Failed to fetch splitter data" }
            }
        }
    }

    override suspend fun getProperties(type: String): Map<String, String>? {
        awaitInit()
        return propertiesCache[type]
    }

    override suspend fun getProperty(type: String, key: String): String? =
        getProperties(type)?.get(key)

    override fun getPropertyFromMemoryCache(type: String, key: String): String? =
        propertiesCache[type]?.get(key)

    override fun observeProperties(type: String): Flow<Map<String, String>> {
        return propertiesCacheState.map { it[type].orEmpty() }.distinctUntilChanged()
    }

    override fun observeProperties(): Flow<Map<String, Map<String, String>>> = propertiesCacheState

    override fun observeProperty(type: String, key: String): Flow<String?> =
        propertiesCacheState.map { it[type]?.get(key) }
            .distinctUntilChanged()

    private suspend fun awaitInit() = updateCacheFromDbJob?.join()

    private fun updateCacheFromRemoteModels(experiments: List<SplitterGroupDto>) {
        propertiesCacheState.update {
            experiments.associate { group ->
                group.type to group.properties.associate { it.key to it.value }
            }
        }
    }

    override fun updateInMemoryCacheFromLocalSource(apiKey: String) {
        updateCacheFromDbJob?.cancel()
        updateCacheFromDbJob = scope.launch {
            propertiesCacheState.update {
                localDataSource.getProperties(apiKey)
                    .groupBy { it.abTestGroupType }
                    .mapValues { (_, properties) -> properties.associate { it.key to it.value } }
            }
            updateCacheFromDbJob = null
        }
    }

    private suspend fun saveToDb(apiKey: String, experiments: List<SplitterGroupDto>) {
        val entities = experiments.flatMap { experiment ->
            experiment.properties.map {
                SplitterPropertyEntity(
                    apiKey = apiKey,
                    abTestGroupType = experiment.type,
                    key = it.key,
                    value = it.value
                )
            }
        }
        localDataSource.replaceProperties(apiKey, entities)
    }

    companion object {
        private const val FETCH_THROTTLE_MS = 3600 * 1000L
        private val fetchTimeStamps = mutableMapOf<String, Long>()
        private val fetchTimestampsLock = Mutex()

        @VisibleForTesting
        suspend fun isFetchAvailable(apiKey: String, timestamp: Long): Boolean =
            fetchTimestampsLock.withLock {
                val lastFetchTimeStamp = fetchTimeStamps[apiKey]
                lastFetchTimeStamp == null || timestamp - lastFetchTimeStamp >= FETCH_THROTTLE_MS
            }

        @VisibleForTesting
        suspend fun updateFetchTimestampForApiKey(apiKey: String, timestamp: Long?) =
            fetchTimestampsLock.withLock {
                timestamp?.let { fetchTimeStamps[apiKey] = it }
                    ?: fetchTimeStamps.remove(apiKey)
            }
    }
}
