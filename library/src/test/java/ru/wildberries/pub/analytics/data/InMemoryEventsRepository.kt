package ru.wildberries.pub.analytics.data

import androidx.room.concurrent.AtomicInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import ru.wildberries.pub.analytics.db.EventEntity
import ru.wildberries.pub.analytics.db.EvictedEventsGroup
import ru.wildberries.pub.analytics.db.OversizedEvent
import ru.wildberries.pub.analytics.domain.ApiData
import ru.wildberries.pub.analytics.event.EventsRepository
import ru.wildberries.pub.analytics.event.EvictionResult

internal class InMemoryEventsRepository : EventsRepository {
    private var lastId = AtomicInt(1)
    private val eventsState = MutableStateFlow(emptyMap<Int, EventEntity>())

    override suspend fun add(event: EventEntity) =
        updateEvents { it + event.addIdIfNew().let { it.id to it } }

    override suspend fun delete(ids: List<Int>) {
        updateEvents { it - ids }
    }

    override suspend fun getFirstEventApiDataOrNull(): ApiData? =
        eventsState.value.values
            .sortWithPriority()
            .firstOrNull()?.apiData

    override suspend fun awaitFirstApiData(): ApiData = eventsState
        .mapNotNull {
            it.values
                .sortWithPriority()
                .firstOrNull()
        }
        .first()
        .apiData

    override fun hasEventsFlow(): Flow<Boolean> = eventsState
        .map { it.isNotEmpty() }
        .distinctUntilChanged()

    override suspend fun <R> useEventsSequence(
        apiData: ApiData,
        limit: Int,
        block: (Sequence<EventEntity>) -> R,
    ): R {
        val sequence = eventsState.value
            .values
            .filter { it.apiData == apiData }
            .sortWithPriority()
            .asSequence()
            .take(limit)
        return block(sequence)
    }

    override suspend fun deleteOldestEventsThatExceed(allowedEventsInCacheCount: Int): EvictionResult? {
        val events = eventsState.value.values
        val exceededCount = events.size - allowedEventsInCacheCount
        if (exceededCount <= 0) return null
        val evicted = events
            .sortedWith(compareBy<EventEntity> { it.importance }.thenBy { it.id })
            .take(exceededCount)
        delete(evicted.map { it.id })
        return EvictionResult(
            limit = allowedEventsInCacheCount,
            sizeBefore = events.size,
            removed = evicted.size,
            groups = evicted
                .groupingBy { it.name to it.importance }
                .eachCount()
                .map { (key, count) -> EvictedEventsGroup(name = key.first, importance = key.second, count = count) },
        )
    }

    override suspend fun deleteOversizedEvents(maxSizeInBytes: Long): List<OversizedEvent> {
        val oversized = eventsState.value.values
            .map { OversizedEvent(id = it.id, name = it.name, size = it.extrasSizeInBytes()) }
            .filter { it.size > maxSizeInBytes }
        delete(oversized.map { it.id })
        return oversized
    }

    fun addEvents(events: Collection<EventEntity>) =
        updateEvents { it + events.associateBy { it.id } }

    fun clear() {
        updateEvents { emptyMap() }
    }

    private fun updateEvents(update: (Map<Int, EventEntity>) -> Map<Int, EventEntity>) {
        eventsState.update(update)
    }

    private val EventEntity.apiData: ApiData get() = ApiData(apiUrl, apiKey)
    private fun EventEntity.extrasSizeInBytes(): Long =
        Json.encodeToString(JsonObject.serializer(), extras).encodeToByteArray().size.toLong()
    private fun EventEntity.addIdIfNew(): EventEntity =
        if (id == 0) copy(id = lastId.andIncrement) else this

    private fun Collection<EventEntity>.sortWithPriority(): List<EventEntity> =
        sortedWith(compareByDescending<EventEntity> { it.importance }.thenBy { it.id })
}