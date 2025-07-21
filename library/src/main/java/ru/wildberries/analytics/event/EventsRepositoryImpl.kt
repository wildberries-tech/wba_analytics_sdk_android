package ru.wildberries.analytics.event

import androidx.room.invalidationTrackerFlow
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import ru.wildberries.analytics.WBAnalytics2Logger
import ru.wildberries.analytics.db.AnalyticsEventsDao
import ru.wildberries.analytics.db.EventEntity
import ru.wildberries.analytics.db.WBAnalytics2Database
import ru.wildberries.analytics.domain.ApiData
import ru.wildberries.analytics.logDebug
import ru.wildberries.analytics.util.CloseableSequence

internal class EventsRepositoryImpl(
    private val eventsDao: AnalyticsEventsDao,
    private val db: WBAnalytics2Database,
    private val log: WBAnalytics2Logger,
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
