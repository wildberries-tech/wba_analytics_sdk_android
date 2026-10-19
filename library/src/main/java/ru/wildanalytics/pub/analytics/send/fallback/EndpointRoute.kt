package ru.wildanalytics.pub.analytics.send.fallback

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.wildanalytics.pub.analytics.ApiUrl
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import ru.wildanalytics.pub.analytics.batch.BatchData
import ru.wildanalytics.pub.analytics.diagnostics.DiagnosticAttributes
import ru.wildanalytics.pub.analytics.diagnostics.EndpointFallbackReporter
import ru.wildanalytics.pub.analytics.diagnostics.toAttributeValue
import ru.wildanalytics.pub.analytics.diagnostics.toDiagnosticAttributes
import ru.wildanalytics.pub.analytics.logDebug
import ru.wildanalytics.pub.analytics.network.NetworkSnapshot
import ru.wildanalytics.pub.analytics.network.NetworkSnapshotProvider
import ru.wildanalytics.pub.analytics.network.UNKNOWN_NETWORK

/**
 * Маршрут отправки одного батча: текущий URL и переключение на резервный по [FallbackTrigger].
 * Живёт один цикл попыток; маркер переключения отправляется в момент переключения.
 * */
internal class EndpointRoute(
    private val batch: BatchData,
    private val endpointFallback: EndpointFallback,
    private val networkSnapshotProvider: NetworkSnapshotProvider,
    private val reporter: EndpointFallbackReporter,
    private val log: WildAnalyticsLogger,
) {

    var url: ApiUrl = endpointFallback.effectiveUrl(batch.apiData)
        private set

    private val fallbackUrl = if (url == batch.apiData.apiUrl) endpointFallback.fallbackUrlOrNull(batch.apiData) else null
    private val trigger = FallbackTrigger()

    /** @return `true`, если маршрут переключён на резервный URL и следующая попытка уйдёт туда. */
    fun onFailure(error: Exception, attempt: Int): Boolean {
        val fallback = fallbackUrl ?: return false
        if (url == fallback) return false
        var network: NetworkSnapshot? = null
        val isTriggered = trigger.onFailure(error) { snapshot().also { network = it }.hasInternet }
        if (!isTriggered || !endpointFallback.activate(batch.apiData)) return false
        log.logDebug { "Switching batch ${batch.model.meta.batchNumber} to fallback URL $fallback" }
        val switch = FallbackSwitch(
            primaryUrl = batch.apiData.apiUrl,
            fallbackUrl = fallback,
            cause = error,
            attempt = attempt,
            batchNumber = batch.model.meta.batchNumber.toString(),
            network = network ?: UNKNOWN_NETWORK,
        )
        runCatching { reporter.report(switch) }
        url = fallback
        return true
    }

    /** Атрибуты `wba_send_failed`: куда ушла последняя попытка и состояние сети. */
    fun failureAttributes(): Map<String, String> = buildMap {
        put(DiagnosticAttributes.HOST, url.toHttpUrlOrNull()?.host ?: url.toAttributeValue())
        put(DiagnosticAttributes.ENDPOINT, if (url == batch.apiData.apiUrl) ENDPOINT_PRIMARY else ENDPOINT_FALLBACK)
        putAll(snapshot().toDiagnosticAttributes())
    }

    private fun snapshot(): NetworkSnapshot = runCatching(networkSnapshotProvider::snapshot).getOrDefault(UNKNOWN_NETWORK)
}

private const val ENDPOINT_PRIMARY = "primary"
private const val ENDPOINT_FALLBACK = "fallback"
