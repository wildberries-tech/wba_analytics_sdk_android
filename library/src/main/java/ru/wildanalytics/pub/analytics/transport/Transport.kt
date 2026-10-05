package ru.wildanalytics.pub.analytics.transport

import kotlinx.serialization.SerializationStrategy
import okhttp3.Headers

/**
* Транспорт отправки данных аналитики.
* @see HttpTransport
* */
public interface Transport {

    /**
     * Метод для отправки данных аналитики.
     * @see HttpTransport
     * @return 200 if succeed, other codes if failed
     * */
    public suspend fun <T> send(
        url: String,
        body: T,
        strategy: SerializationStrategy<T>,
        headers: Headers,
    ): Int
}
