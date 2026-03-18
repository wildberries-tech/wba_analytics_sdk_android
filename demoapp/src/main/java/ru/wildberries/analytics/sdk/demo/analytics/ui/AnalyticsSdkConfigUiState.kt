package ru.wildberries.analytics.sdk.demo.analytics.ui

data class AnalyticsSdkConfigUiState(
    val maxEventsInBatch: Int,
    val maxBatchSizeInBytes: Int,
    val delayBetweenBatchesSeconds: Int,
    val delayBetweenOperationsSeconds: Int,
    val initialDelaySeconds: Int,
    val maxEventsInCache: Int,
    val isRetryEnabled: Boolean,
    val isConfigExpanded: Boolean,
    val error: Int?
)
