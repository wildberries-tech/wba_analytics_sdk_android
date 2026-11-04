package ru.wildberries.pub.analytics.send

import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.wildberries.pub.analytics.NoOpWBAnalytics2Logger
import ru.wildberries.pub.analytics.TestTransactionRunner
import ru.wildberries.pub.analytics.api.BatchModel
import ru.wildberries.pub.analytics.batch.BatchRepositoryImpl
import ru.wildberries.pub.analytics.config.ConfigRepositoryImpl
import ru.wildberries.pub.analytics.config.RetryPolicy
import ru.wildberries.pub.analytics.data.InMemoryEventsRepository
import ru.wildberries.pub.analytics.db.InMemorySentInfoDao
import ru.wildberries.pub.analytics.db.SentInfoEntity
import ru.wildberries.pub.analytics.device.MetadataCollector
import ru.wildberries.pub.analytics.diagnostics.DiagnosticMarker
import ru.wildberries.pub.analytics.diagnostics.DiagnosticsReporter
import ru.wildberries.pub.analytics.diagnostics.QueueEvictionReporter
import ru.wildberries.pub.analytics.send.fallback.EndpointFallback
import ru.wildberries.pub.analytics.testEndpointRouter
import ru.wildberries.pub.analytics.testMetaInfo
import ru.wildberries.pub.analytics.transport.CustomHeadersRepositoryImpl
import ru.wildberries.pub.analytics.transport.Transport
import ru.wildberries.pub.analytics.utils.EventsFactory
import java.io.IOException
import java.net.ConnectException

/** Номера батчей и событий — единый счётчик устройства; переключение URL не должно его разводить. */
internal class SendAllAnalyticEventsOperationCounterTests {

    private val eventsRepository = InMemoryEventsRepository()
    private val sentInfoDao = InMemorySentInfoDao()
    private val configRepository = ConfigRepositoryImpl()
    private val mockTransport = mockk<Transport>()
    private val endpointFallback = EndpointFallback().apply { register(API_KEY, FALLBACK_URL) }
    private val sends = mutableListOf<Send>()

    @BeforeEach
    fun setUp() {
        configRepository.updateConfig {
            copy(
                batching = batching.copy(maxEventsInBatch = BATCH_SIZE),
                transport = mockTransport,
                retryPolicy = RetryPolicy.Exponential(base = 1.5, additional = 2.5, attempts = ATTEMPTS),
            )
        }
    }

    @Test
    fun `given unreachable primary, when batches switch to fallback, then numbering stays continuous`() = runTest {
        // given
        respond { url, _ -> if (url == PRIMARY_URL) throw ConnectException("refused") else 200 }
        addEvents(API_KEY, count = BATCH_SIZE * 3)

        // when
        operation().execute()

        // then
        sends.filter { it.url == PRIMARY_URL }.map { it.batchNumber }.distinct() shouldBe listOf(1UL)
        verifyContinuousDelivery(batches = 3, events = BATCH_SIZE * 3)
        verifyAttemptsOfBatchShareNumbers()
    }

    @Test
    fun `given keys with and without fallback on one host, when sent interleaved, then numbering stays continuous`() = runTest {
        // given
        var primaryFailures = 3
        respond { url, _ -> if (url == PRIMARY_URL && primaryFailures-- > 0) throw ConnectException("refused") else 200 }
        repeat(3) {
            addEvents(API_KEY, count = BATCH_SIZE)
            addEvents(OTHER_API_KEY, count = BATCH_SIZE)
        }

        // when
        operation().execute()

        // then
        sends.filter { it.apiKey == API_KEY && it.code == 200 }.map { it.url }.distinct() shouldBe listOf(FALLBACK_URL)
        sends.filter { it.apiKey == OTHER_API_KEY }.map { it.url }.distinct() shouldBe listOf(PRIMARY_URL)
        verifyContinuousDelivery(batches = 6, events = BATCH_SIZE * 6)
        verifyAttemptsOfBatchShareNumbers()
    }

    @Test
    fun `given fallback down for a whole cycle, when next cycle delivers, then the batch is not renumbered`() = runTest {
        // given
        var fallbackFailures = ATTEMPTS - 3
        respond { url, _ ->
            when {
                url == PRIMARY_URL -> throw ConnectException("refused")
                fallbackFailures-- > 0 -> throw IOException("fallback down")
                else -> 200
            }
        }
        addEvents(API_KEY, count = BATCH_SIZE * 2)

        // when
        operation().execute()

        // then
        sends.filter { it.code == null }.map { it.batchNumber }.distinct() shouldBe listOf(1UL)
        verifyContinuousDelivery(batches = 2, events = BATCH_SIZE * 2)
        verifyAttemptsOfBatchShareNumbers()
    }

    @Test
    fun `given server error responses on fallback, when delivered later, then counters advance only on success`() = runTest {
        // given
        var fallbackErrors = 2
        respond { url, _ ->
            when {
                url == PRIMARY_URL -> throw ConnectException("refused")
                fallbackErrors-- > 0 -> 503
                else -> 200
            }
        }
        addEvents(API_KEY, count = BATCH_SIZE)

        // when
        operation().execute()

        // then
        sends.map { it.batchNumber }.distinct() shouldBe listOf(1UL)
        verifyContinuousDelivery(batches = 1, events = BATCH_SIZE)
    }

    @Test
    fun `given broken marker reporting, when primary is unreachable, then the batch is delivered once to fallback`() = runTest {
        // given
        val diagnostics = mockk<DiagnosticsReporter>(relaxed = true) {
            every { report(DiagnosticMarker.ENDPOINT_FALLBACK, any(), any(), any()) } throws IllegalStateException("broken")
        }
        respond { url, _ -> if (url == PRIMARY_URL) throw ConnectException("refused") else 200 }
        addEvents(API_KEY, count = BATCH_SIZE)

        // when
        operation(diagnostics).execute()

        // then
        sends.count { it.code == 200 } shouldBe 1
        verifyContinuousDelivery(batches = 1, events = BATCH_SIZE)
    }

    private fun verifyContinuousDelivery(batches: Int, events: Int) {
        val delivered = sends.filter { it.code == 200 }
        delivered.map { it.batchNumber } shouldBe (1..batches).map { it.toULong() }
        delivered.flatMap { it.eventNumbers } shouldBe (1..events).map { it.toULong() }
        sentInfoDao.info shouldBe SentInfoEntity(batchesSent = batches.toLong(), eventsSent = events.toLong())
    }

    private suspend fun verifyAttemptsOfBatchShareNumbers() {
        sends.groupBy { it.batchNumber }.values.forEach { attempts ->
            attempts.map { it.eventNumbers }.distinct().size shouldBe 1
            attempts.map { it.apiKey }.distinct().size shouldBe 1
        }
        sends.map { it.url }.toSet() shouldContainOnly setOf(PRIMARY_URL, FALLBACK_URL)
        eventsRepository.getFirstEventApiDataOrNull().shouldBeNull()
    }

    private fun operation(diagnostics: DiagnosticsReporter = mockk(relaxed = true)) = SendAllAnalyticEventsOperation(
        eventsRepository = eventsRepository,
        batchRepository = BatchRepositoryImpl(
            metadataCollector = mockk<MetadataCollector> { coEvery { collect(any()) } returns testMetaInfo() },
            sentInfoDao = sentInfoDao,
            eventsRepository = eventsRepository,
            transactionRunner = TestTransactionRunner(),
            log = NoOpWBAnalytics2Logger,
        ),
        configRepository = configRepository,
        log = NoOpWBAnalytics2Logger,
        customHeadersRepository = CustomHeadersRepositoryImpl(),
        queueEvictionReporter = mockk<QueueEvictionReporter>(relaxed = true),
        diagnostics = diagnostics,
        endpointRouter = testEndpointRouter(endpointFallback, diagnostics = diagnostics),
    )

    private suspend fun addEvents(apiKey: String, count: Int) {
        eventsRepository.addEvents(EventsFactory.createEntities(count = count, apiUrl = PRIMARY_URL, apiKey = apiKey))
    }

    private fun respond(answer: (url: String, batch: BatchModel) -> Int) {
        coEvery { mockTransport.send<BatchModel>(any(), any(), any(), any()) } answers {
            val url = firstArg<String>()
            val batch = secondArg<BatchModel>()
            val send = Send(
                url = url,
                apiKey = arg<Headers>(3)["X-Api-Key"].orEmpty(),
                batchNumber = batch.meta.batchNumber,
                eventNumbers = batch.events.map { it.eventNumber },
            )
            val code = try {
                answer(url, batch)
            } catch (e: Exception) {
                sends += send
                throw e
            }
            sends += send.copy(code = code)
            code
        }
    }

    private data class Send(
        val url: String,
        val apiKey: String,
        val batchNumber: ULong,
        val eventNumbers: List<ULong>,
        val code: Int? = null,
    )

    private companion object {
        const val API_KEY = "apiKey"
        const val OTHER_API_KEY = "otherApiKey"
        const val PRIMARY_URL = "https://a.wb.ru/m/batch"
        const val FALLBACK_URL = "https://afb.wb.ru/m/batch"
        const val BATCH_SIZE = 3
        const val ATTEMPTS = 10
    }
}
