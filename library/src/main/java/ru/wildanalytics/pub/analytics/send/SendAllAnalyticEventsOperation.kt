package ru.wildanalytics.pub.analytics.send

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.InternalSerializationApi
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import ru.wildanalytics.pub.analytics.api.BatchModel
import ru.wildanalytics.pub.analytics.batch.BatchData
import ru.wildanalytics.pub.analytics.batch.BatchRepository
import ru.wildanalytics.pub.analytics.config.ConfigRepository
import ru.wildanalytics.pub.analytics.config.WildAnalyticsConfig
import ru.wildanalytics.pub.analytics.domain.ApiData
import ru.wildanalytics.pub.analytics.event.EventsRepository
import ru.wildanalytics.pub.analytics.logDebug
import ru.wildanalytics.pub.analytics.logError
import ru.wildanalytics.pub.analytics.logException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Операция отправки всех аналитических событий из бд в сервис аналитики посредством батчей.
 * Работает, пока не закончатся события по правилам, описанным в конфиге [WildAnalyticsConfig]
 * @see WildAnalyticsConfig.Default
 * */
internal class SendAllAnalyticEventsOperation(
    private val eventsRepository: EventsRepository,
    private val batchRepository: BatchRepository,
    private val configRepository: ConfigRepository,
    private val log: WildAnalyticsLogger,
) {

    private val sendBatchLock = Mutex()

    suspend fun execute() {
        val config = configRepository.config.value
        val apiData = eventsRepository.getFirstEventApiDataOrNull() ?: let {
            log.logDebug { "No events in db" }
            return
        }
        execute(apiData, config)
    }

    tailrec suspend fun execute(apiData: ApiData, config: WildAnalyticsConfig) {
        sendBatch(config, apiData)
        val nextApiData = eventsRepository.getFirstEventApiDataOrNull()
        if (nextApiData != null) {
            val delay = config.delays.delayBetweenBatches
            log.logDebug { "Found next batch for API URL: ${apiData.apiUrl}, that will be processed after $delay" }
            delay(delay)
            execute(nextApiData, config)
        } else {
            log.logDebug { "No events in db" }
        }
    }

    @OptIn(InternalSerializationApi::class)
    private suspend fun sendBatch(config: WildAnalyticsConfig, apiData: ApiData) {
        sendBatchLock.withLock {
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
        }
    }

    @OptIn(InternalSerializationApi::class)
    private suspend fun sendBatch(
        batchData: BatchData,
        config: WildAnalyticsConfig
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

    companion object {
        const val NAME = "send_all_analytics_events_operation"
    }
}