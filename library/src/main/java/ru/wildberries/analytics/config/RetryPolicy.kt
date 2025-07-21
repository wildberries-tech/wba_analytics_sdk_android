package ru.wildberries.analytics.config

import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

public interface RetryPolicy {
    public fun shouldRetry(attempt: Int, error: Throwable): Boolean
    public fun nextDelay(attempt: Int): Duration

    public data class Exponential(
        private val base: Double,
        private val additional: Double,
        private val attempts: Int,
        private val unit: DurationUnit = DurationUnit.SECONDS,
    ) : RetryPolicy {

        init {
            require(attempts > 0)
            require(base > 1)
            require(additional >= 0)
        }

        override fun shouldRetry(attempt: Int, error: Throwable): Boolean = attempt < attempts

        override fun nextDelay(attempt: Int): Duration =
            (base.pow(attempt) + additional).toDuration(unit)
    }

    public data object NoRetry : RetryPolicy {
        override fun shouldRetry(attempt: Int, error: Throwable): Boolean = false

        override fun nextDelay(attempt: Int): Duration = Duration.ZERO
    }

}

