package ru.wildanalytics.pub.analytics.db

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import ru.wildanalytics.pub.analytics.CoroutineScopeFactory
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import ru.wildanalytics.pub.analytics.util.OldDatabaseMigrationHelper

internal object WildAnalyticsDatabaseFactory {
    private const val DB_NAME = "ru.wildanalytics.pub.analytics.db"

    @OptIn(ExperimentalCoroutinesApi::class)
    fun create(
        context: Context,
        coroutineScopeFactory: CoroutineScopeFactory,
        logger: WildAnalyticsLogger,
    ): WildAnalyticsDatabase {
        val shouldImportFromOldDatabase = !context.getDatabasePath(DB_NAME).exists()

        @Suppress("SpreadOperator")
        val db = Room.databaseBuilder(
            context = context,
            name = DB_NAME,
            factory = { WildAnalyticsDatabase_Impl() }
        )
            .setQueryCoroutineContext(Dispatchers.IO.limitedParallelism(4))
            .fallbackToDestructiveMigration(true)
            .addMigrations(*Migrations().all)
            .build()

        if (shouldImportFromOldDatabase) {
            coroutineScopeFactory.create("OldDatabaseImport").launch {
                OldDatabaseMigrationHelper.importFromOldDatabase(context, db, logger)
            }
        }
        return db
    }
}
