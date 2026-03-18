package ru.wildberries.analytics.sdk.demo.analytics.ui

class AnalyticsInputValidator {

    fun validateCounterId(input: String): Long {
        return input.filter { it.isDigit() }.toLongOrNull()?.coerceAtLeast(MIN_COUNTER_ID)
            ?: MIN_COUNTER_ID
    }

    fun validateEventName(name: String): String = validateIdentifier(name, MAX_EVENT_NAME_LENGTH)

    fun validateEventsPerSendCount(count: String): Int =
        validateInt(count, MIN_EVENTS_PER_SEND, MAX_EVENTS_PER_SEND)

    fun validateMaxEventsInBatch(value: String): Int =
        validateInt(value, MIN_BATCH_EVENTS, MAX_BATCH_EVENTS)

    fun validateMaxBatchSizeInBytes(value: String): Int =
        validateInt(value, MIN_BATCH_SIZE_BYTES, MAX_BATCH_SIZE_BYTES)

    fun validateDelaySeconds(value: String): Int =
        validateInt(value, MIN_DELAY_SECONDS, MAX_DELAY_SECONDS)

    fun validateMaxEventsInCache(value: String): Int =
        validateInt(value, MIN_CACHE_EVENTS, MAX_CACHE_EVENTS)

    fun validateParameterKey(key: String): String = validateIdentifier(key, MAX_PARAM_KEY_LENGTH)

    private fun validateIdentifier(input: String, maxLength: Int): String {
        val filtered =
            input.filter { it in 'a'..'z' || it in 'A'..'Z' || it == '_' || it.isDigit() }
        return filtered.dropWhile { it.isDigit() }.take(maxLength)
    }

    private fun validateInt(value: String, min: Int, max: Int): Int =
        value.toIntOrNull()?.coerceIn(min, max) ?: min

    companion object {
        const val MIN_COUNTER_ID = 0L
        const val MIN_EVENTS_PER_SEND = 1
        const val MAX_EVENTS_PER_SEND = 100
        const val MIN_BATCH_EVENTS = 1
        const val MAX_BATCH_EVENTS = 200
        const val MIN_BATCH_SIZE_BYTES = 1024
        const val MAX_BATCH_SIZE_BYTES = 4194304
        const val MIN_DELAY_SECONDS = 0
        const val MAX_DELAY_SECONDS = 60
        const val MIN_CACHE_EVENTS = 1
        const val MAX_CACHE_EVENTS = 10000
        const val MAX_PARAM_KEY_LENGTH = 40
        const val MAX_EVENT_NAME_LENGTH = 120
    }
}
