package ru.wildanalytics.pub.analytics.db

internal interface TransactionRunner {

    suspend fun <R> withTransaction(action: suspend () -> R): R
}
