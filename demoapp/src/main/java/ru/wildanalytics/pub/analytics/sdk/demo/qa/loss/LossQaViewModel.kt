package ru.wildanalytics.pub.analytics.sdk.demo.qa.loss

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ru.wildanalytics.pub.analytics.sdk.demo.qa.ActiveRun
import ru.wildanalytics.pub.analytics.sdk.demo.qa.QaSuite
import ru.wildanalytics.pub.analytics.sdk.demo.qa.ResultKey
import ru.wildanalytics.pub.analytics.sdk.demo.qa.ScenarioResult
import ru.wildanalytics.pub.analytics.sdk.demo.qa.Verdict
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsRepository
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsSdkConfig
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.QueueStats
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.TransportMode

class LossQaViewModel(
    private val controller: LossController,
    repository: AnalyticsRepository,
    queueStats: Flow<QueueStats?>,
    results: Flow<Map<ResultKey, ScenarioResult>>,
    private val clearResults: () -> Unit,
) : ViewModel() {

    private val floodCount = MutableStateFlow(DEFAULT_FLOOD_COUNT.toString())

    private val run = combine(controller.tracker.active, controller.tracker.verdict, controller.tracker.elapsedSeconds, ::Triple)

    private val sdk = combine(
        queueStats,
        repository.sdkConfigFlow,
        repository.transportMode,
        controller.isDatabaseClosed,
        controller.isBusy
    ) {
            queue, config, transport, closed, busy ->
        LossQaUiState(queue = queue, sdkConfig = config, transportMode = transport, isDatabaseClosed = closed, isBusy = busy)
    }

    val state: StateFlow<LossQaUiState> = combine(
        sdk,
        run,
        results,
        floodCount,
        controller.message
    ) { sdk, (active, verdict, elapsed), results, count, message ->
        sdk.copy(
            statuses = results.filterKeys { it.suite == QaSuite.LOSS }.mapKeys { it.key.scenario }.mapValues { it.value.status },
            active = active,
            verdict = verdict,
            elapsedSeconds = elapsed,
            floodCount = count,
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LossQaUiState())

    fun onRun(scenario: LossScenario) = controller.run(scenario)

    fun onFloodCountChanged(value: String) {
        floodCount.value = value.filter(Char::isDigit).take(MAX_FLOOD_COUNT.toString().length)
    }

    fun onFlood() {
        floodCount.value.toIntOrNull()?.let(controller::floodEvents)
    }

    fun onTransport(mode: TransportMode) = controller.setTransport(mode)

    fun onDrain() = controller.drain()

    fun onReset() = controller.resetSdkSettings()

    fun onRestart() = controller.restartApp()

    fun onClearResults() = clearResults()

    fun onMessageShown() = controller.onMessageShown()
}

data class LossQaUiState(
    val queue: QueueStats? = null,
    val sdkConfig: AnalyticsSdkConfig? = null,
    val transportMode: TransportMode = TransportMode.REAL,
    val isDatabaseClosed: Boolean = false,
    val isBusy: Boolean = false,
    val statuses: Map<String, Verdict.Status> = emptyMap(),
    val active: ActiveRun? = null,
    val verdict: Verdict? = null,
    val elapsedSeconds: Long = 0,
    val floodCount: String = "",
    @StringRes val message: Int? = null,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L
