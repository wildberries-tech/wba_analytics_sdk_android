package ru.wildanalytics.pub.analytics.sdk.demo.sdk

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class QueuePollerTest {

    @Test
    fun readErrorKeepsLastValueInsteadOfEmptyQueue() = runTest {
        val reads = ArrayDeque<() -> QueueStats?>(
            listOf({ QueueStats(5, 1) }, { error("SQLITE_BUSY") }, { null })
        )

        val values = pollQueue(intervalMillis = 1) { reads.removeFirst()() }.take(2).toList()

        values shouldBe listOf(QueueStats(5, 1), null)
    }
}
