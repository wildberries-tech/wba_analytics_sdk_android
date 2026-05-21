package ru.wildanalytics.pub.analytics.utils

import ru.wildanalytics.pub.analytics.WildAnalyticsLogger

internal class TestLogger: WildAnalyticsLogger {
    override val isEnabled: Boolean = true

    override fun logDebug(message: String) {
        print(message)
    }

    override fun logException(e: Exception) {
        e.printStackTrace()
    }

    override fun logError(
        e: Error,
        details: Map<String, String>
    ) {
        e.printStackTrace()
    }

    override fun logWarn(message: String) {
        print("W! $message")
    }
}