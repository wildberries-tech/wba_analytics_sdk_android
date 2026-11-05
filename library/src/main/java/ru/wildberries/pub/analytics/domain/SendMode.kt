package ru.wildberries.pub.analytics.domain

/**
 * Источник отправки батча (strategy-sourced).
 * SCHEDULED — запланированный/JobService путь, IMMEDIATE — ImmediateSendStrategy.
 * @see ru.wildberries.pub.analytics.send.SendStrategy
 */
internal enum class SendMode(val value: Int) {
    SCHEDULED(0),
    IMMEDIATE(1),
}
