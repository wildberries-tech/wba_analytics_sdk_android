package ru.wildanalytics.pub.analytics.sdk.demo.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import java.util.concurrent.atomic.AtomicLong

/** Единственный логгер SDK в demo app: журнал записей и счётчики маркеров `wba_*`. */
class SdkLogger : WildAnalyticsLogger {

    private val nextId = AtomicLong()
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    override val isEnabled: Boolean = true

    override fun logDebug(message: String) {
        Log.d(TAG, message)
    }

    override fun logException(e: Exception) {
        Log.w(TAG, "", e)
        append(LogEntry.Level.EXCEPTION, e.toString(), emptyMap(), e.cause?.toString())
    }

    override fun logError(e: Error, details: Map<String, String>) {
        Log.e(TAG, details.toString(), e)
        append(LogEntry.Level.ERROR, e.message.orEmpty(), details, e.cause?.toString())
    }

    override fun logWarn(message: String) {
        Log.w(TAG, message)
        append(LogEntry.Level.WARN, message, emptyMap(), null)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    private fun append(level: LogEntry.Level, message: String, details: Map<String, String>, cause: String?) {
        val parsed = MarkerParser.parse(message, details)
        val entry = LogEntry(
            id = nextId.incrementAndGet(),
            timestampMillis = System.currentTimeMillis(),
            level = level,
            marker = parsed.name,
            text = parsed.text,
            attributes = parsed.attributes,
            cause = cause,
        )
        _entries.update { (listOf(entry) + it).take(MAX_ENTRIES) }
    }
}

data class LogEntry(
    val id: Long,
    val timestampMillis: Long,
    val level: Level,
    val marker: String?,
    val text: String,
    val attributes: Map<String, String>,
    val cause: String?,
) {
    enum class Level { WARN, ERROR, EXCEPTION }
}

fun List<LogEntry>.markerCounts(): Map<String, Int> =
    mapNotNull { it.marker }.groupingBy { it }.eachCount()

private const val TAG = "WildAnalytics"
private const val MAX_ENTRIES = 500
