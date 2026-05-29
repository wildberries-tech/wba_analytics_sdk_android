package ru.wildanalytics.pub.attribution.api

import android.content.Context
import ru.wildanalytics.pub.analytics.WildAnalytics
import ru.wildanalytics.pub.attribution.impl.data.AttributionDataSourceImpl
import ru.wildanalytics.pub.attribution.impl.logger.NoOpAttributionLogger
import ru.wildanalytics.pub.attribution.impl.logger.SystemAttributionLogger
import ru.wildanalytics.pub.attribution.impl.tracker.WildAttributionTrackerImpl

public interface WildAttributionTracker {

    /**
     * @param analytics экземпляр аналитики для логирования события "app_install"
     * @param onResult вызывается один раз с данными атрибуции или без них,
     * если не было перехода в приложение с рекламной ссылки определённого типа
     * */
    public suspend fun checkAttribution(
        analytics: WildAnalytics,
        onResult: suspend (AttributionData?) -> Unit
    )

    public companion object Factory {

        /**
         * @param context контекст приложения
         * @param withSystemLogs отправлять ли системные логи
         * @param systemLogsTag тэг, по которому отправляются логи в logcat
         * */
        public fun create(
            context: Context,
            withSystemLogs: Boolean,
            systemLogsTag: String?,
        ): WildAttributionTracker {
            val log = if (withSystemLogs) SystemAttributionLogger(systemLogsTag) else NoOpAttributionLogger()
            return WildAttributionTrackerImpl(
                log = log,
                attributionDataSource = AttributionDataSourceImpl(context = context, log = log)
            )
        }
    }
}