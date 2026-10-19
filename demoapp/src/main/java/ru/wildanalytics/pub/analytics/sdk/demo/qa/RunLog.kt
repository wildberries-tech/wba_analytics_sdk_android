package ru.wildanalytics.pub.analytics.sdk.demo.qa

import ru.wildanalytics.pub.analytics.sdk.demo.core.LogEntry
import ru.wildanalytics.pub.analytics.sdk.demo.core.MarkerParser
import ru.wildanalytics.pub.analytics.sdk.demo.core.RequestRecord

data class RunLogItem(
    val id: String,
    val offsetSeconds: Long,
    val kind: Kind,
    val text: String,
    val attributes: Map<String, String>,
) {
    enum class Kind { SUCCESS, FAILURE, MOCK, MARKER }
}

/** Лента прогона: запросы и маркеры `wba_*` с момента старта, по времени. */
object RunLog {

    fun build(
        run: ActiveRun,
        entries: List<LogEntry>,
        requests: List<RequestRecord>,
        aliases: Map<String, String>,
    ): List<RunLogItem> {
        val requestItems = requests.map { request ->
            val kind = when {
                request.isMock -> RunLogItem.Kind.MOCK
                request.isSuccessful -> RunLogItem.Kind.SUCCESS
                else -> RunLogItem.Kind.FAILURE
            }
            val text = "${aliases[request.host] ?: request.host} ${request.result}" + if (request.isMock) " mock" else ""
            Timed(request.timestampMillis, ORDER_REQUEST, RunLogItem("r${request.id}", 0, kind, text, emptyMap()))
        }
        val markerItems = entries.mapNotNull { entry ->
            val marker = entry.marker ?: return@mapNotNull null
            val item = RunLogItem("m${entry.id}", 0, RunLogItem.Kind.MARKER, MarkerParser.shortName(marker), entry.attributes)
            Timed(entry.timestampMillis, ORDER_MARKER, item)
        }
        return (requestItems + markerItems)
            .filter { it.timestampMillis >= run.startedAtMillis }
            .sortedWith(compareBy({ it.timestampMillis }, { it.order }))
            .map { it.item.copy(offsetSeconds = (it.timestampMillis - run.startedAtMillis) / MILLIS_IN_SECOND) }
    }

    private class Timed(val timestampMillis: Long, val order: Int, val item: RunLogItem)
}

private const val ORDER_REQUEST = 0
private const val ORDER_MARKER = 1
private const val MILLIS_IN_SECOND = 1_000L
