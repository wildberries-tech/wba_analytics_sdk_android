package ru.wildanalytics.pub.analytics.automatic

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import ru.wildanalytics.pub.analytics.WildAnalytics

internal class AutomaticEventsStrategyTest : BehaviorSpec({

    Given("AutomaticEventsStrategy.DoNotTrack") {
        val strategy = AutomaticEventsStrategy.DoNotTrack

        When("start and stop called") {
            val wba = mockk<WildAnalytics>(relaxed = true)
            val scope = CoroutineScope(Dispatchers.Unconfined)
            strategy.start(wba, scope)
            strategy.stop()

            Then("No interactions and no errors thrown") {
                verify(exactly = 0) { wba.logEvent(any<String>(), any<Map<String, String>>()) }
            }
        }
    }

    Given("AutomaticEventsStrategy.Track") {
        val registry = mockk<AutomaticEventsApiKeyRegistry>(relaxed = true)
        val tracker = mockk<AutomaticEventsTracker>(relaxed = true)
        val trackerFactory = AutomaticEventsTracker.Factory { tracker }
        val apiKey = "test_key"
        val strategy = AutomaticEventsStrategy.Track(
            apiKey = apiKey,
            registry = registry,
            trackerFactory = trackerFactory,
        )

        When("start is called") {
            val wba = mockk<WildAnalytics>(relaxed = true)
            val scope = CoroutineScope(Dispatchers.Unconfined)
            strategy.start(wba, scope)

            Then("Key is registered in registry and tracker is started") {
                verify(exactly = 1) { registry.register(apiKey) }
                verify(exactly = 1) { tracker.start(wba, scope) }
            }

            When("stop is called") {
                strategy.stop()

                Then("Tracker is stopped and key is unregistered") {
                    verify(exactly = 1) { tracker.stop() }
                    verify(exactly = 1) { registry.unregister(apiKey) }
                }
            }
        }
    }
})
