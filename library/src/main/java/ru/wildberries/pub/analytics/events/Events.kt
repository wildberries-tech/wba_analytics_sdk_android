package ru.wildberries.pub.analytics.events

import android.net.Uri
import ru.wildberries.pub.analytics.WBAnalytics2

/**
 * ANDR-9420: Отправляет информацию о ссылке, которую перехвалило приложение.
 */
public fun WBAnalytics2.logDeepLink(uri: Uri) {
    logEvent("dynamic_link_app_open", mapOf("link" to uri.toString()))
}
