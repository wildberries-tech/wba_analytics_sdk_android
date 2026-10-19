package ru.wildanalytics.pub.analytics.sdk.demo.core

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RequestLogTest {

    private val log = RequestLog()

    @Test
    fun mockRecordKeepsHostAndFlag() {
        log.recordMock("https://a.wb.ru/m/batch", code = 500, error = null)

        val record = log.requests.value.single()
        record.host shouldBe "a.wb.ru"
        record.code shouldBe 500
        record.isMock shouldBe true
        record.isSuccessful shouldBe false
    }

    @Test
    fun newestFirstAndCapped() {
        repeat(MAX + 1) { log.recordMock("https://a.wb.ru/m/batch", code = 200, error = null) }

        log.requests.value shouldHaveSize MAX
        log.requests.value.first().id shouldBe (MAX + 1).toLong()
    }

    @Test
    fun clearRemovesRecords() {
        log.recordMock("https://a.wb.ru/m/batch", code = null, error = "IOException")
        log.clear()

        log.requests.value shouldBe emptyList()
    }

    private companion object {
        const val MAX = 300
    }
}
