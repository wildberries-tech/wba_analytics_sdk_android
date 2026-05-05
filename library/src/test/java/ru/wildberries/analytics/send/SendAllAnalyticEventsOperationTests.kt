package ru.wildberries.analytics.send

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.wildberries.analytics.NoOpWBAnalytics2Logger
import ru.wildberries.analytics.TestTransactionRunner
import ru.wildberries.analytics.api.BatchModel
import ru.wildberries.analytics.batch.BatchRepositoryImpl
import ru.wildberries.analytics.config.ConfigRepositoryImpl
import ru.wildberries.analytics.config.RetryPolicy
import ru.wildberries.analytics.data.InMemoryEventsRepository
import ru.wildberries.analytics.db.InMemorySentInfoDao
import ru.wildberries.analytics.device.MetadataCollector
import ru.wildberries.analytics.domain.ApiData
import ru.wildberries.analytics.testMetaInfo
import ru.wildberries.analytics.transport.Transport
import ru.wildberries.analytics.utils.EventsFactory
import kotlin.time.Duration

internal class SendAllAnalyticEventsOperationTests {
    private lateinit var subject: SendAllAnalyticEventsOperation
    private val sentInfoDao = InMemorySentInfoDao()
    private val metadataCollector = mockk<MetadataCollector> {
        coEvery { collect() } returns testMetaInfo()
    }
    private val eventsRepository = InMemoryEventsRepository()
    private val transactionRunner = TestTransactionRunner()
    private val log = NoOpWBAnalytics2Logger
    private val batchRepository = BatchRepositoryImpl(
        metadataCollector = metadataCollector,
        sentInfoDao = sentInfoDao,
        eventsRepository = eventsRepository,
        transactionRunner = transactionRunner,
        log = log,
    ).let(::spyk)

    private val configRepository = ConfigRepositoryImpl()
    private val succeedTransport = mockk<Transport> {
        coEvery { send<BatchModel>(any(), any(), any(), any()) } returns 200
    }
    private val maxEventsInBatch = 2
    private val apiData = ApiData("apiUrl", "apiKey")

    @BeforeEach
    fun setUp() {
        subject = SendAllAnalyticEventsOperation(
            eventsRepository = eventsRepository,
            batchRepository = batchRepository,
            configRepository = configRepository,
            log = log,
        )
        eventsRepository.clear()
        configRepository.updateConfig {
            copy(
                batching = batching.copy(maxEventsInBatch = maxEventsInBatch),
                transport = succeedTransport
            )
        }
    }

    @Test
    fun succeedMultipleBatchesOperation() = runTest {
        eventsRepository.addEvents(
            EventsFactory.createEntities(
                count = maxEventsInBatch + 1,
                apiUrl = apiData.apiUrl,
                apiKey = apiData.apiKey
            )
        )
        subject.execute()
        coVerify(exactly = 2) {
            batchRepository.getBatch(eq(apiData), any())
            succeedTransport.send<BatchModel>(any(), any(), any(), any())
            eventsRepository.getFirstEventApiDataOrNull()
            batchRepository.batchSent(any())
            eventsRepository.deleteOldestEventsThatExceed(10000)
        }
    }

    @Test
    fun checkDbOversizeAfterSendBatch() = runTest {
        eventsRepository.addEvents(EventsFactory.createEntities(configRepository.config.value.batching.maxEventsInBatch - 1))
        subject.execute()
        coVerify(exactly = 1) {
            batchRepository.batchSent(any())
            eventsRepository.deleteOldestEventsThatExceed(10000)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun failedSendBatchOperation() = runTest {
        eventsRepository.addEvents(EventsFactory.createEntities(10))
        val failedTransport = mockk<Transport> {
            coEvery { send<BatchModel>(any(), any(), any(), any()) } throws OutOfMemoryError()
        }
        configRepository.updateConfig {
            copy(
                transport = failedTransport,
                retryPolicy = object : RetryPolicy {
                    override fun shouldRetry(
                        attempt: Int,
                        error: Throwable
                    ): Boolean = attempt < 3

                    override fun nextDelay(attempt: Int): Duration = Duration.ZERO
                }
            )
        }
        subject.execute()
        coVerify {
            eventsRepository.getFirstEventApiDataOrNull()
            batchRepository.getBatch(any(), any())
            failedTransport.send<BatchModel>(any(), any(), any(), any())
            batchRepository.dropBatch(any())
            eventsRepository.deleteOldestEventsThatExceed(10000)
        }
        coVerify(inverse = true) {
            batchRepository.batchSent(any())
        }
    }
}