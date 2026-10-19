package ru.wildanalytics.pub.analytics.sdk.demo.sdk

import kotlinx.serialization.SerializationStrategy
import okhttp3.Headers
import ru.wildanalytics.pub.analytics.sdk.demo.core.RequestLog
import ru.wildanalytics.pub.analytics.transport.Transport
import java.io.IOException

/** Транспорт SDK с подменой ответа для QA; подменённые ответы попадают в [RequestLog] с пометкой mock. */
class QaTransport(
    private val delegate: Transport,
    private val requestLog: RequestLog,
) : Transport {

    @Volatile
    var mode: TransportMode = TransportMode.REAL

    override suspend fun <T> send(
        url: String,
        body: T,
        strategy: SerializationStrategy<T>,
        headers: Headers,
    ): Int = when (val current = mode) {
        TransportMode.REAL -> delegate.send(url, body, strategy, headers)
        TransportMode.IO_EXCEPTION -> {
            requestLog.recordMock(url, code = null, error = IOException::class.java.simpleName)
            throw IOException("QA: network failure")
        }
        TransportMode.ERROR -> {
            requestLog.recordMock(url, code = null, error = AssertionError::class.java.simpleName)
            throw AssertionError("QA: unexpected error in transport")
        }
        else -> current.code.also { requestLog.recordMock(url, code = it, error = null) }
    }
}

enum class TransportMode(val title: String, val code: Int) {
    REAL("Реальный", 0),
    HTTP_200("200", 200),
    HTTP_413("413", 413),
    HTTP_414("414", 414),
    HTTP_429("429", 429),
    HTTP_500("500", 500),
    IO_EXCEPTION("IOException", 0),
    ERROR("Error", 0),
}
