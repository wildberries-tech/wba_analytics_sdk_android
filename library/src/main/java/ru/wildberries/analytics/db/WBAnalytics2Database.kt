package ru.wildberries.analytics.db

import android.database.Cursor
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.serialization.json.JsonObject
import ru.wildberries.analytics.ApiKey
import ru.wildberries.analytics.ApiUrl
import ru.wildberries.analytics.DEFAULT_PROD_URL
import ru.wildberries.analytics.domain.ApiData
import java.time.OffsetDateTime

@Database(
    entities = [
        EventEntity::class,
        SentInfoEntity::class,
    ],
    version = 6,
    exportSchema = true
)
internal abstract class WBAnalytics2Database : RoomDatabase() {

    abstract fun eventsDao(): AnalyticsEventsDao
    abstract fun sentInfoDao(): SentInfoDao
}

@Entity(tableName = EventEntity.TABLE_NAME)
internal data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(defaultValue = DEFAULT_PROD_URL)
    val apiUrl: ApiUrl,
    val apiKey: ApiKey,
    val name: String,
    @field:TypeConverters(OffsetDateTimeConverter::class)
    val time: OffsetDateTime,
    @field:TypeConverters(KeyValueConverter::class)
    val extras: JsonObject,
) {

    companion object {

        const val TABLE_NAME = "EventEntity"
    }
}

@Dao
internal interface AnalyticsEventsDao {

    @Insert
    suspend fun add(item: EventEntity)

    @Query("SELECT * FROM EventEntity LIMIT :limit")
    suspend fun getAll(limit: Int): List<EventEntity>

    @Query("SELECT * FROM EventEntity WHERE apiKey = :apiKey AND apiUrl = :apiUrl LIMIT :limit")
    fun queryFirst(apiKey: ApiKey, apiUrl: ApiUrl, limit: Int): Cursor

    @Query("SELECT COUNT(*) FROM EventEntity")
    suspend fun getCount(): Int

    @Query("DELETE FROM EventEntity WHERE id IN (:ids)")
    suspend fun delete(ids: List<Int>)

    @Query("SELECT apiKey, apiUrl FROM EventEntity LIMIT 1")
    suspend fun getFirstApiData(): ApiData?

    @Insert
    fun add(items: List<EventEntity>)

    @Query("DELETE FROM EventEntity WHERE id IN (SELECT id FROM EventEntity LIMIT :count)")
    suspend fun deleteEvents(count: Int): Int
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
