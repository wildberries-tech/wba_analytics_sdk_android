package ru.wildanalytics.pub.analytics.event

import kotlinx.coroutines.flow.Flow
import ru.wildanalytics.pub.analytics.db.EventEntity
import ru.wildanalytics.pub.analytics.db.OversizedEvent
import ru.wildanalytics.pub.analytics.domain.ApiData
import ru.wildanalytics.pub.analytics.util.CloseableSequence

internal interface EventsRepository {

    suspend fun add(event: EventEntity)

    suspend fun delete(ids: List<Int>)

    suspend fun getFirstEventApiDataOrNull(): ApiData?

    suspend fun awaitFirstApiData(): ApiData

    fun hasEventsFlow(): Flow<Boolean>

    suspend fun getEventsSequence(apiData: ApiData, limit: Int): CloseableSequence<EventEntity>

    suspend fun deleteOldestEventsThatExceed(allowedEventsInCacheCount: Int): EvictionResult?

    /**
     * Удаляет события, у которых размер extras в байтах больше [maxSizeInBytes].
     * Такие строки не помещаются в CursorWindow и блокируют чтение батча.
     * @return удалённые события.
     */
    suspend fun deleteOversizedEvents(maxSizeInBytes: Long): List<OversizedEvent>
}
