package ru.wildberries.pub.analytics

import ru.wildberries.pub.analytics.db.TransactionRunner

internal class TestTransactionRunner : TransactionRunner {

    override suspend fun <R> withWriteTransaction(action: suspend () -> R): R = action()

    override suspend fun <R> withReadTransaction(action: suspend () -> R): R = action()
}
