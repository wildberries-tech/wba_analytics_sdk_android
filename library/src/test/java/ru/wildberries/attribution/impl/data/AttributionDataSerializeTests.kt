package ru.wildberries.attribution.impl.data

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(InternalSerializationApi::class)
internal class AttributionDataSerializeTests {

    @Test
    fun convertDtoToStringAndToBackToDtoTheSameAsInitial() {
        val dto = AttributionDataDto(
            counterId = "1000",
            link = "link",
            otherFields = mapOf(
                "key1" to JsonPrimitive("field1"),
                "key2" to JsonPrimitive("field2")
            )
        )
        val json = Json.encodeToString(dto)
        println(json)
        assertEquals(dto, Json.decodeFromString<AttributionDataDto>(json))
    }

    @Test
    fun convertStringToDtoAndBackToStringTheSameAsInitial() {
        val json = "{\"counterId\":\"1000\",\"link\":\"link\",\"key1\":\"field1\",\"key2\":\"field2\"}"
        val dto = Json.decodeFromString<AttributionDataDto>(json)
        assertEquals(json,Json.encodeToString(dto))
    }
}
