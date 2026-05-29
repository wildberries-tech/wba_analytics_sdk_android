package ru.wildanalytics.pub.attribution.api

internal interface WildAttributionLogger {
    fun logDebug(error: Throwable? = null, lazyMessage: () -> String)
    fun logError(error: Throwable? = null, lazyMessage: () -> String)
}