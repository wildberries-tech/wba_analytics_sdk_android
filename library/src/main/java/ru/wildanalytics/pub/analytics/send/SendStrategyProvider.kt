package ru.wildanalytics.pub.analytics.send

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import ru.wildanalytics.pub.analytics.event.EventsRepository
import ru.wildanalytics.pub.analytics.network.NetworkAvailabilitySource
import ru.wildanalytics.pub.analytics.util.ProcessForegroundData
import ru.wildanalytics.pub.analytics.util.ProcessForegroundDataProvider

internal class SendStrategyProvider(
    private val eventsRepository: EventsRepository,
    private val log: WildAnalyticsLogger,
    private val networkAvailabilitySource: NetworkAvailabilitySource,
    private val sendOperation: SendAllAnalyticEventsOperation,
    private val sendOperationScheduler: SendOperationScheduler,
    processForegroundDataProvider: ProcessForegroundDataProvider = ProcessForegroundDataProvider(),
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val strategy: Flow<SendStrategy> = processForegroundDataProvider
        .processForegroundData
        .mapLatest { data -> isImmediateSendingAvailableFlow(data) }
        .distinctUntilChanged()
        .map { getStrategy(it) }

    /**
     * @return флаг, можно ли отправлять даннные сразу или стоит воспользоваться инструментом планирования отправки.
     *
     * **При уходе в фон с переднего плана** (напр. сворачивания приложения), разрешаем отправлять сразу до первой блокировки или выключения сети.
     * */
    private suspend fun isImmediateSendingAvailableFlow(data: ProcessForegroundData): Boolean =
        when {
            data.isInForeground -> true
            data.isFromForeground -> networkAvailabilitySource.availabilityFlow
                .first { !it }

            else -> false
        }

    private fun getStrategy(immediate: Boolean): SendStrategy =
        if (immediate) {
            ImmediateSendStrategy(
                eventsRepository = eventsRepository,
                log = log,
                sendOperation = sendOperation,
                networkAvailabilitySource = networkAvailabilitySource,
            )
        } else {
            SchedulingSendStrategy(
                eventsRepository = eventsRepository,
                log = log,
                sendOperationScheduler = sendOperationScheduler,
            )
        }
}