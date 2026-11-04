package ru.wildberries.pub.analytics.batch

import ru.wildberries.pub.analytics.api.BatchModel
import ru.wildberries.pub.analytics.domain.ApiData

internal class BatchData(
    val contentLength: Long,
    val apiData: ApiData,
    val model: BatchModel,
    val eventIds: List<Int>,
    val heaviestEvent: Pair<String, Long>,
)

