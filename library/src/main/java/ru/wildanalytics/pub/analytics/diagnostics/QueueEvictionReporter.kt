package ru.wildanalytics.pub.analytics.diagnostics

import ru.wildanalytics.pub.analytics.db.EventEntity
import ru.wildanalytics.pub.analytics.db.EvictedEventsGroup
import ru.wildanalytics.pub.analytics.event.EvictionResult
import ru.wildanalytics.pub.analytics.util.IdGenerator
import java.time.Clock
import java.time.OffsetDateTime

internal class QueueEvictionReporter(
    private val reporter: DiagnosticsReporter,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
) {

    fun report(result: EvictionResult) {
        val groups = result.groups.sortedWith(
            compareByDescending<EvictedEventsGroup> { it.count }
                .thenBy { it.name }
                .thenBy { it.importance }
        )
        val top = groups.take(MAX_GROUPS)
        val rest = groups.drop(MAX_GROUPS)
        reporter.report(
            marker = DiagnosticMarker.QUEUE_EVICTION,
            message = "Evicted ${result.removed} events exceeding limit ${result.limit}",
            attributes = mapOf(
                DiagnosticAttributes.OP_ID to idGenerator.generateId().toString(),
                DiagnosticAttributes.TIME to OffsetDateTime.now(clock).toString(),
                DiagnosticAttributes.LIMIT to result.limit.toString(),
                DiagnosticAttributes.SIZE_BEFORE to result.sizeBefore.toString(),
                DiagnosticAttributes.SIZE_AFTER to (result.sizeBefore - result.removed).toString(),
                DiagnosticAttributes.REMOVED to result.removed.toString(),
                DiagnosticAttributes.REMOVED_IMPORTANT to result.groups
                    .filter { it.importance > EventEntity.IMPORTANCE_NORMAL }
                    .sumOf { it.count }
                    .toString(),
                DiagnosticAttributes.GROUPS to top.joinToString(separator = ";") { "${it.name}:${it.importance}=${it.count}" },
                DiagnosticAttributes.OTHER_GROUPS to rest.size.toString(),
                DiagnosticAttributes.OTHER_COUNT to rest.sumOf { it.count }.toString(),
            ),
        )
    }

    companion object {
        const val MAX_GROUPS = 20
    }
}
