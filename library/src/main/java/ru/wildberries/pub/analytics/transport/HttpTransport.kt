package ru.wildberries.pub.analytics.transport

import kotlinx.serialization.SerializationStrategy
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.gildor.coroutines.okhttp.await
import ru.wildberries.pub.analytics.api.JsonBody
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration


/**
 * Транспорт для отправки данных аналитики по http.
 * Использует [Call.Factory]
 * */
public class HttpTransport(private val callFactory: Call.Factory) : Transport {

    /**
     * Создаёт транспорт с использованием дефолтного [OkHttpClient].
     * */
    public constructor(
        timeouts: HttpTimeouts = DEFAULT_TIMEOUTS,
        appendToClient: OkHttpClient.Builder.() -> OkHttpClient.Builder = { this },
    ) : this(
        OkHttpClient.Builder()
            .connectTimeout(timeouts.connectTimeout.toJavaDuration())
            .readTimeout(timeouts.readTimeout.toJavaDuration())
            .writeTimeout(timeouts.writeTimeout.toJavaDuration())
            // попытка избавиться от отвалившихся сокетов, вызывающих java.net.SocketTimeoutException при реконнекте
            // https://github.com/square/okhttp/issues/3146
            // Без переиспользования соединений: их закрытие обеспечивает maxIdleConnections = 0.
            // keepAliveDuration — ещё и интервал перепроверки пула, пока соединение занято:
            // при 1 нс очистка пула крутится в холостом цикле на всё время запроса.
            .connectionPool(ConnectionPool(0, 5, TimeUnit.SECONDS))
            .appendToClient()
            .build()
    )

    override suspend fun <T> send(
        url: String,
        body: T,
        strategy: SerializationStrategy<T>,
        headers: Headers,
    ): Int {
        val request = Request.Builder()
            .url(url)
            .post(JsonBody(body, strategy))
            .headers(headers)
            .build()

        val response = callFactory.newCall(request).await()
        response.close()

        return response.code
    }
}

/**
 * [HttpTimeouts.readTimeout] включает доставку тела до сервера: батч до 1 МБ без сжатия доезжает за 45 с на канале от ~24 КБ/с.
 * */
private val DEFAULT_TIMEOUTS = HttpTimeouts(readTimeout = 45.seconds)
