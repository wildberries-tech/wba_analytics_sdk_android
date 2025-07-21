package ru.wildberries.analytics

import androidx.annotation.Size
import kotlinx.serialization.json.JsonObject

public interface WBAnalytics2 {

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
    public fun logEvent(@Size(min = 1, max = 40) name: String, parameters: JsonObject = JsonObject(emptyMap()))

    /**
     * Записывает событие аналитики.
     */
    public fun logEvent(@Size(min = 1, max = 40) name: String, parameters: Map<String, String>)

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
}
