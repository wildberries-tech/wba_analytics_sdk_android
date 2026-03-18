package ru.wildberries.analytics.sdk.demo.analytics.domain

import kotlinx.coroutines.flow.Flow

interface AnalyticsRepository {
    val sdkConfigFlow: Flow<AnalyticsSdkConfig?>
    val sdkConfig: AnalyticsSdkConfig?
    val eventsConfigFlow: Flow<AnalyticsEventConfig>
    val eventsConfig: AnalyticsEventConfig

    fun updateSdkConfig(config: AnalyticsSdkConfig): Result<Unit>
    fun updateSendEventsConfig(config: AnalyticsEventConfig)

    fun resetToDefaults()

    fun getApiKey(counterId: Long): String

    suspend fun logEvents(config: AnalyticsEventConfig): Result<Unit>
}
