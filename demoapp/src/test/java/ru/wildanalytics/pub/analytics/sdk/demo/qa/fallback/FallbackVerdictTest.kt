package ru.wildanalytics.pub.analytics.sdk.demo.qa.fallback

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.sdk.demo.core.LogEntry
import ru.wildanalytics.pub.analytics.sdk.demo.core.RequestRecord
import ru.wildanalytics.pub.analytics.sdk.demo.logEntry
import ru.wildanalytics.pub.analytics.sdk.demo.qa.Verdict.Status.FAILED
import ru.wildanalytics.pub.analytics.sdk.demo.qa.Verdict.Status.PASSED
import ru.wildanalytics.pub.analytics.sdk.demo.qa.Verdict.Status.WAITING
import ru.wildanalytics.pub.analytics.sdk.demo.request

class FallbackVerdictTest {

    @Test
    fun switchSuccessPassesWhenDeliveredToFallback() {
        evaluate(FallbackExpectation.SWITCH_SUCCESS, listOf(fallback()), listOf(request(1, FALLBACK, 200))) shouldBe PASSED
    }

    @Test
    fun switchSuccessFailsWhenFallbackRejects() {
        evaluate(FallbackExpectation.SWITCH_SUCCESS, listOf(fallback()), listOf(request(1, FALLBACK, 503))) shouldBe FAILED
    }

    @Test
    fun switchSuccessWaitsForFallbackRequestAfterMarker() {
        evaluate(FallbackExpectation.SWITCH_SUCCESS, listOf(fallback())) shouldBe WAITING
    }

    @Test
    fun switchSuccessWaitsThenTimesOut() {
        evaluate(FallbackExpectation.SWITCH_SUCCESS) shouldBe WAITING
        evaluate(FallbackExpectation.SWITCH_SUCCESS, elapsedMillis = TIMEOUT) shouldBe FAILED
    }

    @Test
    fun switchFailedPassesWhenFallbackKeepsFailing() {
        val entries = listOf(fallback(), sendFailed("fallback"))
        evaluate(FallbackExpectation.SWITCH_FAILED, entries, listOf(request(1, FALLBACK, 503))) shouldBe PASSED
    }

    @Test
    fun switchFailedFailsOnSuccess() {
        evaluate(FallbackExpectation.SWITCH_FAILED, listOf(fallback()), listOf(request(1, FALLBACK, 200))) shouldBe FAILED
    }

    @Test
    fun noSwitchPassesAfterFailedCycle() {
        evaluate(FallbackExpectation.NO_SWITCH, listOf(sendFailed("primary"))) shouldBe PASSED
    }

    @Test
    fun noSwitchFailsOnFallbackRequest() {
        evaluate(FallbackExpectation.NO_SWITCH, requests = listOf(request(1, FALLBACK, 200))) shouldBe FAILED
    }

    @Test
    fun noSwitchPassesOnTimeoutWithoutAttempts() {
        evaluate(FallbackExpectation.NO_SWITCH, elapsedMillis = TIMEOUT) shouldBe PASSED
    }

    @Test
    fun primaryOnlyPassesOnPrimaryDelivery() {
        evaluate(FallbackExpectation.PRIMARY_ONLY, requests = listOf(request(1, PRIMARY, 200))) shouldBe PASSED
    }

    @Test
    fun primaryOnlyFailsOnSendFailureAndTimeout() {
        evaluate(FallbackExpectation.PRIMARY_ONLY, listOf(sendFailed("primary"))) shouldBe FAILED
        evaluate(FallbackExpectation.PRIMARY_ONLY, elapsedMillis = TIMEOUT) shouldBe FAILED
    }

    private fun evaluate(
        expectation: FallbackExpectation,
        entries: List<LogEntry> = emptyList(),
        requests: List<RequestRecord> = emptyList(),
        elapsedMillis: Long = 0,
    ) = evaluateFallbackVerdict(expectation, entries, requests, elapsedMillis).status

    private fun fallback() = logEntry(1, "[wba_endpoint_fallback] Switched", mapOf("attempt" to "3"))

    private fun sendFailed(endpoint: String) = logEntry(2, "[wba_send_failed] Failed endpoint=$endpoint")

    private companion object {
        const val PRIMARY = "a.wb.ru"
        const val FALLBACK = "afb.wb.ru"
        const val TIMEOUT = 360_000L
    }
}
