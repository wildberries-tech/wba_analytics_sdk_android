package ru.wildanalytics.pub.analytics.send

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal data class RestartPolicy(
    val minDelay: Duration = 1.seconds,
    val maxDelay: Duration = 5.minutes,
) {

    fun delayFor(attempt: Long): Duration {
        var delay = minDelay
        var remaining = attempt
        while (remaining > 0 && delay.isPositive() && delay < maxDelay) {
            delay *= 2
            remaining--
        }
        return delay.coerceAtMost(maxDelay)
    }
}
