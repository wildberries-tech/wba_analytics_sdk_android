package ru.wildberries.analytics

import ru.wildberries.analytics.db.TransactionRunner

internal class TestTransactionRunner : TransactionRunner {

    override suspend fun <R> withTransaction(action: suspend () -> R): R = action()
}
