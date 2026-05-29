package ru.wildanalytics.pub.analytics.batch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import ru.wildanalytics.pub.analytics.api.BatchModel
import ru.wildanalytics.pub.analytics.api.Event
import ru.wildanalytics.pub.analytics.db.SentInfoDao
import ru.wildanalytics.pub.analytics.db.SentInfoEntity
import ru.wildanalytics.pub.analytics.db.TransactionRunner
import ru.wildanalytics.pub.analytics.device.MetadataCollector
import ru.wildanalytics.pub.analytics.domain.ApiData
import ru.wildanalytics.pub.analytics.event.EventsRepository
import ru.wildanalytics.pub.analytics.logError
import ru.wildanalytics.pub.analytics.util.ContentLengthCountingStream
import ru.wildanalytics.pub.analytics.util.toServerModel

internal class BatchRepositoryImpl(
    private val metadataCollector: MetadataCollector,
    private val sentInfoDao: SentInfoDao,
    private val eventsRepository: EventsRepository,
    private val transactionRunner: TransactionRunner,
    private val log: WildAnalyticsLogger
) : BatchRepository {

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun getBatch(apiData: ApiData, config: BatchingConfig): BatchData? {
        val sentInfo = getSentInfo()
        return fulfillBatch(sentInfo, apiData, config)
    }

    private suspend fun getSentInfo(): SentInfoEntity = transactionRunner.withTransaction {
        sentInfoDao.getSentInfo()
            ?: SentInfoEntity()
                .also { sentInfoDao.upsert(it) }
    }

    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    private suspend fun fulfillBatch(
        sentInfo: SentInfoEntity,
        apiData: ApiData,
        config: BatchingConfig
    ): BatchData? = withContext(Dispatchers.IO) {
        val meta = metadataCollector.collect().toServerModel(sentInfo.batchesSent + 1)
        val countingStream = ContentLengthCountingStream()
        Json.encodeToStream(BatchModel(meta, emptyList()), countingStream)
        val eventsToSent = mutableListOf<Event>()
        val eventIds = mutableListOf<Int>()
        val comma = ",".encodeToByteArray()
        var contentLength = countingStream.length
        val firstEventNum = sentInfo.eventsSent + 1

        var maxSizeEvent = "" to 0L
        eventsRepository.getEventsSequence(apiData, config.maxEventsInBatch).use { sequence ->
            for (eventEntity in sequence) {
                val event = eventEntity.toServerModel(firstEventNum + eventsToSent.size)
                Json.encodeToStream(event, countingStream)
                if (countingStream.length >= config.maxBatchSizeInBites && eventsToSent.isNotEmpty()) {
                    logBatchSizeExceeded(
                        eventsToSent.size,
                        maxSizeEvent,
                        config.maxBatchSizeInBites,
                    )
                    break
                }

                //region Поиск тяжёлых событий
                val eventSize = countingStream.length - contentLength
                if (eventSize > maxSizeEvent.second) {
                    maxSizeEvent = eventEntity.name to eventSize
                }
                //endregion

                contentLength = countingStream.length
                eventsToSent.add(event)
                eventIds.add(eventEntity.id)
                countingStream.write(comma)
            }
        }

        if (eventsToSent.isNotEmpty()) {
            BatchData(
                contentLength = contentLength,
                apiData = apiData,
                model = BatchModel(meta, eventsToSent),
                eventIds = eventIds,
                heaviestEvent = maxSizeEvent,
            )
        } else {
            null
        }
    }

    private fun logBatchSizeExceeded(
        count: Int,
        maxSizeEvent: Pair<String, Long>,
        maxBatchSizeInBites: Int
    ) {
        log.logError {
            error(Error("Batch size exceeded $maxBatchSizeInBites bytes"))
            detail("count", count.toString())
            detail("heaviest", maxSizeEvent.first)
            detail("heaviestSize", maxSizeEvent.second.toString())
        }
    }

    override suspend fun batchSent(eventIds: List<Int>) {
        transactionRunner.withTransaction {
            eventsRepository.delete(eventIds)
            val sentInfo = sentInfoDao.getSentInfo() ?: SentInfoEntity()
            sentInfoDao.upsert(sentInfo.incrementSentInfo(eventIds.size))
        }
    }

    override suspend fun dropBatch(eventIds: List<Int>) {
        eventsRepository.delete(eventIds)
    }
}

