package ru.wildberries.pub.analytics.event

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.wildberries.pub.analytics.db.EventEntity
import ru.wildberries.pub.analytics.db.EvictedEventsGroup
import ru.wildberries.pub.analytics.db.RoomTransactionRunner
import ru.wildberries.pub.analytics.db.WBAnalytics2Database
import java.time.OffsetDateTime

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
internal class EventsRepositoryEvictionTest {

    private lateinit var db: WBAnalytics2Database
    private lateinit var repository: EventsRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            context = ApplicationProvider.getApplicationContext(),
            klass = WBAnalytics2Database::class.java,
        )
            .setDriver(androidx.sqlite.driver.AndroidSQLiteDriver())
            .allowMainThreadQueries().build()
        repository = EventsRepositoryImpl(
            eventsDao = db.eventsDao(),
            db = db,
            transactionRunner = RoomTransactionRunner(db)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun removesOldestNormalEventsAndKeepsImportantOnes() = runBlocking {
        insert(
            "a" to NORMAL,
            "b" to NORMAL,
            "c" to NORMAL,
            "begin_checkout" to HIGH,
            "d" to NORMAL,
            "purchase" to HIGH,
        )

        val result = repository.deleteOldestEventsThatExceed(4)

        assertEquals(listOf("c", "begin_checkout", "d", "purchase"), remainingNames())
        assertEquals(4, result?.limit)
        assertEquals(6, result?.sizeBefore)
        assertEquals(2, result?.removed)
        assertEquals(
            setOf(EvictedEventsGroup("a", NORMAL, 1), EvictedEventsGroup("b", NORMAL, 1)),
            result?.groups?.toSet(),
        )
    }

    @Test
    fun removesOldestImportantEventsOnlyWhenNoNormalEventsLeft() = runBlocking {
        insert(
            "begin_checkout" to HIGH,
            "a" to NORMAL,
            "purchase" to HIGH,
            "add_to_cart" to HIGH,
        )

        val result = repository.deleteOldestEventsThatExceed(2)

        assertEquals(listOf("purchase", "add_to_cart"), remainingNames())
        assertEquals(
            setOf(
                EvictedEventsGroup("a", NORMAL, 1),
                EvictedEventsGroup("begin_checkout", HIGH, 1)
            ),
            result?.groups?.toSet(),
        )
    }

    @Test
    fun groupsRemovedEventsByNameAndImportance() = runBlocking {
        insert(
            "view_cart" to NORMAL,
            "view_cart" to NORMAL,
            "view_cart" to HIGH,
            "heartbeat" to NORMAL,
            "purchase" to HIGH,
        )

        val result = repository.deleteOldestEventsThatExceed(1)

        assertEquals(listOf("purchase"), remainingNames())
        assertEquals(
            setOf(
                EvictedEventsGroup("view_cart", NORMAL, 2),
                EvictedEventsGroup("heartbeat", NORMAL, 1),
                EvictedEventsGroup("view_cart", HIGH, 1),
            ),
            result?.groups?.toSet(),
        )
    }

    @Test
    fun returnsNullAndRemovesNothingWhenLimitIsNotExceeded() = runBlocking {
        insert("a" to NORMAL, "b" to HIGH)

        assertNull(repository.deleteOldestEventsThatExceed(2))
        assertEquals(listOf("a", "b"), remainingNames())
    }

    @Test
    fun secondEvictionIsNoOp() = runBlocking {
        insert("a" to NORMAL, "b" to NORMAL, "c" to NORMAL)
        repository.deleteOldestEventsThatExceed(2)

        assertNull(repository.deleteOldestEventsThatExceed(2))
        assertEquals(listOf("b", "c"), remainingNames())
    }

    @Test
    fun keepsImportantEventsWhenDefaultLimitIsExceeded() = runBlocking {
        val important = setOf(10, 5_000, 9_999)
        db.eventsDao().add(
            (0 until 10_003).map { index ->
                event(
                    name = if (index in important) "purchase" else "event",
                    importance = if (index in important) HIGH else NORMAL,
                )
            }
        )

        val result = repository.deleteOldestEventsThatExceed(10_000)

        assertEquals(3, result?.removed)
        assertEquals(listOf(EvictedEventsGroup("event", NORMAL, 3)), result?.groups)
        assertEquals(3, countByName("purchase"))
        assertEquals(10_000, db.eventsDao().getCount())
    }

    @Test
    fun zeroLimitEvictsEverything() = runBlocking {
        insert("a" to NORMAL, "purchase" to HIGH)

        val result = repository.deleteOldestEventsThatExceed(0)

        assertEquals(2, result?.removed)
        assertEquals(emptyList<String>(), remainingNames())
    }

    @Test
    fun useEventsSequenceStreamsInPriorityOrderAndHonorsLimit() = runBlocking {
        insert(
            "a" to NORMAL,
            "b" to NORMAL,
            "important_1" to HIGH,
            "c" to NORMAL,
            "important_2" to HIGH,
        )

        val names = repository.useEventsSequence(
            apiData = ru.wildberries.pub.analytics.domain.ApiData(apiKey = "apiKey", apiUrl = "apiUrl"),
            limit = 3,
        ) { sequence ->
            sequence.map { it.name }.toList()
        }

        assertEquals(listOf("important_1", "important_2", "a"), names)
    }

    @Test
    fun useEventsSequenceSupportsEarlyBreakWithoutReadingAllItems() = runBlocking {
        insert(
            "a" to NORMAL,
            "b" to NORMAL,
            "c" to NORMAL,
        )

        val firstItem = repository.useEventsSequence(
            apiData = ru.wildberries.pub.analytics.domain.ApiData(apiKey = "apiKey", apiUrl = "apiUrl"),
            limit = 10,
        ) { sequence ->
            var result: String? = null
            var count = 0
            for (item in sequence) {
                result = item.name
                count++
                if (count == 1) {
                    break
                }
            }
            result
        }

        assertEquals("a", firstItem)
    }

    @Test
    fun useEventsSequenceHandlesSingleHeavyEventNear2MBLimit() = runBlocking {
        val payload = "x".repeat(1900 * 1024)
        val largeExtras = JsonObject(mapOf("heavy_data" to JsonPrimitive(payload)))
        val largeEvent = EventEntity(
            apiUrl = "apiUrl",
            apiKey = "apiKey",
            name = "huge_event",
            sessionValue = 0u,
            time = OffsetDateTime.now(),
            extras = largeExtras,
            importance = NORMAL,
        )
        db.eventsDao().add(largeEvent)

        val readSize = repository.useEventsSequence(
            apiData = ru.wildberries.pub.analytics.domain.ApiData(apiKey = "apiKey", apiUrl = "apiUrl"),
            limit = 1,
        ) { sequence ->
            val event = sequence.first()
            event.extras.toString().length
        }

        assert(readSize >= 1900 * 1024)
    }

    @Test
    fun useEventsSequenceStreamsBatchLargerThanCursorWindow2MB() = runBlocking {
        // 5 events of 900 KB each = 4.5 MB batch, exceeding default 2MB CursorWindow
        val payload = "x".repeat(900 * 1024)
        for (i in 1..5) {
            val event = EventEntity(
                apiUrl = "apiUrl",
                apiKey = "apiKey",
                name = "event_$i",
                sessionValue = 0u,
                time = OffsetDateTime.now(),
                extras = JsonObject(mapOf("data" to JsonPrimitive(payload))),
                importance = NORMAL,
            )
            db.eventsDao().add(event)
        }

        var totalEventsRead = 0
        var totalPayloadBytes = 0L

        repository.useEventsSequence(
            apiData = ru.wildberries.pub.analytics.domain.ApiData(apiKey = "apiKey", apiUrl = "apiUrl"),
            limit = 10,
        ) { sequence ->
            for (event in sequence) {
                totalEventsRead++
                totalPayloadBytes += event.extras.toString().length
            }
        }

        assertEquals(5, totalEventsRead)
        assert(totalPayloadBytes >= 4 * 1024 * 1024L)
    }

    private suspend fun insert(vararg events: Pair<String, Int>) {
        events.forEach { (name, importance) -> db.eventsDao().add(event(name, importance)) }
    }

    private fun event(name: String, importance: Int) = EventEntity(
        apiUrl = "apiUrl",
        apiKey = "apiKey",
        name = name,
        sessionValue = 0u,
        time = OffsetDateTime.now(),
        extras = JsonObject(emptyMap()),
        importance = importance,
    )

    private fun remainingNames(): List<String> = runBlocking {
        db.eventsDao().getAll(100_000).map { it.name }
    }

    private fun countByName(name: String): Int = runBlocking {
        db.eventsDao().getAll(100_000).count { it.name == name }
    }

    private companion object {
        const val NORMAL = EventEntity.IMPORTANCE_NORMAL
        const val HIGH = EventEntity.IMPORTANCE_HIGH
    }
}
