package ru.wildberries.pub.analytics.event

import androidx.room.invalidationTrackerFlow
import androidx.room.useReaderConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import ru.wildberries.pub.analytics.db.AnalyticsEventsDao
import ru.wildberries.pub.analytics.db.EventEntity
import ru.wildberries.pub.analytics.db.OversizedEvent
import ru.wildberries.pub.analytics.db.TransactionRunner
import ru.wildberries.pub.analytics.db.WBAnalytics2Database
import ru.wildberries.pub.analytics.domain.ApiData

internal class EventsRepositoryImpl(
    private val eventsDao: AnalyticsEventsDao,
    private val db: WBAnalytics2Database,
    private val transactionRunner: TransactionRunner,
) : EventsRepository {

    private val statementMapper = EventSequenceFromStatementMapper()

    override suspend fun add(event: EventEntity) {
        eventsDao.add(event)
    }

    override suspend fun delete(ids: List<Int>) = eventsDao.delete(ids)

    override suspend fun getFirstEventApiDataOrNull(): ApiData? = eventsDao.getFirstApiData()

    override suspend fun awaitFirstApiData(): ApiData =
        db.invalidationTrackerFlow(EventEntity.TABLE_NAME)
            .mapNotNull { getFirstEventApiDataOrNull() }
            .first()

    override fun hasEventsFlow(): Flow<Boolean> = db.invalidationTracker
        .createFlow(EventEntity.TABLE_NAME)
        .map { getFirstEventApiDataOrNull() != null }
        .distinctUntilChanged()

    override suspend fun <R> useEventsSequence(
        apiData: ApiData,
        limit: Int,
        block: (Sequence<EventEntity>) -> R,
    ): R = db.useReaderConnection { transactor ->
        transactor.usePrepared(
            "SELECT * FROM EventEntity WHERE apiKey = ? AND apiUrl = ? ORDER BY importance DESC, id ASC LIMIT ?"
        ) { statement ->
            statement.bindText(1, apiData.apiKey)
            statement.bindText(2, apiData.apiUrl)
            statement.bindLong(3, limit.toLong())

            val sequence = statementMapper.mapToSequence(statement)
            block(sequence)
        }
    }

    override suspend fun deleteOldestEventsThatExceed(allowedEventsInCacheCount: Int): EvictionResult? =
        transactionRunner.withWriteTransaction {
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
        transactionRunner.withWriteTransaction {
            eventsDao.getOversizedEvents(maxSizeInBytes).also { oversized ->
                if (oversized.isNotEmpty()) {
                    eventsDao.delete(oversized.map { it.id })
                }
            }
        }
}
