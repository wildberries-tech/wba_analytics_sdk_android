package ru.wildberries.attribution.impl.logger

import ru.wildberries.attribution.api.WBAttributionLogger

internal class NoOpAttributionLogger : WBAttributionLogger {

    override fun logDebug(error: Throwable?, lazyMessage: () -> String) = Unit

    override fun logError(error: Throwable?, lazyMessage: () -> String) = Unit
}
