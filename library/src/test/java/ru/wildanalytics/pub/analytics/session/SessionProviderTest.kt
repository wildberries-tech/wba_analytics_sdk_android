package ru.wildanalytics.pub.analytics.session

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import ru.wildanalytics.pub.analytics.util.IdGenerator
import ru.wildanalytics.pub.analytics.util.ProcessForegroundData
import ru.wildanalytics.pub.analytics.util.ProcessForegroundDataProvider
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
internal class SessionProviderTest : BehaviorSpec({
    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = TestScope(testDispatcher)

    Given("SessionProviderImpl with mocked dependencies") {
        val uuids = mutableListOf(
            UUID(0, 1),
            UUID(0, 2),
            UUID(0, 3),
            UUID(0, 4)
        )
        val idGenerator = object : IdGenerator {
            override fun generateId(): UUID = uuids.removeAt(0)
        }
        val flow = MutableSharedFlow<ProcessForegroundData>()
        val provider = mockk<ProcessForegroundDataProvider>()
        every { provider.processForegroundData } returns flow

        val sessionProvider = SessionProviderImpl(idGenerator, provider, testScope)

        Then("it should have initial session id") {
            sessionProvider.currentSessionValue shouldBe 1u
        }

        When("foreground data is emitted") {
            runTest(testDispatcher) {
                flow.emit(ProcessForegroundData(isInForeground = true, isFromForeground = false))

                Then("session id should be refreshed") {
                    sessionProvider.currentSessionValue shouldBe 2u
                }
            }
        }

        When("background data is emitted") {
            runTest(testDispatcher) {
                flow.emit(ProcessForegroundData(isInForeground = false, isFromForeground = true))

                Then("session id should be refreshed again") {
                    sessionProvider.currentSessionValue shouldBe 3u
                }
            }
        }
    }
})
