package ru.wildanalytics.pub.analytics

import kotlinx.serialization.json.JsonObject

/**
 * Дообогащает событие аналитики дополнительными полями.
 *
 * Вызывается в момент обработки события (не на потоке вызывающего
 * [WildAnalytics.logEvent]). Возвращает список полей для добавления; пустой
 * список означает, что для данного события добавлять нечего.
 *
 * Гарантии:
 * - enricher НЕ может перезатереть существующие параметры события (исходные
 *   параметры и общие параметры неприкосновенны), только добавить новые;
 * - при совпадении ключей побеждает первый: между enricher'ами — добавленный
 *   первым, внутри списка — первое вхождение ключа.
 *
 * @param eventName имя события.
 * @param parameters текущие параметры события (только для чтения, для принятия решения).
 */
public fun interface EventEnricher {

    public suspend fun enrich(eventName: String, parameters: JsonObject): List<AnalyticsField>
}
