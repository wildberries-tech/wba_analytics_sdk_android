package ru.wildberries.analytics.send

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.wildberries.analytics.util.ProcessForegroundData
import ru.wildberries.analytics.utils.TestLogger
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class SendStrategyProviderTests {

    lateinit var subject: SendStrategyProvider
    val networkAvailabilityFlow = MutableSharedFlow<Boolean>(replay = 1)
    val processForegroundDataFlow = MutableSharedFlow<ProcessForegroundData>(replay = 1)

    @BeforeEach
    fun setUp() {
        subject = SendStrategyProvider(
            eventsRepository = mockk(),
            log = TestLogger(),
            networkAvailabilitySource = mockk() {
                every { availabilityFlow } answers { networkAvailabilityFlow.distinctUntilChanged() }
            },
            sendOperation = mockk(),
            sendOperationScheduler = mockk(),
            processForegroundDataProvider = mockk() {
                every { processForegroundData } answers { processForegroundDataFlow.distinctUntilChanged() }
            },
        )
    }

    @Test
    fun provideImmediateStrategyOnForeground() = runTest {
        val strategies = collectStrategies()
        moveToForeground()
        turnOnNet()
        turnOffNet()
        turnOnNet()
        turnOffNet()
        assertEquals(1, strategies.size)
        assert(strategies.last() is ImmediateSendStrategy)
    }

    @Test
    fun awaitNetBlockingBeforeSchedulingWhenMovedToBackground() = runTest {
        val strategies = collectStrategies()
        moveToForeground()
        turnOnNet()
        moveToBackground()
        assertEquals(1, strategies.size)
        assert(strategies.last() is ImmediateSendStrategy)
        turnOffNet()
        assertEquals(2, strategies.size)
        assert(strategies.last() is SchedulingSendStrategy)
    }

    @Test
    fun provideSchedulingStrategyIfBackgroundOnly() = runTest {
        val strategies = collectStrategies()
        moveToBackground(isFromForeground = false)
        turnOnNet()
        turnOffNet()
        turnOnNet()
        turnOffNet()
        assertEquals(1, strategies.size)
        assert(strategies.last() is SchedulingSendStrategy)
    }

    private fun TestScope.collectStrategies(): List<SendStrategy> {
        val strategies = mutableListOf<SendStrategy>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            subject.strategy.toList(strategies)
        }
        return strategies
    }

    private suspend fun moveToForeground() {
        processForegroundDataFlow.emit(
            ProcessForegroundData(
                isInForeground = true,
                isFromForeground = false
            )
        )
    }

    private suspend fun moveToBackground(isFromForeground: Boolean = true) {
        processForegroundDataFlow.emit(
            ProcessForegroundData(
                isInForeground = false,
                isFromForeground = isFromForeground,
            )
        )
    }

    private suspend fun turnOnNet() {
        networkAvailabilityFlow.emit(true)
    }

    private suspend fun turnOffNet() {
        networkAvailabilityFlow.emit(false)
    }
}