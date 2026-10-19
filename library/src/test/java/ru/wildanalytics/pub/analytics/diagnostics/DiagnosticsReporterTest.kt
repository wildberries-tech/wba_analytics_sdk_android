package ru.wildanalytics.pub.analytics.diagnostics

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger

internal class DiagnosticsReporterTest {

    private val logger = RecordingLogger()
    private val reporter = DiagnosticsReporter(log = logger, sdkVersion = "9.9.9")

    @Test
    fun errorMarkerGoesToLogErrorWithAttributesAndSdkVersion() {
        val cause = IllegalStateException("disk is full")

        reporter.report(DiagnosticMarker.QUEUE_EVICTION, "Evicted 6 events", mapOf("removed" to "6"), cause)

        val (error, details) = logger.errors.single()
        error.message shouldBe "[wba_queue_eviction] Evicted 6 events"
        error.cause shouldBe cause
        details shouldBe mapOf("removed" to "6", DiagnosticAttributes.SDK_VERSION to "9.9.9")
        logger.warnings.shouldBeEmpty()
    }

    @Test
    fun warnMarkerGoesToLogWarnAsSingleLine() {
        reporter.report(
            DiagnosticMarker.SEND_FAILED,
            "Batch was not sent",
            mapOf("code" to "500", "attempts" to "10"),
            IllegalStateException("ignored for warn"),
        )

        val message = logger.warnings.single()
        message shouldBe "[wba_send_failed] Batch was not sent code=500, attempts=10, sdk_version=9.9.9"
        logger.errors.shouldBeEmpty()
    }

    @Test
    fun markersHaveExpectedIdsAndLevels() {
        DiagnosticMarker.entries.associate { it.id to it.level } shouldBe mapOf(
            "wba_queue_eviction" to DiagnosticMarker.Level.ERROR,
            "wba_insert_failed" to DiagnosticMarker.Level.ERROR,
            "wba_send_failed" to DiagnosticMarker.Level.WARN,
            "wba_endpoint_fallback" to DiagnosticMarker.Level.ERROR,
            "wba_send_loop_restarted" to DiagnosticMarker.Level.WARN,
        )
    }

    @Test
    fun everyReportIsDelivered() {
        repeat(100) { reporter.report(DiagnosticMarker.QUEUE_EVICTION, "evicted") }

        logger.errors shouldHaveSize 100
    }

    @Test
    fun doesNotPropagateLoggerFailure() {
        val reporter = DiagnosticsReporter(
            log = object : RecordingLogger() {
                override fun logError(e: Error, details: Map<String, String>) = error("broken logger")
                override fun logWarn(message: String) = error("broken logger")
            },
            sdkVersion = "9.9.9",
        )

        reporter.report(DiagnosticMarker.QUEUE_EVICTION, "evicted")
        reporter.report(DiagnosticMarker.SEND_FAILED, "failed")
    }

    @Test
    fun doesNothingWhenLoggerIsDisabled() {
        val disabledLogger = object : RecordingLogger() {
            override val isEnabled: Boolean = false
        }
        val reporter = DiagnosticsReporter(log = disabledLogger, sdkVersion = "9.9.9")

        reporter.report(DiagnosticMarker.QUEUE_EVICTION, "evicted")
        reporter.report(DiagnosticMarker.SEND_FAILED, "failed")

        disabledLogger.errors.shouldBeEmpty()
        disabledLogger.warnings.shouldBeEmpty()
    }

    @Test
    fun sdkVersionAttributeIsNotOverriddenByCaller() {
        reporter.report(DiagnosticMarker.QUEUE_EVICTION, "evicted", mapOf(DiagnosticAttributes.SDK_VERSION to "fake"))
        reporter.report(DiagnosticMarker.SEND_FAILED, "failed", mapOf(DiagnosticAttributes.SDK_VERSION to "fake"))

        logger.errors.single().second shouldContain (DiagnosticAttributes.SDK_VERSION to "9.9.9")
        logger.warnings.single() shouldStartWith "[wba_send_failed] failed"
        logger.warnings.single() shouldContain "sdk_version=9.9.9"
    }

    private open class RecordingLogger : WildAnalyticsLogger {
        val errors = mutableListOf<Pair<Error, Map<String, String>>>()
        val warnings = mutableListOf<String>()
        override val isEnabled: Boolean = true
        override fun logDebug(message: String) = Unit
        override fun logException(e: Exception) = Unit
        override fun logError(e: Error, details: Map<String, String>) {
            errors += e to details
        }
        override fun logWarn(message: String) {
            warnings += message
        }
    }
}
