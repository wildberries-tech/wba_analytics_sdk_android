package ru.wildanalytics.pub.analytics

import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.atomic.AtomicReference

internal class EventEnricherRegistryImpl(
    private val log: WildAnalyticsLogger,
) : EventEnricherRegistry {

    private val enrichers = AtomicReference(persistentListOf<EventEnricher>())

    override fun append(enricher: EventEnricher) {
        enrichers.updateAndGet { it.add(enricher) }
    }

    override suspend fun enrich(eventName: String, parameters: JsonObject): JsonObject {
        val snapshot = enrichers.get()
        if (snapshot.isEmpty()) {
            return parameters
        }

        val added = LinkedHashMap<String, JsonElement>()
        for (enricher in snapshot) {
            for (field in runEnricher(enricher, eventName, parameters)) {
                if (!parameters.containsKey(field.key) && !added.containsKey(field.key)) {
                    added[field.key] = field.value
                }
            }
        }

        if (added.isEmpty()) {
            return parameters
        }
        return JsonObject(parameters + added)
    }

    override fun clear() {
        enrichers.set(persistentListOf())
    }

    private suspend fun runEnricher(
        enricher: EventEnricher,
        eventName: String,
        parameters: JsonObject,
    ): List<AnalyticsField> = try {
        enricher.enrich(eventName, parameters)
    } catch (e: Exception) {
        log.logWarn { "EventEnricher failed for event '$eventName': ${e.message}" }
        emptyList()
    }
}
