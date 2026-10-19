package ru.wildanalytics.pub.analytics.sdk.demo.sdk

import kotlinx.serialization.Serializable

@Serializable
data class AnalyticsEndpointConfigData(
    val mainUrl: String = DEFAULT_MAIN_URL,
    val fallbackUrl: String = "",
)

const val DEFAULT_MAIN_URL = "https://a.wb.ru/m/batch"
const val DEFAULT_FALLBACK_URL = "https://afb.wb.ru/m/batch"
