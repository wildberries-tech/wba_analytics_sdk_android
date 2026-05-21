package ru.wildanalytics.pub.analytics

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("Unused")
internal class RateLimitTest : BehaviorSpec({

    given("Flow emitting 10 numbers with delay 3 seconds and rateLimit 10 seconds") {
        val logger = TestCoroutineLogger()
        val flow = flow {
            logger.log("f:start")
            repeat(10) {
                logger.log("f:emit $it")
                emit(it)
                logger.log("f:delay 3s")
                delay(3.seconds)
            }
        }.rateLimit(10.seconds)
            .onEach {
                logger.log("onEach $it")
            }

        `when`("run flow") {
            runTest {
                logger.log("W:start")
                flow.launchIn(this)

                logger.log("W:advice 1s")
                advanceTimeBy(1000)

                logger.log("W:advice 3s")
                advanceTimeBy(3000)

                logger.log("W:advice 10s")
                advanceUntilIdle()
            }

            then("Log contains onEach every 10 seconds") {
                val expected = listOf(
                    "____0: W:start",
                    "____0: W:advice 1s",
                    "____0: f:start",
                    "____0: f:emit 0",
                    "____0: f:delay 3s",
                    "_1000: W:advice 3s",
                    "_3000: f:emit 1",
                    "_3000: f:delay 3s",
                    "_4000: W:advice 10s",
                    "_6000: f:emit 2",
                    "_6000: f:delay 3s",
                    "_9000: f:emit 3",
                    "_9000: f:delay 3s",
                    "10000: onEach 3",
                    "12000: f:emit 4",
                    "12000: f:delay 3s",
                    "15000: f:emit 5",
                    "15000: f:delay 3s",
                    "18000: f:emit 6",
                    "18000: f:delay 3s",
                    "21000: f:emit 7",
                    "21000: f:delay 3s",
                    "22000: onEach 7",
                    "24000: f:emit 8",
                    "24000: f:delay 3s",
                    "27000: f:emit 9",
                    "27000: f:delay 3s",
                    "34000: onEach 9",
                )
                logger.logLines shouldBe expected
            }
        }
    }
})
