package ru.wildanalytics.pub.analytics.send

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.NoOpWildAnalyticsLogger
import ru.wildanalytics.pub.analytics.config.WildAnalyticsConfig
import ru.wildanalytics.pub.analytics.domain.ApiData
import ru.wildanalytics.pub.analytics.event.EventsRepository
import ru.wildanalytics.pub.analytics.network.NetworkAvailabilitySource
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class ImmediateSendStrategyTest {

    private val apiData = ApiData("apiUrl", "apiKey")
    private val config = WildAnalyticsConfig.Default
    private val eventsRepository = mockk<EventsRepository>(relaxUnitFun = true) {
        coEvery { awaitFirstApiData() } returns apiData
    }
    private val networkAvailabilitySource = mockk<NetworkAvailabilitySource> {
        every { availabilityFlow } returns flowOf(true)
    }

    @Test
    fun permanentFailureDoesNotSpinInHotLoop() = runTest {
        var attempts = 0
        val sendOperation = mockk<SendAllAnalyticEventsOperation> {
            coEvery { execute(any(), any()) } answers {
                attempts++
                if (attempts > HOT_LOOP_GUARD) throw CancellationException("hot loop")
                throw IOException("Row too big to fit into CursorWindow")
            }
        }
        val job = launch { createSubject(sendOperation).invoke(config) }

        advanceTimeBy(10.minutes)
        job.cancel()

        attempts shouldBeLessThanOrEqual 10
    }

    @Test
    fun zeroDelayConfigStillBacksOffOnFailure() = runTest {
        var attempts = 0
        val sendOperation = mockk<SendAllAnalyticEventsOperation> {
            coEvery { execute(any(), any()) } answers {
                attempts++
                if (attempts > HOT_LOOP_GUARD) throw CancellationException("hot loop")
                throw IOException()
            }
        }
        val zeroDelayConfig = config.copy(
            delays = config.delays.copy(delayBetweenOperations = 0.seconds, delayBetweenBatches = 0.seconds)
        )
        val job = launch { createSubject(sendOperation).invoke(zeroDelayConfig) }

        advanceTimeBy(1.minutes)
        job.cancel()

        attempts shouldBeLessThanOrEqual 10
    }

    @Test
    fun sendingResumesAfterTransientFailure() = runTest {
        var attempts = 0
        val sendOperation = mockk<SendAllAnalyticEventsOperation> {
            coEvery { execute(any(), any()) } answers {
                attempts++
                if (attempts == 1) throw IOException()
            }
        }
        val job = launch { createSubject(sendOperation).invoke(config) }

        advanceTimeBy(1.minutes)
        job.cancel()

        attempts shouldBeGreaterThan 2
    }

    @Test
    fun backoffResetsAfterSuccess() = runTest {
        val callTimes = mutableListOf<Long>()
        val sendOperation = mockk<SendAllAnalyticEventsOperation> {
            coEvery { execute(any(), any()) } answers {
                callTimes += testScheduler.currentTime
                if (callTimes.size != 2) throw IOException()
            }
        }
        val job = launch { createSubject(sendOperation).invoke(config) }

        advanceTimeBy(35.seconds)
        job.cancel()

        val base = config.delays.delayBetweenOperations.inWholeMilliseconds
        callTimes shouldBe listOf(0L, base, base * 2, base * 3)
    }

    private fun createSubject(sendOperation: SendAllAnalyticEventsOperation) = ImmediateSendStrategy(
        eventsRepository = eventsRepository,
        log = NoOpWildAnalyticsLogger,
        sendOperation = sendOperation,
        networkAvailabilitySource = networkAvailabilitySource,
    )

    private companion object {
        const val HOT_LOOP_GUARD = 1_000
    }
}
