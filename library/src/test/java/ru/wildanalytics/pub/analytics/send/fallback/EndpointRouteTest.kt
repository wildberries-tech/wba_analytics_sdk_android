package ru.wildanalytics.pub.analytics.send.fallback

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.diagnostics.DiagnosticMarker
import ru.wildanalytics.pub.analytics.diagnostics.DiagnosticsReporter
import ru.wildanalytics.pub.analytics.network.NetworkSnapshot
import ru.wildanalytics.pub.analytics.network.NetworkSnapshotProvider
import ru.wildanalytics.pub.analytics.testBatch
import ru.wildanalytics.pub.analytics.testEndpointRouter
import java.io.IOException
import java.net.ConnectException

internal class EndpointRouteTest {

    private val diagnostics = mockk<DiagnosticsReporter>(relaxed = true)
    private val endpointFallback = EndpointFallback().apply { register(API_KEY, FALLBACK_URL) }
    private var network = VALIDATED
    private val networkProvider = NetworkSnapshotProvider { network }

    @Test
    fun `given fallback and internet, when route switches, then marker is reported once at the switch`() {
        // given
        val route = route()

        // when
        val switched = (1..3).map { route.onFailure(ConnectException("refused"), attempt = it) }

        // then
        switched shouldBe listOf(false, false, true)
        route.url shouldBe FALLBACK_URL
        val attributes = reportedFallbackAttributes()
        attributes["primary_host"] shouldBe "a.wb.ru"
        attributes["fallback_host"] shouldBe "afb.wb.ru"
        attributes["exception"] shouldBe ConnectException::class.java.name
        attributes["attempt"] shouldBe "3"
        attributes["validated"] shouldBe "true"
    }

    @Test
    fun `given switched route, when fallback attempt fails, then route keeps fallback without second marker`() {
        // given
        val route = switchedRoute()

        // when
        val switchedAgain = route.onFailure(IOException("fallback down"), attempt = 4)

        // then
        switchedAgain shouldBe false
        route.url shouldBe FALLBACK_URL
        verify(exactly = 1) { diagnostics.report(DiagnosticMarker.ENDPOINT_FALLBACK, any(), any(), any()) }
    }

    @Test
    fun `given no fallback for key, when attempts fail, then route keeps primary`() {
        // given
        val route = route(apiKey = OTHER_API_KEY)

        // when
        repeat(5) { route.onFailure(ConnectException("refused"), attempt = it + 1) }

        // then
        route.url shouldBe PRIMARY_URL
        verifyNoFallbackReported()
    }

    @Test
    fun `given network provider throws, when attempts fail, then route keeps primary without crash`() {
        // given
        val route = testEndpointRouter(endpointFallback, { error("binder died") }, diagnostics)
            .routeFor(testBatch(PRIMARY_URL, API_KEY))

        // when
        repeat(5) { route.onFailure(ConnectException("refused"), attempt = it + 1) }

        // then
        route.url shouldBe PRIMARY_URL
        route.failureAttributes()["network"] shouldBe "unknown"
    }

    @Test
    fun `given primary and switched routes, when failure attributes are built, then they name host and endpoint`() {
        // given
        val primary = route()
        val switched = switchedRoute()

        // when
        val primaryAttributes = primary.failureAttributes()
        val switchedAttributes = switched.failureAttributes()

        // then
        primaryAttributes shouldBe mapOf("host" to "a.wb.ru", "endpoint" to "primary", "network" to "wifi", "validated" to "true")
        switchedAttributes["host"] shouldBe "afb.wb.ru"
        switchedAttributes["endpoint"] shouldBe "fallback"
    }

    private fun route(apiUrl: String = PRIMARY_URL, apiKey: String = API_KEY) =
        testEndpointRouter(endpointFallback, networkProvider, diagnostics).routeFor(testBatch(apiUrl, apiKey))

    private fun switchedRoute() = route().apply {
        repeat(3) { onFailure(ConnectException("refused"), attempt = it + 1) }
    }

    private fun reportedFallbackAttributes(): Map<String, String> {
        val attributes = slot<Map<String, String>>()
        verify(exactly = 1) { diagnostics.report(DiagnosticMarker.ENDPOINT_FALLBACK, any(), capture(attributes), any()) }
        return attributes.captured
    }

    private fun verifyNoFallbackReported() {
        verify(exactly = 0) { diagnostics.report(DiagnosticMarker.ENDPOINT_FALLBACK, any(), any(), any()) }
    }

    private companion object {
        const val API_KEY = "apiKey"
        const val OTHER_API_KEY = "otherApiKey"
        const val PRIMARY_URL = "https://a.wb.ru/m/batch"
        const val FALLBACK_URL = "https://afb.wb.ru/m/batch"
        val VALIDATED = NetworkSnapshot(transports = "wifi", validated = true)
    }
}
