package ru.wildanalytics.pub.analytics.diagnostics

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.network.NetworkSnapshot
import ru.wildanalytics.pub.analytics.send.HttpStatusException
import ru.wildanalytics.pub.analytics.send.fallback.FallbackSwitch

internal class EndpointFallbackReporterTest {

    private val diagnostics = mockk<DiagnosticsReporter>(relaxed = true)
    private val reporter = EndpointFallbackReporter(diagnostics)

    @Test
    fun `given switch after server error, when reported, then marker has all attributes`() {
        // given
        val switch = FallbackSwitch(
            primaryUrl = "https://a.wb.ru/m/batch",
            fallbackUrl = "https://afb.wb.ru/m/batch",
            cause = HttpStatusException(503),
            attempt = 3,
            batchNumber = "7",
            network = NetworkSnapshot(transports = "cellular", validated = true),
        )

        // when
        reporter.report(switch)

        // then
        val attributes = slot<Map<String, String>>()
        verify { diagnostics.report(DiagnosticMarker.ENDPOINT_FALLBACK, any(), capture(attributes), switch.cause) }
        attributes.captured shouldBe mapOf(
            "primary_host" to "a.wb.ru",
            "fallback_host" to "afb.wb.ru",
            "code" to "503",
            "exception" to HttpStatusException::class.java.name,
            "error" to "Server error 503",
            "attempt" to "3",
            "batch_number" to "7",
            "network" to "cellular",
            "validated" to "true",
        )
    }
}
