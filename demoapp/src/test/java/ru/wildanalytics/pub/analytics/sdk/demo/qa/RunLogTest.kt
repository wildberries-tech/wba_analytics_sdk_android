package ru.wildanalytics.pub.analytics.sdk.demo.qa

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.sdk.demo.logEntry
import ru.wildanalytics.pub.analytics.sdk.demo.request

class RunLogTest {

    private val run = ActiveRun(QaSuite.FALLBACK, scenario = "S1", startedAtMillis = 1_000)

    @Test
    fun mergesChronologicallyAndDropsOlderThanStart() {
        val items = RunLog.build(
            run = run,
            entries = listOf(logEntry(5_000, "[wba_endpoint_fallback] Switched", mapOf("attempt" to "3"))),
            requests = listOf(
                request(4_000, "a.wb.ru", error = "UnknownHostException"),
                request(500, "a.wb.ru", code = 200),
            ),
            aliases = mapOf("a.wb.ru" to "main"),
        )

        items.map { it.kind } shouldBe listOf(RunLogItem.Kind.FAILURE, RunLogItem.Kind.MARKER)
        items[0].text shouldBe "main UnknownHost"
        items[0].offsetSeconds shouldBe 3
        items[1].text shouldBe "endpoint_fallback"
        items[1].attributes shouldBe mapOf("attempt" to "3")
    }

    @Test
    fun requestBeforeMarkerAtSameMoment() {
        val items = RunLog.build(
            run = run,
            entries = listOf(logEntry(2_000, "[wba_send_failed] Failed")),
            requests = listOf(request(2_000, "afb.wb.ru", code = 200)),
            aliases = emptyMap(),
        )

        items.map { it.kind } shouldBe listOf(RunLogItem.Kind.SUCCESS, RunLogItem.Kind.MARKER)
    }

    @Test
    fun mockRequestsAreMarked() {
        val items = RunLog.build(run, emptyList(), listOf(request(2_000, "a.wb.ru", code = 500, isMock = true)), emptyMap())

        items.single().kind shouldBe RunLogItem.Kind.MOCK
        items.single().text shouldBe "a.wb.ru 500 mock"
    }

    @Test
    fun plainEntriesAreSkipped() {
        RunLog.build(run, listOf(logEntry(2_000, "plain text")), emptyList(), emptyMap()) shouldBe emptyList()
    }
}
