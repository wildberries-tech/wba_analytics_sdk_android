package ru.wildanalytics.pub.analytics.device

import android.annotation.SuppressLint
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import ru.wildanalytics.pub.analytics.logDebug
import ru.wildanalytics.pub.analytics.logException

/**
 * Провайдер маркетинговой модели.
 *
 * В Android нет публичного API для "человеческого" названия устройства вроде "Galaxy S21 5G".
 * [android.os.Build.MODEL] даёт только модельный идентификатор (например, "SM-G991B").
 * Некоторые OEM хранят маркетинговое название в недокументированных системных свойствах,
 * поэтому читаем их через рефлексию. При неудаче возвращаем пустую строку.
 */
@SuppressLint("PrivateApi")
internal class WildMarketingModelProviderImpl(
    private val log: WildAnalyticsLogger,
) : WildMarketingModelProvider {

    override val marketingModel: String by lazy {
        try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getMethod("get", String::class.java)
            val keys = listOf(
                "ro.product.marketname",
                "ro.config.marketing_name",
                "ro.oplus.market.name",
                "ro.vivo.market.name",
                "ro.oppo.market.name",
                "ro.product.display"
            )
            for (key in keys) {
                val value = getMethod.invoke(null, key) as? String
                if (!value.isNullOrBlank()) {
                    log.logDebug { "Marketing model resolved from $key: $value" }
                    return@lazy value
                }
            }
            log.logDebug { "Marketing model not found in system properties" }
            ""
        } catch (e: Throwable) {
            log.logException { Exception("Failed to resolve marketing model", e) }
            ""
        }
    }
}
