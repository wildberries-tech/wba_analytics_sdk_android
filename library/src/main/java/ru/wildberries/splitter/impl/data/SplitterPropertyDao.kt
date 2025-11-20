package ru.wildberries.splitter.impl.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
internal interface SplitterPropertyDao {

    @Query("SELECT * FROM splitter_properties WHERE apiKey = :apiKey")
    suspend fun getProperties(apiKey: String): List<SplitterPropertyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProperties(properties: List<SplitterPropertyEntity>)

    @Query("DELETE FROM splitter_properties WHERE apiKey = :apiKey")
    suspend fun deleteProperties(apiKey: String)

    @Transaction
    suspend fun replaceProperties(apiKey: String, properties: List<SplitterPropertyEntity>) {
        deleteProperties(apiKey)
        insertProperties(properties)
    }
}