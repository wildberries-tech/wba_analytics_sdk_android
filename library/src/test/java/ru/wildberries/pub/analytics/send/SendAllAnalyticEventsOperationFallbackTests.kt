package ru.wildberries.pub.analytics.send

import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
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
import ru.wildberries.pub.analytics.device.MetadataCollector
import ru.wildberries.pub.analytics.diagnostics.DiagnosticMarker
import ru.wildberries.pub.analytics.diagnostics.DiagnosticsReporter
import ru.wildberries.pub.analytics.diagnostics.QueueEvictionReporter
import ru.wildberries.pub.analytics.network.NetworkSnapshot
import ru.wildberries.pub.analytics.send.fallback.EndpointFallback
import ru.wildberries.pub.analytics.testEndpointRouter
import ru.wildberries.pub.analytics.testMetaInfo
import ru.wildberries.pub.analytics.transport.CustomHeadersRepositoryImpl
import ru.wildberries.pub.analytics.transport.Transport
import ru.wildberries.pub.analytics.utils.EventsFactory
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException

internal class SendAllAnalyticEventsOperationFallbackTests {

    private val eventsRepository = InMemoryEventsRepository()
    private val configRepository = ConfigRepositoryImpl()
    private val mockTransport = mockk<Transport>()
    private val diagnostics = mockk<DiagnosticsReporter>(relaxed = true)
    private val endpointFallback = EndpointFallback()
    private var network = VALIDATED
    private val sentUrls = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        configRepository.updateConfig {
            copy(
                batching = batching.copy(maxEventsInBatch = BATCH_SIZE),
                transport = mockTransport,
                retryPolicy = RETRY_POLICY,
            )
        }
        endpointFallback.register(API_KEY, FALLBACK_URL)
    }

    @Test
    fun `given unreachable primary, when batches are sent, then third failure switches the same batch to fallback`() = runTest {
        // given
        respond(primary = { throw UnknownHostException("a.wb.ru") })
        addEvents(BATCH_SIZE * 2)

        // when
        operation().execute()

        // then
        sentUrls shouldBe listOf(PRIMARY_URL, PRIMARY_URL, PRIMARY_URL, FALLBACK_URL, FALLBACK_URL)
        verifyQueueIsEmpty()
        verifyFallbackReported { it["attempt"] == "3" }
        verifySendFailed(times = 0)
    }

    @Test
    fun `given switch, when fallback attempt follows, then it is sent without retry delay`() = runTest {
        // given
        addEvents(1)
        val sentAt = mutableListOf<Long>()
        respond(primary = { throw ConnectException("refused") }, onSend = { sentAt += testScheduler.currentTime })

        // when
        operation().execute()

        // then
        sentAt[2] shouldBeGreaterThan sentAt[1]
        sentAt[3] shouldBe sentAt[2]
    }

    @Test
    fun `given retry policy with three attempts, when all fail, then fallback is still tried`() = runTest {
        // given
        configRepository.updateConfig { copy(retryPolicy = RetryPolicy.Exponential(base = 1.5, additional = 0.0, attempts = 3)) }
        respond(primary = { throw ConnectException("refused") })
        addEvents(1)

        // when
        operation().execute()

        // then
        sentUrls shouldBe listOf(PRIMARY_URL, PRIMARY_URL, PRIMARY_URL, FALLBACK_URL)
        verifyQueueIsEmpty()
    }

    @Test
    fun `given no retry policy, when attempts fail, then sending never switches`() = runTest {
        // given
        configRepository.updateConfig { copy(retryPolicy = RetryPolicy.NoRetry) }
        var calls = 0
        respond(primary = { if (++calls <= 3) throw ConnectException("refused") else 200 })
        val subject = operation()

        // when
        repeat(3) {
            addEvents(1)
            subject.execute()
        }

        // then
        sentUrls.distinct() shouldBe listOf(PRIMARY_URL)
        verifyNoFallback()
    }

    @Test
    fun `given unavailable fallback, when cycle ends, then send_failed names fallback`() = runTest {
        // given
        respond(primary = { throw ConnectException("refused") }, fallback = { throw IOException("fallback down") })
        addEvents(1)

        // when
        operation().execute()

        // then
        sentUrls.take(3).distinct() shouldBe listOf(PRIMARY_URL)
        sentUrls.drop(3).distinct() shouldBe listOf(FALLBACK_URL)
        verifyFallbackReported { true }
        verifySendFailed(times = 1) {
            it["endpoint"] == "fallback" && it["host"] == "afb.wb.ru" && it["error"] == "fallback down" && it["network"] == "wifi"
        }
    }

    @Test
    fun `given switched host, when next batch is sent, then it goes straight to fallback`() = runTest {
        // given
        respond(primary = { throw ConnectException("refused") })
        val subject = operation()
        addEvents(1)
        subject.execute()
        sentUrls.clear()
        addEvents(1)

        // when
        subject.execute()

        // then
        sentUrls shouldBe listOf(FALLBACK_URL)
        verifyFallbackReported { true }
    }

    @Test
    fun `given no fallback configured, when primary is unreachable, then sending matches master`() = runTest {
        // given
        val subject = operation(fallback = EndpointFallback())
        respond(primary = { throw ConnectException("refused") })
        addEvents(1)

        // when
        subject.execute()

        // then
        sentUrls.distinct() shouldBe listOf(PRIMARY_URL)
        sentUrls.size shouldBe ATTEMPTS + 1
        verifyNoFallback()
        verifySendFailed(times = 1) { it["endpoint"] == "primary" && it["host"] == "a.wb.ru" && it["attempts"] == "$ATTEMPTS" }
    }

    private fun operation(fallback: EndpointFallback = endpointFallback) = SendAllAnalyticEventsOperation(
        eventsRepository = eventsRepository,
        batchRepository = BatchRepositoryImpl(
            metadataCollector = mockk<MetadataCollector> { coEvery { collect(any()) } returns testMetaInfo() },
            sentInfoDao = InMemorySentInfoDao(),
            eventsRepository = eventsRepository,
            transactionRunner = TestTransactionRunner(),
            log = NoOpWBAnalytics2Logger,
        ),
        configRepository = configRepository,
        log = NoOpWBAnalytics2Logger,
        customHeadersRepository = CustomHeadersRepositoryImpl(),
        queueEvictionReporter = mockk<QueueEvictionReporter>(relaxed = true),
        diagnostics = diagnostics,
        endpointRouter = testEndpointRouter(fallback, { network }, diagnostics),
    )

    private suspend fun addEvents(count: Int) {
        eventsRepository.addEvents(EventsFactory.createEntities(count = count, apiUrl = PRIMARY_URL, apiKey = API_KEY))
    }

    private fun respond(primary: () -> Int, fallback: () -> Int = { 200 }, onSend: () -> Unit = {}) {
        coEvery { mockTransport.send<BatchModel>(any(), any(), any(), any()) } answers {
            val url = firstArg<String>()
            sentUrls += url
            onSend()
            when {
                sentUrls.size > ATTEMPTS -> 200
                url == FALLBACK_URL -> fallback()
                else -> primary()
            }
        }
    }

    private suspend fun verifyQueueIsEmpty() {
        eventsRepository.getFirstEventApiDataOrNull().shouldBeNull()
    }

    private fun verifyFallbackReported(attributes: (Map<String, String>) -> Boolean) {
        verify(exactly = 1) {
            diagnostics.report(
                marker = DiagnosticMarker.ENDPOINT_FALLBACK,
                message = any(),
                attributes = match(attributes),
                throwable = any(),
            )
        }
    }

    private fun verifyNoFallback() {
        verify(exactly = 0) { diagnostics.report(DiagnosticMarker.ENDPOINT_FALLBACK, any(), any(), any()) }
        coVerify(exactly = 0) { mockTransport.send<BatchModel>(FALLBACK_URL, any(), any(), any()) }
    }

    private fun verifySendFailed(times: Int, attributes: (Map<String, String>) -> Boolean = { true }) {
        verify(exactly = times) {
            diagnostics.report(
                marker = DiagnosticMarker.SEND_FAILED,
                message = any(),
                attributes = match(attributes),
                throwable = any(),
            )
        }
    }

    private companion object {
        const val API_KEY = "apiKey"
        const val PRIMARY_URL = "https://a.wb.ru/m/batch"
        const val FALLBACK_URL = "https://afb.wb.ru/m/batch"
        const val BATCH_SIZE = 2
        const val ATTEMPTS = 10
        val RETRY_POLICY = RetryPolicy.Exponential(base = 1.5, additional = 2.5, attempts = ATTEMPTS)
        val VALIDATED = NetworkSnapshot(transports = "wifi", validated = true)
    }
}
