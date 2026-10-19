package ru.wildanalytics.pub.analytics.sdk.demo.core

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MarkerParserTest {

    @Test
    fun parsesMarkerAndAttributesFromMessage() {
        val parsed = MarkerParser.parse("[wba_send_failed] Send failed host=a.wb.ru, endpoint=primary, code=498")

        parsed.name shouldBe "wba_send_failed"
        parsed.text shouldBe "Send failed"
        parsed.attributes shouldBe mapOf("host" to "a.wb.ru", "endpoint" to "primary", "code" to "498")
    }

    @Test
    fun detailsOverrideMessageAttributes() {
        val parsed = MarkerParser.parse(
            message = "[wba_endpoint_fallback] Switched attempt=4",
            details = mapOf("attempt" to "3", "batch_number" to "7"),
        )

        parsed.attributes shouldBe mapOf("attempt" to "3", "batch_number" to "7")
    }

    @Test
    fun messageWithoutMarker() {
        val parsed = MarkerParser.parse("plain text")

        parsed.name.shouldBeNull()
        parsed.text shouldBe "plain text"
        parsed.attributes shouldBe emptyMap()
    }

    @Test
    fun shortNameDropsPrefix() {
        MarkerParser.shortName("wba_endpoint_fallback") shouldBe "endpoint_fallback"
    }
}
