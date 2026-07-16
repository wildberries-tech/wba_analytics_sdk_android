package ru.wildanalytics.pub.analytics

import kotlinx.serialization.json.JsonObject

/**
 * Хранит зарегистрированные [EventEnricher] и применяет их к событию.
 */
internal interface EventEnricherRegistry {

    /**
     * Регистрирует ещё один enricher. Thread-safe.
     */
    fun append(enricher: EventEnricher)

    /**
     * Применяет все enricher'ы к [parameters] и возвращает дообогащённый набор.
     *
     * Исходные [parameters] не модифицируются: добавляются только поля, чьих
     * ключей ещё нет. Если enricher'ов нет — возвращается тот же экземпляр.
     */
    suspend fun enrich(eventName: String, parameters: JsonObject): JsonObject

    /**
     * Удаляет все enricher'ы. Используется при [WildAnalytics.finish].
     */
    fun clear()
}
