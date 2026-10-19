package ru.wildanalytics.pub.analytics.sdk.demo.qa

import androidx.annotation.StringRes

enum class QaSuite { FALLBACK, LOSS }

data class Verdict(
    val status: Status,
    @StringRes val reason: Int,
) {
    enum class Status { WAITING, PASSED, FAILED }
}

data class ActiveRun(
    val suite: QaSuite,
    val scenario: String,
    val startedAtMillis: Long,
    val id: Long = 0,
)

data class ScenarioResult(
    val status: Verdict.Status,
    val elapsedSeconds: Long,
)

data class ResultKey(
    val suite: QaSuite,
    val scenario: String,
)

data class PendingRun(
    val suite: QaSuite,
    val scenario: String?,
)
