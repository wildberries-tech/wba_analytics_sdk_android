package ru.wildanalytics.pub.analytics.event

import android.database.Cursor
import ru.wildanalytics.pub.analytics.db.EventEntity
import ru.wildanalytics.pub.analytics.db.KeyValueConverter
import ru.wildanalytics.pub.analytics.db.OffsetDateTimeConverter
import ru.wildanalytics.pub.analytics.util.CloseableSequence

internal class EventSequenceFromCursorMapper {
    fun mapToSequence(cursor: Cursor): CloseableSequence<EventEntity> = EventsSequence(cursor)
}

private class EventsSequence(private val cursor: Cursor) : CloseableSequence<EventEntity> {

    override fun iterator(): Iterator<EventEntity> = if (cursor.moveToFirst()) {
        val id = cursor.getColumnIndexOrThrow("id")
        val apiUrl = cursor.getColumnIndexOrThrow("apiUrl")
        val apiKey = cursor.getColumnIndexOrThrow("apiKey")
        val name = cursor.getColumnIndexOrThrow("name")
        val time = cursor.getColumnIndexOrThrow("time")
        val extras = cursor.getColumnIndexOrThrow("extras")
        val importance = cursor.getColumnIndexOrThrow("importance")
        val timeConverter = OffsetDateTimeConverter()
        val extrasConverter = KeyValueConverter()

        iterator {
            do {
                yield(
                    EventEntity(
                        id = cursor.getInt(id),
                        apiUrl = cursor.getString(apiUrl),
                        apiKey = cursor.getString(apiKey),
                        name = cursor.getString(name),
                        time = timeConverter.toDate(cursor.getString(time)),
                        extras = extrasConverter.fromString(cursor.getString(extras)),
                        importance = cursor.getInt(importance),
                    )
                )
            } while (cursor.moveToNext())
        }
    } else {
        object : Iterator<EventEntity> {
            override fun hasNext(): Boolean = false

            override fun next(): EventEntity {
                throw NoSuchElementException()
            }
        }
    }

    override fun close() {
        cursor.close()
    }
}
