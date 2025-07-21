package ru.wildberries.analytics.db

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal class KeyValueConverter {

    @TypeConverter
    fun toString(src: JsonObject): String {
        return Json.encodeToString(src)
    }

    @TypeConverter
    fun fromString(src: String): JsonObject {
        return Json.decodeFromString(src)
    }
}
