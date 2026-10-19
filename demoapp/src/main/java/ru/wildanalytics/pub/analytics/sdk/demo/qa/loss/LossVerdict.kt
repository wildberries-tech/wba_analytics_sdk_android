package ru.wildanalytics.pub.analytics.sdk.demo.qa.loss

import ru.wildanalytics.pub.analytics.sdk.demo.R
import ru.wildanalytics.pub.analytics.sdk.demo.core.LogEntry
import ru.wildanalytics.pub.analytics.sdk.demo.qa.Verdict

fun evaluateLossVerdict(scenario: LossScenario, entries: List<LogEntry>, elapsedMillis: Long): Verdict {
    val markers = entries.mapNotNull { it.marker }.toSet()
    val result = when (scenario) {
        LossScenario.QUEUE_EVICTION -> entries.firstOrNull { it.marker == MARKER_EVICTION }?.let { eviction ->
            if (eviction.attributes[ATTR_REMOVED_IMPORTANT] == "0") {
                pass(R.string.qa_loss_reason_important_kept)
            } else {
                fail(R.string.qa_loss_reason_important_removed)
            }
        }
        LossScenario.EVENTS_KEPT_ON_FAILURE -> when {
            MARKER_EVICTION in markers -> fail(R.string.qa_loss_reason_events_evicted)
            MARKER_SEND_FAILED in markers -> pass(R.string.qa_loss_reason_events_kept)
            else -> null
        }
        LossScenario.SEND_LOOP_RESTART -> pass(R.string.qa_loss_reason_loop_restarted).takeIf { MARKER_LOOP_RESTARTED in markers }
        LossScenario.INSERT_FAILED -> pass(R.string.qa_loss_reason_insert_reported).takeIf { MARKER_INSERT_FAILED in markers }
    }
    return when {
        result != null -> result
        elapsedMillis >= TIMEOUT_MILLIS -> fail(R.string.qa_loss_reason_no_marker)
        else -> Verdict(Verdict.Status.WAITING, R.string.qa_loss_reason_waiting)
    }
}

private fun pass(reason: Int) = Verdict(Verdict.Status.PASSED, reason)
private fun fail(reason: Int) = Verdict(Verdict.Status.FAILED, reason)

private const val MARKER_EVICTION = "wba_queue_eviction"
private const val MARKER_SEND_FAILED = "wba_send_failed"
private const val MARKER_LOOP_RESTARTED = "wba_send_loop_restarted"
private const val MARKER_INSERT_FAILED = "wba_insert_failed"
private const val ATTR_REMOVED_IMPORTANT = "removed_important"
private const val TIMEOUT_MILLIS = 180_000L
