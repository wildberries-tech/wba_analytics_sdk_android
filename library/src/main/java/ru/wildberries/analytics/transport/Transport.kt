package ru.wildberries.analytics.transport

import kotlinx.serialization.SerializationStrategy

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
        headers: Map<String, String>,
    ): Int
}