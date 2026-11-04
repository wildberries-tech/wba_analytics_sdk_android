package ru.wildberries.pub.analytics.db

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.serialization.json.JsonObject
import ru.wildberries.pub.analytics.ApiKey
import ru.wildberries.pub.analytics.ApiUrl
import ru.wildberries.pub.analytics.DEFAULT_PROD_URL
import ru.wildberries.pub.analytics.domain.ApiData
import java.time.OffsetDateTime

@Database(
    entities = [
        EventEntity::class,
        SentInfoEntity::class,
    ],
    version = 7,
    autoMigrations = [
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
    exportSchema = true
)
@TypeConverters(
    OffsetDateTimeConverter::class,
    KeyValueConverter::class,
    ULongConverter::class,
)
internal abstract class WBAnalytics2Database : RoomDatabase() {

    abstract fun eventsDao(): AnalyticsEventsDao
    abstract fun sentInfoDao(): SentInfoDao
}

@Entity(
    tableName = EventEntity.TABLE_NAME,
    indices = [
        Index(
            value = ["importance", "id"],
            orders = [Index.Order.DESC, Index.Order.ASC]
        )
    ]
)
internal data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(defaultValue = DEFAULT_PROD_URL)
    val apiUrl: ApiUrl,
    val apiKey: ApiKey,
    val name: String,
    @ColumnInfo(defaultValue = "0")
    val sessionValue: ULong,
    @field:TypeConverters(OffsetDateTimeConverter::class)
    val time: OffsetDateTime,
    @field:TypeConverters(KeyValueConverter::class)
    val extras: JsonObject,
    @ColumnInfo(defaultValue = IMPORTANCE_NORMAL.toString())
    val importance: Int = IMPORTANCE_NORMAL,
) {

    companion object {

        const val TABLE_NAME = "EventEntity"

        /**
         * Обычный уровень важности события.
         */
        const val IMPORTANCE_NORMAL = 0

        /**
         * Высокий уровень важности события. Такие события отправляются в первую очередь.
         */
        const val IMPORTANCE_HIGH = 100
    }
}

@Dao
internal interface AnalyticsEventsDao {

    @Insert
    suspend fun add(item: EventEntity)

    @Query("SELECT * FROM EventEntity LIMIT :limit")
    suspend fun getAll(limit: Int): List<EventEntity>

    @Query("SELECT COUNT(*) FROM EventEntity")
    suspend fun getCount(): Int

    @Query("DELETE FROM EventEntity WHERE id IN (:ids)")
    suspend fun delete(ids: List<Int>)

    @Query("SELECT apiKey, apiUrl FROM EventEntity ORDER BY importance DESC, id ASC LIMIT 1")
    suspend fun getFirstApiData(): ApiData?

    @Insert
    fun add(items: List<EventEntity>)

    // Без ORDER BY SQLite выбирает строки по индексу (importance DESC) и удаляет важные события первыми.
    // Удаляем сначала самые старые обычные, важные — в последнюю очередь.
    @Query("DELETE FROM EventEntity WHERE id IN (SELECT id FROM EventEntity ORDER BY importance ASC, id ASC LIMIT :count)")
    suspend fun deleteEvents(count: Int): Int

    @Query(
        "SELECT name, importance, COUNT(*) AS count FROM EventEntity " +
            "WHERE id IN (SELECT id FROM EventEntity ORDER BY importance ASC, id ASC LIMIT :count) " +
            "GROUP BY name, importance"
    )
    suspend fun getEvictionCandidates(count: Int): List<EvictedEventsGroup>

    @Query("SELECT id, name, length(CAST(extras AS BLOB)) AS size FROM EventEntity WHERE length(CAST(extras AS BLOB)) > :maxSizeInBytes")
    suspend fun getOversizedEvents(maxSizeInBytes: Long): List<OversizedEvent>
}

@Entity
internal data class SentInfoEntity(
    /** synthetic id*/
    @PrimaryKey val id: Int = 0,
    val batchesSent: Long = 0,
    val eventsSent: Long = 0,
) {

    fun incrementSentInfo(eventsSentInBatch: Int): SentInfoEntity =
        copy(batchesSent = batchesSent + 1, eventsSent = eventsSent + eventsSentInBatch)
}

@Dao
internal interface SentInfoDao {

    @Query("SELECT * FROM SentInfoEntity")
    suspend fun getSentInfo(): SentInfoEntity?

    @Upsert
    suspend fun upsert(info: SentInfoEntity)
}

internal data class EvictedEventsGroup(
    val name: String,
    val importance: Int,
    val count: Int,
)

internal data class OversizedEvent(
    val id: Int,
    val name: String,
    val size: Long,
)
