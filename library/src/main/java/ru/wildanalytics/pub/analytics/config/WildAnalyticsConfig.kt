package ru.wildanalytics.pub.analytics.config

import ru.wildanalytics.pub.analytics.batch.BatchingConfig
import ru.wildanalytics.pub.analytics.transport.HttpTransport
import ru.wildanalytics.pub.analytics.transport.Transport
import kotlin.time.Duration.Companion.seconds

/**
 * @property delays задержки, связанные с отправкой событий аналитики.
 * @property batching конфигурация отправки батчей.
 * @property maxEventsInCache после превышения этого значения количества строк в табличке, начинаем удалять события,
 * которые не получилось отправить после всех попыток. Повтор попыток отправки осуществляется посредством [retryPolicy].
 * @property retryPolicy конфигурация повторной отправки событий аналитики.
 * @property transport транспорт для отправки событий аналитики.
 * */
public data class WildAnalyticsConfig(
    val delays: SendingDelays,
    val batching: BatchingConfig,
    val maxEventsInCache: Int,
    val retryPolicy: RetryPolicy,
    val transport: Transport,
) {

    public companion object {

        public val Default: WildAnalyticsConfig = WildAnalyticsConfig(
            delays = SendingDelays(
                delayBetweenBatches = 2.seconds,
                delayBetweenOperations = 10.seconds,
                initialDelay = 2.seconds,
            ),
            batching = BatchingConfig(
                // Берём не более 200 событий из БД чтобы не сильно
                // нагружать сервер и клиент в сложных случаях.
                maxEventsInBatch = 200,
                // Передаём не более 1 мб данных(либо 1 событие, если превышает), чтобы
                // не сильно нагружать сервер и клиент в сложных случаях.
                maxBatchSizeInBites = 1024 * 1024
            ),
            maxEventsInCache = 10_000,
            // Минимальная задержка: 4 сек
            // Максимальная задержка: ~60 сек
            // Общая длительность всех 10 попыток: ~115 сек
            retryPolicy = RetryPolicy.Exponential(
                attempts = 10,
                base = 1.5,
                additional = 2.5,
            ),
            transport = HttpTransport(),
        )
    }
}

