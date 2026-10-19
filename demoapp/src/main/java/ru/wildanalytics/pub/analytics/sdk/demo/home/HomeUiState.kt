package ru.wildanalytics.pub.analytics.sdk.demo.home

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.wildanalytics.pub.analytics.sdk.demo.core.LogEntry
import ru.wildanalytics.pub.analytics.sdk.demo.core.RequestRecord
import ru.wildanalytics.pub.analytics.sdk.demo.qa.ActiveRun
import ru.wildanalytics.pub.analytics.sdk.demo.qa.QaSuite
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsEndpointConfig
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsSdkConfig
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.DEFAULT_MAIN_URL
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.QueueStats
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.TransportMode

data class HomeUiState(
    val queue: QueueStats? = null,
    val sdkConfig: AnalyticsSdkConfig? = null,
    val lastSend: RequestRecord? = null,
    val endpoint: EndpointStatus = EndpointStatus(DEFAULT_HOST, null, null),
    val markerCounts: Map<String, Int> = emptyMap(),
    val transportMode: TransportMode = TransportMode.REAL,
    val periodicSeconds: Int = 0,
    val qa: Map<QaSuite, SuiteSummary> = emptyMap(),
    val isSending: Boolean = false,
)

data class SuiteSummary(
    val passed: Int,
    val total: Int,
    val active: ActiveRun?,
)

data class EndpointStatus(
    val mainHost: String,
    val fallbackHost: String?,
    val switchedAtMillis: Long?,
) {
    val isFallbackActive: Boolean
        get() = switchedAtMillis != null
}

/** Резерв активен, если в этом процессе был `wba_endpoint_fallback` для основного хоста. */
fun endpointStatus(config: AnalyticsEndpointConfig, entries: List<LogEntry>): EndpointStatus {
    val mainHost = config.mainUrl.ifBlank { DEFAULT_MAIN_URL }.let { it.toHttpUrlOrNull()?.host ?: it }
    val switch = entries.lastOrNull { it.marker == MARKER_FALLBACK && it.attributes[ATTR_PRIMARY_HOST] == mainHost }
    return EndpointStatus(mainHost, config.fallbackUrl.toHttpUrlOrNull()?.host, switch?.timestampMillis)
}

val HOME_MARKERS = listOf(
    "wba_send_failed",
    "wba_endpoint_fallback",
    "wba_queue_eviction",
    "wba_send_loop_restarted",
    "wba_insert_failed",
)

private const val DEFAULT_HOST = "a.wb.ru"
private const val MARKER_FALLBACK = "wba_endpoint_fallback"
private const val ATTR_PRIMARY_HOST = "primary_host"
