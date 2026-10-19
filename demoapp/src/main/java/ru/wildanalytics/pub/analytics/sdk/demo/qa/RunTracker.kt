package ru.wildanalytics.pub.analytics.sdk.demo.qa

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.wildanalytics.pub.analytics.sdk.demo.core.LogEntry
import ru.wildanalytics.pub.analytics.sdk.demo.core.RequestRecord

/** Активный прогон набора: считает вердикт в скоупе приложения и сохраняет первый итоговый. */
@OptIn(ExperimentalCoroutinesApi::class)
class RunTracker(
    val suite: QaSuite,
    entries: StateFlow<List<LogEntry>>,
    requests: StateFlow<List<RequestRecord>>,
    private val results: ResultStore,
    scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val evaluate: (String, List<LogEntry>, List<RequestRecord>, Long) -> Verdict,
) {

    private val _active = MutableStateFlow<ActiveRun?>(null)
    val active: StateFlow<ActiveRun?> = _active.asStateFlow()

    private var settled: Pair<ActiveRun, Verdict>? = null
    private var nextRunId = 0L

    val elapsedSeconds: StateFlow<Long> = _active.flatMapLatest { run ->
        if (run == null) flowOf(0L) else ticker().map { (now() - run.startedAtMillis) / MILLIS_IN_SECOND }
    }.stateIn(scope, SharingStarted.Eagerly, 0L)

    val verdict: StateFlow<Verdict?> = _active.flatMapLatest { run ->
        if (run == null) {
            flowOf(null)
        } else {
            combine(entries, requests, ticker()) { e, r, _ ->
                val since = run.startedAtMillis
                settle(
                    run,
                    evaluate(
                        run.scenario,
                        e.filter { it.timestampMillis >= since },
                        r.filter { it.timestampMillis >= since },
                        now() - since
                    )
                )
            }
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    fun start(scenario: String) {
        _active.value = ActiveRun(suite, scenario, now(), ++nextRunId)
    }

    fun stop() {
        _active.value = null
    }

    private fun settle(run: ActiveRun, verdict: Verdict): Verdict {
        settled?.takeIf { it.first == run }?.let { return it.second }
        if (verdict.status != Verdict.Status.WAITING) {
            settled = run to verdict
            results.save(suite, run.scenario, ScenarioResult(verdict.status, (now() - run.startedAtMillis) / MILLIS_IN_SECOND))
        }
        return verdict
    }

    private fun ticker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MILLIS)
        }
    }
}

private const val TICK_MILLIS = 1_000L
private const val MILLIS_IN_SECOND = 1_000L
