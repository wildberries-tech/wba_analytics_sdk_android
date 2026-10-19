package ru.wildanalytics.pub.analytics.sdk.demo.qa.loss

import android.util.Log
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.wildanalytics.pub.analytics.sdk.demo.R
import ru.wildanalytics.pub.analytics.sdk.demo.qa.PendingRun
import ru.wildanalytics.pub.analytics.sdk.demo.qa.QaSuite
import ru.wildanalytics.pub.analytics.sdk.demo.qa.RunTracker
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsRepository
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.QueueStats
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.TransportMode
import kotlin.time.Duration.Companion.seconds

/** Набор «Потеря событий»: сценарии выполняются в текущем процессе через мок-транспорт и flood. */
class LossController(
    private val repository: () -> AnalyticsRepository,
    private val closeDatabase: () -> Unit,
    private val queueStats: StateFlow<QueueStats?>,
    private val restart: (PendingRun) -> Unit,
    val tracker: RunTracker,
    private val scope: CoroutineScope,
) {

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _isDatabaseClosed = MutableStateFlow(false)
    val isDatabaseClosed: StateFlow<Boolean> = _isDatabaseClosed.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    fun run(scenario: LossScenario) = launchExclusive {
        val error = prepare(scenario)
        if (error != null) {
            _message.value = error
        } else {
            tracker.start(scenario.name)
            act(scenario)
        }
    }

    fun rerun() {
        scenario()?.let(::run)
    }

    fun sendMore() {
        scope.launch { flood(MORE_EVENTS) }
    }

    fun scenario(): LossScenario? = tracker.active.value?.let { run -> LossScenario.entries.firstOrNull { it.name == run.scenario } }

    fun floodEvents(count: Int) {
        scope.launch { flood(count.coerceIn(1, MAX_FLOOD_COUNT)) }
    }

    fun setTransport(mode: TransportMode) {
        repository().setTransportMode(mode).onFailure { Log.e(TAG, "Failed to set transport mode", it) }
    }

    fun drain() = launchExclusive {
        _message.value = drainQueue() ?: R.string.qa_loss_message_drained
    }

    fun resetSdkSettings() {
        repository().resetToDefaults()
        _message.value = R.string.qa_loss_message_reset
    }

    fun restartApp() {
        restart(PendingRun(QaSuite.LOSS, scenario = null))
    }

    fun resume(name: String?) {
        name?.let { scenario -> if (LossScenario.entries.any { it.name == scenario }) tracker.start(scenario) }
    }

    fun onMessageShown() {
        _message.value = null
    }

    @StringRes
    private suspend fun prepare(scenario: LossScenario): Int? = when (scenario) {
        LossScenario.INSERT_FAILED -> null
        LossScenario.QUEUE_EVICTION -> drainQueue() ?: applySdkSettings(maxEventsInCache = EVICTION_LIMIT, isRetryEnabled = false)
        LossScenario.EVENTS_KEPT_ON_FAILURE -> drainQueue() ?: applySdkSettings(isRetryEnabled = false)
        LossScenario.SEND_LOOP_RESTART -> drainQueue()
    }

    private suspend fun act(scenario: LossScenario) {
        when (scenario) {
            LossScenario.QUEUE_EVICTION -> {
                setTransport(TransportMode.HTTP_500)
                flood(EVICTION_FLOOD_COUNT)
            }
            LossScenario.EVENTS_KEPT_ON_FAILURE -> {
                setTransport(TransportMode.HTTP_500)
                flood(KEPT_FLOOD_COUNT)
            }
            LossScenario.SEND_LOOP_RESTART -> {
                setTransport(TransportMode.ERROR)
                flood(1)
            }
            LossScenario.INSERT_FAILED -> {
                withContext(Dispatchers.IO) { closeDatabase() }
                _isDatabaseClosed.value = true
                flood(KEPT_FLOOD_COUNT)
            }
        }
    }

    @StringRes
    private suspend fun drainQueue(): Int? {
        if (_isDatabaseClosed.value) return R.string.qa_loss_message_database_closed
        val previous = repository().transportMode.value
        setTransport(TransportMode.HTTP_200)
        val drained = withTimeoutOrNull(DRAIN_TIMEOUT) { queueStats.first { it == null || it.total == 0L } } != null
        if (previous != TransportMode.HTTP_200) setTransport(previous)
        return if (drained) null else R.string.qa_loss_message_drain_timeout
    }

    @StringRes
    private fun applySdkSettings(maxEventsInCache: Int? = null, isRetryEnabled: Boolean? = null): Int? {
        val current = repository().sdkConfig ?: return R.string.qa_loss_message_failed
        return repository().updateSdkConfig(
            current.copy(
                maxEventsInCache = maxEventsInCache ?: current.maxEventsInCache,
                isRetryEnabled = isRetryEnabled ?: current.isRetryEnabled,
            )
        ).onFailure { Log.e(TAG, "Failed to update SDK settings", it) }
            .fold(onSuccess = { null }, onFailure = { R.string.qa_loss_message_failed })
    }

    private suspend fun flood(count: Int) {
        repository().logFlood(count).onFailure { Log.e(TAG, "Flood failed", it) }
    }

    private fun launchExclusive(block: suspend () -> Unit) {
        if (!_isBusy.compareAndSet(expect = false, update = true)) {
            _message.value = R.string.qa_loss_message_busy
            return
        }
        scope.launch {
            runCatching { block() }.onFailure {
                Log.e(TAG, "Loss QA action failed", it)
                _message.value = R.string.qa_loss_message_failed
            }
            _isBusy.value = false
        }
    }
}

const val DEFAULT_FLOOD_COUNT = 200
const val MAX_FLOOD_COUNT = 20_000
private const val TAG = "WildAnalytics"
private const val MORE_EVENTS = 5
private const val EVICTION_LIMIT = 50
private const val EVICTION_FLOOD_COUNT = 200
private const val KEPT_FLOOD_COUNT = 5
private val DRAIN_TIMEOUT = 60.seconds
