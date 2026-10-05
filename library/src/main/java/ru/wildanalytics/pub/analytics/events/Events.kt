package ru.wildanalytics.pub.analytics.events

import android.net.Uri
import ru.wildanalytics.pub.analytics.WildAnalytics

/**
 * Отправляет информацию о ссылке, которую перехватило приложение (dynamic_link_app_open).
 * Обязательно для корректной атрибуции переходов.
 */
public fun WildAnalytics.logDeepLink(uri: Uri, referrerUrl: String? = null) {
    val params = buildMap {
        put("link", uri.toString())
        if (referrerUrl != null) {
            put("referrerURL", referrerUrl)
        }
    }
    logEvent("dynamic_link_app_open", params)
}

/**
 * Отправляет информацию об открытии приложения из push-уведомления (dynamic_push_app_open).
 * Обязательно для корректной атрибуции переходов.
 */
public fun WildAnalytics.logPushOpen(link: String, push: String) {
    logEvent("dynamic_push_app_open", mapOf("link" to link, "push" to push))
}
