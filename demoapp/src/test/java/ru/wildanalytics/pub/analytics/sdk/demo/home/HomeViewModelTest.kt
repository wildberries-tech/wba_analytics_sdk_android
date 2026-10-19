package ru.wildanalytics.pub.analytics.sdk.demo.home

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import ru.wildanalytics.pub.analytics.sdk.demo.FakeAnalyticsRepository
import ru.wildanalytics.pub.analytics.sdk.demo.MainDispatcherExtension
import ru.wildanalytics.pub.analytics.sdk.demo.core.LogEntry
import ru.wildanalytics.pub.analytics.sdk.demo.core.RequestRecord
import ru.wildanalytics.pub.analytics.sdk.demo.logEntry
import ru.wildanalytics.pub.analytics.sdk.demo.qa.ActiveRun
import ru.wildanalytics.pub.analytics.sdk.demo.qa.QaSuite
import ru.wildanalytics.pub.analytics.sdk.demo.qa.ResultKey
import ru.wildanalytics.pub.analytics.sdk.demo.qa.ScenarioResult
import ru.wildanalytics.pub.analytics.sdk.demo.qa.Verdict
import ru.wildanalytics.pub.analytics.sdk.demo.request
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.QueueStats
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.TransportMode

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val repository = FakeAnalyticsRepository()
    private val queue = MutableStateFlow<QueueStats?>(null)
    private val entries = MutableStateFlow<List<LogEntry>>(emptyList())
    private val requests = MutableStateFlow<List<RequestRecord>>(emptyList())
    private val results = MutableStateFlow<Map<ResultKey, ScenarioResult>>(emptyMap())
    private val active = MutableStateFlow<List<ActiveRun>>(emptyList())

    private fun TestScope.viewModel(): HomeViewModel {
        val vm = HomeViewModel(
            repository = repository,
            queueStats = queue,
            entries = entries,
            requests = requests,
            results = results,
            periodicSeconds = MutableStateFlow(30),
            activeRuns = active,
            suiteSizes = mapOf(QaSuite.FALLBACK to 7, QaSuite.LOSS to 4),
        )
        backgroundScope.launch(main.dispatcher) { vm.state.collect {} }
        runCurrent()
        return vm
    }

    @Test
    fun missingDatabaseShowsNoQueue() = runTest(main.dispatcher) {
        viewModel().state.value.queue.shouldBeNull()
    }

    @Test
    fun exposesMockTransportAndPeriodic() = runTest(main.dispatcher) {
        repository.transportMode.value = TransportMode.HTTP_500
        val state = viewModel().state.value

        state.transportMode shouldBe TransportMode.HTTP_500
        state.periodicSeconds shouldBe 30
    }

    @Test
    fun summarizesLastSendMarkersAndQa() = runTest(main.dispatcher) {
        queue.value = QueueStats(total = 12, important = 3)
        requests.value = listOf(request(2, "afb.wb.ru", code = 200, id = 2), request(1, "a.wb.ru", error = "UnknownHostException", id = 1))
        entries.value = listOf(logEntry(1, "[wba_send_failed] x"), logEntry(2, "[wba_send_failed] y", id = 3))
        results.value = mapOf(
            ResultKey(QaSuite.FALLBACK, "A") to ScenarioResult(Verdict.Status.PASSED, 1),
            ResultKey(QaSuite.FALLBACK, "B") to ScenarioResult(Verdict.Status.FAILED, 1),
        )
        active.value = listOf(ActiveRun(QaSuite.LOSS, "X", 0))

        val state = viewModel().state.value

        state.queue shouldBe QueueStats(12, 3)
        state.lastSend?.host shouldBe "afb.wb.ru"
        state.markerCounts["wba_send_failed"] shouldBe 2
        state.qa.getValue(QaSuite.FALLBACK) shouldBe SuiteSummary(passed = 1, total = 7, active = null)
        state.qa.getValue(QaSuite.LOSS).active?.scenario shouldBe "X"
    }

    @Test
    fun sendEventLogsTemplate() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onSendEvent()
        runCurrent()

        repository.loggedEvents.single() shouldBe repository.eventsConfig
    }
}
