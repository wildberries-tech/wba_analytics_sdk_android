package ru.wildberries.analytics.sdk.demo.analytics.data

import kotlinx.serialization.Serializable

@Serializable
data class AnalyticsSdkConfigData(
    val maxEventsInBatch: Int,
    val maxBatchSizeInBytes: Int,
    val delayBetweenBatchesSeconds: Int,
    val delayBetweenOperationsSeconds: Int,
    val initialDelaySeconds: Int,
    val maxEventsInCache: Int,
    val isRetryEnabled: Boolean
)
