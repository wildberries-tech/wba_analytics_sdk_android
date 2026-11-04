package ru.wildberries.pub.analytics.diagnostics

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import ru.wildberries.pub.analytics.WBAnalytics2Logger
import ru.wildberries.pub.analytics.db.EventEntity
import ru.wildberries.pub.analytics.db.EvictedEventsGroup
import ru.wildberries.pub.analytics.event.EvictionResult
import ru.wildberries.pub.analytics.util.RandomIdGenerator
import java.time.Clock

internal class HostLoggerCompatibilityTest {

    @Test
    fun hostLoggerWithoutChangesReceivesEvictionSummaryAsNonFatal() {
        val hostLogger = HostShapedLogger()
        val reporter = QueueEvictionReporter(
            reporter = DiagnosticsReporter(log = hostLogger, sdkVersion = "1.0.60"),
            clock = Clock.systemUTC(),
            idGenerator = RandomIdGenerator(),
        )

        reporter.report(
            EvictionResult(
                limit = 10_000,
                sizeBefore = 10_150,
                removed = 150,
                groups = listOf(
                    EvictedEventsGroup(name = "view_item", importance = EventEntity.IMPORTANCE_NORMAL, count = 149),
                    EvictedEventsGroup(name = "begin_checkout", importance = EventEntity.IMPORTANCE_HIGH, count = 1),
                ),
            )
        )

        val nonFatal = hostLogger.nonFatals.single()
        nonFatal.cause?.message.orEmpty() shouldStartWith "[wba_queue_eviction]"
        val message = nonFatal.message.orEmpty()
        message shouldContain "removed=150"
        message shouldContain "removed_important=1"
        message shouldContain "groups=view_item:0=149;begin_checkout:100=1"
        message shouldContain "sdk_version=1.0.60"
    }

    private class HostShapedLogger : WBAnalytics2Logger {
        val nonFatals = mutableListOf<Exception>()
        override val isEnabled: Boolean get() = true
        override fun logDebug(message: String) = Unit
        override fun logException(e: Exception) {
            nonFatals += e
        }
        override fun logError(e: Error, details: Map<String, String>) {
            val detailsString = details.entries.joinToString { (name, value) -> "$name=$value" }
            nonFatals += SuppressedErrorException("Error Details: $detailsString", e)
        }
        override fun logWarn(message: String) = Unit
    }

    private class SuppressedErrorException(message: String, cause: Throwable) : Exception(message, cause)
}
