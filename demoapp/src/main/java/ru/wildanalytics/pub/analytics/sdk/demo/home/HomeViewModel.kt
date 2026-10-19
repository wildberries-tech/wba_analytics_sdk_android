package ru.wildanalytics.pub.analytics.sdk.demo.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.wildanalytics.pub.analytics.sdk.demo.core.LogEntry
import ru.wildanalytics.pub.analytics.sdk.demo.core.RequestRecord
import ru.wildanalytics.pub.analytics.sdk.demo.core.markerCounts
import ru.wildanalytics.pub.analytics.sdk.demo.qa.ActiveRun
import ru.wildanalytics.pub.analytics.sdk.demo.qa.QaSuite
import ru.wildanalytics.pub.analytics.sdk.demo.qa.ResultKey
import ru.wildanalytics.pub.analytics.sdk.demo.qa.ScenarioResult
import ru.wildanalytics.pub.analytics.sdk.demo.qa.Verdict
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsRepository
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.QueueStats

class HomeViewModel(
    private val repository: AnalyticsRepository,
    queueStats: Flow<QueueStats?>,
    entries: Flow<List<LogEntry>>,
    requests: Flow<List<RequestRecord>>,
    results: Flow<Map<ResultKey, ScenarioResult>>,
    periodicSeconds: Flow<Int>,
    activeRuns: Flow<List<ActiveRun>>,
    private val suiteSizes: Map<QaSuite, Int>,
) : ViewModel() {

    private val isSending = MutableStateFlow(false)

    private val sdk = combine(
        queueStats,
        repository.sdkConfigFlow,
        repository.endpointConfigFlow,
        repository.transportMode,
        periodicSeconds,
    ) { queue, config, endpoint, transport, periodic ->
        HomeUiState(queue = queue, sdkConfig = config, transportMode = transport, periodicSeconds = periodic) to endpoint
    }

    private val qa = combine(results, activeRuns) { results, active ->
        suiteSizes.mapValues { (suite, total) ->
            SuiteSummary(
                passed = results.count { (key, result) -> key.suite == suite && result.status == Verdict.Status.PASSED },
                total = total,
                active = active.firstOrNull { it.suite == suite },
            )
        }
    }

    val state: StateFlow<HomeUiState> = combine(sdk, entries, requests, qa, isSending) { (base, endpoint), entries, requests, qa, sending ->
        base.copy(
            lastSend = requests.firstOrNull(),
            endpoint = endpointStatus(endpoint, entries),
            markerCounts = entries.markerCounts(),
            qa = qa,
            isSending = sending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    fun onSendEvent() {
        viewModelScope.launch {
            isSending.value = true
            repository.logEvents(repository.eventsConfig).onFailure { Log.e(TAG, "Failed to log events", it) }
            isSending.value = false
        }
    }
}

private const val TAG = "WildAnalytics"
private const val STOP_TIMEOUT_MILLIS = 5_000L
