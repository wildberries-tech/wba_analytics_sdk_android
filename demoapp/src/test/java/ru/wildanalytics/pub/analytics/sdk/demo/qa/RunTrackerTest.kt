package ru.wildanalytics.pub.analytics.sdk.demo.qa

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.sdk.demo.FakeKeyValueStore
import ru.wildanalytics.pub.analytics.sdk.demo.core.LogEntry
import ru.wildanalytics.pub.analytics.sdk.demo.core.RequestRecord

@OptIn(ExperimentalCoroutinesApi::class)
class RunTrackerTest {

    private val entries = MutableStateFlow<List<LogEntry>>(emptyList())
    private val requests = MutableStateFlow<List<RequestRecord>>(emptyList())
    private val results = ResultStore(FakeKeyValueStore())
    private var nextVerdict = Verdict(Verdict.Status.WAITING, 0)

    private fun TestScope.tracker() = RunTracker(
        suite = QaSuite.FALLBACK,
        entries = entries,
        requests = requests,
        results = results,
        scope = backgroundScope,
        now = { testScheduler.currentTime },
        evaluate = { _, _, _, _ -> nextVerdict },
    )

    @Test
    fun settledVerdictIsSavedOnceAndKept() = runTest {
        val tracker = tracker()
        tracker.start("S1")
        runCurrent()
        nextVerdict = Verdict(Verdict.Status.PASSED, 1)
        advanceTimeBy(2_000)
        nextVerdict = Verdict(Verdict.Status.FAILED, 2)
        advanceTimeBy(2_000)

        tracker.verdict.value?.status shouldBe Verdict.Status.PASSED
        results.results.value[ResultKey(QaSuite.FALLBACK, "S1")]?.status shouldBe Verdict.Status.PASSED
    }

    @Test
    fun newRunStartsWaitingAfterSettledRun() = runTest {
        val tracker = tracker()
        tracker.start("S1")
        nextVerdict = Verdict(Verdict.Status.PASSED, 1)
        advanceTimeBy(2_000)

        nextVerdict = Verdict(Verdict.Status.WAITING, 0)
        tracker.start("S1")
        advanceTimeBy(2_000)

        tracker.verdict.value?.status shouldBe Verdict.Status.WAITING
    }

    @Test
    fun stopClearsActiveRun() = runTest {
        val tracker = tracker()
        tracker.start("S1")
        runCurrent()
        tracker.stop()
        runCurrent()

        tracker.active.value shouldBe null
        tracker.verdict.value shouldBe null
    }
}
