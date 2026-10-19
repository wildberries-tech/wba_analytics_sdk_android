package ru.wildanalytics.pub.analytics.send

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import ru.wildanalytics.pub.analytics.api.BatchModel
import ru.wildanalytics.pub.analytics.batch.BatchData
import ru.wildanalytics.pub.analytics.batch.BatchRepository
import ru.wildanalytics.pub.analytics.config.ConfigRepository
import ru.wildanalytics.pub.analytics.config.WildAnalyticsConfig
import ru.wildanalytics.pub.analytics.diagnostics.DiagnosticAttributes
import ru.wildanalytics.pub.analytics.diagnostics.DiagnosticMarker
import ru.wildanalytics.pub.analytics.diagnostics.DiagnosticsReporter
import ru.wildanalytics.pub.analytics.diagnostics.QueueEvictionReporter
import ru.wildanalytics.pub.analytics.diagnostics.toAttributeValue
import ru.wildanalytics.pub.analytics.domain.ApiData
import ru.wildanalytics.pub.analytics.event.EventsRepository
import ru.wildanalytics.pub.analytics.logDebug
import ru.wildanalytics.pub.analytics.logError
import ru.wildanalytics.pub.analytics.send.fallback.EndpointRoute
import ru.wildanalytics.pub.analytics.send.fallback.EndpointRouter
import ru.wildanalytics.pub.analytics.transport.CustomHeadersRepository
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
    private val customHeadersRepository: CustomHeadersRepository,
    private val queueEvictionReporter: QueueEvictionReporter,
    private val diagnostics: DiagnosticsReporter,
    private val endpointRouter: EndpointRouter,
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

    private suspend fun sendBatch(config: WildAnalyticsConfig, apiData: ApiData) {
        sendBatchLock.withLock {
            log.logDebug { "Reading batch with batching strategy: ${config.batching}" }
            val batch = batchRepository.getBatch(apiData, config.batching)
            if (batch != null) {
                val batchNumber = batch.model.meta.batchNumber
                log.logDebug { "Processing batch for API URL: ${apiData.apiUrl} events count ${batch.eventIds.size}, content length ${batch.contentLength}" }
                val eventIds = batch.eventIds
                val route = endpointRouter.routeFor(batch)
                val sendResult = sendBatch(batch, config, route)
                if (log.isEnabled && batch.model.events.isNotEmpty()) {
                    val eventNumber = batch.model.events.first().eventNumber
                    log.logDebug("Sending ${eventIds.size} events ($eventNumber - ${eventNumber + eventIds.lastIndex.toULong()} event_num) with $batchNumber batch_number, ${batch.contentLength} byte size")
                }
                when (sendResult) {
                    // При успешной отправке обновляем данные по отправленным событиям и удаляем элементы.
                    SendResult.Success -> {
                        log.logDebug { "Successfully sent batch $batchNumber" }
                        batchRepository.batchSent(eventIds)
                        log.logDebug { "Apply changes to database after sending, delete ${eventIds.size} events" }
                    }

                    // Если произошла критическая ошибка - удаляем элементы.
                    SendResult.CriticalFail -> {
                        log.logError {
                            error(Error("Critical failure in batch $batchNumber"))
                            detail("batchNumber", batchNumber.toString())
                            detail("eventsCount", eventIds.size.toString())
                            detail("batchSize", batch.contentLength.toString())
                        }
                        batchRepository.dropBatch(eventIds)
                    }

                    is SendResult.Fail -> diagnostics.report(
                        marker = DiagnosticMarker.SEND_FAILED,
                        message = "Batch was not sent, events are kept for the next attempt",
                        attributes = sendFailureAttributes(sendResult.cause, sendResult.attempts, batch) + route.failureAttributes(),
                        throwable = sendResult.cause,
                    )
                }
                eventsRepository.deleteOldestEventsThatExceed(config.maxEventsInCache)?.let(queueEvictionReporter::report)
            }
        }
    }

    private fun sendFailureAttributes(cause: Exception, attempts: Int, batch: BatchData): Map<String, String> =
        buildMap {
            (cause as? HttpStatusException)?.let { put(DiagnosticAttributes.CODE, it.code.toString()) }
            put(DiagnosticAttributes.EXCEPTION, cause.javaClass.name)
            cause.message?.let { put(DiagnosticAttributes.ERROR, it.toAttributeValue()) }
            put(DiagnosticAttributes.ATTEMPTS, attempts.toString())
            put(DiagnosticAttributes.EVENTS, batch.eventIds.size.toString())
            put(DiagnosticAttributes.BATCH_NUMBER, batch.model.meta.batchNumber.toString())
            put(DiagnosticAttributes.BATCH_SIZE, batch.contentLength.toString())
        }

    private suspend fun sendBatch(
        batchData: BatchData,
        config: WildAnalyticsConfig,
        route: EndpointRoute,
    ): SendResult {
        val batch: BatchModel = batchData.model
        val apiData = batchData.apiData
        val batchNumber = batch.meta.batchNumber
        var attempt = 1
        val retryPolicy = config.retryPolicy
        val transport = config.transport
        log.logDebug { "Starting to send batch $batchNumber to ${route.url}" }
        while (true) {
            try {
                log.logDebug { "Attempt $attempt to send batch $batchNumber" }
                val responseCode = transport.send(
                    url = route.url,
                    body = batch,
                    strategy = BatchModel.serializer(),
                    headers = customHeadersRepository.current().newBuilder()
                        .set("X-Api-Key", apiData.apiKey)
                        .build(),
                )
                if (responseCode != 200) {
                    throw HttpStatusException(responseCode)
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
                log.logDebug { "Attempt $attempt to send batch $batchNumber failed: $exception" }
                if (route.onFailure(exception, attempt)) {
                    attempt++
                } else if (retryPolicy.shouldRetry(attempt, exception)) {
                    val delay = retryPolicy.nextDelay(attempt)
                    log.logDebug { "Waiting $delay before next attempt for batch $batchNumber" }
                    delay(delay)
                    attempt++
                } else {
                    log.logDebug { "Sending failed on $attempt attempt" }
                    return SendResult.Fail(exception, attempt)
                }
            }
        }
    }

    private sealed interface SendResult {
        data object Success : SendResult
        data object CriticalFail : SendResult
        class Fail(val cause: Exception, val attempts: Int) : SendResult
    }

    companion object {
        const val NAME = "send_all_analytics_events_operation"
    }
}

internal class HttpStatusException(val code: Int) : IOException("Server error $code")
