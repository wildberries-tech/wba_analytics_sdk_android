package ru.wildberries.pub.analytics.event

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.wildberries.pub.analytics.db.EventEntity
import java.lang.reflect.Modifier

internal class EventEntityStatementMapperTest {

    @Test
    fun `all declared EventEntity fields match expected columns in statement mapper`() {
        val entityFieldNames = EventEntity::class.java.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && !it.name.startsWith("$") }
            .map { it.name }
            .toSet()

        assertEquals(
            setOf(
                "id",
                "apiUrl",
                "apiKey",
                "name",
                "sessionValue",
                "time",
                "extras",
                "importance",
            ),
            entityFieldNames,
            "EventEntity fields have changed! If you added, removed or renamed a field in EventEntity, " +
                "you must update EventSequenceFromStatementMapper.EXPECTED_COLUMNS and its mapping logic."
        )
    }

    @Test
    fun `empty statement produces empty sequence`() {
        val statement = mockk<androidx.sqlite.SQLiteStatement>(relaxed = true) {
            every { step() } returns false
        }

        val mapper = EventSequenceFromStatementMapper()
        val sequence = mapper.mapToSequence(statement)

        assertEquals(0, sequence.toList().size)
    }
}
