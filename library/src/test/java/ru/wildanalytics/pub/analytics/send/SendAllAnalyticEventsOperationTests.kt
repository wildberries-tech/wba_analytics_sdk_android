package ru.wildanalytics.pub.analytics.send

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.wildanalytics.pub.analytics.NoOpWildAnalyticsLogger
import ru.wildanalytics.pub.analytics.TestTransactionRunner
import ru.wildanalytics.pub.analytics.api.BatchModel
import ru.wildanalytics.pub.analytics.batch.BatchRepositoryImpl
import ru.wildanalytics.pub.analytics.config.ConfigRepositoryImpl
import ru.wildanalytics.pub.analytics.config.RetryPolicy
import ru.wildanalytics.pub.analytics.data.InMemoryEventsRepository
import ru.wildanalytics.pub.analytics.db.EventEntity
import ru.wildanalytics.pub.analytics.db.EvictedEventsGroup
import ru.wildanalytics.pub.analytics.db.InMemorySentInfoDao
import ru.wildanalytics.pub.analytics.diagnostics.DiagnosticMarker
import ru.wildanalytics.pub.analytics.diagnostics.DiagnosticsReporter
import ru.wildanalytics.pub.analytics.diagnostics.QueueEvictionReporter
import ru.wildanalytics.pub.analytics.device.MetadataCollector
import ru.wildanalytics.pub.analytics.domain.ApiData
import ru.wildanalytics.pub.analytics.event.EvictionResult
import ru.wildanalytics.pub.analytics.testEndpointRouter
import ru.wildanalytics.pub.analytics.testMetaInfo
import ru.wildanalytics.pub.analytics.transport.CustomHeadersRepositoryImpl
import ru.wildanalytics.pub.analytics.transport.Transport
import ru.wildanalytics.pub.analytics.utils.EventsFactory
import java.io.IOException
import kotlin.time.Duration

internal class SendAllAnalyticEventsOperationTests {
    private lateinit var subject: SendAllAnalyticEventsOperation
    private val sentInfoDao = InMemorySentInfoDao()
    private val metadataCollector = mockk<MetadataCollector> {
        coEvery { collect(any()) } returns testMetaInfo()
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
    private val failingTransport = mockk<Transport>()
    private val customHeadersRepository = CustomHeadersRepositoryImpl()
    private val queueEvictionReporter = mockk<QueueEvictionReporter>(relaxed = true)
    private val diagnostics = mockk<DiagnosticsReporter>(relaxed = true)
    private val maxEventsInBatch = 2
    private val apiData = ApiData("apiUrl", "apiKey")

    @BeforeEach
    fun setUp() {
        subject = SendAllAnalyticEventsOperation(
            eventsRepository = eventsRepository,
            batchRepository = batchRepository,
            configRepository = configRepository,
            log = log,
            customHeadersRepository = customHeadersRepository,
            queueEvictionReporter = queueEvictionReporter,
            diagnostics = diagnostics,
            endpointRouter = testEndpointRouter(),
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
    fun evictionFailureIsPropagatedWithoutLosingOrDuplicatingSentBatch() = runTest {
        val failingEvictionRepository = spyk(InMemoryEventsRepository())
        failingEvictionRepository.addEvents(EventsFactory.createEntities(1, apiUrl = apiData.apiUrl, apiKey = apiData.apiKey))
        coEvery { failingEvictionRepository.deleteOldestEventsThatExceed(any()) } throws IllegalStateException("disk I/O error")
        val subject = SendAllAnalyticEventsOperation(
            eventsRepository = failingEvictionRepository,
            batchRepository = BatchRepositoryImpl(
                metadataCollector = metadataCollector,
                sentInfoDao = InMemorySentInfoDao(),
                eventsRepository = failingEvictionRepository,
                transactionRunner = transactionRunner,
                log = log,
            ),
            configRepository = configRepository,
            log = log,
            customHeadersRepository = customHeadersRepository,
            queueEvictionReporter = queueEvictionReporter,
            diagnostics = diagnostics,
            endpointRouter = testEndpointRouter(),
        )

        runCatching { subject.execute() }.exceptionOrNull().shouldBeInstanceOf<IllegalStateException>()
        coVerify(exactly = 1) { succeedTransport.send<BatchModel>(any(), any(), any(), any()) }
        failingEvictionRepository.getFirstEventApiDataOrNull() shouldBe null

        coEvery { failingEvictionRepository.deleteOldestEventsThatExceed(any()) } returns null
        subject.execute()

        coVerify(exactly = 1) { succeedTransport.send<BatchModel>(any(), any(), any(), any()) }
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

    @Test
    fun sendsApiKeyHeaderWhenNoCustomHeaders() = runTest {
        val headersSlot = slot<Headers>()
        coEvery {
            succeedTransport.send<BatchModel>(any(), any(), any(), capture(headersSlot))
        } returns 200
        eventsRepository.addEvents(
            EventsFactory.createEntities(count = 1, apiUrl = apiData.apiUrl, apiKey = apiData.apiKey)
        )
        subject.execute()
        headersSlot.captured.values("X-Api-Key") shouldBe listOf(apiData.apiKey)
        headersSlot.captured.size shouldBe 1
    }

    @Test
    fun includesCustomHeaderWhenSet() = runTest {
        val headersSlot = slot<Headers>()
        coEvery {
            succeedTransport.send<BatchModel>(any(), any(), any(), capture(headersSlot))
        } returns 200
        customHeadersRepository.set("X-Wbaas-Token", "antibot")
        eventsRepository.addEvents(
            EventsFactory.createEntities(count = 1, apiUrl = apiData.apiUrl, apiKey = apiData.apiKey)
        )
        subject.execute()
        headersSlot.captured.values("X-Wbaas-Token") shouldBe listOf("antibot")
        headersSlot.captured.values("X-Api-Key") shouldBe listOf(apiData.apiKey)
        headersSlot.captured.size shouldBe 2
    }

    @Test
    fun apiKeyHeaderIsNotOverriddenByCustomHeader() = runTest {
        val headersSlot = slot<Headers>()
        coEvery {
            succeedTransport.send<BatchModel>(any(), any(), any(), capture(headersSlot))
        } returns 200
        customHeadersRepository.set("X-Api-Key", "hacked")
        eventsRepository.addEvents(
            EventsFactory.createEntities(count = 1, apiUrl = apiData.apiUrl, apiKey = apiData.apiKey)
        )
        subject.execute()
        headersSlot.captured.values("X-Api-Key") shouldBe listOf(apiData.apiKey)
        headersSlot.captured.size shouldBe 1
    }

    @Test
    fun eventsDeliveredInTheSameRunAreNotEvicted() = runTest {
        configRepository.updateConfig { copy(maxEventsInCache = 3) }
        eventsRepository.addEvents(evictionScenarioEvents())
        val sentEvents = recordSentEvents(succeedTransport)

        subject.execute()

        sentEvents shouldContainExactly listOf("begin_checkout", "a", "b", "c", "d")
        coVerify(exactly = 0) { queueEvictionReporter.report(any()) }
    }

    @Test
    fun evictsExceedingEventsAfterFailedBatchAndReportsResult() = runTest {
        configRepository.updateConfig {
            copy(maxEventsInCache = 3, transport = failingTransport, retryPolicy = RetryPolicy.NoRetry)
        }
        eventsRepository.addEvents(evictionScenarioEvents())
        val sentEvents = recordSentEvents(failingTransport) { call -> if (call == 1) 500 else 200 }

        subject.execute()

        coVerifyOrder {
            failingTransport.send<BatchModel>(any(), any(), any(), any())
            queueEvictionReporter.report(
                EvictionResult(
                    limit = 3,
                    sizeBefore = 5,
                    removed = 2,
                    groups = listOf(
                        EvictedEventsGroup(name = "a", importance = EventEntity.IMPORTANCE_NORMAL, count = 1),
                        EvictedEventsGroup(name = "b", importance = EventEntity.IMPORTANCE_NORMAL, count = 1),
                    ),
                )
            )
            batchRepository.getBatch(eq(apiData), any())
        }
        sentEvents shouldContainExactly listOf("begin_checkout", "c", "d")
    }

    private fun evictionScenarioEvents() = listOf(
        EventsFactory.createEntity(id = 1, name = "a", apiUrl = apiData.apiUrl, apiKey = apiData.apiKey),
        EventsFactory.createEntity(
            id = 2,
            name = "begin_checkout",
            apiUrl = apiData.apiUrl,
            apiKey = apiData.apiKey,
            importance = EventEntity.IMPORTANCE_HIGH,
        ),
        EventsFactory.createEntity(id = 3, name = "b", apiUrl = apiData.apiUrl, apiKey = apiData.apiKey),
        EventsFactory.createEntity(id = 4, name = "c", apiUrl = apiData.apiUrl, apiKey = apiData.apiKey),
        EventsFactory.createEntity(id = 5, name = "d", apiUrl = apiData.apiUrl, apiKey = apiData.apiKey),
    )

    private fun recordSentEvents(transport: Transport, responseCode: (call: Int) -> Int = { 200 }): List<String> {
        val sentEvents = mutableListOf<String>()
        val batchSlot = slot<BatchModel>()
        var calls = 0
        coEvery { transport.send<BatchModel>(any(), capture(batchSlot), any(), any()) } answers {
            val code = responseCode(++calls)
            if (code == 200) sentEvents += batchSlot.captured.events.map { it.name }
            code
        }
        return sentEvents
    }

    @Test
    fun doesNotReportWhenLimitIsNotExceeded() = runTest {
        eventsRepository.addEvents(EventsFactory.createEntities(count = 3, apiUrl = apiData.apiUrl, apiKey = apiData.apiKey))

        subject.execute()

        coVerify(exactly = 0) { queueEvictionReporter.report(any()) }
    }

    @Test
    fun singleEventWith429AfterRetriesIsKeptAndSentOnNextAttempt() = runTest {
        var calls = 0
        coEvery { failingTransport.send<BatchModel>(any(), any(), any(), any()) } answers {
            if (++calls <= 3) 429 else 200
        }
        configRepository.updateConfig {
            copy(
                transport = failingTransport,
                retryPolicy = RetryPolicy.Exponential(base = 1.5, additional = 0.0, attempts = 3),
            )
        }
        eventsRepository.addEvents(listOf(EventsFactory.createEntity(id = 1, name = "view_cart")))

        subject.execute()

        coVerify(exactly = 4) { failingTransport.send<BatchModel>(any(), any(), any(), any()) }
        coVerify(exactly = 0) { batchRepository.dropBatch(any()) }
        coVerify(exactly = 1) { batchRepository.batchSent(listOf(1)) }
        verify(exactly = 1) {
            diagnostics.report(
                marker = DiagnosticMarker.SEND_FAILED,
                message = any(),
                attributes = match { it["code"] == "429" && it["attempts"] == "3" && it["events"] == "1" },
                throwable = ofType<HttpStatusException>(),
            )
        }
    }

    @Test
    fun batchWith429IsKeptAndSentOnNextAttempt() = runTest {
        respondWith(429, then = 200)
        eventsRepository.addEvents(EventsFactory.createEntities(count = 2))

        subject.execute()

        coVerify(exactly = 0) { batchRepository.dropBatch(any()) }
        coVerify(exactly = 1) { batchRepository.batchSent(any()) }
        verifySendFailed(code = "429")
    }

    @Test
    fun serverErrorKeepsSingleEventAndSendsItOnNextAttempt() = runTest {
        respondWith(500, then = 200)
        eventsRepository.addEvents(EventsFactory.createEntities(count = 1))

        subject.execute()

        coVerify(exactly = 0) { batchRepository.dropBatch(any()) }
        coVerify(exactly = 1) { batchRepository.batchSent(any()) }
        verifySendFailed(code = "500")
    }

    @Test
    fun uriTooLongKeepsEventsAndSendsThemOnNextAttempt() = runTest {
        respondWith(414, then = 200)
        eventsRepository.addEvents(EventsFactory.createEntities(count = 1))

        subject.execute()

        coVerify(exactly = 0) { batchRepository.dropBatch(any()) }
        coVerify(exactly = 1) { batchRepository.batchSent(any()) }
        verifySendFailed(code = "414")
    }

    @Test
    fun transportExceptionKeepsEventsAndSendsThemOnNextAttempt() = runTest {
        var calls = 0
        coEvery { failingTransport.send<BatchModel>(any(), any(), any(), any()) } answers {
            if (++calls == 1) throw IOException("timeout") else 200
        }
        configRepository.updateConfig { copy(transport = failingTransport, retryPolicy = RetryPolicy.NoRetry) }
        eventsRepository.addEvents(EventsFactory.createEntities(count = 1))

        subject.execute()

        coVerify(exactly = 0) { batchRepository.dropBatch(any()) }
        coVerify(exactly = 1) { batchRepository.batchSent(any()) }
        verify {
            diagnostics.report(
                marker = DiagnosticMarker.SEND_FAILED,
                message = any(),
                attributes = match { "code" !in it && it["exception"] == IOException::class.java.name && it["attempts"] == "1" },
                throwable = ofType<IOException>(),
            )
        }
    }

    @Test
    fun retryPolicyStillReceivesIOExceptionForHttpErrors() = runTest {
        val errors = mutableListOf<Throwable>()
        val recordingPolicy = object : RetryPolicy {
            override fun shouldRetry(attempt: Int, error: Throwable): Boolean {
                errors += error
                return false
            }
            override fun nextDelay(attempt: Int): Duration = Duration.ZERO
        }
        respondWith(503, then = 200, retryPolicy = recordingPolicy)
        eventsRepository.addEvents(EventsFactory.createEntities(count = 1))

        subject.execute()

        (errors.single() is IOException) shouldBe true
    }

    @Test
    fun httpStatusExceptionKeepsLegacyMessage() {
        HttpStatusException(503).message shouldBe "Server error 503"
    }

    private fun respondWith(code: Int, then: Int = code, retryPolicy: RetryPolicy = RetryPolicy.NoRetry) {
        coEvery { failingTransport.send<BatchModel>(any(), any(), any(), any()) } returnsMany listOf(code, then)
        configRepository.updateConfig { copy(transport = failingTransport, retryPolicy = retryPolicy) }
    }

    private fun verifySendFailed(code: String) {
        verify(exactly = 1) {
            diagnostics.report(
                marker = DiagnosticMarker.SEND_FAILED,
                message = any(),
                attributes = match { it["code"] == code },
                throwable = any(),
            )
        }
    }
}