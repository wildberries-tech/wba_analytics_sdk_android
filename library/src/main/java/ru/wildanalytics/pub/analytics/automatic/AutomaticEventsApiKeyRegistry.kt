package ru.wildanalytics.pub.analytics.automatic

import ru.wildanalytics.pub.analytics.ApiKey
import java.util.concurrent.ConcurrentHashMap

internal interface AutomaticEventsApiKeyRegistry {
    fun register(apiKey: ApiKey)
    fun unregister(apiKey: ApiKey)
    fun isEnabled(apiKey: ApiKey): Boolean
}

internal class AutomaticEventsApiKeyRegistryImpl : AutomaticEventsApiKeyRegistry {

    private val keyCounts = ConcurrentHashMap<ApiKey, Int>()

    override fun register(apiKey: ApiKey) {
        keyCounts.compute(apiKey) { _, count -> (count ?: 0) + 1 }
    }

    override fun unregister(apiKey: ApiKey) {
        keyCounts.compute(apiKey) { _, count ->
            if (count == null || count <= 1) null else count - 1
        }
    }

    override fun isEnabled(apiKey: ApiKey): Boolean = keyCounts.containsKey(apiKey)
}
