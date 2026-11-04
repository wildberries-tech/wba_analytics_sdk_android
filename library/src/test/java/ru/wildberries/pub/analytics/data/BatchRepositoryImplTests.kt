package ru.wildberries.pub.analytics.data

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import ru.wildberries.pub.analytics.MAX_EVENT_EXTRAS_SIZE_IN_BYTES
import ru.wildberries.pub.analytics.TestTransactionRunner
import ru.wildberries.pub.analytics.api.BatchModel
import ru.wildberries.pub.analytics.api.Event
import ru.wildberries.pub.analytics.batch.BatchRepositoryImpl
import ru.wildberries.pub.analytics.batch.BatchingConfig
import ru.wildberries.pub.analytics.config.WBA2Config
import ru.wildberries.pub.analytics.db.EventEntity
import ru.wildberries.pub.analytics.db.OversizedEvent
import ru.wildberries.pub.analytics.db.SentInfoDao
import ru.wildberries.pub.analytics.db.SentInfoEntity
import ru.wildberries.pub.analytics.db.TransactionRunner
import ru.wildberries.pub.analytics.device.MetadataCollector
import ru.wildberries.pub.analytics.domain.ApiData
import ru.wildberries.pub.analytics.event.EventsRepository
import ru.wildberries.pub.analytics.testMeta
import ru.wildberries.pub.analytics.testMetaInfo
import ru.wildberries.pub.analytics.util.ContentLengthCountingStream
import ru.wildberries.pub.analytics.util.toServerModel
import ru.wildberries.pub.analytics.utils.EventsFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Suppress("Unused")
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

    Given("Reading events fails because of an oversized row") {
        val eventEntities: List<EventEntity> = EventsFactory.createEntities(2)
        val oversizedEvent = OversizedEvent(id = 100, name = "begin_checkout", size = 3_000_000)
        val sentInfoDao = createSentDao()
        var firstCall = true
        val eventsRepository: EventsRepository = mockk {
            coEvery { useEventsSequence<Any?>(any(), any(), any()) }.answers {
                if (firstCall) {
                    firstCall = false
                    error("Row too big to fit into CursorWindow")
                } else {
                    val block = thirdArg<(Sequence<EventEntity>) -> Any?>()
                    block(eventEntities.asSequence())
                }
            }
            coEvery { deleteOversizedEvents(any()) } returns listOf(oversizedEvent)
        }
        val subject = createBatchRepository(eventsRepository, sentInfoDao)

        When("Batch requested") {
            val batch = subject.getBatch(apiData, WBA2Config.Default.batching)

            Then("Oversized events are removed and batch is read again") {
                coVerify(exactly = 1) { eventsRepository.deleteOversizedEvents(MAX_EVENT_EXTRAS_SIZE_IN_BYTES) }
                assertNotNull(batch)
                assertEquals(eventEntities.map { it.id }, batch.eventIds)
            }
        }
    }

    Given("Reading events fails and there are no oversized rows") {
        val error = IllegalStateException("disk I/O error")
        val eventsRepository: EventsRepository = mockk {
            coEvery { useEventsSequence<Any?>(any(), any(), any()) } throws error
            coEvery { deleteOversizedEvents(any()) } returns emptyList()
        }
        val subject = createBatchRepository(eventsRepository)

        When("Batch requested") {
            val thrown = runCatching { subject.getBatch(apiData, WBA2Config.Default.batching) }.exceptionOrNull()

            Then("Original error is propagated without retry") {
                assertEquals(error.message, thrown?.message)
                coVerify(exactly = 1) { eventsRepository.useEventsSequence<Any?>(any(), any(), any()) }
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
    val eventsRepository: EventsRepository = mockk {
        coEvery { useEventsSequence<Any?>(any(), any(), any()) }.answers {
            val limit = secondArg<Int>()
            val block = thirdArg<(Sequence<EventEntity>) -> Any?>()
            block(eventEntities.take(limit).asSequence())
        }
    }
    return createBatchRepository(eventsRepository, sentInfoDao)
}

private fun createBatchRepository(
    eventsRepository: EventsRepository,
    sentInfoDao: SentInfoDao = createSentDao(),
): BatchRepositoryImpl {
    val meta = testMetaInfo()
    val metaCollector: MetadataCollector = mockk {
        coEvery { collect(any()) } returns meta
    }
    val transactionRunner: TransactionRunner = TestTransactionRunner()
    return BatchRepositoryImpl(
        metadataCollector = metaCollector,
        sentInfoDao = sentInfoDao,
        eventsRepository = eventsRepository,
        transactionRunner = transactionRunner,
        log = mockk(relaxed = true)
    )
}

private fun List<EventEntity>.mapToEvents(startEventNum: Long): List<Event> =
    mapIndexed { index, eventEntity -> eventEntity.toServerModel(startEventNum + index) }

private fun createSentDao() = object : SentInfoDao {
    var sentInfo = SentInfoEntity()
    override suspend fun getSentInfo(): SentInfoEntity = sentInfo

    override suspend fun upsert(info: SentInfoEntity) {
        sentInfo = info
    }
}

@OptIn(ExperimentalSerializationApi::class)
private val BatchModel.contentLength: Int
    get() = ContentLengthCountingStream().use { stream ->
        Json.encodeToStream(this, stream)
        stream.length.toInt()
    }
