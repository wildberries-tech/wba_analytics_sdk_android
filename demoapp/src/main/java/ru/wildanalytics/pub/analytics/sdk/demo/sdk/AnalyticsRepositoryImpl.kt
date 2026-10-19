package ru.wildanalytics.pub.analytics.sdk.demo.sdk

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.wildanalytics.pub.analytics.AnalyticsField
import ru.wildanalytics.pub.analytics.WildAnalytics
import ru.wildanalytics.pub.analytics.config.RetryPolicy
import ru.wildanalytics.pub.analytics.config.WildAnalyticsConfig
import ru.wildanalytics.pub.analytics.sdk.demo.core.RequestLog
import ru.wildanalytics.pub.analytics.transport.HttpTransport
import ru.wildanalytics.pub.attribution.api.AttributionStrategy
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

class AnalyticsRepositoryImpl(
    private val context: Context,
    requestLog: RequestLog,
) : AnalyticsRepository {

    private val sdkConfigDataSource = ReflectionConfigDataSource(context)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val sdkConfigFlow: Flow<AnalyticsSdkConfig?> = sdkConfigDataSource.configFlow
        .map { it?.toDomain() }
    override val sdkConfig: AnalyticsSdkConfig?
        get() = sdkConfigDataSource.configFlow.value?.toDomain()

    private val preferences: AnalyticsPreferences = AnalyticsPreferences(context)
    private val _eventsConfigFlow =
        MutableStateFlow((preferences.loadEventConfig() ?: AnalyticsEventConfigData()).toDomain())
    override val eventsConfigFlow: Flow<AnalyticsEventConfig> =
        _eventsConfigFlow.asStateFlow()
    override val eventsConfig: AnalyticsEventConfig
        get() = _eventsConfigFlow.value

    private val _endpointConfigFlow =
        MutableStateFlow((preferences.loadEndpointConfig() ?: AnalyticsEndpointConfigData()).toDomain())
    override val endpointConfigFlow: Flow<AnalyticsEndpointConfig> =
        _endpointConfigFlow.asStateFlow()
    override val endpointConfig: AnalyticsEndpointConfig
        get() = _endpointConfigFlow.value

    @Volatile
    private var wbaInstance: WildAnalytics = createInstance(eventsConfig.apiKey, endpointConfig)
    private val transport = QaTransport(
        delegate = HttpTransport(appendToClient = {
            addInterceptor(ChuckerInterceptor.Builder(context).build())
            addInterceptor(requestLog)
        }),
        requestLog = requestLog,
    )
    private val _transportMode = MutableStateFlow(TransportMode.REAL)
    override val transportMode: StateFlow<TransportMode> = _transportMode.asStateFlow()

    init {
        val savedConfig = preferences.loadSdkConfig()
        if (savedConfig != null) {
            updateSdkConfig(savedConfig.toDomain())
        } else {
            sdkConfigDataSource.updateConfig { it.copy(transport = transport) }
        }
        setupInstance()
    }

    // Демонстрация EventEnricher: ко всем событиям добавляется несколько полей.
    private fun registerDemoEnrichers(instance: WildAnalytics) {
        instance.addEventEnricher { eventName, _ ->
            listOf(
                AnalyticsField("event_name_length", JsonPrimitive(eventName.length)),
                AnalyticsField("enriched", JsonPrimitive(true)),
            )
        }
    }

    override fun updateSendEventsConfig(config: AnalyticsEventConfig) {
        preferences.saveEventConfig(config.toData())
        val current = _eventsConfigFlow.value
        val currentParams = current.commonParameters
        val newParams = config.commonParameters

        if (current.apiKey != config.apiKey) {
            recreateInstance(apiKey = config.apiKey)
        }

        if (currentParams != newParams) {
            val removedKeys = currentParams.keys - newParams.keys
            val syncMap = mutableMapOf<String, String?>()
            syncMap.putAll(newParams)
            removedKeys.forEach { syncMap[it] = null }
            wbaInstance.setCommonParameters(syncMap)
        }

        _eventsConfigFlow.value = config
    }

    override fun updateEndpointConfig(config: AnalyticsEndpointConfig) {
        if (config == _endpointConfigFlow.value) return
        preferences.saveEndpointConfig(config.toData())
        _endpointConfigFlow.value = config
        recreateInstance()
    }

    override fun setTransportMode(mode: TransportMode): Result<Unit> {
        transport.mode = mode
        _transportMode.value = mode
        return sdkConfigDataSource.updateConfig { it.copy(transport = transport) }
    }

    override fun resetToDefaults() {
        preferences.clear()
        transport.mode = TransportMode.REAL
        _transportMode.value = TransportMode.REAL
        updateSdkConfig(WildAnalyticsConfig.Default.copy(transport = transport).toDomain())
        _eventsConfigFlow.value = AnalyticsEventConfigData().toDomain()
        _endpointConfigFlow.value = AnalyticsEndpointConfigData().toDomain()
        recreateInstance()
    }

    override fun updateSdkConfig(config: AnalyticsSdkConfig): Result<Unit> {
        preferences.saveSdkConfig(config.toData())

        return sdkConfigDataSource.updateConfig { sdkConfig ->
            sdkConfig.copy(
                transport = transport,
                batching = sdkConfig.batching.copy(
                    maxEventsInBatch = config.maxEventsInBatch,
                    maxBatchSizeInBites = config.maxBatchSizeInBytes
                ),
                delays = sdkConfig.delays.copy(
                    delayBetweenBatches = config.delayBetweenBatchesSeconds.seconds,
                    delayBetweenOperations = config.delayBetweenOperationsSeconds.seconds,
                    initialDelay = config.initialDelaySeconds.seconds
                ),
                maxEventsInCache = config.maxEventsInCache,
                retryPolicy = if (config.isRetryEnabled) {
                    WildAnalyticsConfig.Default.retryPolicy
                } else {
                    RetryPolicy.NoRetry
                }
            )
        }
    }

    override suspend fun logEvents(config: AnalyticsEventConfig): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val parameters = Json.decodeFromString<JsonObject>(config.value)

                if (config.isImportant) {
                    repeat(config.count) {
                        wbaInstance.logImportantEvent(config.name, parameters)
                    }
                } else {
                    repeat(config.count) {
                        wbaInstance.logEvent(config.name, parameters)
                    }
                }
            }
        }

    override suspend fun logFlood(count: Int): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                repeat(count) { index ->
                    val parameters = JsonObject(mapOf("index" to JsonPrimitive(index)))
                    if (index % FLOOD_IMPORTANT_EVERY == 0) {
                        wbaInstance.logImportantEvent(FLOOD_IMPORTANT_EVENT, parameters)
                    } else {
                        wbaInstance.logEvent(FLOOD_EVENT, parameters)
                    }
                }
            }
        }

    private fun recreateInstance(apiKey: String = eventsConfig.apiKey) {
        wbaInstance.finish()
        wbaInstance = createInstance(apiKey, endpointConfig)
        setupInstance()
    }

    private fun setupInstance() {
        wbaInstance.setCommonParameters(eventsConfig.commonParameters)
        registerDemoEnrichers(wbaInstance)
    }

    private fun createInstance(apiKey: String, endpoint: AnalyticsEndpointConfig): WildAnalytics {
        val mainUrl = endpoint.mainUrl.ifBlank { DEFAULT_MAIN_URL }
        val fallbackUrl = endpoint.fallbackUrl
        return WildAnalytics(
            context = context,
            apiUrlProvider = { mainUrl },
            apiKey = apiKey,
            enableAutomaticEvents = true,
            attributionStrategy = AttributionStrategy.HandleLinkOnAttribution(withSystemLogs = true) {},
            fallbackApiUrl = fallbackUrl.ifBlank { null },
        )
    }
}

private fun AnalyticsSdkConfigData.toDomain() = AnalyticsSdkConfig(
    maxEventsInBatch = maxEventsInBatch,
    maxBatchSizeInBytes = maxBatchSizeInBytes,
    delayBetweenBatchesSeconds = delayBetweenBatchesSeconds,
    delayBetweenOperationsSeconds = delayBetweenOperationsSeconds,
    initialDelaySeconds = initialDelaySeconds,
    maxEventsInCache = maxEventsInCache,
    isRetryEnabled = isRetryEnabled
)

private fun AnalyticsSdkConfig.toData() = AnalyticsSdkConfigData(
    maxEventsInBatch = maxEventsInBatch,
    maxBatchSizeInBytes = maxBatchSizeInBytes,
    delayBetweenBatchesSeconds = delayBetweenBatchesSeconds,
    delayBetweenOperationsSeconds = delayBetweenOperationsSeconds,
    initialDelaySeconds = initialDelaySeconds,
    maxEventsInCache = maxEventsInCache,
    isRetryEnabled = isRetryEnabled
)

private fun WildAnalyticsConfig.toDomain() = AnalyticsSdkConfig(
    maxEventsInBatch = batching.maxEventsInBatch,
    maxBatchSizeInBytes = batching.maxBatchSizeInBites,
    delayBetweenBatchesSeconds = delays.delayBetweenBatches.toInt(DurationUnit.SECONDS),
    delayBetweenOperationsSeconds = delays.delayBetweenOperations.toInt(DurationUnit.SECONDS),
    initialDelaySeconds = delays.initialDelay.toInt(DurationUnit.SECONDS),
    maxEventsInCache = maxEventsInCache,
    isRetryEnabled = retryPolicy != RetryPolicy.NoRetry
)

private fun AnalyticsEventConfigData.toDomain() = AnalyticsEventConfig(
    counterId = counterId,
    name = eventName,
    value = eventValue,
    count = eventsCount,
    isImportant = isImportant,
    commonParameters = commonParameters,
    periodicLoggingIntervalSeconds = periodicLoggingIntervalSeconds,
)

private fun AnalyticsEventConfig.toData() = AnalyticsEventConfigData(
    counterId = counterId,
    eventName = name,
    eventValue = value,
    eventsCount = count,
    isImportant = isImportant,
    commonParameters = commonParameters,
    periodicLoggingIntervalSeconds = periodicLoggingIntervalSeconds,
)

private fun AnalyticsEndpointConfigData.toDomain() = AnalyticsEndpointConfig(
    mainUrl = mainUrl,
    fallbackUrl = fallbackUrl,
)

private fun AnalyticsEndpointConfig.toData() = AnalyticsEndpointConfigData(
    mainUrl = mainUrl,
    fallbackUrl = fallbackUrl,
)

private const val FLOOD_IMPORTANT_EVERY = 5
private const val FLOOD_EVENT = "qa_normal"
private const val FLOOD_IMPORTANT_EVENT = "qa_important"
