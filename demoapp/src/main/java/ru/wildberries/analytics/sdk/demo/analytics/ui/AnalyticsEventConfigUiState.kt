package ru.wildberries.analytics.sdk.demo.analytics.ui

data class AnalyticsEventConfigUiState(
    val counterId: Long,
    val apiKey: String,
    val eventName: String,
    val eventValue: String,
    val eventValueError: Int?,
    val eventsPerSendCount: Int,
    val isImportant: Boolean,
    val commonParameters: List<ParameterUiState>
)
