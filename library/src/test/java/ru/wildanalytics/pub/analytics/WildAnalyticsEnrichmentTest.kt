package ru.wildanalytics.pub.analytics

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.JsonPrimitive
import ru.wildanalytics.pub.analytics.event.EventsRepository
import ru.wildanalytics.pub.analytics.session.SessionProvider
import ru.wildanalytics.pub.analytics.transport.CustomHeadersRepository
import ru.wildanalytics.pub.analytics.utils.TestLogger
import java.time.Clock
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("Unused")
internal class WildAnalyticsEnrichmentTest : BehaviorSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = TestScope(testDispatcher)

    Given("WildAnalyticsImpl with a real enricher registry") {
        val repository = mockk<EventsRepository>(relaxed = true)
        val zonedDateTime = ZonedDateTime.now()
        val clock = Clock.fixed(zonedDateTime.toInstant(), zonedDateTime.zone)
        val coroutineScopeFactory = object : CoroutineScopeFactory {
            override fun create(debugName: String) = testScope
        }
        val sessionProvider = mockk<SessionProvider>(relaxed = true)
        val customHeadersRepository = mockk<CustomHeadersRepository>(relaxed = true)

        fun newSubject() = WildAnalyticsImpl(
            apiUrlProvider = { "apiUrl" },
            apiKey = "apiKey",
            isCollectionEnabled = true,
            clock = clock,
            eventsRepository = repository,
            sessionProvider = sessionProvider,
            log = TestLogger(),
            coroutineScopeFactory = coroutineScopeFactory,
            customHeadersRepository = customHeadersRepository,
            enricherRegistry = EventEnricherRegistryImpl(TestLogger()),
        )

        When("an enricher is registered after construction and an event is logged") {
            val subject = newSubject()
            subject.addEventEnricher { _, _ -> listOf(AnalyticsField("enriched", JsonPrimitive("yes"))) }
            subject.logEvent("event", mapOf("a" to "1"))

            Then("the stored event keeps originals and gains the enriched field") {
                coVerify {
                    repository.add(
                        match {
                            it.extras["a"] == JsonPrimitive("1") &&
                                it.extras["enriched"] == JsonPrimitive("yes")
                        }
                    )
                }
            }
        }

        When("an enricher tries to overwrite an original parameter") {
            val subject = newSubject()
            subject.addEventEnricher { _, _ -> listOf(AnalyticsField("a", JsonPrimitive("overwritten"))) }
            subject.logEvent("event", mapOf("a" to "original"))

            Then("the original parameter is preserved") {
                coVerify {
                    repository.add(
                        match {
                            it.extras["a"] == JsonPrimitive("original")
                        }
                    )
                }
            }
        }
    }
})
