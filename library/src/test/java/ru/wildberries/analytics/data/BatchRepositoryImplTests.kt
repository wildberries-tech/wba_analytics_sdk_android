package ru.wildberries.analytics.data

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import ru.wildberries.analytics.TestTransactionRunner
import ru.wildberries.analytics.api.BatchModel
import ru.wildberries.analytics.api.Event
import ru.wildberries.analytics.batch.BatchRepositoryImpl
import ru.wildberries.analytics.batch.BatchingConfig
import ru.wildberries.analytics.config.WBA2Config
import ru.wildberries.analytics.db.EventEntity
import ru.wildberries.analytics.db.SentInfoDao
import ru.wildberries.analytics.db.SentInfoEntity
import ru.wildberries.analytics.db.TransactionRunner
import ru.wildberries.analytics.device.MetadataCollector
import ru.wildberries.analytics.domain.ApiData
import ru.wildberries.analytics.event.EventsRepository
import ru.wildberries.analytics.testMeta
import ru.wildberries.analytics.testMetaInfo
import ru.wildberries.analytics.util.CloseableSequence
import ru.wildberries.analytics.util.ContentLengthCountingStream
import ru.wildberries.analytics.util.toServerModel
import ru.wildberries.analytics.utils.EventsFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(InternalSerializationApi::class)
internal class BatchRepositoryImplTests : BehaviorSpec({

    val apiData = ApiData(apiUrl = "apiUrl", apiKey = "apiKey")

    Given("Events more then maxEventsInBatch And less then maxBatchSizeInBites") {

        val sentInfoDao = createSentDao()
        val config: BatchingConfig = WBA2Config.Default.batching.copy(maxEventsInBatch = 2)
        val eventEntities: List<EventEntity> = EventsFactory.createEntities(config.maxEventsInBatch)

        val subject = createBatchRepository(eventEntities, sentInfoDao)

        When("Batch requested") {
            val batch = subject.getBatch(apiData, config)
            assertNotNull(batch)

            Then("Batch contains all events from portion") {
                val expectedEvents = eventEntities.mapToEvents(sentInfoDao.sentInfo.eventsSent + 1)
                assertEquals(expectedEvents, batch.model.events)
            }
        }
    }

    Given("Events less then maxEventsInBatch And less then maxBatchSizeInBites") {
        val config: BatchingConfig = WBA2Config.Default.batching.copy(
            maxEventsInBatch = 3,
            maxBatchSizeInBites = Int.MAX_VALUE
        )
        val sentInfoDao = createSentDao()
        val eventEntities: List<EventEntity> =
            EventsFactory.createEntities(config.maxEventsInBatch - 1)

        val subject = createBatchRepository(eventEntities, sentInfoDao)

        When("Batch requested") {
            val batch = subject.getBatch(apiData, config)
            assertNotNull(batch)

            Then("Batch contains all events") {
                val expectedEvents = eventEntities.mapToEvents(sentInfoDao.sentInfo.eventsSent + 1)
                assertEquals(expectedEvents, batch.model.events)
            }
        }
    }

    Given("Events less then maxEventsInBatch And more then maxBatchSizeInBites") {
        val maxEventsInBatch = 5
        val eventEntities: List<EventEntity> = EventsFactory.createEntities(maxEventsInBatch - 1)
        val sentInfoDao = createSentDao()
        val subject = createBatchRepository(eventEntities, sentInfoDao)
        val sentInfo = sentInfoDao.sentInfo
        val events = eventEntities.mapToEvents(sentInfo.eventsSent + 1)
        val overlappedBatch =
            BatchModel(events = events.toList(), meta = testMeta(sentInfo.batchesSent + 1))
        val maxBatchSizeInBites = overlappedBatch.contentLength - 1
        val config = WBA2Config.Default.batching.copy(
            maxEventsInBatch = maxEventsInBatch,
            maxBatchSizeInBites = maxBatchSizeInBites
        )

        When("Batch requested") {
            val batch = subject.getBatch(apiData, config)
            assertNotNull(batch)

            Then("Content length less then maxBatchSizeInBites") {
                assert(batch.contentLength < config.maxBatchSizeInBites)
            }

            Then("Received data contains expected batch") {
                val expectedBatch =
                    overlappedBatch.copy(events = overlappedBatch.events.dropLast(1))
                assertEquals(expectedBatch, batch.model)
            }
        }
    }

    Given("No events in store") {
        val subject = createBatchRepository(emptyList())

        When("Batch requested") {
            val batch = subject.getBatch(apiData, WBA2Config.Default.batching)

            Then("Batch not received") {
                assertNull(batch)
            }
        }
    }
})

private fun createBatchRepository(
    eventEntities: List<EventEntity>,
    sentInfoDao: SentInfoDao = createSentDao()
): BatchRepositoryImpl {
    val meta = testMetaInfo()
    val metaCollector: MetadataCollector = mockk {
        every { collect() } returns meta
    }
    val transactionRunner: TransactionRunner = TestTransactionRunner()
    val eventsRepository: EventsRepository = mockk {
        coEvery { getEventsSequence(any(), any()) }.answers {
            val limit = secondArg<Int>()
            getEventSequence(eventEntities.take(limit))
        }
    }
    return BatchRepositoryImpl(
        metadataCollector = metaCollector,
        sentInfoDao = sentInfoDao,
        eventsRepository = eventsRepository,
        transactionRunner = transactionRunner,
        log = mockk(relaxed = true)
    )
}

@OptIn(InternalSerializationApi::class)
private fun List<EventEntity>.mapToEvents(startEventNum: Long): List<Event> =
    mapIndexed { index, eventEntity -> eventEntity.toServerModel(startEventNum + index) }

private fun getEventSequence(eventEntities: List<EventEntity>) =
    object : CloseableSequence<EventEntity> {
        override fun iterator(): Iterator<EventEntity> = eventEntities.iterator()

        override fun close() = Unit
    }

private fun createSentDao() = object : SentInfoDao {
    var sentInfo = SentInfoEntity()
    override suspend fun getSentInfo(): SentInfoEntity = sentInfo

    override suspend fun upsert(info: SentInfoEntity) {
        sentInfo = info
    }
}

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
private val BatchModel.contentLength: Int
    get() = ContentLengthCountingStream().use { stream ->
        Json.encodeToStream(this, stream)
        stream.length.toInt()
    }
