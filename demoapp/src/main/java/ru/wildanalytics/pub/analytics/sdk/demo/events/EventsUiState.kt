package ru.wildanalytics.pub.analytics.sdk.demo.events

import androidx.annotation.StringRes
import java.util.UUID

data class EventsUiState(
    val counterId: Long = 0,
    val apiKey: String = "",
    val name: String = "",
    val body: String = "",
    @StringRes val bodyError: Int? = null,
    val count: Int = 1,
    val isImportant: Boolean = false,
    val parameters: List<ParameterUiState> = emptyList(),
    val periodicSeconds: Int = 0,
)

data class ParameterUiState(
    val id: String = UUID.randomUUID().toString(),
    val key: String = "",
    val value: String = "",
)
