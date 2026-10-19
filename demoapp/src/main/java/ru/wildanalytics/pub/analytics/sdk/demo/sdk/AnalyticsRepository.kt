package ru.wildanalytics.pub.analytics.sdk.demo.sdk

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AnalyticsRepository {
    val sdkConfigFlow: Flow<AnalyticsSdkConfig?>
    val sdkConfig: AnalyticsSdkConfig?
    val eventsConfigFlow: Flow<AnalyticsEventConfig>
    val eventsConfig: AnalyticsEventConfig
    val endpointConfigFlow: Flow<AnalyticsEndpointConfig>
    val endpointConfig: AnalyticsEndpointConfig
    val transportMode: StateFlow<TransportMode>

    fun updateSdkConfig(config: AnalyticsSdkConfig): Result<Unit>
    fun updateSendEventsConfig(config: AnalyticsEventConfig)
    fun updateEndpointConfig(config: AnalyticsEndpointConfig)
    fun setTransportMode(mode: TransportMode): Result<Unit>

    fun resetToDefaults()

    suspend fun logEvents(config: AnalyticsEventConfig): Result<Unit>
    suspend fun logFlood(count: Int): Result<Unit>
}
