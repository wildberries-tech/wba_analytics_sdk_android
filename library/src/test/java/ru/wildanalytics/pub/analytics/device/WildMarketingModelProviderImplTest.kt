package ru.wildanalytics.pub.analytics.device

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import kotlin.test.assertEquals

internal class WildMarketingModelProviderImplTest : BehaviorSpec({

    val log = mockk<WildAnalyticsLogger>(relaxed = true)

    beforeEach {
        every { log.isEnabled } returns true
    }

    Given("Marketing model provider is created") {

        When("marketingModel is accessed") {
            val provider = WildMarketingModelProviderImpl(log)
            val model = provider.marketingModel

            Then("It resolves the model") {
                assertEquals("", model)
            }
        }

        When("marketingModel is accessed multiple times") {
            val provider = WildMarketingModelProviderImpl(log)
            repeat(3) { provider.marketingModel }

            Then("It is resolved only once") {
                verify(exactly = 1) { log.logException(any()) }
            }
        }
    }
})
