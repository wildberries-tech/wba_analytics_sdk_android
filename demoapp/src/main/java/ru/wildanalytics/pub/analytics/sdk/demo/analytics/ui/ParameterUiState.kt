package ru.wildanalytics.pub.analytics.sdk.demo.analytics.ui

import java.util.UUID

data class ParameterUiState(
    val id: String = UUID.randomUUID().toString(),
    val key: String = "",
    val value: String = ""
)
