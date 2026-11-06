package ru.wildberries.pub.analytics.send

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import ru.wildberries.pub.analytics.WBAnalytics2Logger
import ru.wildberries.pub.analytics.config.WBA2Config
import ru.wildberries.pub.analytics.event.EventsRepository
import ru.wildberries.pub.analytics.logDebug
import ru.wildberries.pub.analytics.logError
import ru.wildberries.pub.analytics.network.NetworkAvailabilitySource
import ru.wildberries.pub.analytics.domain.SendMode
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal interface SendStrategy {
    suspend operator fun invoke(config: WBA2Config)
}

internal class SchedulingSendStrategy(
    private val eventsRepository: EventsRepository,
    private val log: WBAnalytics2Logger,
    private val sendOperationScheduler: SendOperationScheduler,
) : SendStrategy {

    override suspend fun invoke(config: WBA2Config) {
        log.logDebug { "`Scheduling` events send strategy selected" }
        eventsRepository.hasEventsFlow()
            .onCompletion { sendOperationScheduler.cancel() }
            .conflate()
            .onEach { hasEvents ->
                if (hasEvents) {
                    sendOperationScheduler.schedule()
                } else {
                    val delay = config.delays.delayBetweenOperations
                    log.logDebug { "Events in db are over, await new events after $delay" }
                    delay(delay)
                }
            }
            .collect()
    }
}

internal class ImmediateSendStrategy(
    private val eventsRepository: EventsRepository,
    private val log: WBAnalytics2Logger,
    private val sendOperation: SendAllAnalyticEventsOperation,
    private val networkAvailabilitySource: NetworkAvailabilitySource,
) : SendStrategy {

    override suspend fun invoke(config: WBA2Config) {
        log.logDebug { "`Immediate` events send strategy selected" }
        networkAvailabilitySource.availabilityFlow
            .filter { it }
            .collectLatest { sendEventsProcess(config) }
    }

    private suspend fun sendEventsProcess(config: WBA2Config) {
        var failedAttempts = 0
        while (currentCoroutineContext().isActive) {
            try {
                val apiData = eventsRepository.awaitFirstApiData()
                sendOperation.execute(apiData, config, SendMode.IMMEDIATE)
                failedAttempts = 0
                val delay = config.delays.delayBetweenOperations
                log.logDebug { "Events in db are over, await new events after $delay" }
                delay(delay)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (log.isEnabled) {
                    log.logException(e)
                }
                delay(errorDelay(config, ++failedAttempts))
            } catch (e: OutOfMemoryError) {
                log.logError {
                    error(e)
                    detail("context", "sendEventsProcess")
                }
                delay(errorDelay(config, ++failedAttempts))
            }
        }
    }

    private fun errorDelay(config: WBA2Config, failedAttempts: Int): Duration {
        val multiplier = 1 shl (failedAttempts - 1).coerceIn(0, MAX_ERROR_DELAY_SHIFT)
        val baseDelay = config.delays.delayBetweenOperations.coerceAtLeast(MIN_ERROR_DELAY)
        return (baseDelay * multiplier).coerceAtMost(MAX_ERROR_DELAY)
    }
}

private const val MAX_ERROR_DELAY_SHIFT = 10
private val MIN_ERROR_DELAY = 1.seconds
private val MAX_ERROR_DELAY = 5.minutes
