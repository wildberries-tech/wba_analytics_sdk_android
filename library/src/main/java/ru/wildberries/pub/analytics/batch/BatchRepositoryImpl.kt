package ru.wildberries.pub.analytics.batch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import ru.wildberries.pub.analytics.MAX_EVENT_EXTRAS_SIZE_IN_BYTES
import ru.wildberries.pub.analytics.WBAnalytics2Logger
import ru.wildberries.pub.analytics.api.BatchModel
import ru.wildberries.pub.analytics.api.Event
import ru.wildberries.pub.analytics.db.OversizedEvent
import ru.wildberries.pub.analytics.db.SentInfoDao
import ru.wildberries.pub.analytics.db.SentInfoEntity
import ru.wildberries.pub.analytics.db.TransactionRunner
import ru.wildberries.pub.analytics.device.MetadataCollector
import ru.wildberries.pub.analytics.domain.ApiData
import ru.wildberries.pub.analytics.domain.SendMode
import ru.wildberries.pub.analytics.event.EventsRepository
import ru.wildberries.pub.analytics.logError
import ru.wildberries.pub.analytics.logWarn
import ru.wildberries.pub.analytics.util.ContentLengthCountingStream
import ru.wildberries.pub.analytics.util.toServerModel
import kotlin.coroutines.cancellation.CancellationException

internal class BatchRepositoryImpl(
    private val metadataCollector: MetadataCollector,
    private val sentInfoDao: SentInfoDao,
    private val eventsRepository: EventsRepository,
    private val transactionRunner: TransactionRunner,
    private val log: WBAnalytics2Logger
) : BatchRepository {

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun getBatch(apiData: ApiData, config: BatchingConfig, sendMode: SendMode): BatchData? {
        val sentInfo = getSentInfo()
        return try {
            fulfillBatch(sentInfo, apiData, config, sendMode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val oversized = eventsRepository.deleteOversizedEvents(MAX_EVENT_EXTRAS_SIZE_IN_BYTES)
            if (oversized.isEmpty()) {
                throw e
            }
            oversized.forEach(::logOversizedEventRemoved)
            fulfillBatch(sentInfo, apiData, config, sendMode)
        }
    }

    private suspend fun getSentInfo(): SentInfoEntity {
        val existing = transactionRunner.withReadTransaction {
            sentInfoDao.getSentInfo()
        }
        return existing ?: transactionRunner.withWriteTransaction {
            sentInfoDao.getSentInfo()
                ?: SentInfoEntity().also { sentInfoDao.upsert(it) }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun fulfillBatch(
        sentInfo: SentInfoEntity,
        apiData: ApiData,
        config: BatchingConfig,
        sendMode: SendMode,
    ): BatchData? = withContext(Dispatchers.IO) {
        val meta = metadataCollector.collect(apiData.apiKey).toServerModel(sentInfo.batchesSent + 1, sendMode)
        val countingStream = ContentLengthCountingStream()
        Json.encodeToStream(BatchModel(meta, emptyList()), countingStream)
        val eventsToSent = mutableListOf<Event>()
        val eventIds = mutableListOf<Int>()
        val comma = ",".encodeToByteArray()
        var contentLength = countingStream.length
        val firstEventNum = sentInfo.eventsSent + 1

        var maxSizeEvent = "" to 0L
        eventsRepository.useEventsSequence(apiData, config.maxEventsInBatch) { sequence ->
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
        log.logWarn {
            "Batch size exceeded $maxBatchSizeInBites bytes: count=$count, " +
                "heaviest=${maxSizeEvent.first}, heaviestSize=${maxSizeEvent.second}"
        }
    }

    private fun logOversizedEventRemoved(event: OversizedEvent) {
        log.logError {
            error(Error("Oversized event removed from database"))
            detail("name", event.name)
            detail("size", event.size.toString())
        }
    }

    override suspend fun batchSent(eventIds: List<Int>) {
        transactionRunner.withWriteTransaction {
            eventsRepository.delete(eventIds)
            val sentInfo = sentInfoDao.getSentInfo() ?: SentInfoEntity()
            sentInfoDao.upsert(sentInfo.incrementSentInfo(eventIds.size))
        }
    }

    override suspend fun dropBatch(eventIds: List<Int>) {
        eventsRepository.delete(eventIds)
    }
}

