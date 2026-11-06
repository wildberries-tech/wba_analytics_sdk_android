package ru.wildberries.pub.analytics.utils

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.wildberries.pub.analytics.ApiKey
import ru.wildberries.pub.analytics.ApiUrl
import ru.wildberries.pub.analytics.db.EventEntity
import ru.wildberries.pub.analytics.testDate
import java.util.concurrent.atomic.AtomicInteger

internal object EventsFactory {

    private val lastId = AtomicInteger()

    fun createEntities(count: Int, apiUrl: ApiUrl = "apiUrl", apiKey: ApiKey = "apiKey"): List<EventEntity> = (1..count)
        .map { createEvent(apiUrl, apiKey) }

    fun createEntity(
        id: Int,
        name: String,
        apiUrl: ApiUrl = "apiUrl",
        apiKey: ApiKey = "apiKey",
        importance: Int = EventEntity.IMPORTANCE_NORMAL,
    ): EventEntity = createEvent(apiUrl = apiUrl, apiKey = apiKey, id = id).copy(name = name, importance = importance)

    private fun createEvent(
        apiUrl: ApiUrl,
        apiKey: ApiKey,
        id: Int = lastId.incrementAndGet()
    ) = EventEntity(
        id = id,
        apiUrl = apiUrl,
        apiKey = apiKey,
        name = "event$id",
        sessionValue = id.toULong(),
        time = testDate(),
        extras = JsonObject(mapOf("param1" to JsonPrimitive(id)))
    )
}
