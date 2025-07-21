package ru.wildberries.analytics.db

internal interface TransactionRunner {

    suspend fun <R> withTransaction(action: suspend () -> R): R
}
