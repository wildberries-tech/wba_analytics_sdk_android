package ru.wildanalytics.pub.analytics

import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers
import ru.wildanalytics.pub.analytics.db.EventEntity
import ru.wildanalytics.pub.analytics.event.EventsRepository
import ru.wildanalytics.pub.analytics.session.SessionProvider
import ru.wildanalytics.pub.analytics.transport.CustomHeadersRepository
import java.time.Clock
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicReference

@OptIn(DelicateCoroutinesApi::class)
internal class WildAnalyticsImpl(
    private val apiUrlProvider: () -> ApiUrl,
    private val apiKey: ApiKey,
    @Volatile
    override var isCollectionEnabled: Boolean,
    private val clock: Clock,
    private val eventsRepository: EventsRepository,
    private val sessionProvider: SessionProvider,
    private val log: WildAnalyticsLogger,
    coroutineScopeFactory: CoroutineScopeFactory,
    private val customHeadersRepository: CustomHeadersRepository,
    private val enricherRegistry: EventEnricherRegistry,
) : WildAnalytics {

    private val myScope = coroutineScopeFactory.create(javaClass.simpleName)
    private val channel = Channel<EventEntity>(capacity = Channel.UNLIMITED)
    private val commonParameters = AtomicReference(persistentHashMapOf<String, JsonElement>())

    init {
        channel.consumeAsFlow()
            .onEach {
                try {
                    // Обогащаем в consumer-корутине, чтобы не нагружать поток вызывающего.
                    val enriched = enricherRegistry.enrich(it.name, it.extras)
                    eventsRepository.add(it.copy(extras = enriched))
                } catch (_: Exception) {
                    // Защита от недоступности БД.
                    // Без БД просто не будем собирать события аналитики.
                }
            }
            .launchIn(myScope)
    }

    override fun finish() {
        isCollectionEnabled = false
        myScope.cancel()
        channel.cancel()
        commonParameters.set(persistentHashMapOf())
        enricherRegistry.clear()
    }

    override fun addEventEnricher(enricher: EventEnricher) {
        enricherRegistry.append(enricher)
    }

    override fun logEvent(name: String, parameters: Map<String, String>) {
        logEvent(name, parameters.toJsonObject())
    }

    override fun logEvent(name: String, parameters: JsonObject) {
        enqueueEvent(name, EventEntity.IMPORTANCE_NORMAL, parameters)
    }

    override fun logImportantEvent(name: String, parameters: Map<String, String>) {
        logImportantEvent(name, parameters.toJsonObject())
    }

    override fun logImportantEvent(name: String, parameters: JsonObject) {
        enqueueEvent(name, EventEntity.IMPORTANCE_HIGH, parameters)
    }

    private fun enqueueEvent(name: String, importance: Int, parameters: JsonObject) {
        require(name.length in 1..MAX_EVENT_NAME_LENGTH) {
            "event name($name) length not in 1..$MAX_EVENT_NAME_LENGTH"
        }

        if (!isCollectionEnabled)
            return

        log.logDebug { ("$name${importanceDebugEventSuffix(importance)} : $parameters") }

        channel.trySend(
            EventEntity(
                name = name,
                sessionValue = sessionProvider.currentSessionValue,
                apiUrl = apiUrlProvider(),
                apiKey = apiKey,
                time = OffsetDateTime.now(clock),
                extras = mergeParameters(parameters),
                importance = importance,
            )
        )
    }

    private fun importanceDebugEventSuffix(importance: Int): String =
        if (importance == EventEntity.IMPORTANCE_HIGH) "(!)" else ""

    private fun mergeParameters(parameters: JsonObject): JsonObject {
        val common = commonParameters.get()

        if (parameters.isEmpty())
            return JsonObject(common)

        if (common.isEmpty())
            return parameters

        return JsonObject(common + parameters)
    }

    override fun setCommonParameter(key: String, value: String?) {
        // Собираем переменные даже если isCollectionEnabled == false
        // чтобы ничего не потерять когда аналитика станет включена.
        // Но если уже был закрыт канал, то нет смысла собирать переменные дальше.
        if (channel.isClosedForSend)
            return

        require(key.length in 1..40)

        // lock-free реализация записи переменной.
        commonParameters.updateAndGet {
            if (value == null) {
                it.remove(key)
            } else {
                it.put(key, JsonPrimitive(value))
            }
        }
    }

    override fun setCommonParameters(src: Map<String, String?>) {
        if (channel.isClosedForSend)
            return

        commonParameters.updateAndGet {
            src.entries.fold(it) { acc, (key, value) ->
                require(key.length in 1..40)

                if (value == null) {
                    acc.remove(key)
                } else {
                    acc.put(key, JsonPrimitive(value))
                }
            }
        }
    }

    override fun setCustomHeader(key: String, value: String?) {
        if (key.isBlank()) {
            log.logWarn { "custom header key is blank, skipped" }
            return
        }
        customHeadersRepository.set(key, value)
    }

    override fun setCustomHeaders(headers: Headers) {
        customHeadersRepository.setAll(headers)
    }
}
