package ru.wildanalytics.pub.analytics.send

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class RestartPolicyTest {

    @Test
    fun defaultDelayGrowsExponentiallyUpToFiveMinutes() {
        val policy = RestartPolicy()

        policy.delayFor(0) shouldBe 1.seconds
        policy.delayFor(1) shouldBe 2.seconds
        policy.delayFor(8) shouldBe 256.seconds
        policy.delayFor(9) shouldBe 5.minutes
        policy.delayFor(Long.MAX_VALUE) shouldBe 5.minutes
    }

    @Test
    fun negativeAttemptUsesMinDelay() {
        RestartPolicy().delayFor(-1) shouldBe 1.seconds
    }

    @Test
    fun customBoundsAreRespected() {
        val policy = RestartPolicy(minDelay = 3.seconds, maxDelay = 10.seconds)

        policy.delayFor(0) shouldBe 3.seconds
        policy.delayFor(1) shouldBe 6.seconds
        policy.delayFor(2) shouldBe 10.seconds
    }

    @Test
    fun minDelayAboveMaxIsCappedByMax() {
        RestartPolicy(minDelay = 1.minutes, maxDelay = 10.seconds).delayFor(0) shouldBe 10.seconds
    }

    @Test
    fun zeroMinDelayDoesNotLoopForever() {
        RestartPolicy(minDelay = Duration.ZERO).delayFor(Long.MAX_VALUE) shouldBe Duration.ZERO
    }
}
