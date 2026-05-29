package ru.wildanalytics.pub.analytics.send

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.testCoroutineScheduler
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import ru.wildanalytics.pub.analytics.TestScopeFactory
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import ru.wildanalytics.pub.analytics.config.ConfigRepository
import ru.wildanalytics.pub.analytics.config.WildAnalyticsConfig

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class)
internal class WildAnalyticsSenderServiceTest : BehaviorSpec() {

    init {
        // Почему-то тестовый скоуп работат только если его включть так.
        coroutineTestScope = true

        lateinit var log: WildAnalyticsLogger

        Given("Dao and events") {
            val config = WildAnalyticsConfig.Companion.Default
            val configRepository = mockk<ConfigRepository> {
                every { this@mockk.config } returns MutableStateFlow(config)
            }

            val sendStrategy = mockk<SendStrategy>(relaxUnitFun = true)
            val sendStrategyProvider = mockk<SendStrategyProvider> {
                every { strategy } returns flowOf(sendStrategy)
            }

            log = mockk(relaxed = true)
            `when`("Start service") {
                WildAnalyticsSenderService(
                    log = log,
                    configRepository = configRepository,
                    coroutineScopeFactory = TestScopeFactory(parentScope = this),
                    sendStrategyProvider = sendStrategyProvider,
                )
                testCoroutineScheduler.advanceTimeBy(15_000)
                coroutineContext.cancelChildren()
                then("Send strategy invoked") {
                    coVerify {
                        sendStrategy.invoke(eq(config))
                    }
                }
            }
        }
    }
}