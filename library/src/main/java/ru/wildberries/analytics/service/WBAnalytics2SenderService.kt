package ru.wildberries.analytics.service

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.serialization.InternalSerializationApi
import ru.wildberries.analytics.CoroutineScopeFactory
import ru.wildberries.analytics.WBAnalytics2Logger
import ru.wildberries.analytics.api.BatchModel
import ru.wildberries.analytics.batch.BatchData
import ru.wildberries.analytics.batch.BatchRepository
import ru.wildberries.analytics.config.ConfigRepository
import ru.wildberries.analytics.config.WBA2Config
import ru.wildberries.analytics.domain.ApiData
import ru.wildberries.analytics.event.EventsRepository
import ru.wildberries.analytics.logDebug
import ru.wildberries.analytics.logError
import ru.wildberries.analytics.logException
import ru.wildberries.analytics.network.NetworkAvailabilitySource
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

@OptIn(InternalSerializationApi::class)
@Suppress("OPT_IN_USAGE")
internal class WBAnalytics2SenderService(
    private val eventsRepository: EventsRepository,
    private val log: WBAnalytics2Logger,
    private val batchRepository: BatchRepository,
    configRepository: ConfigRepository,
    networkAvailabilitySource: NetworkAvailabilitySource,
    coroutineScopeFactory: CoroutineScopeFactory,
) {

    private val serviceScope = coroutineScopeFactory.create(javaClass.simpleName)

    init {
        log.logDebug { "Initializing WBAnalytics2SenderService" }
        combine(
            networkAvailabilitySource.isAvailableFlow,
            configRepository.config.onEach { log.logDebug("WBA config: $it") }
        ) { isAvailable, config ->
            isAvailable to config
        }
            .mapLatest { (isAvailable, config) ->
                if (isAvailable) {
                    sendEventsProcess(config)
                }
            }
            .catch { throwable ->
                when (throwable) {
                    is Exception -> log.logException { throwable }
                    is Error -> log.logError {
                        error(throwable)
                    }
                }
            }
            .launchIn(serviceScope)
    }

    private suspend fun sendEventsProcess(config: WBA2Config) {
        delay(config.delays.initialDelay)
        eventsRepository.deleteOldestEventsThatExceed(config.maxEventsInCache)
        while (currentCoroutineContext().isActive) {
            try {
                val apiData = eventsRepository.awaitFirstApiData()
                processSendOperation(apiData, config)
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

    private tailrec suspend fun processSendOperation(apiData: ApiData, config: WBA2Config) {
        log.logDebug { "Reading batch with batching strategy: ${config.batching}" }
        val batch = batchRepository.getBatch(apiData, config.batching)
        if (batch != null) {
            val batchNumber = batch.model.meta.batchNumber
            log.logDebug { "Processing batch for API URL: ${apiData.apiUrl} events count ${batch.eventIds.size}, content length ${batch.contentLength}" }
            val eventIds = batch.eventIds
            val sendResult = sendBatch(batch, config)
            if (log.isEnabled && batch.model.events.isNotEmpty()) {
                val eventNumber = batch.model.events.first().eventNumber
                log.logDebug("Sending ${eventIds.size} events ($eventNumber - ${eventNumber + eventIds.lastIndex.toULong()} event_num) with $batchNumber batch_number, ${batch.contentLength} byte size")
            }
            when {
                // При успешной отправке обновляем данные по отправленным событиям и удаляем элементы.
                sendResult == SendResult.Success -> {
                    log.logDebug { "Successfully sent batch $batchNumber" }
                    batchRepository.batchSent(eventIds)
                    log.logDebug { "Apply changes to database after sending, delete ${eventIds.size} events" }
                }

                // Если произошла критическая ошибка - удаляем элементы.
                sendResult == SendResult.CriticalFail -> {
                    log.logError {
                        error(Error("Critical failure in batch $batchNumber"))
                        detail("batchNumber", batchNumber.toString())
                        detail("eventsCount", eventIds.size.toString())
                        detail("batchSize", batch.contentLength.toString())
                    }
                    batchRepository.dropBatch(eventIds)
                }
            }
            eventsRepository.deleteOldestEventsThatExceed(config.maxEventsInCache)
        }

        val nextApiData = eventsRepository.getFirstEventApiDataOrNull()
        if (nextApiData != null) {
            val delay = config.delays.delayBetweenBatches
            log.logDebug { "Found next batch for API URL: ${apiData.apiUrl}, that will be processed after $delay" }
            delay(delay)
            processSendOperation(nextApiData, config)
        } else {
            val delay = config.delays.delayBetweenOperations
            log.logDebug { "Events in db are over, await new events after $delay" }
            delay(delay)
        }
    }

    private suspend fun sendBatch(
        batchData: BatchData,
        config: WBA2Config
    ): SendResult {
        val batch: BatchModel = batchData.model
        val apiData = batchData.apiData
        val batchNumber = batch.meta.batchNumber
        var attempt = 1
        val retryPolicy = config.retryPolicy
        val transport = config.transport
        log.logDebug { "Starting to send batch $batchNumber to ${apiData.apiUrl}" }
        while (true) {
            try {
                log.logDebug { "Attempt $attempt to send batch $batchNumber" }
                val responseCode = transport.send(
                    url = apiData.apiUrl,
                    body = batch,
                    strategy = BatchModel.serializer(),
                    headers = mapOf("X-Api-Key" to apiData.apiKey),
                )
                if (responseCode != 200) {
                    throw IOException("Server error $responseCode")
                }
                return SendResult.Success
            } catch (e: CancellationException) {
                log.logDebug { "Batch $batchNumber was cancelled on attempt $attempt" }
                throw e
            } catch (e: OutOfMemoryError) {
                val (heaviestEventName, heaviestEventSize) = batchData.heaviestEvent
                log.logError {
                    error(e)
                    detail("count", batch.events.size.toString())
                    detail("heaviest", heaviestEventName)
                    detail("heaviestSize", heaviestEventSize.toString())
                    detail("batchNumber", batchNumber.toString())
                    detail("attempt", attempt.toString())
                }
                return SendResult.CriticalFail
            } catch (exception: Exception) {
                log.logException { exception }
                if (retryPolicy.shouldRetry(attempt, exception)) {
                    val delay = retryPolicy.nextDelay(attempt)
                    log.logDebug { "Waiting $delay before next attempt for batch $batchNumber" }
                    delay(delay)
                    attempt++
                } else {
                    log.logDebug { "Sending failed on $attempt attempt" }
                    return SendResult.Fail
                }
            }
        }
    }

    private enum class SendResult {
        Success, Fail, CriticalFail;
    }
}
