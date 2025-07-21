package ru.wildberries.analytics.db

import androidx.room.RoomDatabase
import androidx.room.withTransaction

internal class RoomTransactionRunner(private val db: RoomDatabase) : TransactionRunner {

    override suspend fun <R> withTransaction(action: suspend () -> R): R = db.withTransaction(action)
}
