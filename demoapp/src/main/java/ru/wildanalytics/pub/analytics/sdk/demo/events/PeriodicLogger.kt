package ru.wildanalytics.pub.analytics.sdk.demo.events

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsRepository
import kotlin.time.Duration.Companion.seconds

/** Отправка текущего шаблона события с интервалом, пока приложение в фоне; живёт вне экранов. */
@OptIn(ExperimentalCoroutinesApi::class)
class PeriodicLogger(
    private val repository: () -> AnalyticsRepository,
    background: BackgroundStateProvider,
    scope: CoroutineScope,
) {

    val intervalSeconds: StateFlow<Int> = repository().eventsConfigFlow
        .map { it.periodicLoggingIntervalSeconds }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    init {
        combine(intervalSeconds, background.isInBackground) { interval, inBackground -> interval.takeIf { it > 0 && inBackground } }
            .flatMapLatest { interval -> if (interval == null) emptyFlow() else ticks(interval) }
            .launchIn(scope)
    }

    private fun ticks(intervalSeconds: Int) = flow<Unit> {
        while (true) {
            delay(intervalSeconds.seconds)
            val repository = repository()
            repository.logEvents(repository.eventsConfig).onFailure { Log.e(TAG, "Failed to log periodic background event", it) }
        }
    }
}

private const val TAG = "WildAnalytics"
