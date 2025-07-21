package ru.wildberries.analytics.event

import ru.wildberries.analytics.db.EventEntity
import ru.wildberries.analytics.domain.ApiData
import ru.wildberries.analytics.util.CloseableSequence

internal interface EventsRepository {

    suspend fun add(event: EventEntity)

    suspend fun delete(ids: List<Int>)

    suspend fun getFirstEventApiDataOrNull(): ApiData?

    suspend fun awaitFirstApiData(): ApiData

    suspend fun getEventsSequence(apiData: ApiData, limit: Int): CloseableSequence<EventEntity>

    suspend fun deleteOldestEventsThatExceed(allowedEventsInCacheCount: Int)
}
