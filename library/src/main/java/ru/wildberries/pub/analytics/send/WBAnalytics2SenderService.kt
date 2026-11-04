package ru.wildberries.pub.analytics.send

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import ru.wildberries.pub.analytics.CoroutineScopeFactory
import ru.wildberries.pub.analytics.WBAnalytics2Logger
import ru.wildberries.pub.analytics.config.ConfigRepository
import ru.wildberries.pub.analytics.config.WBA2Config
import ru.wildberries.pub.analytics.diagnostics.DiagnosticAttributes
import ru.wildberries.pub.analytics.diagnostics.DiagnosticMarker
import ru.wildberries.pub.analytics.diagnostics.DiagnosticsReporter
import ru.wildberries.pub.analytics.logDebug
import kotlin.coroutines.cancellation.CancellationException

@Suppress("OPT_IN_USAGE")
internal class WBAnalytics2SenderService(
    log: WBAnalytics2Logger,
    configRepository: ConfigRepository,
    coroutineScopeFactory: CoroutineScopeFactory,
    sendStrategyProvider: SendStrategyProvider,
    diagnostics: DiagnosticsReporter,
    restartPolicy: RestartPolicy,
) {

    init {
        log.logDebug { "Initializing WBAnalytics2SenderService" }
        val scope = coroutineScopeFactory.create(javaClass.simpleName)
        configRepository.config
            .onEach { log.logDebug("WBA config: $it") }
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

    private suspend fun invokeStrategy(strategy: SendStrategy, config: WBA2Config) {
        try {
            strategy.invoke(config)
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            throw IllegalStateException("Send strategy was cancelled unexpectedly", e)
        }
    }
}
