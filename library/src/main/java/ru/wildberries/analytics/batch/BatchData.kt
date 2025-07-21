package ru.wildberries.analytics.batch

import kotlinx.serialization.InternalSerializationApi
import ru.wildberries.analytics.api.BatchModel
import ru.wildberries.analytics.domain.ApiData
@OptIn(InternalSerializationApi::class)
internal class BatchData(
    val contentLength: Long,
    val apiData: ApiData,
    val model: BatchModel,
    val eventIds: List<Int>,
    val heaviestEvent: Pair<String, Long>,
)
