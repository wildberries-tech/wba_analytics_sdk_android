package ru.wildanalytics.pub.analytics.send

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.NoOpWildAnalyticsLogger
import ru.wildanalytics.pub.analytics.TestTransactionRunner
import ru.wildanalytics.pub.analytics.api.BatchModel
import ru.wildanalytics.pub.analytics.batch.BatchRepositoryImpl
import ru.wildanalytics.pub.analytics.config.ConfigRepositoryImpl
import ru.wildanalytics.pub.analytics.config.RetryPolicy
import ru.wildanalytics.pub.analytics.data.InMemoryEventsRepository
import ru.wildanalytics.pub.analytics.db.InMemorySentInfoDao
import ru.wildanalytics.pub.analytics.device.MetadataCollector
import ru.wildanalytics.pub.analytics.domain.ApiData
import ru.wildanalytics.pub.analytics.testMetaInfo
import ru.wildanalytics.pub.analytics.transport.Transport
import ru.wildanalytics.pub.analytics.utils.EventsFactory
import kotlin.time.Duration

internal class SendAllAnalyticEventsOperationTests {
    private lateinit var subject: SendAllAnalyticEventsOperation
    private val sentInfoDao = InMemorySentInfoDao()
    private val metadataCollector = mockk<MetadataCollector> {
        coEvery { collect() } returns testMetaInfo()
    }
    private val eventsRepository = InMemoryEventsRepository()
    private val transactionRunner = TestTransactionRunner()
    private val log = NoOpWildAnalyticsLogger
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