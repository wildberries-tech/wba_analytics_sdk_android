package ru.wildanalytics.pub.analytics

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonPrimitive
import ru.wildanalytics.pub.analytics.automatic.AutomaticEventsStrategy
import ru.wildanalytics.pub.analytics.event.EventsRepository
import java.time.Clock
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("Unused")
internal class WildAnalyticsRequiredCommonParametersTest : BehaviorSpec({

    Given("WildAnalyticsImpl with required common parameters client_id and lang") {
        val zonedDateTime = ZonedDateTime.now()
        val clock = Clock.fixed(zonedDateTime.toInstant(), zonedDateTime.zone)

        class Fixture {
            val testScope = TestScope(UnconfinedTestDispatcher())
            val repository = mockk<EventsRepository>(relaxed = true)
            val log = mockk<WildAnalyticsLogger>(relaxed = true).also {
                every { it.isEnabled } returns true
            }
            val subject = WildAnalyticsImpl(
                apiUrlProvider = { "apiUrl" },
                apiKey = "apiKey",
                isCollectionEnabled = true,
                clock = clock,
                eventsRepository = repository,
                sessionProvider = mockk(relaxed = true),
                log = log,
                coroutineScopeFactory = object : CoroutineScopeFactory {
                    override fun create(debugName: String) = testScope
                },
                customHeadersRepository = mockk(relaxed = true),
                enricherRegistry = EventEnricherRegistryImpl(log),
                automaticEventsStrategy = AutomaticEventsStrategy.DoNotTrack,
                commonParametersGate = CommonParametersGate(
                    requiredKeys = setOf("client_id", "lang"),
                    timeout = 5.seconds,
                    log = log,
                ),
            )
        }

        When("an event is logged before required parameters are set") {
            val fixture = Fixture()
            fixture.subject.logEvent("event", mapOf("a" to "1"))

            Then("the event is not stored yet") {
                coVerify(exactly = 0) { fixture.repository.add(any()) }
            }

            And("only one required parameter is set") {
                fixture.subject.setCommonParameter("client_id", "user")

                Then("the event is still held") {
                    coVerify(exactly = 0) { fixture.repository.add(any()) }
                }
            }

            And("the last required parameter is set") {
                fixture.subject.setCommonParameter("lang", "ru")

                Then("the event is stored with all common parameters filled in") {
                    coVerify(exactly = 1) {
                        fixture.repository.add(
                            match {
                                it.extras["a"] == JsonPrimitive("1") &&
                                    it.extras["client_id"] == JsonPrimitive("user") &&
                                    it.extras["lang"] == JsonPrimitive("ru")
                            }
                        )
                    }
                }
            }
        }

        When("a required parameter is explicitly set to null") {
            val fixture = Fixture()
            fixture.subject.logEvent("event")
            fixture.subject.setCommonParameters(mapOf("client_id" to null, "lang" to "ru"))

            Then("the gate opens and the event is stored without that key") {
                coVerify(exactly = 1) {
                    fixture.repository.add(
                        match {
                            !it.extras.containsKey("client_id") && it.extras["lang"] == JsonPrimitive("ru")
                        }
                    )
                }
            }
        }

        When("required parameters are never set and the timeout passes") {
            val fixture = Fixture()
            fixture.subject.logEvent("event", mapOf("a" to "1"))

            Then("the event is stored as is and a warning is logged") {
                // yield даёт unconfined-корутине consumer'а стартовать до сдвига времени и возобновиться после.
                yield()
                fixture.testScope.testScheduler.advanceTimeBy(6.seconds)
                yield()

                coVerify(exactly = 1) {
                    fixture.repository.add(match { it.extras["a"] == JsonPrimitive("1") })
                }
                verify { fixture.log.logWarn(match { "client_id" in it && "lang" in it }) }
            }

            And("a parameter is set after the timeout") {
                fixture.subject.setCommonParameter("client_id", "late")
                fixture.subject.logEvent("next")

                Then("subsequent events are stored immediately with it") {
                    coVerify(exactly = 1) {
                        fixture.repository.add(
                            match { it.name == "next" && it.extras["client_id"] == JsonPrimitive("late") }
                        )
                    }
                }
            }
        }

        When("an event carries its own value for a required key") {
            val fixture = Fixture()
            fixture.subject.logEvent("event", mapOf("client_id" to "own"))
            fixture.subject.setCommonParameters(mapOf("client_id" to "common", "lang" to "ru"))

            Then("the event value wins over the common one") {
                coVerify(exactly = 1) {
                    fixture.repository.add(match { it.extras["client_id"] == JsonPrimitive("own") })
                }
            }
        }

        When("a common parameter changes between logEvent and processing") {
            val fixture = Fixture()
            fixture.subject.setCommonParameter("lang", "ru")
            fixture.subject.logEvent("event")
            fixture.subject.setCommonParameter("lang", "en")
            fixture.subject.setCommonParameter("client_id", "user")

            Then("the value captured at logEvent time is kept") {
                coVerify(exactly = 1) {
                    fixture.repository.add(match { it.extras["lang"] == JsonPrimitive("ru") })
                }
            }
        }

        When("the gate is already open and a required key is removed") {
            val fixture = Fixture()
            fixture.subject.setCommonParameters(mapOf("client_id" to "user", "lang" to "ru"))
            fixture.subject.setCommonParameter("client_id", null)
            fixture.subject.logEvent("event")

            Then("events keep flowing without waiting") {
                coVerify(exactly = 1) {
                    fixture.repository.add(match { !it.extras.containsKey("client_id") })
                }
            }
        }
    }

    Given("WildAnalyticsImpl without required common parameters") {
        val testScope = TestScope(UnconfinedTestDispatcher())
        val repository = mockk<EventsRepository>(relaxed = true)
        val subject = WildAnalyticsImpl(
            apiUrlProvider = { "apiUrl" },
            apiKey = "apiKey",
            isCollectionEnabled = true,
            clock = Clock.systemUTC(),
            eventsRepository = repository,
            sessionProvider = mockk(relaxed = true),
            log = mockk(relaxed = true),
            coroutineScopeFactory = object : CoroutineScopeFactory {
                override fun create(debugName: String) = testScope
            },
            customHeadersRepository = mockk(relaxed = true),
            enricherRegistry = EventEnricherRegistryImpl(mockk(relaxed = true)),
            automaticEventsStrategy = AutomaticEventsStrategy.DoNotTrack,
            commonParametersGate = CommonParametersGate(
                requiredKeys = emptySet(),
                timeout = 5.seconds,
                log = mockk(relaxed = true),
            ),
        )

        When("an event is logged") {
            subject.logEvent("event")

            Then("it is stored immediately") {
                coVerify(exactly = 1) { repository.add(any()) }
            }
        }
    }
})
