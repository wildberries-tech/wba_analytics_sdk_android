package ru.wildanalytics.pub.analytics.sdk.demo.sdk

data class AnalyticsSdkConfig(
    val maxEventsInBatch: Int,
    val maxBatchSizeInBytes: Int,
    val delayBetweenBatchesSeconds: Int,
    val delayBetweenOperationsSeconds: Int,
    val initialDelaySeconds: Int,
    val maxEventsInCache: Int,
    val isRetryEnabled: Boolean,
)
