package ru.wildanalytics.pub.analytics.send.fallback

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.send.HttpStatusException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal class FallbackTriggerTest {

    private val trigger = FallbackTrigger()

    @Test
    fun `given internet, when third unreachable failure in a row, then triggers`() {
        // given
        val errors = listOf(UnknownHostException(), ConnectException(), SocketTimeoutException())

        // when
        val results = errors.map { trigger.onFailure(it) { true } }

        // then
        results shouldBe listOf(false, false, true)
    }

    @Test
    fun `given server errors and io errors, when three in a row, then they count as unreachable`() {
        // given
        val errors = listOf(HttpStatusException(500), HttpStatusException(503), IOException("reset"))

        // when
        val results = errors.map { trigger.onFailure(it) { true } }

        // then
        results.last() shouldBe true
    }

    @Test
    fun `given two failures, when response below 500 arrives, then streak resets`() {
        // given
        repeat(2) { trigger.onFailure(ConnectException()) { true } }

        // when
        trigger.onFailure(HttpStatusException(404)) { true }

        // then
        trigger.onFailure(ConnectException()) { true } shouldBe false
    }

    @Test
    fun `given two failures, when failure happens without internet, then streak resets`() {
        // given
        repeat(2) { trigger.onFailure(ConnectException()) { true } }

        // when
        trigger.onFailure(ConnectException()) { false }

        // then
        trigger.onFailure(ConnectException()) { true } shouldBe false
    }

    @Test
    fun `given internet, when non network errors repeat, then they do not trigger`() {
        // given
        val errors = listOf(IllegalStateException(), IllegalArgumentException(), IllegalStateException())

        // when
        val results = errors.map { trigger.onFailure(it) { true } }

        // then
        results shouldBe listOf(false, false, false)
    }
}
