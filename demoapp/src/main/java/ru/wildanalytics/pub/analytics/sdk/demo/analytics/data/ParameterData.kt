package ru.wildanalytics.pub.analytics.sdk.demo.analytics.data

import kotlinx.serialization.Serializable

@Serializable
data class ParameterData(
    val id: String,
    val key: String,
    val value: String
)