package ru.wildanalytics.pub.analytics.batch

import ru.wildanalytics.pub.analytics.domain.ApiData

internal interface BatchRepository {

    /** Вызываем это после успешной отправки батча*/
    suspend fun batchSent(eventIds: List<Int>)

    /** Вызываем это, если надо дропнуть батч и пробовать со следующей порцией данных*/
    suspend fun dropBatch(eventIds: List<Int>)

    /** Здесь мы получаем все необходимые данные для отправки батча или null, если нет событий для отправки*/
    suspend fun getBatch(apiData: ApiData, config: BatchingConfig): BatchData?
}
