package ru.wildanalytics.pub.analytics.event

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.wildanalytics.pub.analytics.db.EventEntity
import ru.wildanalytics.pub.analytics.db.EvictedEventsGroup
import ru.wildanalytics.pub.analytics.db.WildAnalyticsDatabase
import java.time.OffsetDateTime

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
internal class EventsRepositoryEvictionTest {

    private lateinit var db: WildAnalyticsDatabase
    private lateinit var repository: EventsRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            context = ApplicationProvider.getApplicationContext(),
            klass = WildAnalyticsDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = EventsRepositoryImpl(eventsDao = db.eventsDao(), db = db)
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
            setOf(EvictedEventsGroup("a", NORMAL, 1), EvictedEventsGroup("begin_checkout", HIGH, 1)),
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

    private fun remainingNames(): List<String> =
        db.openHelper.readableDatabase.query("SELECT name FROM EventEntity ORDER BY id ASC").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun countByName(name: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM EventEntity WHERE name = ?", arrayOf(name)).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val NORMAL = EventEntity.IMPORTANCE_NORMAL
        const val HIGH = EventEntity.IMPORTANCE_HIGH
    }
}
