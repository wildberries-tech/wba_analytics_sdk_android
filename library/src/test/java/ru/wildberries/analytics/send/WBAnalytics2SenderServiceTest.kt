package ru.wildberries.analytics.send

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.testCoroutineScheduler
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import ru.wildberries.analytics.TestScopeFactory
import ru.wildberries.analytics.WBAnalytics2Logger
import ru.wildberries.analytics.config.ConfigRepository
import ru.wildberries.analytics.config.WBA2Config

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class)
internal class WBAnalytics2SenderServiceTest : BehaviorSpec() {

    init {
        // Почему-то тестовый скоуп работат только если его включть так.
        coroutineTestScope = true

        lateinit var log: WBAnalytics2Logger

        Given("Dao and events") {
            val config = WBA2Config.Companion.Default
            val configRepository = mockk<ConfigRepository> {
                every { this@mockk.config } returns MutableStateFlow(config)
            }

            val sendStrategy = mockk<SendStrategy>(relaxUnitFun = true)
            val sendStrategyProvider = mockk<SendStrategyProvider> {
                every { strategy } returns flowOf(sendStrategy)
            }

            log = mockk(relaxed = true)
            `when`("Start service") {
                WBAnalytics2SenderService(
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