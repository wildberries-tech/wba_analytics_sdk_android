package ru.wildberries.splitter.impl.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SplitterPropertyEntity::class], version = 1, exportSchema = true)
internal abstract class SplitterDatabase : RoomDatabase() {
    abstract fun splitterPropertyDao(): SplitterPropertyDao

    companion object {

        @Volatile
        private var Instance: SplitterDatabase? = null

        fun getInstance(context: Context): SplitterDatabase = Instance ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                SplitterDatabase::class.java,
                "ru.wildberries.splitter.lib.db"
            ).build()
            Instance = instance
            instance
        }
    }
}