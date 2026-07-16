package ru.wildanalytics.pub.analytics

import androidx.annotation.Size
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers

internal const val MAX_EVENT_NAME_LENGTH = 120L

public interface WildAnalytics {

    /**
     * Разрешает приём событий.
     */
    public var isCollectionEnabled: Boolean

    /**
     * Записывает событие аналитики.
     * Более продвинутая версия, которая позволяет отправлять более сложные события.
     *
     * Например:
     * ```
     * logEvent("purchase", buildJsonObject {
     *     put("totalPrice", 8300)
     *     put("currency", "RUB")
     *     put("products", buildJsonArray {
     *         add(buildJsonObject {
     *             put("article", 1234)
     *             put("price", 1500)
     *             put("currency", "RUB")
     *         })
     *         add(buildJsonObject {
     *             put("article", 5678)
     *             put("price", 6800)
     *             put("currency", "RUB")
     *         })
     *     })
     * })
     * ```
     */
    public fun logEvent(@Size(min = 1, max = MAX_EVENT_NAME_LENGTH) name: String, parameters: JsonObject = JsonObject(emptyMap()))

    /**
     * Записывает событие аналитики с высоким приоритетом.
     * Такие события будут отправлены раньше обычных.
     *
     * Аналогично [logEvent], эта версия позволяет отправлять более сложные события.
     *
     * ВАЖНО: Не злоупотребляйте важными событиями. Если помечать все события как важные,
     * логика приоритетности перестанет работать эффективно.
     */
    public fun logImportantEvent(@Size(min = 1, max = MAX_EVENT_NAME_LENGTH) name: String, parameters: JsonObject = JsonObject(emptyMap()))

    /**
     * Записывает событие аналитики.
     */
    public fun logEvent(@Size(min = 1, max = MAX_EVENT_NAME_LENGTH) name: String, parameters: Map<String, String>)

    /**
     * Записывает событие аналитики с высоким приоритетом.
     * Такие события будут отправлены раньше обычных.
     *
     * ВАЖНО: Не злоупотребляйте важными событиями. Если помечать все события как важные,
     * логика приоритетности перестанет работать эффективно.
     */
    public fun logImportantEvent(@Size(min = 1, max = MAX_EVENT_NAME_LENGTH) name: String, parameters: Map<String, String>)

    /**
     * Завершает приём событий этой аналитикой навсегда.
     */
    public fun finish()

    /**
     * Устанавливает параметр, который будет добавлен к каждому событию при создании события.
     *
     * Функция thread-safe.
     *
     * @param value Если значение `null`, то [key] удаляется из списка общих параметров.
     */
    public fun setCommonParameter(@Size(min = 1, max = 40) key: String, value: String?)

    /**
     * Устанавливает несколько общих параметров.
     *
     * Функция thread-safe.
     *
     * Эту функцию предпочтительнее использовать, чем множество вызовов
     * [setCommonParameter] т.к. она это делает более оптимально.
     */
    public fun setCommonParameters(src: Map<String, String?>)

    /**
     * Устанавливает HTTP-заголовок, добавляемый к каждому запросу аналитики.
     *
     * Частный случай — антибот-токен: `setCustomHeader("X-Wbaas-Token", token)`.
     *
     * Функция thread-safe.
     *
     * @param value Если значение `null`, то [key] удаляется из набора кастомных заголовков.
     */
    public fun setCustomHeader(@Size(min = 1) key: String, value: String?)

    /**
     * Добавляет несколько кастомных HTTP-заголовков (merge/upsert).
     *
     * Для каждого имени из [headers] его значения заменяют текущие значения этого
     * имени; имена, которых нет в [headers], остаются без изменений. Одно имя может
     * содержать несколько значений (HTTP-заголовки — мультимапа).
     *
     * Удаление заголовка делается через [setCustomHeader] с `value == null`
     * (иммутабельный [Headers] не выражает удаление).
     *
     * Функция thread-safe.
     *
     * Эту функцию предпочтительнее использовать, чем множество вызовов [setCustomHeader].
     */
    public fun setCustomHeaders(headers: Headers)

    /**
     * Регистрирует [EventEnricher], который может добавить одно или несколько
     * полей к каждому событию аналитики.
     *
     * Enricher вызывается асинхронно в момент обработки события (не на потоке
     * вызывающего [logEvent]) и НЕ может перезатереть существующие параметры
     * события или общие параметры — только добавить новые.
     *
     * При совпадении ключей побеждает первый: между enricher'ами — добавленный
     * первым, внутри возвращённого списка — первое вхождение ключа.
     *
     * Функция thread-safe.
     */
    public fun addEventEnricher(enricher: EventEnricher)
}
