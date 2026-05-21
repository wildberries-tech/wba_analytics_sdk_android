package ru.wildanalytics.pub.attribution.impl.logger

import ru.wildanalytics.pub.attribution.api.WildAttributionLogger

internal class NoOpAttributionLogger : WildAttributionLogger {

    override fun logDebug(error: Throwable?, lazyMessage: () -> String) = Unit

    override fun logError(error: Throwable?, lazyMessage: () -> String) = Unit
}
