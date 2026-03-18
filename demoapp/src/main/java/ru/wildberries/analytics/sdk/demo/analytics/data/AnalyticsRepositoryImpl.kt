package ru.wildberries.analytics.sdk.demo.analytics.data

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import ru.wildberries.analytics.WBAnalytics2
import ru.wildberries.analytics.config.WBA2Config
import ru.wildberries.analytics.config.RetryPolicy
import ru.wildberries.analytics.sdk.demo.analytics.domain.AnalyticsSdkConfig
import ru.wildberries.analytics.sdk.demo.analytics.domain.AnalyticsEventConfig
import ru.wildberries.analytics.sdk.demo.analytics.domain.AnalyticsRepository
import ru.wildberries.analytics.transport.HttpTransport
import ru.wildberries.attribution.api.AttributionStrategy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.DurationUnit
import kotlin.time.Duration.Companion.seconds

class AnalyticsRepositoryImpl(
    private val context: Context
) : AnalyticsRepository {

    private val sdkConfigDataSource = ReflectionConfigDataSource(context)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val sdkConfigFlow: Flow<AnalyticsSdkConfig?> = sdkConfigDataSource.configFlow
        .map { it?.toDomain() }
    override val sdkConfig: AnalyticsSdkConfig?
        get() = sdkConfigDataSource.configFlow.value?.toDomain()

    private val preferences: AnalyticsPreferences = AnalyticsPreferences(context)
    private val _sendEventsConfigFlow =
        MutableStateFlow((preferences.loadEventConfig() ?: AnalyticsEventConfigData()).toDomain())
    override val eventsConfigFlow: Flow<AnalyticsEventConfig> =
        _sendEventsConfigFlow.asStateFlow()
    override val eventsConfig: AnalyticsEventConfig
        get() = _sendEventsConfigFlow.value

    private var wbaInstance: WBAnalytics2 = WBAnalytics2(
        context = context,
        apiKey = eventsConfig.apiKey,
        attributionStrategy = AttributionStrategy.HandleLinkOnAttribution(withSystemLogs = true) {},
    )
    private val transport = HttpTransport(appendToClient = {
        this.addInterceptor(ChuckerInterceptor.Builder(context).build())
    })


    init {
        preferences.loadSdkConfig()?.let { savedConfig ->
            updateSdkConfig(savedConfig.toDomain())
        }
        wbaInstance.setCommonParameters(eventsConfig.commonParameters)
    }

    override fun updateSendEventsConfig(config: AnalyticsEventConfig) {
        preferences.saveEventConfig(config.toData())
        val current = _sendEventsConfigFlow.value
        val currentParams = current.commonParameters
        val newParams = config.commonParameters

        if (current.apiKey != config.apiKey) {
            updateApiKey(config.apiKey)
        }

        if (currentParams != newParams) {
            val removedKeys = currentParams.keys - newParams.keys
            val syncMap = mutableMapOf<String, String?>()
            syncMap.putAll(newParams)
            removedKeys.forEach { syncMap[it] = null }
            wbaInstance.setCommonParameters(syncMap)
        }

        _sendEventsConfigFlow.value = config
    }

    override fun resetToDefaults() {
        preferences.clear()
        updateSdkConfig(WBA2Config.Default.copy(transport = transport).toDomain())
        _sendEventsConfigFlow.value = AnalyticsEventConfigData().toDomain()
    }

    override fun getApiKey(counterId: Long): String = counterId.toApiKey()

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
                    WBA2Config.Default.retryPolicy
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

    private fun updateApiKey(apiKey: String) {
        wbaInstance = WBAnalytics2(
            context = context,
            apiKey = apiKey,
            attributionStrategy = AttributionStrategy.HandleLinkOnAttribution(withSystemLogs = true) {},
        )
        wbaInstance.setCommonParameters(_sendEventsConfigFlow.value.commonParameters)
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

private fun WBA2Config.toDomain() = AnalyticsSdkConfig(
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
    commonParameters = commonParameters.associate { it.key to it.value }
)

private fun AnalyticsEventConfig.toData() = AnalyticsEventConfigData(
    counterId = counterId,
    eventName = name,
    eventValue = value,
    eventsCount = count,
    isImportant = isImportant,
    commonParameters = commonParameters.map { (k, v) ->
        ParameterData(
            id = UUID.randomUUID().toString(), key = k, value = v
        )
    }
)

@OptIn(ExperimentalEncodingApi::class)
private fun Long.toApiKey(): String {
    val binary = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        .putLong(this)
        .array()
    return Base64.encode(binary)
}