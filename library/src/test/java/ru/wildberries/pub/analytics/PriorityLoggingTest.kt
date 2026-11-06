package ru.wildberries.pub.analytics

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.Headers
import ru.wildberries.pub.analytics.automatic.AutomaticEventsStrategy
import ru.wildberries.pub.analytics.db.EventEntity
import ru.wildberries.pub.analytics.event.EventsRepository
import ru.wildberries.pub.analytics.transport.CustomHeadersRepository
import java.time.Clock
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.seconds

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

        val customHeadersRepository = mockk<CustomHeadersRepository>(relaxed = true)
        val log = mockk<WBAnalytics2Logger>(relaxed = true)
        every { log.isEnabled } returns true

        val subject = WBAnalytics2Impl(
            diagnostics = mockk(relaxed = true),
            apiUrlProvider = { "apiUrl" },
            apiKey = "apiKey",
            isCollectionEnabled = true,
            clock = clock,
            eventsRepository = repository,
            log = log,
            coroutineScopeFactory = coroutineScopeFactory,
            customHeadersRepository = customHeadersRepository,
            sessionProvider = mockk(relaxed = true),
            enricherRegistry = EventEnricherRegistryImpl(log = mockk(relaxed = true)),
            automaticEventsStrategy = AutomaticEventsStrategy.DoNotTrack,
            commonParametersGate = CommonParametersGate(emptySet(), 5.seconds, mockk(relaxed = true)),
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

        When("setCustomHeader is called") {
            subject.setCustomHeader("X-Wbaas-Token", "token")

            Then("repository.set is called with same args") {
                verify { customHeadersRepository.set("X-Wbaas-Token", "token") }
            }
        }

        When("setCustomHeaders is called") {
            val headers = Headers.Builder().add("X-Wbaas-Token", "token").build()
            subject.setCustomHeaders(headers)

            Then("repository.setAll is called with same headers") {
                verify { customHeadersRepository.setAll(headers) }
            }
        }

        When("setCustomHeader is called with a blank key") {
            subject.setCustomHeader("  ", "value")

            Then("a warning is logged and repository.set is not called with a blank key") {
                verify { log.logWarn(any<String>()) }
                verify(exactly = 0) { customHeadersRepository.set(match { it.isBlank() }, any()) }
            }
        }
    }
})
