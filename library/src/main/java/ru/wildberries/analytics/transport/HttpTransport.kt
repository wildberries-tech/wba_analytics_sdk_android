package ru.wildberries.analytics.transport

import kotlinx.serialization.SerializationStrategy
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.gildor.coroutines.okhttp.await
import ru.wildberries.analytics.api.JsonBody
import java.util.concurrent.TimeUnit
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
        timeouts: HttpTimeouts = HttpTimeouts(),
        appendToClient: OkHttpClient.Builder.() -> OkHttpClient.Builder = { this }
    ) : this(
        OkHttpClient.Builder()
            .connectTimeout(timeouts.connectTimeout.toJavaDuration())
            .readTimeout(timeouts.readTimeout.toJavaDuration())
            .writeTimeout(timeouts.writeTimeout.toJavaDuration())
            // попытка избавиться от отвалившихся сокетов, вызывающих java.net.SocketTimeoutException при реконнекте
            // https://github.com/square/okhttp/issues/3146
            .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
            .appendToClient()
            .build()
    )

    override suspend fun <T> send(
        url: String,
        body: T,
        strategy: SerializationStrategy<T>,
        headers: Map<String, String>
    ): Int {
        val request = Request.Builder()
            .url(url)
            .post(JsonBody(body, strategy))
            .headers(headers.toHeaders())
            .build()

        val response = callFactory.newCall(request).await()
        response.close()

        return response.code
    }
}