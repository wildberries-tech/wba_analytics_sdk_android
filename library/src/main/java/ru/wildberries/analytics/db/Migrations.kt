package ru.wildberries.analytics.db

import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal class Migrations {

    val all: Array<Migration> = emptyArray()
}

internal class AutoMigration4to5 : AutoMigrationSpec {

    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO SentInfoEntity (`id`,`batchesSent`, `eventsSent`) values (0, 0, 0)")
    }
}
