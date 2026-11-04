package ru.wildberries.pub.analytics.db

import androidx.room.RoomDatabase
import androidx.room.deferredTransaction
import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection

internal class RoomTransactionRunner(private val db: RoomDatabase) : TransactionRunner {

    override suspend fun <R> withWriteTransaction(action: suspend () -> R): R =
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                action()
            }
        }

    override suspend fun <R> withReadTransaction(action: suspend () -> R): R =
        db.useReaderConnection { transactor ->
            transactor.deferredTransaction {
                action()
            }
        }
}
