package ru.wildanalytics.pub.analytics.sdk.demo.qa.loss

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.sdk.demo.FakeAnalyticsRepository
import ru.wildanalytics.pub.analytics.sdk.demo.FakeKeyValueStore
import ru.wildanalytics.pub.analytics.sdk.demo.R
import ru.wildanalytics.pub.analytics.sdk.demo.core.LogEntry
import ru.wildanalytics.pub.analytics.sdk.demo.core.RequestRecord
import ru.wildanalytics.pub.analytics.sdk.demo.qa.QaSuite
import ru.wildanalytics.pub.analytics.sdk.demo.qa.ResultStore
import ru.wildanalytics.pub.analytics.sdk.demo.qa.RunTracker
import ru.wildanalytics.pub.analytics.sdk.demo.qa.Verdict
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.QueueStats

@OptIn(ExperimentalCoroutinesApi::class)
class LossControllerTest {

    private val queue = MutableStateFlow<QueueStats?>(QueueStats(total = 10, important = 0))

    private fun TestScope.controller() = LossController(
        repository = { FakeAnalyticsRepository() },
        closeDatabase = {},
        queueStats = queue,
        restart = {},
        tracker = RunTracker(
            suite = QaSuite.LOSS,
            entries = MutableStateFlow<List<LogEntry>>(emptyList()),
            requests = MutableStateFlow<List<RequestRecord>>(emptyList()),
            results = ResultStore(FakeKeyValueStore()),
            scope = backgroundScope,
            now = { testScheduler.currentTime },
            evaluate = { _, _, _, _ -> Verdict(Verdict.Status.WAITING, 0) },
        ),
        scope = backgroundScope,
    )

    @Test
    fun runWhileBusyReportsMessage() = runTest {
        val controller = controller()
        controller.run(LossScenario.SEND_LOOP_RESTART)
        runCurrent()

        controller.run(LossScenario.QUEUE_EVICTION)

        controller.message.value shouldBe R.string.qa_loss_message_busy
        controller.tracker.active.value shouldBe null
    }
}
