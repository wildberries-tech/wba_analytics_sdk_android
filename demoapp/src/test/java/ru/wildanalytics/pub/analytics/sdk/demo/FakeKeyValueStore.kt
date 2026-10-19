package ru.wildanalytics.pub.analytics.sdk.demo

import ru.wildanalytics.pub.analytics.sdk.demo.core.LogEntry
import ru.wildanalytics.pub.analytics.sdk.demo.core.MarkerParser
import ru.wildanalytics.pub.analytics.sdk.demo.core.RequestRecord
import ru.wildanalytics.pub.analytics.sdk.demo.qa.KeyValueStore

class FakeKeyValueStore(private val values: MutableMap<String, String> = mutableMapOf()) : KeyValueStore {
    override fun get(key: String): String? = values[key]
    override fun all(): Map<String, String> = values.toMap()
    override fun put(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
    override fun clear() = values.clear()
}

fun logEntry(
    timestampMillis: Long,
    message: String,
    details: Map<String, String> = emptyMap(),
    id: Long = timestampMillis,
): LogEntry {
    val parsed = MarkerParser.parse(message, details)
    return LogEntry(id, timestampMillis, LogEntry.Level.WARN, parsed.name, parsed.text, parsed.attributes, null)
}

fun request(
    timestampMillis: Long,
    host: String,
    code: Int? = null,
    error: String? = null,
    isMock: Boolean = false,
    id: Long = timestampMillis,
) = RequestRecord(id, timestampMillis, host, code, error, isMock)
