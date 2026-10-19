package ru.wildanalytics.pub.analytics

import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToStream
import okhttp3.Headers
import ru.wildanalytics.pub.analytics.automatic.AutomaticEventsStrategy
import ru.wildanalytics.pub.analytics.db.EventEntity
import ru.wildanalytics.pub.analytics.event.EventsRepository
import ru.wildanalytics.pub.analytics.session.SessionProvider
import ru.wildanalytics.pub.analytics.transport.CustomHeadersRepository
import ru.wildanalytics.pub.analytics.util.ContentLengthCountingStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import ru.wildanalytics.pub.analytics.diagnostics.DiagnosticAttributes
import ru.wildanalytics.pub.analytics.diagnostics.DiagnosticMarker
import ru.wildanalytics.pub.analytics.diagnostics.DiagnosticsReporter
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
    private val automaticEventsStrategy: AutomaticEventsStrategy,
    private val commonParametersGate: CommonParametersGate,
    private val diagnostics: DiagnosticsReporter = DiagnosticsReporter(log, sdkVersion = "unknown"),
) : WildAnalytics {

    private val myScope = coroutineScopeFactory.create(javaClass.simpleName)
    private val channel = Channel<EventEntity>(capacity = Channel.UNLIMITED)
    private val commonParameters = AtomicReference(persistentHashMapOf<String, JsonElement>())
    private val reportedOversizedEvents = mutableSetOf<String>()
    private val reportedInsertFailures = java.util.concurrent.ConcurrentHashMap.newKeySet<Class<*>>()

    init {
        channel.consumeAsFlow()
            .onStart { commonParametersGate.await() }
            .onEach {
                try {
                    // Обогащаем в consumer-корутине, чтобы не нагружать поток вызывающего.
                    val enriched = enricherRegistry.enrich(it.name, fillMissingCommonParameters(it.extras))
                    val size = enriched.serializedSizeInBytes()
                    if (size > MAX_EVENT_EXTRAS_SIZE_IN_BYTES) {
                        logOversizedEventDropped(it.name, size)
                    } else {
                        eventsRepository.add(it.copy(extras = enriched))
                    }
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    currentCoroutineContext().ensureActive()
                    reportInsertFailed(it.name, e)
                } catch (e: Exception) {
                    // Защита от недоступности БД.
                    // Без БД просто не будем собирать события аналитики.
                    reportInsertFailed(it.name, e)
                }
            }
            .launchIn(myScope)

        automaticEventsStrategy.start(this, myScope)
    }

    override fun finish() {
        isCollectionEnabled = false
        myScope.cancel()
        channel.cancel()
        commonParameters.set(persistentHashMapOf())
        enricherRegistry.clear()
        automaticEventsStrategy.stop()
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

    private fun logOversizedEventDropped(name: String, size: Long) {
        if (reportedOversizedEvents.add(name)) {
            log.logError {
                error(Error("Event extras size exceeded $MAX_EVENT_EXTRAS_SIZE_IN_BYTES bytes"))
                detail("name", name)
                detail("size", size.toString())
            }
        } else {
            log.logWarn { "Event $name dropped: extras size $size exceeded $MAX_EVENT_EXTRAS_SIZE_IN_BYTES bytes" }
        }
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

    // Общие параметры, появившиеся после logEvent, дописываются только в отсутствующие ключи:
    // снапшот на момент logEvent и явные параметры события остаются в приоритете.
    private fun fillMissingCommonParameters(extras: JsonObject): JsonObject {
        val common = commonParameters.get()

        if (common.isEmpty() || common.keys.all(extras::containsKey))
            return extras

        return JsonObject(common + extras)
    }

    override fun setCommonParameter(key: String, value: String?) {
        putCommonParameter(key, value?.let(::JsonPrimitive))
    }

    override fun setCommonParameter(key: String, value: Boolean) {
        putCommonParameter(key, JsonPrimitive(value))
    }

    override fun setCommonParameter(key: String, value: Number) {
        putCommonParameter(key, JsonPrimitive(value))
    }

    private fun putCommonParameter(key: String, value: JsonPrimitive?) {
        // Собираем переменные даже если isCollectionEnabled == false
        // чтобы ничего не потерять когда аналитика станет включена.
        // Но если уже был закрыт канал, то нет смысла собирать переменные дальше.
        if (channel.isClosedForSend)
            return

        require(key.length in 1..MAX_COMMON_PARAMETER_KEY_LENGTH)

        // lock-free реализация записи переменной.
        commonParameters.updateAndGet {
            if (value == null) {
                it.remove(key)
            } else {
                it.put(key, value)
            }
        }
        commonParametersGate.onAssigned(listOf(key))
    }

    override fun setCommonParameters(src: Map<String, String?>) {
        if (channel.isClosedForSend)
            return

        commonParameters.updateAndGet {
            src.entries.fold(it) { acc, (key, value) ->
                require(key.length in 1..MAX_COMMON_PARAMETER_KEY_LENGTH)

                if (value == null) {
                    acc.remove(key)
                } else {
                    acc.put(key, JsonPrimitive(value))
                }
            }
        }
        commonParametersGate.onAssigned(src.keys)
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

    private fun reportInsertFailed(name: String, e: Exception) {
        if (!reportedInsertFailures.add(e.javaClass)) return
        diagnostics.report(
            marker = ru.wildanalytics.pub.analytics.diagnostics.DiagnosticMarker.INSERT_FAILED,
            message = "Event dropped: failed to store",
            attributes = mapOf(
                ru.wildanalytics.pub.analytics.diagnostics.DiagnosticAttributes.EVENT_NAME to name,
                ru.wildanalytics.pub.analytics.diagnostics.DiagnosticAttributes.EXCEPTION to e.javaClass.name,
            ),
            throwable = e,
        )
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun JsonObject.serializedSizeInBytes(): Long =
    ContentLengthCountingStream().use { stream ->
        Json.encodeToStream(JsonObject.serializer(), this, stream)
        stream.length
    }
