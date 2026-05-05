package ru.wildberries.analytics.utils

import ru.wildberries.analytics.WBAnalytics2Logger

internal class TestLogger: WBAnalytics2Logger {
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