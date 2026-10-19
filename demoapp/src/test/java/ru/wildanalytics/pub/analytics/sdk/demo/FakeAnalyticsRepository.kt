package ru.wildanalytics.pub.analytics.sdk.demo

import kotlinx.coroutines.flow.MutableStateFlow
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsEndpointConfig
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsEventConfig
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsRepository
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsSdkConfig
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.DEFAULT_MAIN_URL
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.TransportMode

class FakeAnalyticsRepository : AnalyticsRepository {

    val sdkUpdates = mutableListOf<AnalyticsSdkConfig>()
    val endpointUpdates = mutableListOf<AnalyticsEndpointConfig>()
    val loggedEvents = mutableListOf<AnalyticsEventConfig>()
    var resets = 0

    override val sdkConfigFlow = MutableStateFlow<AnalyticsSdkConfig?>(DEFAULT_SDK_CONFIG)
    override val sdkConfig: AnalyticsSdkConfig? get() = sdkConfigFlow.value
    override val eventsConfigFlow = MutableStateFlow(DEFAULT_EVENT_CONFIG)
    override val eventsConfig: AnalyticsEventConfig get() = eventsConfigFlow.value
    override val endpointConfigFlow = MutableStateFlow(AnalyticsEndpointConfig(DEFAULT_MAIN_URL, ""))
    override val endpointConfig: AnalyticsEndpointConfig get() = endpointConfigFlow.value
    override val transportMode = MutableStateFlow(TransportMode.REAL)

    override fun updateSdkConfig(config: AnalyticsSdkConfig): Result<Unit> {
        sdkUpdates += config
        sdkConfigFlow.value = config
        return Result.success(Unit)
    }

    override fun updateSendEventsConfig(config: AnalyticsEventConfig) {
        eventsConfigFlow.value = config
    }

    override fun updateEndpointConfig(config: AnalyticsEndpointConfig) {
        endpointUpdates += config
        endpointConfigFlow.value = config
    }

    override fun setTransportMode(mode: TransportMode): Result<Unit> {
        transportMode.value = mode
        return Result.success(Unit)
    }

    override fun resetToDefaults() {
        resets++
    }

    override suspend fun logEvents(config: AnalyticsEventConfig): Result<Unit> {
        loggedEvents += config
        return Result.success(Unit)
    }

    override suspend fun logFlood(count: Int): Result<Unit> = Result.success(Unit)

    companion object {
        val DEFAULT_SDK_CONFIG = AnalyticsSdkConfig(
            maxEventsInBatch = 50,
            maxBatchSizeInBytes = 100_000,
            delayBetweenBatchesSeconds = 1,
            delayBetweenOperationsSeconds = 5,
            initialDelaySeconds = 2,
            maxEventsInCache = 10_000,
            isRetryEnabled = true,
        )
        val DEFAULT_EVENT_CONFIG = AnalyticsEventConfig(
            counterId = 1,
            name = "fake_event",
            value = "{}",
            count = 1,
            isImportant = false,
            commonParameters = emptyMap(),
            periodicLoggingIntervalSeconds = 0,
        )
    }
}
