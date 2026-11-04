package ru.wildberries.pub.analytics.event

import kotlinx.coroutines.flow.Flow
import ru.wildberries.pub.analytics.db.EventEntity
import ru.wildberries.pub.analytics.db.OversizedEvent
import ru.wildberries.pub.analytics.domain.ApiData

internal interface EventsRepository {

    suspend fun add(event: EventEntity)

    suspend fun delete(ids: List<Int>)

    suspend fun getFirstEventApiDataOrNull(): ApiData?

    suspend fun awaitFirstApiData(): ApiData

    fun hasEventsFlow(): Flow<Boolean>

    /**
     * Предоставляет потоковую последовательность событий [EventEntity].
     * Последовательность является одноразовой (one-shot) и валидна только внутри [block].
     * После завершения [block] ресурсы (statement и reader connection) немедленно освобождаются.
     */
    suspend fun <R> useEventsSequence(
        apiData: ApiData,
        limit: Int,
        block: (Sequence<EventEntity>) -> R,
    ): R

    suspend fun deleteOldestEventsThatExceed(allowedEventsInCacheCount: Int): EvictionResult?

    /**
     * Удаляет события, у которых размер extras в байтах больше [maxSizeInBytes].
     * Такие строки не помещаются в CursorWindow и блокируют чтение батча.
     * @return удалённые события.
     */
    suspend fun deleteOversizedEvents(maxSizeInBytes: Long): List<OversizedEvent>
}
