package ru.wildanalytics.pub.analytics.sdk.demo.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ru.wildanalytics.pub.analytics.sdk.demo.core.LogEntry
import ru.wildanalytics.pub.analytics.sdk.demo.core.RequestRecord
import ru.wildanalytics.pub.analytics.sdk.demo.events.BackgroundStateProviderImpl
import ru.wildanalytics.pub.analytics.sdk.demo.events.PeriodicLogger
import ru.wildanalytics.pub.analytics.sdk.demo.qa.PendingRunStore
import ru.wildanalytics.pub.analytics.sdk.demo.qa.PrefsKeyValueStore
import ru.wildanalytics.pub.analytics.sdk.demo.qa.ProcessRestarter
import ru.wildanalytics.pub.analytics.sdk.demo.qa.QaSuite
import ru.wildanalytics.pub.analytics.sdk.demo.qa.ResultStore
import ru.wildanalytics.pub.analytics.sdk.demo.qa.RunActions
import ru.wildanalytics.pub.analytics.sdk.demo.qa.RunTracker
import ru.wildanalytics.pub.analytics.sdk.demo.qa.Verdict
import ru.wildanalytics.pub.analytics.sdk.demo.qa.fallback.FallbackScenario
import ru.wildanalytics.pub.analytics.sdk.demo.qa.fallback.FallbackSession
import ru.wildanalytics.pub.analytics.sdk.demo.qa.fallback.evaluateFallbackVerdict
import ru.wildanalytics.pub.analytics.sdk.demo.qa.loss.LossController
import ru.wildanalytics.pub.analytics.sdk.demo.qa.loss.LossScenario
import ru.wildanalytics.pub.analytics.sdk.demo.qa.loss.evaluateLossVerdict
import ru.wildanalytics.pub.analytics.sdk.demo.core.RequestLog
import ru.wildanalytics.pub.analytics.sdk.demo.core.SdkLogger
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsRepository
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsRepositoryImpl
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.QueueStats
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.SdkDatabaseInspector
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.pollQueue

/** Зависимости, которые живут дольше экрана. */
class AppContainer(private val application: Application) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val logger = SdkLogger()
    val requestLog = RequestLog()
    val database = SdkDatabaseInspector(application)
    val repository: AnalyticsRepository by lazy { AnalyticsRepositoryImpl(application, requestLog) }

    val queueStats: StateFlow<QueueStats?> = pollQueue(
        QUEUE_POLL_MILLIS,
        database::readStats
    ).stateIn(appScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val results = ResultStore(PrefsKeyValueStore(application, RESULTS_PREFS))
    private val pendingRuns = PendingRunStore(PrefsKeyValueStore(application, PENDING_PREFS))
    private val restarter = ProcessRestarter(application, pendingRuns)

    val fallback = FallbackSession(
        repository = { repository },
        database = database,
        restarter = restarter,
        tracker = tracker(QaSuite.FALLBACK) { scenario, entries, requests, elapsed ->
            evaluateFallbackVerdict(FallbackScenario.valueOf(scenario).expectation, entries, requests, elapsed)
        },
        scope = appScope,
    )

    val loss = LossController(
        repository = { repository },
        closeDatabase = database::closeSdkDatabase,
        queueStats = queueStats,
        restart = restarter::restart,
        tracker = tracker(QaSuite.LOSS) { scenario, entries, _, elapsed ->
            evaluateLossVerdict(LossScenario.valueOf(scenario), entries, elapsed)
        },
        scope = appScope,
    )

    val periodicLogger by lazy { PeriodicLogger({ repository }, BackgroundStateProviderImpl(), appScope) }

    /** Продолжает прогон после перезапуска процесса; возвращает набор, на экран которого нужно вернуться. */
    fun resumePending(): QaSuite? {
        val run = pendingRuns.take() ?: return null
        when (run.suite) {
            QaSuite.FALLBACK -> fallback.resume(run.scenario)
            QaSuite.LOSS -> loss.resume(run.scenario)
        }
        return run.suite
    }

    fun tracker(suite: QaSuite): RunTracker = when (suite) {
        QaSuite.FALLBACK -> fallback.tracker
        QaSuite.LOSS -> loss.tracker
    }

    fun runActions(suite: QaSuite): RunActions = when (suite) {
        QaSuite.FALLBACK -> RunActions(fallback::sendMore, fallback::rerun, fallback::aliases)
        QaSuite.LOSS -> RunActions(loss::sendMore, loss::rerun) { emptyMap() }
    }

    val suiteSizes = mapOf(QaSuite.FALLBACK to FallbackScenario.entries.size, QaSuite.LOSS to LossScenario.entries.size)

    private fun tracker(
        suite: QaSuite,
        evaluate: (String, List<LogEntry>, List<RequestRecord>, Long) -> Verdict,
    ) = RunTracker(suite, logger.entries, requestLog.requests, results, appScope, evaluate = evaluate)
}

private const val RESULTS_PREFS = "qa_results"
private const val PENDING_PREFS = "qa_pending"
private const val QUEUE_POLL_MILLIS = 1_000L
private const val STOP_TIMEOUT_MILLIS = 5_000L
