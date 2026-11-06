package ru.wildberries.pub.analytics.db

internal interface TransactionRunner {

    suspend fun <R> withWriteTransaction(action: suspend () -> R): R

    suspend fun <R> withReadTransaction(action: suspend () -> R): R
}
