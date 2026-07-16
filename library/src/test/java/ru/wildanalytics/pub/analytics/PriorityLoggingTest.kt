package ru.wildanalytics.pub.analytics

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import ru.wildanalytics.pub.analytics.db.EventEntity
import ru.wildanalytics.pub.analytics.event.EventsRepository
import ru.wildanalytics.pub.analytics.session.SessionProvider
import ru.wildanalytics.pub.analytics.transport.CustomHeadersRepository
import java.time.Clock
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("Unused")
internal class PriorityLoggingTest : BehaviorSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = TestScope(testDispatcher)

    Given("WildAnalyticsImpl with mocked repository") {
        val repository = mockk<EventsRepository>(relaxed = true)
        val zonedDateTime = ZonedDateTime.now()
        val clock = Clock.fixed(zonedDateTime.toInstant(), zonedDateTime.zone)
        val coroutineScopeFactory = object : CoroutineScopeFactory {
            override fun create(debugName: String) = testScope
        }

        val subject = WildAnalyticsImpl(
            apiUrlProvider = { "apiUrl" },
            apiKey = "apiKey",
            isCollectionEnabled = true,
            clock = clock,
            eventsRepository = repository,
            sessionProvider = mockk<SessionProvider>(relaxed = true),
            log = mockk(relaxed = true),
            coroutineScopeFactory = coroutineScopeFactory,
            customHeadersRepository = mockk<CustomHeadersRepository>(relaxed = true),
            enricherRegistry = EventEnricherRegistryImpl(mockk(relaxed = true)),
        )

        When("logEvent is called") {
            subject.logEvent("normal_event", emptyMap())

            Then("Repository add is called with IMPORTANCE_NORMAL") {
                coVerify {
                    repository.add(match {
                        it.name == "normal_event" && it.importance == EventEntity.IMPORTANCE_NORMAL
                    })
                }
            }
        }

        When("logImportantEvent is called") {
            subject.logImportantEvent("important_event", emptyMap())

            Then("Repository add is called with IMPORTANCE_HIGH") {
                coVerify {
                    repository.add(match {
                        it.name == "important_event" && it.importance == EventEntity.IMPORTANCE_HIGH
                    })
                }
            }
        }
    }
})
