package ru.wildanalytics.pub.analytics.diagnostics

import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.db.EventEntity
import ru.wildanalytics.pub.analytics.db.EvictedEventsGroup
import ru.wildanalytics.pub.analytics.event.EvictionResult
import ru.wildanalytics.pub.analytics.util.IdGenerator
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

internal class QueueEvictionReporterTest {

    private val diagnosticsReporter = mockk<DiagnosticsReporter>(relaxed = true)
    private val operationId = UUID.fromString("00000000-0000-0000-0000-000000000042")
    private val subject = QueueEvictionReporter(
        reporter = diagnosticsReporter,
        clock = Clock.fixed(Instant.parse("2026-10-02T12:00:00Z"), ZoneOffset.UTC),
        idGenerator = object : IdGenerator {
            override fun generateId(): UUID = operationId
        },
    )

    @Test
    fun reportsSummaryOfEviction() {
        subject.report(
            EvictionResult(
                limit = 10_000,
                sizeBefore = 10_006,
                removed = 6,
                groups = listOf(
                    EvictedEventsGroup("heartbeat", EventEntity.IMPORTANCE_NORMAL, 1),
                    EvictedEventsGroup("view_cart", EventEntity.IMPORTANCE_NORMAL, 3),
                    EvictedEventsGroup("begin_checkout", EventEntity.IMPORTANCE_HIGH, 2),
                ),
            )
        )

        val attributes = captureAttributes()
        attributes shouldContain ("op_id" to operationId.toString())
        attributes shouldContain ("time" to "2026-10-02T12:00Z")
        attributes shouldContain ("limit" to "10000")
        attributes shouldContain ("size_before" to "10006")
        attributes shouldContain ("size_after" to "10000")
        attributes shouldContain ("removed" to "6")
        attributes shouldContain ("removed_important" to "2")
        attributes shouldContain ("groups" to "view_cart:0=3;begin_checkout:100=2;heartbeat:0=1")
        attributes shouldContain ("other_groups" to "0")
        attributes shouldContain ("other_count" to "0")
    }

    @Test
    fun limitsGroupsToTopAndAggregatesTheRest() {
        val groups = (1..QueueEvictionReporter.MAX_GROUPS + 3).map { index ->
            EvictedEventsGroup("event$index", EventEntity.IMPORTANCE_NORMAL, 100 - index)
        }

        subject.report(EvictionResult(limit = 10, sizeBefore = 5_000, removed = groups.sumOf { it.count }, groups = groups))

        val attributes = captureAttributes()
        attributes["groups"]!!.split(";").size shouldBe QueueEvictionReporter.MAX_GROUPS
        attributes shouldContain ("other_groups" to "3")
        attributes shouldContain ("other_count" to groups.takeLast(3).sumOf { it.count }.toString())
    }

    @Test
    fun groupsWithEqualCountAreOrderedByNameThenImportance() {
        subject.report(
            EvictionResult(
                limit = 10,
                sizeBefore = 16,
                removed = 6,
                groups = listOf(
                    EvictedEventsGroup("view_cart", EventEntity.IMPORTANCE_HIGH, 2),
                    EvictedEventsGroup("heartbeat", EventEntity.IMPORTANCE_NORMAL, 2),
                    EvictedEventsGroup("view_cart", EventEntity.IMPORTANCE_NORMAL, 2),
                ),
            )
        )

        captureAttributes() shouldContain ("groups" to "heartbeat:0=2;view_cart:0=2;view_cart:100=2")
    }

    private fun captureAttributes(): Map<String, String> {
        val attributes = slot<Map<String, String>>()
        verify {
            diagnosticsReporter.report(
                marker = DiagnosticMarker.QUEUE_EVICTION,
                message = any(),
                attributes = capture(attributes),
            )
        }
        return attributes.captured
    }
}
