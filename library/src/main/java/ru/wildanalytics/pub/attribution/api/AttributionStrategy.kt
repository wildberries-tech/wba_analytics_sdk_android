package ru.wildanalytics.pub.attribution.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.launch
import ru.wildanalytics.pub.analytics.CoroutineScopeFactory
import ru.wildanalytics.pub.analytics.WildAnalytics

public sealed class AttributionStrategy {

    internal abstract fun execute(
        context: Context,
        analytics: WildAnalytics,
        scopeFactory: CoroutineScopeFactory
    )

    /**
     * Проверяем атрибуцию, отправляем событие "app_install" с данными, полученными от сервиса,
     * при наличии [AttributionData.link] - вызывается [handleLink].
     * @param withSystemLogs отправлять ли системные логи
     * @param systemLogsTag тэг, по которому отправляются логи в logcat
     * @param handleLink колбэк для обработки диплинка, если он есть в данных атрибуции
     * */
    public data class HandleLinkOnAttribution(
        val systemLogsTag: String? = "WildAttributionTracker",
        val withSystemLogs: Boolean = Log.isLoggable(systemLogsTag, Log.DEBUG),
        val handleLink: suspend (link: String) -> Unit
    ) : AttributionStrategy() {

        override fun execute(
            context: Context,
            analytics: WildAnalytics,
            scopeFactory: CoroutineScopeFactory,
        ) {
            val scope = scopeFactory.create(javaClass.simpleName)
            scope.launch {
                val tracker = WildAttributionTracker.create(context, withSystemLogs, systemLogsTag)
                tracker.checkAttribution(analytics) { data ->
                    data?.link?.let { handleLink(it) }
                }
            }
        }
    }

    /**
     * Не проверяем атрибуцию.
     * */
    public object NotCheckAttribution : AttributionStrategy() {

        override fun execute(
            context: Context,
            analytics: WildAnalytics,
            scopeFactory: CoroutineScopeFactory,
        ): Unit = Unit
    }
}