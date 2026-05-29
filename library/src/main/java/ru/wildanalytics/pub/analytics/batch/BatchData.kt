package ru.wildanalytics.pub.analytics.batch

import kotlinx.serialization.InternalSerializationApi
import ru.wildanalytics.pub.analytics.api.BatchModel
import ru.wildanalytics.pub.analytics.domain.ApiData
@OptIn(InternalSerializationApi::class)
internal class BatchData(
    val contentLength: Long,
    val apiData: ApiData,
    val model: BatchModel,
    val eventIds: List<Int>,
    val heaviestEvent: Pair<String, Long>,
)
