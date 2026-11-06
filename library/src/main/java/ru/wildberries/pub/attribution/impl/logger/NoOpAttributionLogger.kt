package ru.wildberries.pub.attribution.impl.logger

import ru.wildberries.pub.attribution.api.WBAttributionLogger

internal class NoOpAttributionLogger : WBAttributionLogger {

    override fun logDebug(error: Throwable?, lazyMessage: () -> String) = Unit

    override fun logError(error: Throwable?, lazyMessage: () -> String) = Unit
}
