package ru.wildanalytics.pub.analytics.utils

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.wildanalytics.pub.analytics.ApiKey
import ru.wildanalytics.pub.analytics.ApiUrl
import ru.wildanalytics.pub.analytics.db.EventEntity
import ru.wildanalytics.pub.analytics.testDate
import java.util.concurrent.atomic.AtomicInteger

internal object EventsFactory {

    private val lastId = AtomicInteger()

    fun createEntities(count: Int, apiUrl: ApiUrl = "apiUrl", apiKey: ApiKey = "apiKey"): List<EventEntity> = (1..count)
        .map { createEvent(apiUrl, apiKey) }

    private fun createEvent(
        apiUrl: ApiUrl,
        apiKey: ApiKey,
        id: Int = lastId.incrementAndGet()
    ) = EventEntity(
        id = id,
        apiUrl = apiUrl,
        apiKey = apiKey,
        name = "event$id",
        time = testDate(),
        extras = JsonObject(mapOf("param1" to JsonPrimitive(id)))
    )
}
