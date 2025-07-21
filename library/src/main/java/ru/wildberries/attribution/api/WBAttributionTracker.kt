package ru.wildberries.attribution.api

import android.content.Context
import ru.wildberries.analytics.WBAnalytics2
import ru.wildberries.attribution.impl.data.AttributionDataSourceImpl
import ru.wildberries.attribution.impl.logger.NoOpAttributionLogger
import ru.wildberries.attribution.impl.logger.SystemAttributionLogger
import ru.wildberries.attribution.impl.tracker.WBAttributionTrackerImpl

public interface WBAttributionTracker {

    /**
     * @param analytics экземпляр аналитики для логирования события "app_install"
     * @param onResult вызывается один раз с данными атрибуции или без них,
     * если не было перехода в приложение с рекламной ссылки определённого типа
     * */
    public suspend fun checkAttribution(
        analytics: WBAnalytics2,
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
        ): WBAttributionTracker {
            val log = if (withSystemLogs) SystemAttributionLogger(systemLogsTag) else NoOpAttributionLogger()
            return WBAttributionTrackerImpl(
                log = log,
                attributionDataSource = AttributionDataSourceImpl(context = context, log = log)
            )
        }
    }
}