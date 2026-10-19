package ru.wildanalytics.pub.analytics

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.JsonPrimitive
import ru.wildanalytics.pub.analytics.automatic.AutomaticEventsStrategy
import ru.wildanalytics.pub.analytics.event.EventsRepository
import ru.wildanalytics.pub.analytics.utils.TestLogger
import java.time.Clock
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("Unused")
internal class WildAnalyticsCommonParametersTest : BehaviorSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = TestScope(testDispatcher)

    Given("WildAnalyticsImpl") {
        val repository = mockk<EventsRepository>(relaxed = true)
        val zonedDateTime = ZonedDateTime.now()
        val clock = Clock.fixed(zonedDateTime.toInstant(), zonedDateTime.zone)
        val coroutineScopeFactory = object : CoroutineScopeFactory {
            override fun create(debugName: String) = testScope
        }

        fun newSubject() = WildAnalyticsImpl(
            apiUrlProvider = { "apiUrl" },
            apiKey = "apiKey",
            isCollectionEnabled = true,
            clock = clock,
            eventsRepository = repository,
            sessionProvider = mockk(relaxed = true),
            log = TestLogger(),
            coroutineScopeFactory = coroutineScopeFactory,
            customHeadersRepository = mockk(relaxed = true),
            enricherRegistry = EventEnricherRegistryImpl(TestLogger()),
            automaticEventsStrategy = AutomaticEventsStrategy.DoNotTrack,
            commonParametersGate = CommonParametersGate(emptySet(), 5.seconds, mockk(relaxed = true)),
        )

        When("a String common parameter is set and an event is logged") {
            val subject = newSubject()
            subject.setCommonParameter("client_id", "123")
            subject.logEvent("event", emptyMap())

            Then("the stored event contains it as a JSON string") {
                coVerify {
                    repository.add(match { it.extras["client_id"] == JsonPrimitive("123") })
                }
            }
        }

        When("a Number common parameter is set and an event is logged") {
            val subject = newSubject()
            subject.setCommonParameter("vpn_status", 1)
            subject.logEvent("event", emptyMap())

            Then("the stored event contains it as a JSON number, not a string") {
                coVerify {
                    repository.add(
                        match {
                            it.extras["vpn_status"] == JsonPrimitive(1) &&
                                    it.extras["vpn_status"] != JsonPrimitive("1")
                        }
                    )
                }
            }
        }

        When("a Boolean common parameter is set and an event is logged") {
            val subject = newSubject()
            subject.setCommonParameter("is_authorized", true)
            subject.logEvent("event", emptyMap())

            Then("the stored event contains it as a JSON boolean") {
                coVerify {
                    repository.add(match { it.extras["is_authorized"] == JsonPrimitive(true) })
                }
            }
        }

        When("a Number common parameter is removed via null and an event is logged") {
            val subject = newSubject()
            subject.setCommonParameter("vpn_status", 1)
            subject.setCommonParameter("vpn_status", null)
            subject.logEvent("event", emptyMap())

            Then("the stored event does not contain the parameter") {
                coVerify {
                    repository.add(match { "vpn_status" !in it.extras })
                }
            }
        }

        When("a typed common parameter is overwritten with another type") {
            val subject = newSubject()
            subject.setCommonParameter("flag", "1")
            subject.setCommonParameter("flag", 1)
            subject.logEvent("event", emptyMap())

            Then("the latest value and type win") {
                coVerify {
                    repository.add(match { it.extras["flag"] == JsonPrimitive(1) })
                }
            }
        }
    }
})
