package ru.wildberries.splitter.impl

import android.util.Log

/**
 * Interface for logging debug messages inside Splitter SDK.
 */
internal interface SplitterLogger {
    fun logDebug(message: () -> String)
    fun logError(throwable: Throwable, message: () -> String)
}

internal class SystemSplitterLogger(private val tag: String?) : SplitterLogger {

    override fun logDebug(message: () -> String) {
        Log.d(tag, message())
    }

    override fun logError(throwable: Throwable, message: () -> String) {
        Log.e(tag, message(), throwable)
    }
}

internal class NoOpSplitterLogger : SplitterLogger {
    override fun logDebug(message: () -> String) = Unit

    override fun logError(throwable: Throwable, message: () -> String) = Unit
}
