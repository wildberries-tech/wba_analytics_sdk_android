package ru.wildanalytics.pub.analytics.event

import androidx.room.invalidationTrackerFlow
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import ru.wildanalytics.pub.analytics.db.AnalyticsEventsDao
import ru.wildanalytics.pub.analytics.db.EventEntity
import ru.wildanalytics.pub.analytics.db.WildAnalyticsDatabase
import ru.wildanalytics.pub.analytics.domain.ApiData
import ru.wildanalytics.pub.analytics.logDebug
import ru.wildanalytics.pub.analytics.util.CloseableSequence

internal class EventsRepositoryImpl(
    private val eventsDao: AnalyticsEventsDao,
    private val db: WildAnalyticsDatabase,
    private val log: WildAnalyticsLogger,
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

    override suspend fun deleteOldestEventsThatExceed(allowedEventsInCacheCount: Int) {
        db.withTransaction {
            val count = eventsDao.getCount()
            val exceededCount = count - allowedEventsInCacheCount
            if (exceededCount > 0) {
                log.logDebug { "Events in database($count) more than allowed($allowedEventsInCacheCount). Remove first exceeded($exceededCount)" }
                val removedCount = eventsDao.deleteEvents(exceededCount)
                log.logDebug { "Removed $removedCount exceeded events" }
            }
        }
    }
}
