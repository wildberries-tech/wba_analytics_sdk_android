package ru.wildberries.analytics

public interface WBAnalytics2Logger {

    public val isEnabled: Boolean

    public fun logDebug(message: String)

    public fun logException(e: Exception)

    public fun logError(e: Error, details: Map<String, String>)

    public fun logWarn(message: String)
}

internal inline fun WBAnalytics2Logger.logDebug(lambda: () -> String) {
    if (isEnabled) {
        logDebug(lambda())
    }
}

internal inline fun WBAnalytics2Logger.logException(lambda: () -> Exception) {
    if (isEnabled) {
        logException(lambda())
    }
}

internal class ErrorLogBuilder {
    private var error: Error? = null
    private val details = mutableMapOf<String, String>()

    fun error(error: Error) {
        this.error = error
    }

    fun detail(key: String, value: String) {
        details[key] = value
    }

    fun build(): Pair<Error, Map<String, String>> {
        val finalError = error ?: throw IllegalStateException("Error must be set")
        return finalError to details
    }
}

internal inline fun WBAnalytics2Logger.logError(builder: ErrorLogBuilder.() -> Unit) {
    if (isEnabled) {
        val errorLogBuilder = ErrorLogBuilder()
        errorLogBuilder.builder()
        val (error, details) = errorLogBuilder.build()
        logError(error, details)
    }
}

internal inline fun WBAnalytics2Logger.logWarn(lazyMessage: () -> String) {
    if (isEnabled) {
        logWarn(lazyMessage())
    }
}
