package ru.wildanalytics.pub.analytics.sdk.demo.qa.fallback

import androidx.annotation.StringRes
import ru.wildanalytics.pub.analytics.sdk.demo.R
import ru.wildanalytics.pub.analytics.sdk.demo.core.LogEntry
import ru.wildanalytics.pub.analytics.sdk.demo.core.RequestRecord
import ru.wildanalytics.pub.analytics.sdk.demo.qa.Verdict

fun evaluateFallbackVerdict(
    expectation: FallbackExpectation,
    entries: List<LogEntry>,
    requests: List<RequestRecord>,
    elapsedMillis: Long,
): Verdict {
    val hasFallbackMarker = entries.any { it.marker == MARKER_FALLBACK }
    val sendFailures = entries.filter { it.marker == MARKER_SEND_FAILED }
    val fallbackRequests = requests.filter { it.host == FALLBACK_HOST }
    val isDeliveredToFallback = fallbackRequests.any { it.isSuccessful }
    val isSwitched = hasFallbackMarker || fallbackRequests.isNotEmpty()
    val isTimedOut = elapsedMillis >= TIMEOUT_MILLIS
    return when (expectation) {
        FallbackExpectation.SWITCH_SUCCESS -> when {
            hasFallbackMarker && isDeliveredToFallback -> passed(R.string.qa_reason_switched_and_delivered)
            fallbackRequests.isNotEmpty() && !isDeliveredToFallback -> failed(R.string.qa_reason_fallback_not_success)
            isTimedOut -> failed(R.string.qa_reason_no_switch)
            else -> waiting(R.string.qa_reason_waiting_switch)
        }
        FallbackExpectation.SWITCH_FAILED -> when {
            isDeliveredToFallback -> failed(R.string.qa_reason_fallback_unexpected_success)
            hasFallbackMarker && sendFailures.any { it.attributes[ATTR_ENDPOINT] == ENDPOINT_FALLBACK } ->
                passed(R.string.qa_reason_fallback_failed_kept)
            isTimedOut -> failed(R.string.qa_reason_no_switch)
            else -> waiting(R.string.qa_reason_waiting_switch)
        }
        FallbackExpectation.NO_SWITCH -> when {
            isSwitched -> failed(R.string.qa_reason_unexpected_switch)
            sendFailures.isNotEmpty() -> passed(R.string.qa_reason_no_switch_after_failure)
            isTimedOut -> passed(R.string.qa_reason_no_switch_no_attempts)
            else -> waiting(R.string.qa_reason_observing)
        }
        FallbackExpectation.PRIMARY_ONLY -> when {
            isSwitched -> failed(R.string.qa_reason_unexpected_switch)
            requests.any { it.host == PRIMARY_HOST && it.isSuccessful } -> passed(R.string.qa_reason_primary_delivered)
            sendFailures.isNotEmpty() -> failed(R.string.qa_reason_primary_failed)
            isTimedOut -> failed(R.string.qa_reason_no_requests)
            else -> waiting(R.string.qa_reason_waiting_primary)
        }
    }
}

private fun passed(@StringRes reason: Int) = Verdict(Verdict.Status.PASSED, reason)
private fun failed(@StringRes reason: Int) = Verdict(Verdict.Status.FAILED, reason)
private fun waiting(@StringRes reason: Int) = Verdict(Verdict.Status.WAITING, reason)

const val MARKER_FALLBACK = "wba_endpoint_fallback"
private const val MARKER_SEND_FAILED = "wba_send_failed"
private const val ATTR_ENDPOINT = "endpoint"
private const val ENDPOINT_FALLBACK = "fallback"
private const val PRIMARY_HOST = "a.wb.ru"
private const val FALLBACK_HOST = "afb.wb.ru"
private const val TIMEOUT_MILLIS = 360_000L
