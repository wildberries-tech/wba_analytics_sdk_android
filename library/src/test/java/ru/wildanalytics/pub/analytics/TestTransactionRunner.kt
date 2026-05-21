package ru.wildanalytics.pub.analytics

import ru.wildanalytics.pub.analytics.db.TransactionRunner

internal class TestTransactionRunner : TransactionRunner {

    override suspend fun <R> withTransaction(action: suspend () -> R): R = action()
}
