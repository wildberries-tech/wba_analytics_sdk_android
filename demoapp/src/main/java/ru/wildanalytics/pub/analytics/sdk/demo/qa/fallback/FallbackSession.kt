package ru.wildanalytics.pub.analytics.sdk.demo.qa.fallback

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.wildanalytics.pub.analytics.sdk.demo.qa.PendingRun
import ru.wildanalytics.pub.analytics.sdk.demo.qa.ProcessRestarter
import ru.wildanalytics.pub.analytics.sdk.demo.qa.QaSuite
import ru.wildanalytics.pub.analytics.sdk.demo.qa.RunTracker
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsEndpointConfig
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsRepository
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.SdkDatabaseInspector

/** Набор «Резервный домен»: каждый сценарий стартует в чистом процессе, переключение живёт до перезапуска. */
class FallbackSession(
    private val repository: () -> AnalyticsRepository,
    private val database: SdkDatabaseInspector,
    private val restarter: ProcessRestarter,
    val tracker: RunTracker,
    private val scope: CoroutineScope,
) {

    fun run(scenario: FallbackScenario) {
        repository().updateEndpointConfig(AnalyticsEndpointConfig(scenario.mainUrl, scenario.fallbackUrl))
        database.deleteSdkDatabase()
        restarter.restart(PendingRun(QaSuite.FALLBACK, scenario.name))
    }

    fun resume(name: String?) {
        if (FallbackScenario.entries.none { it.name == name }) return
        tracker.start(requireNotNull(name))
        sendMore()
    }

    fun rerun() {
        scenario()?.let(::run)
    }

    fun sendMore() {
        scope.launch {
            val repository = repository()
            repository.logEvents(repository.eventsConfig.copy(count = QA_EVENTS)).onFailure { Log.e(TAG, "QA events failed", it) }
        }
    }

    fun scenario(): FallbackScenario? =
        tracker.active.value?.let { run -> FallbackScenario.entries.firstOrNull { it.name == run.scenario } }

    fun aliases(): Map<String, String> {
        val scenario = scenario() ?: return emptyMap()
        return listOfNotNull(
            scenario.mainUrl.toHttpUrlOrNull()?.host?.let { it to ALIAS_MAIN },
            scenario.fallbackUrl.toHttpUrlOrNull()?.host?.let { it to ALIAS_FALLBACK },
        ).toMap()
    }
}

private const val TAG = "WildAnalytics"
private const val ALIAS_MAIN = "main"
private const val ALIAS_FALLBACK = "fallback"
