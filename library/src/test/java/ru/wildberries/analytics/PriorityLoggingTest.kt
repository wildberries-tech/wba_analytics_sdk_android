package ru.wildberries.analytics

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.MockKMatcherScope
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import ru.wildberries.analytics.db.EventEntity
import ru.wildberries.analytics.event.EventsRepository
import java.time.Clock
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("Unused")
internal class PriorityLoggingTest : BehaviorSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = TestScope(testDispatcher)

    Given("WBAnalytics2Impl with mocked repository") {
        val repository = mockk<EventsRepository>(relaxed = true)
        val zonedDateTime = ZonedDateTime.now()
        val clock = Clock.fixed(zonedDateTime.toInstant(), zonedDateTime.zone)
        val coroutineScopeFactory = object : CoroutineScopeFactory {
            override fun create(debugName: String) = testScope
        }

        val subject = WBAnalytics2Impl(
            apiUrlProvider = { "apiUrl" },
            apiKey = "apiKey",
            isCollectionEnabled = true,
            clock = clock,
            eventsRepository = repository,
            log = mockk(relaxed = true),
            coroutineScopeFactory = coroutineScopeFactory
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
