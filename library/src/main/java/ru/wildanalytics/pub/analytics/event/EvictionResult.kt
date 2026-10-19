package ru.wildanalytics.pub.analytics.event

import ru.wildanalytics.pub.analytics.db.EvictedEventsGroup

internal data class EvictionResult(
    val limit: Int,
    val sizeBefore: Int,
    val removed: Int,
    val groups: List<EvictedEventsGroup>,
)
