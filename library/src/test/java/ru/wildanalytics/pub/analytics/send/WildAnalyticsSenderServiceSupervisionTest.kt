package ru.wildanalytics.pub.analytics.send

import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.NoOpWildAnalyticsLogger
import ru.wildanalytics.pub.analytics.TestScopeFactory
import ru.wildanalytics.pub.analytics.config.ConfigRepository
import ru.wildanalytics.pub.analytics.config.WildAnalyticsConfig
import ru.wildanalytics.pub.analytics.diagnostics.DiagnosticMarker
import ru.wildanalytics.pub.analytics.diagnostics.DiagnosticsReporter
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class WildAnalyticsSenderServiceSupervisionTest {

    private val wbaConfig = WildAnalyticsConfig.Default
    private val diagnostics = mockk<DiagnosticsReporter>(relaxed = true)

    @Test
    fun restartsStrategyAfterException() = runTest {
        var invocations = 0
        startService {
            invocations++
            require(invocations > 1) { "NetworkCallback was not registered" }
            awaitCancellation()
        }

        advanceTimeBy(1.minutes)
        coroutineContext.cancelChildren()

        invocations shouldBe 2
        verify(exactly = 1) {
            diagnostics.report(
                marker = DiagnosticMarker.SEND_LOOP_RESTARTED,
                message = any(),
                attributes = any(),
                throwable = ofType<IllegalArgumentException>(),
            )
        }
    }

    @Test
    fun restartsStrategyAfterError() = runTest {
        var invocations = 0
        startService {
            invocations++
            if (invocations == 1) throw StackOverflowError()
            awaitCancellation()
        }

        advanceTimeBy(1.minutes)
        coroutineContext.cancelChildren()

        invocations shouldBe 2
    }

    @Test
    fun permanentFailureDoesNotSpinInHotLoop() = runTest {
        var invocations = 0
        startService {
            invocations++
            throw IOException("disk I/O error")
        }

        advanceTimeBy(30.minutes)
        coroutineContext.cancelChildren()

        invocations shouldBeGreaterThanOrEqual 2
        invocations shouldBeLessThanOrEqual 20
    }

    @Test
    fun foreignCancellationExceptionRestartsStrategy() = runTest {
        var invocations = 0
        startService {
            invocations++
            if (invocations == 1) throw CancellationException("Room: connection pool has been closed")
            awaitCancellation()
        }

        advanceTimeBy(1.minutes)
        coroutineContext.cancelChildren()

        invocations shouldBe 2
        verify(exactly = 1) {
            diagnostics.report(
                marker = DiagnosticMarker.SEND_LOOP_RESTARTED,
                message = any(),
                attributes = any(),
                throwable = match<Throwable> { it is IllegalStateException && it.message == "Send strategy was cancelled unexpectedly" },
            )
        }
    }

    @Test
    fun scopeCancellationStopsWithoutRestartOrDiagnostic() = runTest {
        var invocations = 0
        startService {
            invocations++
            awaitCancellation()
        }
        advanceTimeBy(1.minutes)

        coroutineContext.cancelChildren()
        advanceTimeBy(1.minutes)

        invocations shouldBe 1
        verify(exactly = 0) { diagnostics.report(any(), any(), any(), any()) }
    }

    @Test
    fun strategySwitchIsNotReportedAsRestart() = runTest {
        val invoked = mutableListOf<String>()
        val first = mockk<SendStrategy> {
            coEvery { this@mockk.invoke(any()) } coAnswers {
                invoked += "first"
                awaitCancellation()
            }
        }
        val second = mockk<SendStrategy> {
            coEvery { this@mockk.invoke(any()) } coAnswers {
                invoked += "second"
                awaitCancellation()
            }
        }
        val strategies = MutableStateFlow(first)
        WildAnalyticsSenderService(
            log = NoOpWildAnalyticsLogger,
            configRepository = mockk<ConfigRepository> {
                every { this@mockk.config } returns MutableStateFlow(wbaConfig)
            },
            coroutineScopeFactory = TestScopeFactory(parentScope = this),
            sendStrategyProvider = mockk<SendStrategyProvider> {
                every { this@mockk.strategy } returns strategies
            },
            diagnostics = diagnostics,
            restartPolicy = RestartPolicy(),
        )

        advanceTimeBy(1.minutes)
        strategies.value = second
        advanceTimeBy(1.minutes)
        coroutineContext.cancelChildren()

        invoked shouldBe listOf("first", "second")
        verify(exactly = 0) { diagnostics.report(any(), any(), any(), any()) }
    }

    @Test
    fun restartWaitsForPolicyDelay() = runTest {
        var invocations = 0
        startService(restartPolicy = RestartPolicy(minDelay = 10.seconds, maxDelay = 10.seconds)) {
            invocations++
            if (invocations == 1) throw IOException("disk I/O error")
            awaitCancellation()
        }

        advanceTimeBy(wbaConfig.delays.initialDelay + 9.seconds)
        invocations shouldBe 1
        advanceTimeBy(wbaConfig.delays.initialDelay + 2.seconds)
        coroutineContext.cancelChildren()

        invocations shouldBe 2
    }

    private fun TestScope.startService(
        restartPolicy: RestartPolicy = RestartPolicy(),
        strategyBody: suspend () -> Unit,
    ) {
        val strategy = mockk<SendStrategy> {
            coEvery { this@mockk.invoke(any()) } coAnswers { strategyBody() }
        }
        WildAnalyticsSenderService(
            log = NoOpWildAnalyticsLogger,
            configRepository = mockk<ConfigRepository> {
                every { this@mockk.config } returns MutableStateFlow(wbaConfig)
            },
            coroutineScopeFactory = TestScopeFactory(parentScope = this),
            sendStrategyProvider = mockk<SendStrategyProvider> {
                every { this@mockk.strategy } returns flowOf(strategy)
            },
            diagnostics = diagnostics,
            restartPolicy = restartPolicy,
        )
    }
}
