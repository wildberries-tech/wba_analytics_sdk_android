package ru.wildberries.pub.analytics

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationStrategy
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.wildberries.pub.analytics.api.BatchModel
import ru.wildberries.pub.analytics.automatic.AutomaticEventsStrategy
import ru.wildberries.pub.analytics.batch.BatchRepositoryImpl
import ru.wildberries.pub.analytics.batch.BatchingConfig
import ru.wildberries.pub.analytics.config.ConfigRepositoryImpl
import ru.wildberries.pub.analytics.config.RetryPolicy
import ru.wildberries.pub.analytics.config.SendingDelays
import ru.wildberries.pub.analytics.db.RoomTransactionRunner
import ru.wildberries.pub.analytics.db.WBAnalytics2Database
import ru.wildberries.pub.analytics.device.MetadataCollector
import ru.wildberries.pub.analytics.diagnostics.DiagnosticsReporter
import ru.wildberries.pub.analytics.diagnostics.QueueEvictionReporter
import ru.wildberries.pub.analytics.event.EventsRepositoryImpl
import ru.wildberries.pub.analytics.send.SendAllAnalyticEventsOperation
import ru.wildberries.pub.analytics.transport.CustomHeadersRepositoryImpl
import ru.wildberries.pub.analytics.transport.Transport
import ru.wildberries.pub.analytics.util.RandomIdGenerator
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
internal class EventDeliveryEndToEndTest {

    private lateinit var db: WBAnalytics2Database
    private lateinit var analytics: WBAnalytics2Impl
    private lateinit var sendOperation: SendAllAnalyticEventsOperation
    private val consumerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val configRepository = ConfigRepositoryImpl()
    private val transport = RecordingTransport()
    private val logger = RecordingLogger()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            context = ApplicationProvider.getApplicationContext(),
            klass = WBAnalytics2Database::class.java,
        )
            .setDriver(androidx.sqlite.driver.AndroidSQLiteDriver())
            .allowMainThreadQueries().build()
        val eventsRepository = EventsRepositoryImpl(
            eventsDao = db.eventsDao(),
            db = db,
            transactionRunner = RoomTransactionRunner(db)
        )
        val diagnostics = DiagnosticsReporter(log = logger, sdkVersion = "test")
        analytics = WBAnalytics2Impl(
            apiUrlProvider = { "https://example.com" },
            apiKey = "apiKey",
            isCollectionEnabled = true,
            clock = Clock.systemUTC(),
            eventsRepository = eventsRepository,
            sessionProvider = mockk(relaxed = true),
            log = logger,
            coroutineScopeFactory = object : CoroutineScopeFactory {
                override fun create(debugName: String) = consumerScope
            },
            customHeadersRepository = CustomHeadersRepositoryImpl(),
            enricherRegistry = EventEnricherRegistryImpl(logger),
            automaticEventsStrategy = AutomaticEventsStrategy.DoNotTrack,
            commonParametersGate = CommonParametersGate(emptySet(), 5.seconds, logger),
            diagnostics = diagnostics,
        )
        sendOperation = SendAllAnalyticEventsOperation(
            eventsRepository = eventsRepository,
            batchRepository = BatchRepositoryImpl(
                metadataCollector = mockk<MetadataCollector> {
                    coEvery { collect(any()) } returns testMetaInfo()
                },
                transactionRunner = RoomTransactionRunner(db),
                eventsRepository = eventsRepository,
                sentInfoDao = db.sentInfoDao(),
                log = logger,
            ),
            configRepository = configRepository,
            log = logger,
            customHeadersRepository = CustomHeadersRepositoryImpl(),
            queueEvictionReporter = QueueEvictionReporter(
                reporter = diagnostics,
                clock = Clock.systemUTC(),
                idGenerator = RandomIdGenerator(),
            ),
            diagnostics = diagnostics,
            endpointRouter = testEndpointRouter(),
        )
        configure(maxEventsInCache = 10_000)
    }

    @After
    fun tearDown() {
        analytics.finish()
        consumerScope.cancel()
        db.close()
    }

    @Test
    fun allEventsAreDeliveredInImportanceOrderWithContinuousNumbering() = runBlocking {
        log(
            NORMAL to "a",
            HIGH to "begin_checkout",
            NORMAL to "b",
            HIGH to "purchase",
            NORMAL to "c"
        )

        sendOperation.execute()

        assertEquals(
            listOf(listOf("begin_checkout", "purchase", "a"), listOf("b", "c")),
            transport.deliveredBatches.map { batch -> batch.events.map { it.name } },
        )
        assertContinuousNumbering()
        assertEquals(0, db.eventsDao().getCount())
        assertTrue(logger.diagnostics.isEmpty())
    }

    @Test
    fun overflowEvictsOldestNormalEventsAndDeliversImportantOnes() = runBlocking {
        configure(maxEventsInCache = 5)
        transport.failures = 1
        log(
            NORMAL to "n1",
            HIGH to "begin_checkout",
            NORMAL to "n2",
            NORMAL to "n3",
            NORMAL to "n4",
            NORMAL to "n5",
            HIGH to "purchase",
            NORMAL to "n6",
        )

        sendOperation.execute()

        assertEquals(
            listOf("begin_checkout", "purchase", "n4", "n5", "n6"),
            transport.deliveredBatches.flatMap { batch -> batch.events.map { it.name } },
        )
        assertContinuousNumbering()
        assertEquals(
            listOf("wba_send_failed", "wba_queue_eviction"),
            logger.diagnostics.map { it.marker }
        )
        val eviction = logger.diagnostics.last()
        assertEquals("3", eviction.attributes["removed"])
        assertEquals("0", eviction.attributes["removed_important"])
        assertEquals("n1:0=1;n2:0=1;n3:0=1", eviction.attributes["groups"])
    }

    @Test
    fun overflowWithOnlyImportantEventsEvictsOldestImportant() = runBlocking {
        configure(maxEventsInCache = 2)
        transport.failures = 1
        log(HIGH to "i1", HIGH to "i2", HIGH to "i3")

        sendOperation.execute()

        assertEquals(
            listOf("i2", "i3"),
            transport.deliveredBatches.flatMap { batch -> batch.events.map { it.name } }
        )
        assertEquals(
            "1",
            logger.diagnostics.single { it.marker == "wba_queue_eviction" }.attributes["removed_important"]
        )
    }

    @Test
    fun overflowWithHealthyServerDeliversEverythingWithoutEviction() = runBlocking {
        configure(maxEventsInCache = 4)
        log(
            NORMAL to "a",
            HIGH to "begin_checkout",
            NORMAL to "b",
            NORMAL to "c",
            NORMAL to "d",
            NORMAL to "e"
        )

        sendOperation.execute()

        assertEquals(
            listOf("begin_checkout", "a", "b", "c", "d", "e"),
            transport.deliveredBatches.flatMap { batch -> batch.events.map { it.name } },
        )
        assertTrue(logger.diagnostics.isEmpty())
    }

    @Test
    fun failedSendKeepsEventsAndDeliversThemLaterWithoutNumberingGaps() = runBlocking {
        log(HIGH to "begin_checkout", NORMAL to "a", NORMAL to "b", NORMAL to "c")
        transport.failures = 2

        sendOperation.execute()

        assertEquals(
            listOf("begin_checkout", "a", "b", "c"),
            transport.deliveredBatches.flatMap { batch -> batch.events.map { it.name } },
        )
        assertContinuousNumbering()
        assertEquals(0, db.eventsDao().getCount())
        assertEquals(
            listOf("wba_send_failed", "wba_send_failed"),
            logger.diagnostics.map { it.marker }
        )
        assertEquals(listOf("500", "500"), logger.diagnostics.map { it.attributes["code"] })
    }

    @Test
    fun importantEventsSurviveOverflowWhileServerIsDown() = runBlocking {
        configure(maxEventsInCache = 4)
        log(HIGH to "begin_checkout", NORMAL to "a", NORMAL to "b")
        transport.failures = 1
        transport.onFailure = { log(NORMAL to "c", HIGH to "purchase", NORMAL to "d") }

        sendOperation.execute()

        assertEquals(
            listOf("begin_checkout", "purchase", "c", "d"),
            transport.deliveredBatches.flatMap { batch -> batch.events.map { it.name } },
        )
        assertContinuousNumbering()
        val eviction = logger.diagnostics.single { it.marker == "wba_queue_eviction" }
        assertEquals("a:0=1;b:0=1", eviction.attributes["groups"])
        assertEquals("0", eviction.attributes["removed_important"])
    }

    @Test
    fun criticalFailureDropsOnlyFailedBatch() = runBlocking {
        log(HIGH to "begin_checkout", NORMAL to "a", NORMAL to "b", NORMAL to "c")
        transport.failure = OutOfMemoryError()

        sendOperation.execute()

        assertEquals(
            listOf("c"),
            transport.deliveredBatches.flatMap { batch -> batch.events.map { it.name } }
        )
        assertEquals(0, db.eventsDao().getCount())
    }

    private suspend fun log(vararg events: Pair<Int, String>) {
        val before = db.eventsDao().getCount()
        events.forEach { (importance, name) ->
            if (importance == HIGH) {
                analytics.logImportantEvent(name, mapOf("key" to name))
            } else {
                analytics.logEvent(name, mapOf("key" to name))
            }
        }
        withTimeout(5.seconds) {
            while (db.eventsDao().getCount() < before + events.size) delay(10)
        }
    }

    private fun configure(maxEventsInCache: Int) {
        configRepository.updateConfig {
            copy(
                delays = SendingDelays(
                    delayBetweenOperations = Duration.ZERO,
                    delayBetweenBatches = Duration.ZERO
                ),
                batching = BatchingConfig(maxEventsInBatch = 3, maxBatchSizeInBites = 1024 * 1024),
                maxEventsInCache = maxEventsInCache,
                retryPolicy = RetryPolicy.NoRetry,
                transport = this@EventDeliveryEndToEndTest.transport,
            )
        }
    }

    private fun assertContinuousNumbering() {
        val eventNumbers =
            transport.deliveredBatches.flatMap { batch -> batch.events.map { it.eventNumber } }
        assertEquals((1uL..eventNumbers.size.toULong()).toList(), eventNumbers)
        val batchNumbers = transport.deliveredBatches.map { it.meta.batchNumber }
        assertEquals((1uL..batchNumbers.size.toULong()).toList(), batchNumbers)
    }

    private class RecordingTransport : Transport {
        val deliveredBatches = mutableListOf<BatchModel>()
        var failures = 0
        var onFailure: suspend () -> Unit = {}
        var failure: Throwable? = null

        override suspend fun <T> send(
            url: String,
            body: T,
            strategy: SerializationStrategy<T>,
            headers: Headers,
        ): Int {
            failure?.let {
                failure = null
                throw it
            }
            if (failures > 0) {
                failures--
                onFailure()
                return 500
            }
            deliveredBatches += body as BatchModel
            return 200
        }
    }

    private class RecordingLogger : WBAnalytics2Logger {
        val diagnostics = mutableListOf<RecordedDiagnostic>()
        override val isEnabled: Boolean = true
        override fun logDebug(message: String) = Unit
        override fun logException(e: Exception) = Unit
        override fun logError(e: Error, details: Map<String, String>) {
            markerOf(e.message.orEmpty())?.let { diagnostics += RecordedDiagnostic(it, details) }
        }

        override fun logWarn(message: String) {
            markerOf(message)?.let { marker ->
                val attributes =
                    ATTRIBUTE.findAll(message).associate { it.groupValues[1] to it.groupValues[2] }
                diagnostics += RecordedDiagnostic(marker, attributes)
            }
        }

        private fun markerOf(text: String): String? = MARKER.find(text)?.groupValues?.get(1)

        private companion object {
            val MARKER = Regex("""^\[(wba_[a-z0-9_]+)]""")
            val ATTRIBUTE = Regex("""\b([a-z_]+)=([^,]+)""")
        }
    }

    private data class RecordedDiagnostic(val marker: String, val attributes: Map<String, String>)

    private companion object {
        const val NORMAL = 0
        const val HIGH = 100
    }
}
