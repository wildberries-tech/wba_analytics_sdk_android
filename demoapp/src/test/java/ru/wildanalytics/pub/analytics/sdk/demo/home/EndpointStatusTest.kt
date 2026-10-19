package ru.wildanalytics.pub.analytics.sdk.demo.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.sdk.demo.logEntry
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsEndpointConfig

class EndpointStatusTest {

    private val config = AnalyticsEndpointConfig("https://a.wb.ru/m/batch", "https://afb.wb.ru/m/batch")

    @Test
    fun activeAfterFallbackMarkerForMainHost() {
        val entries = listOf(logEntry(42, "[wba_endpoint_fallback] Switched", mapOf("primary_host" to "a.wb.ru")))

        endpointStatus(config, entries) shouldBe EndpointStatus("a.wb.ru", "afb.wb.ru", switchedAtMillis = 42)
    }

    @Test
    fun inactiveWithoutMarkerOrForOtherHost() {
        val other = listOf(logEntry(42, "[wba_endpoint_fallback] Switched", mapOf("primary_host" to "x.wb.ru")))

        endpointStatus(config, emptyList()).isFallbackActive shouldBe false
        endpointStatus(config, other).isFallbackActive shouldBe false
    }

    @Test
    fun blankMainUsesDefaultAndBlankFallbackIsNull() {
        endpointStatus(AnalyticsEndpointConfig("", ""), emptyList()) shouldBe EndpointStatus("a.wb.ru", null, null)
    }
}
