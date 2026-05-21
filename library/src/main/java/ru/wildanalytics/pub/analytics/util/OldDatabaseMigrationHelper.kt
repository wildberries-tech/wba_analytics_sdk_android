package ru.wildanalytics.pub.analytics.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import ru.wildanalytics.pub.analytics.db.AnalyticsEventsDao
import ru.wildanalytics.pub.analytics.db.EventEntity
import ru.wildanalytics.pub.analytics.db.SentInfoDao
import ru.wildanalytics.pub.analytics.db.SentInfoEntity
import ru.wildanalytics.pub.analytics.db.WildAnalyticsDatabase
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal object OldDatabaseMigrationHelper {
    private const val OLD_DB_NAME = "ru.wildberries.analytics.db"

    /**
     * Выполняет копирование данных из старой БД в новую через DAO.
     */
    suspend fun importFromOldDatabase(
        context: Context,
        newDb: WildAnalyticsDatabase,
        logger: WildAnalyticsLogger,
    ) = withContext(Dispatchers.IO) {
        val oldDbFile = context.getDatabasePath(OLD_DB_NAME)
        if (!oldDbFile.exists()) return@withContext

        val hasPrivateLib = try {
            Class.forName("ru.wildberries.analytics.WBAnalytics2")
            true
        } catch (_: Exception) {
            false
        }

        try {
            logger.logDebug("Starting data import from $OLD_DB_NAME")

            SQLiteDatabase.openDatabase(oldDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                .use { oldDb ->
                    syncSentInfo(oldDb, newDb.sentInfoDao(), logger)

                    if (!hasPrivateLib) {
                        syncEvents(oldDb, newDb.eventsDao(), logger)
                    }
                }

            logger.logDebug("Import completed successfully")
        } catch (e: Exception) {
            logger.logException(e)
        } finally {
            // Удаляем старый файл, если мы единственные владельцы (даже если импорт упал)
            if (!hasPrivateLib) {
                try {
                    if (context.deleteDatabase(OLD_DB_NAME)) {
                        logger.logDebug("Old database deleted")
                    } else{
                        logger.logDebug("Old database not deleted")
                    }
                } catch (e: Exception){
                    logger.logDebug("Failed to delete old database")
                    logger.logException(e)
                }
            }
        }
    }

    private suspend fun syncSentInfo(
        oldDb: SQLiteDatabase,
        sentInfoDao: SentInfoDao,
        logger: WildAnalyticsLogger,
    ) {
        oldDb.rawQuery("SELECT batchesSent, eventsSent FROM SentInfoEntity LIMIT 1", null)
            .use { cursor ->
                if (cursor.moveToFirst()) {
                    val stats = SentInfoEntity(
                        batchesSent = cursor.getLong(0),
                        eventsSent = cursor.getLong(1)
                    )
                    sentInfoDao.upsert(stats)
                    logger.logDebug("SentInfo synced: batches=${stats.batchesSent}, events=${stats.eventsSent}")
                }
            }
    }

    private suspend fun syncEvents(
        oldDb: SQLiteDatabase,
        eventsDao: AnalyticsEventsDao,
        logger: WildAnalyticsLogger,
    ) {
        oldDb.rawQuery(
            "SELECT apiUrl, apiKey, name, time, extras, importance FROM EventEntity",
            null
        ).use { cursor ->
            val batch = mutableListOf<EventEntity>()
            val json = Json { ignoreUnknownKeys = true }
            val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

            while (cursor.moveToNext()) {
                try {
                    batch.add(
                        EventEntity(
                            apiUrl = cursor.getString(0),
                            apiKey = cursor.getString(1),
                            name = cursor.getString(2),
                            time = OffsetDateTime.parse(cursor.getString(3), formatter),
                            extras = json.decodeFromString<JsonObject>(cursor.getString(4)),
                            importance = cursor.getInt(5)
                        )
                    )
                } catch (e: Exception) {
                    logger.logException(e)
                }

                if (batch.size >= 100) {
                    eventsDao.add(batch)
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                eventsDao.add(batch)
            }
            logger.logDebug("Events synced")
        }
    }
}
