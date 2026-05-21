package ru.wildanalytics.pub.analytics

internal object NoOpWildAnalyticsLogger : WildAnalyticsLogger {
    override val isEnabled: Boolean = true

    override fun logDebug(message: String) = Unit

    override fun logException(e: Exception) = Unit

    override fun logError(
        e: Error,
        details: Map<String, String>
    ) = Unit

    override fun logWarn(message: String) = Unit
}
