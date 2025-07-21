package ru.wildberries.attribution.api

internal interface WBAttributionLogger {
    fun logDebug(error: Throwable? = null, lazyMessage: () -> String)
    fun logError(error: Throwable? = null, lazyMessage: () -> String)
}