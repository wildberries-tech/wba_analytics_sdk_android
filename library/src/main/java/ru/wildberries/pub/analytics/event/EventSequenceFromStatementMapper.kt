package ru.wildberries.pub.analytics.event

import androidx.sqlite.SQLiteStatement
import ru.wildberries.pub.analytics.db.EventEntity
import ru.wildberries.pub.analytics.db.KeyValueConverter
import ru.wildberries.pub.analytics.db.OffsetDateTimeConverter

internal class EventSequenceFromStatementMapper {
    fun mapToSequence(statement: SQLiteStatement): Sequence<EventEntity> = EventsStatementSequence(statement)
}

private class EventsStatementSequence(
    private val statement: SQLiteStatement,
) : Sequence<EventEntity> {

    override fun iterator(): Iterator<EventEntity> = if (statement.step()) {
        val columnCount = statement.getColumnCount()
        val columnIndices = (0 until columnCount).associate { statement.getColumnName(it) to it }

        fun getIndexOrThrow(columnName: String): Int =
            columnIndices[columnName]
                ?: throw IllegalArgumentException(
                    "Column '$columnName' does not exist. Available columns: ${columnIndices.keys}"
                )

        val id = getIndexOrThrow("id")
        val apiUrl = getIndexOrThrow("apiUrl")
        val apiKey = getIndexOrThrow("apiKey")
        val name = getIndexOrThrow("name")
        val sessionValue = getIndexOrThrow("sessionValue")
        val time = getIndexOrThrow("time")
        val extras = getIndexOrThrow("extras")
        val importance = getIndexOrThrow("importance")
        val timeConverter = OffsetDateTimeConverter()
        val extrasConverter = KeyValueConverter()

        iterator {
            do {
                yield(
                    EventEntity(
                        id = statement.getLong(id).toInt(),
                        apiUrl = statement.getText(apiUrl),
                        apiKey = statement.getText(apiKey),
                        name = statement.getText(name),
                        sessionValue = statement.getLong(sessionValue).toULong(),
                        time = timeConverter.toDate(statement.getText(time)),
                        extras = extrasConverter.fromString(statement.getText(extras)),
                        importance = statement.getLong(importance).toInt(),
                    )
                )
            } while (statement.step())
        }
    } else {
        object : Iterator<EventEntity> {
            override fun hasNext(): Boolean = false

            override fun next(): EventEntity {
                throw NoSuchElementException()
            }
        }
    }
}
