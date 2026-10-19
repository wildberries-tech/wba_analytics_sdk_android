package ru.wildanalytics.pub.analytics.sdk.demo.qa

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.sdk.demo.FakeKeyValueStore

class QaStoresTest {

    @Test
    fun resultsSurviveReload() {
        val kv = FakeKeyValueStore()
        ResultStore(kv).save(QaSuite.FALLBACK, "S1", ScenarioResult(Verdict.Status.PASSED, 19))

        ResultStore(kv).results.value shouldBe mapOf(ResultKey(QaSuite.FALLBACK, "S1") to ScenarioResult(Verdict.Status.PASSED, 19))
    }

    @Test
    fun corruptedValuesAreIgnored() {
        val kv = FakeKeyValueStore(
            mutableMapOf("FALLBACK:S1" to "BROKEN", "UNKNOWN:S2" to "PASSED:1", "LOSS:S3" to "FAILED:12")
        )

        ResultStore(kv).results.value shouldBe mapOf(ResultKey(QaSuite.LOSS, "S3") to ScenarioResult(Verdict.Status.FAILED, 12))
    }

    @Test
    fun clearOnlyAffectsSuite() {
        val store = ResultStore(FakeKeyValueStore())
        store.save(QaSuite.FALLBACK, "S1", ScenarioResult(Verdict.Status.PASSED, 1))
        store.save(QaSuite.LOSS, "S2", ScenarioResult(Verdict.Status.PASSED, 2))

        store.clear(QaSuite.FALLBACK)

        store.results.value.keys shouldBe setOf(ResultKey(QaSuite.LOSS, "S2"))
    }

    @Test
    fun pendingRunIsTakenOnce() {
        val store = PendingRunStore(FakeKeyValueStore())
        store.save(PendingRun(QaSuite.LOSS, scenario = null))

        store.take() shouldBe PendingRun(QaSuite.LOSS, scenario = null)
        store.take().shouldBeNull()
    }

    @Test
    fun corruptedPendingRunIsDropped() {
        PendingRunStore(FakeKeyValueStore(mutableMapOf("pending_run" to "NOPE"))).take().shouldBeNull()
    }
}
