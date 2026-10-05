package ru.wildanalytics.pub.analytics.automatic

import io.kotest.core.spec.style.BehaviorSpec
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class AutomaticEventsApiKeyRegistryTest : BehaviorSpec({

    Given("AutomaticEventsApiKeyRegistry instance") {
        val registry = AutomaticEventsApiKeyRegistryImpl()
        val apiKey = "test_api_key"

        When("No keys registered") {
            Then("isEnabled returns false") {
                assertFalse(registry.isEnabled(apiKey))
            }
        }

        When("Key registered once") {
            registry.register(apiKey)

            Then("isEnabled returns true") {
                assertTrue(registry.isEnabled(apiKey))
            }
        }

        When("Same key registered second time") {
            registry.register(apiKey)

            Then("isEnabled still returns true") {
                assertTrue(registry.isEnabled(apiKey))
            }
        }

        When("One instance unregisters") {
            registry.unregister(apiKey)

            Then("isEnabled remains true because one instance is still active") {
                assertTrue(registry.isEnabled(apiKey))
            }
        }

        When("Second instance unregisters") {
            registry.unregister(apiKey)

            Then("isEnabled returns false") {
                assertFalse(registry.isEnabled(apiKey))
            }
        }

        When("Extra unregister called") {
            registry.unregister(apiKey)

            Then("isEnabled still returns false without errors") {
                assertFalse(registry.isEnabled(apiKey))
            }
        }
    }
})
