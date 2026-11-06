package ru.wildberries.pub.analytics

import io.kotest.core.spec.style.BehaviorSpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.utf8Size
import ru.wildberries.pub.analytics.api.Event
import java.time.OffsetDateTime
import kotlin.test.assertEquals

internal class DiagnosticsTest : BehaviorSpec({

    Given("Simple Event and Json") {
        val event = Event(
            name = "hello",
            sessionValue = 123u,
            time = OffsetDateTime.MIN,
            eventNumber = 1u,
            data = mapOf(
                "abc" to "1",
                "def" to "world",
            )
        )
        val json = Json.encodeToString(event)

        When("Compute sizes") {
            val size = guessSize(event)
            val jsonSize = json.utf8Size().toInt()

            Then("Size from guessSize is same as utf8Size") {
                assertEquals(jsonSize, size)
            }
        }
    }
})
