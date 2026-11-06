/**
 * Содержит функции, который необходимы для диагностики внутренних проблем аналитики.
 */

package ru.wildberries.pub.analytics

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import ru.wildberries.pub.analytics.api.Event
import java.io.OutputStream

@OptIn(ExperimentalSerializationApi::class)
internal fun guessSize(event: Event): Int {
    val counter = LengthCountingOutputStream()
    Json.encodeToStream(event, counter)
    return counter.length
}

internal fun findHeaviestEvent(apiEvents: List<Event>): Pair<Event, Int>? {
    return try {
        apiEvents
            .zip(apiEvents.map { guessSize(it) })
            .maxBy { (_, size) -> size }
    } catch (e: OutOfMemoryError) {
        // Если не смогли, то ничего не поделать...
        null
    }
}

private class LengthCountingOutputStream : OutputStream() {

    var length: Int = 0
        private set

    override fun write(b: Int) {
        length++
    }

    override fun write(b: ByteArray) {
        length += b.size
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        length += len
    }
}
