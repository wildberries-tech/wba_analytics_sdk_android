package ru.wildberries.analytics

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
import ru.wildberries.analytics.db.EventEntity
import ru.wildberries.analytics.event.EventsRepository
import java.time.Clock
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicReference

@OptIn(DelicateCoroutinesApi::class)
internal class WBAnalytics2Impl(
    private val apiUrlProvider: () -> ApiUrl,
    private val apiKey: ApiKey,
    @Volatile
    override var isCollectionEnabled: Boolean,
    private val clock: Clock,
    private val eventsRepository: EventsRepository,
    private val log: WBAnalytics2Logger,
    coroutineScopeFactory: CoroutineScopeFactory,
) : WBAnalytics2 {

    private val myScope = coroutineScopeFactory.create(javaClass.simpleName)
    private val channel = Channel<EventEntity>(capacity = Channel.UNLIMITED)
    private val commonParameters = AtomicReference(persistentHashMapOf<String, JsonElement>())

    init {
        channel.consumeAsFlow()
            .onEach {
                try {
                    eventsRepository.add(it)
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
    }

    override fun logEvent(name: String, parameters: Map<String, String>) {
        logEvent(name, parameters.toJsonObject())
    }

    override fun logEvent(name: String, parameters: JsonObject) {
        require(name.length in 1..40)

        if (!isCollectionEnabled)
            return

        log.logDebug { ("$name: $parameters") }

        channel.trySend(
            EventEntity(
                name = name,
                apiUrl = apiUrlProvider(),
                apiKey = apiKey,
                time = OffsetDateTime.now(clock),
                extras = mergeParameters(parameters),
            )
        )
    }

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
}
