package ru.wildberries.analytics.config

import kotlin.time.Duration

/**
 * @property initialDelay первоначальная задержка, после которой начинаем слать события аналитики.
 * @property delayBetweenOperations задержка после одной целой (всех событий из БД) операции отправки событий аналитики.
 * @property delayBetweenBatches задержка между успешной отправкой одного батча и началом отправки следующего (если не последний).
 * */
public data class SendingDelays(
    val delayBetweenOperations: Duration,
    val delayBetweenBatches: Duration,
    val initialDelay: Duration = delayBetweenBatches,
)
