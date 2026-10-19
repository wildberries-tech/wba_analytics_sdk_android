package ru.wildanalytics.pub.analytics.send

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import ru.wildanalytics.pub.analytics.CoroutineScopeFactory
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import ru.wildanalytics.pub.analytics.config.ConfigRepository
import ru.wildanalytics.pub.analytics.config.WildAnalyticsConfig
import ru.wildanalytics.pub.analytics.diagnostics.DiagnosticAttributes
import ru.wildanalytics.pub.analytics.diagnostics.DiagnosticMarker
import ru.wildanalytics.pub.analytics.diagnostics.DiagnosticsReporter
import ru.wildanalytics.pub.analytics.logDebug
import kotlin.coroutines.cancellation.CancellationException

@Suppress("OPT_IN_USAGE")
internal class WildAnalyticsSenderService(
    log: WildAnalyticsLogger,
    configRepository: ConfigRepository,
    coroutineScopeFactory: CoroutineScopeFactory,
    sendStrategyProvider: SendStrategyProvider,
    diagnostics: DiagnosticsReporter,
    restartPolicy: RestartPolicy,
) {

    init {
        log.logDebug { "Initializing WildAnalyticsSenderService" }
        val scope = coroutineScopeFactory.create(javaClass.simpleName)
        configRepository.config
            .onEach { log.logDebug("WildAnalytics config: $it") }
            .flatMapLatest { config ->
                delay(config.delays.initialDelay)
                sendStrategyProvider.strategy
                    .mapLatest { strategy -> invokeStrategy(strategy, config) }
            }
            .retryWhen { cause, attempt ->
                val delay = restartPolicy.delayFor(attempt)
                diagnostics.report(
                    marker = DiagnosticMarker.SEND_LOOP_RESTARTED,
                    message = "Send loop failed, restart in $delay",
                    attributes = mapOf(
                        DiagnosticAttributes.EXCEPTION to cause.javaClass.name,
                        DiagnosticAttributes.ATTEMPT to attempt.toString(),
                    ),
                    throwable = cause,
                )
                delay(delay)
                true
            }
            .launchIn(scope)
    }

    private suspend fun invokeStrategy(strategy: SendStrategy, config: WildAnalyticsConfig) {
        try {
            strategy.invoke(config)
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            throw IllegalStateException("Send strategy was cancelled unexpectedly", e)
        }
    }
}
