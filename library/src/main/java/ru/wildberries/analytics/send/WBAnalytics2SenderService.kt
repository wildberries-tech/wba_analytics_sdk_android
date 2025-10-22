package ru.wildberries.analytics.send

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import ru.wildberries.analytics.CoroutineScopeFactory
import ru.wildberries.analytics.WBAnalytics2Logger
import ru.wildberries.analytics.config.ConfigRepository
import ru.wildberries.analytics.logDebug
import ru.wildberries.analytics.logError
import ru.wildberries.analytics.logException

@Suppress("OPT_IN_USAGE")
internal class WBAnalytics2SenderService(
    log: WBAnalytics2Logger,
    configRepository: ConfigRepository,
    coroutineScopeFactory: CoroutineScopeFactory,
    sendStrategyProvider: SendStrategyProvider,
) {

    init {
        log.logDebug { "Initializing WBAnalytics2SenderService" }
        val scope = coroutineScopeFactory.create(javaClass.simpleName)
        configRepository.config
            .onEach { log.logDebug("WBA config: $it") }
            .flatMapLatest { config ->
                delay(config.delays.initialDelay)
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
