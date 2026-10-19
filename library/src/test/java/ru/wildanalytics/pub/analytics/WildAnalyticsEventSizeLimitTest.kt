package ru.wildanalytics.pub.analytics

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.automatic.AutomaticEventsStrategy
import ru.wildanalytics.pub.analytics.event.EventsRepository
import java.time.Clock
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class WildAnalyticsEventSizeLimitTest {

    private val repository = mockk<EventsRepository>(relaxed = true)
    private val log = mockk<WildAnalyticsLogger>(relaxed = true) {
        every { isEnabled } returns true
    }

    @Test
    fun oversizedEventIsDroppedAndReported() = runTest {
        val subject = newSubject()

        subject.logEvent("begin_checkout", eventWithItemsOfSize(MAX_EVENT_EXTRAS_SIZE_IN_BYTES.toInt()))
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.add(any()) }
        verify(exactly = 1) { log.logError(any(), match { it["name"] == "begin_checkout" }) }
    }

    @Test
    fun repeatedOversizedEventIsReportedAsErrorOnce() = runTest {
        val subject = newSubject()

        repeat(3) {
            subject.logEvent("begin_checkout", eventWithItemsOfSize(MAX_EVENT_EXTRAS_SIZE_IN_BYTES.toInt()))
        }
        advanceUntilIdle()

        verify(exactly = 1) { log.logError(any(), any()) }
        verify(exactly = 2) { log.logWarn(any()) }
    }

    @Test
    fun eventExactlyAtLimitIsStored() = runTest {
        val subject = newSubject()
        val jsonOverhead = """{"items":""}""".length

        subject.logEvent(
            "begin_checkout",
            eventWithItemsOfSize(MAX_EVENT_EXTRAS_SIZE_IN_BYTES.toInt() - jsonOverhead),
        )
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.add(any()) }
    }

    @Test
    fun regularEventIsStored() = runTest {
        val subject = newSubject()

        subject.logEvent("view_cart", mapOf("a" to "1"))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.add(match { it.name == "view_cart" }) }
    }

    private fun TestScope.newSubject(): WildAnalyticsImpl {
        val zonedDateTime = ZonedDateTime.now()
        val consumerScope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        return WildAnalyticsImpl(
            apiUrlProvider = { "apiUrl" },
            apiKey = "apiKey",
            isCollectionEnabled = true,
            clock = Clock.fixed(zonedDateTime.toInstant(), zonedDateTime.zone),
            eventsRepository = repository,
            sessionProvider = mockk(relaxed = true),
            log = log,
            coroutineScopeFactory = object : CoroutineScopeFactory {
                override fun create(debugName: String) = consumerScope
            },
            customHeadersRepository = mockk(relaxed = true),
            enricherRegistry = EventEnricherRegistryImpl(NoOpWildAnalyticsLogger),
            automaticEventsStrategy = AutomaticEventsStrategy.DoNotTrack,
            commonParametersGate = CommonParametersGate(emptySet(), 5.seconds, NoOpWildAnalyticsLogger),
        )
    }

    private fun eventWithItemsOfSize(size: Int) =
        JsonObject(mapOf("items" to JsonPrimitive("x".repeat(size))))
}
