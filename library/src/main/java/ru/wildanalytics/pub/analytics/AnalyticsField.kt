package ru.wildanalytics.pub.analytics

import kotlinx.serialization.json.JsonElement

/**
 * Поле (пара ключ/значение), которое [EventEnricher] добавляет к событию аналитики.
 */
public class AnalyticsField(
    public val key: String,
    public val value: JsonElement,
)
