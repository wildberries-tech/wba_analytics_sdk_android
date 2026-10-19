package ru.wildanalytics.pub.analytics.sdk.demo.qa.loss

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.sdk.demo.core.LogEntry
import ru.wildanalytics.pub.analytics.sdk.demo.logEntry
import ru.wildanalytics.pub.analytics.sdk.demo.qa.Verdict.Status.FAILED
import ru.wildanalytics.pub.analytics.sdk.demo.qa.Verdict.Status.PASSED
import ru.wildanalytics.pub.analytics.sdk.demo.qa.Verdict.Status.WAITING

class LossVerdictTest {

    @Test
    fun evictionPassesWhenImportantKept() {
        evaluate(LossScenario.QUEUE_EVICTION, logEntry(1, "[wba_queue_eviction] Evicted removed_important=0, removed=150")) shouldBe PASSED
    }

    @Test
    fun evictionFailsWhenImportantRemoved() {
        evaluate(LossScenario.QUEUE_EVICTION, logEntry(1, "[wba_queue_eviction] Evicted removed_important=3")) shouldBe FAILED
    }

    @Test
    fun keptPassesOnSendFailureWithoutEviction() {
        evaluate(LossScenario.EVENTS_KEPT_ON_FAILURE, logEntry(1, "[wba_send_failed] Failed code=500")) shouldBe PASSED
    }

    @Test
    fun keptFailsOnEviction() {
        evaluate(
            LossScenario.EVENTS_KEPT_ON_FAILURE,
            logEntry(1, "[wba_send_failed] Failed code=500"),
            logEntry(2, "[wba_queue_eviction] Evicted removed_important=0"),
        ) shouldBe FAILED
    }

    @Test
    fun restartAndInsertPassOnTheirMarkers() {
        evaluate(LossScenario.SEND_LOOP_RESTART, logEntry(1, "[wba_send_loop_restarted] attempt=0")) shouldBe PASSED
        evaluate(LossScenario.INSERT_FAILED, logEntry(1, "[wba_insert_failed] Insert failed")) shouldBe PASSED
    }

    @Test
    fun waitsThenTimesOut() {
        evaluate(LossScenario.SEND_LOOP_RESTART) shouldBe WAITING
        evaluateLossVerdict(LossScenario.SEND_LOOP_RESTART, emptyList(), elapsedMillis = 180_000).status shouldBe FAILED
    }

    private fun evaluate(scenario: LossScenario, vararg entries: LogEntry) =
        evaluateLossVerdict(scenario, entries.toList(), elapsedMillis = 0).status
}
