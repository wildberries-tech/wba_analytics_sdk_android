package ru.wildanalytics.pub.analytics.sdk.demo.sdk

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.wildanalytics.pub.analytics.WildAnalyticsServiceLocator

/** Чтение очереди SDK напрямую из SQLite и закрытие БД SDK для QA-сценария. */
class SdkDatabaseInspector(private val context: Context) {

    fun readStats(): QueueStats? {
        val file = context.getDatabasePath(DATABASE_NAME)
        if (!file.exists()) return null
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT COUNT(*), IFNULL(SUM(importance > 0), 0) FROM EventEntity", null).use { cursor ->
                cursor.moveToFirst()
                QueueStats(total = cursor.getLong(0), important = cursor.getLong(1))
            }
        }
    }

    fun closeSdkDatabase() {
        val locator = WildAnalyticsServiceLocator.getInstance(context)
        val database = locator.get(Class.forName(SDK_DATABASE_CLASS))
        database.javaClass.getMethod("close").invoke(database)
    }

    fun deleteSdkDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }
}

/** Опрос очереди: `null` — БД ещё нет; при ошибке чтения остаётся прошлое значение. */
fun pollQueue(intervalMillis: Long, read: () -> QueueStats?): Flow<QueueStats?> = flow {
    while (true) {
        runCatching(read)
            .onSuccess { emit(it) }
            .onFailure { Log.w(TAG, "Failed to read SDK queue", it) }
        delay(intervalMillis)
    }
}

data class QueueStats(
    val total: Long,
    val important: Long,
)

private const val TAG = "WildAnalytics"
private const val DATABASE_NAME = "ru.wildanalytics.pub.analytics.db"
private const val SDK_DATABASE_CLASS = "ru.wildanalytics.pub.analytics.db.WildAnalyticsDatabase"
