package ru.wildanalytics.pub.analytics.send.fallback

import ru.wildanalytics.pub.analytics.send.HttpStatusException
import java.io.IOException

/**
 * Правило переключения: [threshold] неудачных попыток подряд, при которых хост недоступен и интернет есть.
 * Любая другая попытка сбрасывает счётчик.
 * */
internal class FallbackTrigger(private val threshold: Int = FALLBACK_AFTER_FAILED_ATTEMPTS) {

    private var failuresInRow = 0

    /** @return `true`, пока набрано не меньше [threshold] неудач подряд. */
    fun onFailure(error: Exception, hasInternet: () -> Boolean): Boolean {
        failuresInRow = if (error.countsAsUnreachable() && hasInternet()) failuresInRow + 1 else 0
        return failuresInRow >= threshold
    }
}

/** Сетевая ошибка или HTTP 5xx; ответ с кодом ниже 500 означает, что хост доступен. */
internal fun Exception.countsAsUnreachable(): Boolean =
    this is IOException && !(this is HttpStatusException && code < SERVER_ERROR_CODE)

internal const val FALLBACK_AFTER_FAILED_ATTEMPTS = 3
private const val SERVER_ERROR_CODE = 500
