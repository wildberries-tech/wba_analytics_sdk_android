package ru.wildanalytics.pub.analytics

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.automatic.AutomaticEventsStrategy
import ru.wildanalytics.pub.analytics.diagnostics.DiagnosticMarker
import ru.wildanalytics.pub.analytics.diagnostics.DiagnosticsReporter
import ru.wildanalytics.pub.analytics.event.EventsRepository
import java.time.Clock
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class WildAnalyticsInsertFailureTest {

    private val repository = mockk<EventsRepository>(relaxed = true)
    private val diagnostics = mockk<DiagnosticsReporter>(relaxed = true)

    @Test
    fun failedInsertIsReportedAndNextEventIsStored() = runTest {
        val failure = IllegalStateException("database or disk is full")
        coEvery { repository.add(match { it.name == "begin_checkout" }) } throws failure
        val subject = newSubject()

        subject.logEvent("begin_checkout", mapOf("a" to "1"))
        subject.logEvent("view_cart", mapOf("a" to "1"))
        advanceUntilIdle()

        verify(exactly = 1) {
            diagnostics.report(
                marker = DiagnosticMarker.INSERT_FAILED,
                message = any(),
                attributes = mapOf("name" to "begin_checkout", "exception" to IllegalStateException::class.java.name),
                throwable = failure,
            )
        }
        coVerify(exactly = 1) { repository.add(match { it.name == "view_cart" }) }
    }

    @Test
    fun foreignCancellationExceptionDoesNotStopConsumer() = runTest {
        coEvery { repository.add(match { it.name == "begin_checkout" }) } throws CancellationException("foreign")
        val subject = newSubject()

        subject.logEvent("begin_checkout", mapOf("a" to "1"))
        subject.logEvent("view_cart", mapOf("a" to "1"))
        advanceUntilIdle()

        verify(exactly = 1) {
            diagnostics.report(
                marker = DiagnosticMarker.INSERT_FAILED,
                message = any(),
                attributes = any(),
                throwable = ofType<CancellationException>(),
            )
        }
        coVerify(exactly = 1) { repository.add(match { it.name == "view_cart" }) }
    }

    @Test
    fun scopeCancellationIsNotReported() = runTest {
        val consumerScope = CoroutineScope(backgroundScope.coroutineContext + Job() + UnconfinedTestDispatcher(testScheduler))
        coEvery { repository.add(any()) } coAnswers { awaitCancellation() }
        val subject = newSubject(consumerScope)

        subject.logEvent("begin_checkout", mapOf("a" to "1"))
        advanceUntilIdle()
        consumerScope.cancel()
        advanceUntilIdle()

        verify(exactly = 0) { diagnostics.report(any(), any(), any(), any()) }
    }

    @Test
    fun repeatedFailureOfSameTypeIsReportedOnce() = runTest {
        coEvery { repository.add(any()) } throws IllegalStateException("database or disk is full")
        val subject = newSubject()

        repeat(1000) { subject.logEvent("event_$it", mapOf("a" to "1")) }
        advanceUntilIdle()

        coVerify(exactly = 1000) { repository.add(any()) }
        verify(exactly = 1) {
            diagnostics.report(
                marker = DiagnosticMarker.INSERT_FAILED,
                message = any(),
                attributes = mapOf("name" to "event_0", "exception" to IllegalStateException::class.java.name),
                throwable = any(),
            )
        }
    }

    @Test
    fun failuresOfDifferentTypesAreReportedSeparately() = runTest {
        coEvery { repository.add(match { it.name == "a" }) } throws IllegalStateException("full")
        coEvery { repository.add(match { it.name == "b" }) } throws IllegalArgumentException("bad")
        val subject = newSubject()

        repeat(3) {
            subject.logEvent("a", mapOf("a" to "1"))
            subject.logEvent("b", mapOf("a" to "1"))
        }
        advanceUntilIdle()

        verify(exactly = 1) { diagnostics.report(DiagnosticMarker.INSERT_FAILED, any(), any(), ofType<IllegalStateException>()) }
        verify(exactly = 1) { diagnostics.report(DiagnosticMarker.INSERT_FAILED, any(), any(), ofType<IllegalArgumentException>()) }
    }

    @Test
    fun successfulInsertIsNotReported() = runTest {
        val subject = newSubject()

        subject.logEvent("view_cart", mapOf("a" to "1"))
        advanceUntilIdle()

        verify(exactly = 0) { diagnostics.report(any(), any(), any(), any()) }
    }

    private fun TestScope.newSubject(
        consumerScope: CoroutineScope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
    ): WildAnalyticsImpl {
        val zonedDateTime = ZonedDateTime.now()
        return WildAnalyticsImpl(
            apiUrlProvider = { "apiUrl" },
            apiKey = "apiKey",
            isCollectionEnabled = true,
            clock = Clock.fixed(zonedDateTime.toInstant(), zonedDateTime.zone),
            eventsRepository = repository,
            sessionProvider = mockk(relaxed = true),
            log = NoOpWildAnalyticsLogger,
            coroutineScopeFactory = object : CoroutineScopeFactory {
                override fun create(debugName: String) = consumerScope
            },
            customHeadersRepository = mockk(relaxed = true),
            enricherRegistry = EventEnricherRegistryImpl(NoOpWildAnalyticsLogger),
            automaticEventsStrategy = AutomaticEventsStrategy.DoNotTrack,
            commonParametersGate = CommonParametersGate(emptySet(), 5.seconds, NoOpWildAnalyticsLogger),
            diagnostics = diagnostics,
        )
    }
}
