package ru.wildanalytics.pub.analytics.event

import androidx.room.invalidationTrackerFlow
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import ru.wildanalytics.pub.analytics.db.AnalyticsEventsDao
import ru.wildanalytics.pub.analytics.db.EventEntity
import ru.wildanalytics.pub.analytics.db.OversizedEvent
import ru.wildanalytics.pub.analytics.db.WildAnalyticsDatabase
import ru.wildanalytics.pub.analytics.domain.ApiData
import ru.wildanalytics.pub.analytics.util.CloseableSequence

internal class EventsRepositoryImpl(
    private val eventsDao: AnalyticsEventsDao,
    private val db: WildAnalyticsDatabase,
) : EventsRepository {

    private val cursorMapper = EventSequenceFromCursorMapper()

    override suspend fun add(event: EventEntity) {
        eventsDao.add(event)
    }

    override suspend fun delete(ids: List<Int>) = eventsDao.delete(ids)

    override suspend fun getFirstEventApiDataOrNull(): ApiData? = eventsDao.getFirstApiData()

    override suspend fun awaitFirstApiData(): ApiData =
        db.invalidationTrackerFlow(EventEntity.TABLE_NAME)
            .mapNotNull { getFirstEventApiDataOrNull() }
            .first()

    override fun hasEventsFlow(): Flow<Boolean> = db.invalidationTrackerFlow(EventEntity.TABLE_NAME)
        .map { getFirstEventApiDataOrNull() != null }
        .distinctUntilChanged()

    override suspend fun getEventsSequence(
        apiData: ApiData,
        limit: Int
    ): CloseableSequence<EventEntity> =
        cursorMapper.mapToSequence(eventsDao.queryFirst(apiData.apiKey, apiData.apiUrl, limit))

    override suspend fun deleteOldestEventsThatExceed(allowedEventsInCacheCount: Int): EvictionResult? =
        db.withTransaction {
            val count = eventsDao.getCount()
            val exceededCount = count - allowedEventsInCacheCount
            if (exceededCount > 0) {
                val groups = eventsDao.getEvictionCandidates(exceededCount)
                val removedCount = eventsDao.deleteEvents(exceededCount)
                EvictionResult(
                    limit = allowedEventsInCacheCount,
                    sizeBefore = count,
                    removed = removedCount,
                    groups = groups,
                )
            } else {
                null
            }
        }

    override suspend fun deleteOversizedEvents(maxSizeInBytes: Long): List<OversizedEvent> =
        db.withTransaction {
            eventsDao.getOversizedEvents(maxSizeInBytes).also { oversized ->
                if (oversized.isNotEmpty()) {
                    eventsDao.delete(oversized.map { it.id })
                }
            }
        }
}
