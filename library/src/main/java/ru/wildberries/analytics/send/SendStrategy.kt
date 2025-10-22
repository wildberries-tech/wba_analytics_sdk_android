package ru.wildberries.analytics.send

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import ru.wildberries.analytics.WBAnalytics2Logger
import ru.wildberries.analytics.config.WBA2Config
import ru.wildberries.analytics.event.EventsRepository
import ru.wildberries.analytics.logDebug
import ru.wildberries.analytics.logError
import ru.wildberries.analytics.network.NetworkAvailabilitySource
import kotlin.coroutines.cancellation.CancellationException

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
        eventsRepository.deleteOldestEventsThatExceed(config.maxEventsInCache)
        while (currentCoroutineContext().isActive) {
            try {
                val apiData = eventsRepository.awaitFirstApiData()
                sendOperation.execute(apiData, config)
                val delay = config.delays.delayBetweenOperations
                log.logDebug { "Events in db are over, await new events after $delay" }
                delay(delay)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (log.isEnabled) {
                    log.logException(e)
                }
            } catch (e: OutOfMemoryError) {
                log.logError {
                    error(e)
                    detail("context", "sendEventsProcess")
                }
            }
        }
    }
}