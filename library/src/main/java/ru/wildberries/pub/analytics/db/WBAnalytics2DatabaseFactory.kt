package ru.wildberries.pub.analytics.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import ru.wildberries.pub.analytics.CoroutineScopeFactory
import ru.wildberries.pub.analytics.WBAnalytics2Logger
import ru.wildberries.pub.analytics.util.OldDatabaseMigrationHelper

internal object WBAnalytics2DatabaseFactory {
    private const val DB_NAME = "ru.wildberries.pub.analytics.db"

    @OptIn(ExperimentalCoroutinesApi::class)
    fun create(
        context: Context,
        coroutineScopeFactory: CoroutineScopeFactory,
        logger: WBAnalytics2Logger,
    ): WBAnalytics2Database {
        val shouldImportFromOldDatabase = !context.getDatabasePath(DB_NAME).exists()

        @Suppress("SpreadOperator")
        val db = Room.databaseBuilder(
            context = context,
            name = DB_NAME,
            factory = { WBAnalytics2Database_Impl() }
        )
            .setDriver(AndroidSQLiteDriver())
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
