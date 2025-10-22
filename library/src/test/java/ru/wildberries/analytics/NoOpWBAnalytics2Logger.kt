package ru.wildberries.analytics

internal object NoOpWBAnalytics2Logger : WBAnalytics2Logger {
    override val isEnabled: Boolean = true

    override fun logDebug(message: String) = Unit

    override fun logException(e: Exception) = Unit

    override fun logError(
        e: Error,
        details: Map<String, String>
    ) = Unit
}