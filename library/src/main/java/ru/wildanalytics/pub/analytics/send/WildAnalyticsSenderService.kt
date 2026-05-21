package ru.wildanalytics.pub.analytics.send

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import ru.wildanalytics.pub.analytics.CoroutineScopeFactory
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import ru.wildanalytics.pub.analytics.config.ConfigRepository
import ru.wildanalytics.pub.analytics.logDebug
import ru.wildanalytics.pub.analytics.logError
import ru.wildanalytics.pub.analytics.logException

@Suppress("OPT_IN_USAGE")
internal class WildAnalyticsSenderService(
    log: WildAnalyticsLogger,
    configRepository: ConfigRepository,
    coroutineScopeFactory: CoroutineScopeFactory,
    sendStrategyProvider: SendStrategyProvider,
) {

    init {
        log.logDebug { "Initializing WildAnalyticsSenderService" }
        val scope = coroutineScopeFactory.create(javaClass.simpleName)
        configRepository.config
            .onEach { log.logDebug("WildAnalytics config: $it") }
            .flatMapLatest { config ->
                delay(config.delays.initialDelay)
                return@flatMapLatest flowOf(Unit)
                sendStrategyProvider.strategy
                    .mapLatest { strategy -> strategy.invoke(config) }
            }
            .catch { throwable ->
                when (throwable) {
                    is Exception -> log.logException { throwable }
                    is Error -> log.logError {
                        error(throwable)
                    }
                }
            }
            .launchIn(scope)
    }
}
