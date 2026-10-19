package ru.wildanalytics.pub.analytics.sdk.demo.qa.fallback

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ru.wildanalytics.pub.analytics.sdk.demo.R
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.DemoScaffold
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.MonoText
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.OverflowMenu
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.ScenarioRow
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.scenarioStatus
import ru.wildanalytics.pub.analytics.sdk.demo.qa.ActiveRun
import ru.wildanalytics.pub.analytics.sdk.demo.qa.ActiveRunBanner
import ru.wildanalytics.pub.analytics.sdk.demo.qa.QaSuite
import ru.wildanalytics.pub.analytics.sdk.demo.qa.ResultKey
import ru.wildanalytics.pub.analytics.sdk.demo.qa.ScenarioResult
import ru.wildanalytics.pub.analytics.sdk.demo.qa.Verdict

class FallbackQaViewModel(
    private val session: FallbackSession,
    results: Flow<Map<ResultKey, ScenarioResult>>,
    private val clearResults: () -> Unit,
) : ViewModel() {

    val state: StateFlow<FallbackQaUiState> = combine(
        results,
        session.tracker.active,
        session.tracker.verdict,
        session.tracker.elapsedSeconds,
    ) { results, active, verdict, elapsed ->
        FallbackQaUiState(
            statuses = results.filterKeys { it.suite == QaSuite.FALLBACK }.mapKeys { it.key.scenario }.mapValues { it.value.status },
            active = active,
            verdict = verdict,
            elapsedSeconds = elapsed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FallbackQaUiState())

    fun onRun(scenario: FallbackScenario) = session.run(scenario)

    fun onClearResults() = clearResults()
}

data class FallbackQaUiState(
    val statuses: Map<String, Verdict.Status> = emptyMap(),
    val active: ActiveRun? = null,
    val verdict: Verdict? = null,
    val elapsedSeconds: Long = 0,
)

@Composable
fun FallbackQaScreen(viewModel: FallbackQaViewModel, onBack: () -> Unit, onRunLog: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DemoScaffold(
        title = stringResource(R.string.qa_suite_fallback),
        onBack = onBack,
        actions = { OverflowMenu(listOf(R.string.qa_clear_results to viewModel::onClearResults)) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            val active = state.active
            val activeScenario = active?.let { run -> FallbackScenario.entries.firstOrNull { it.name == run.scenario } }
            if (activeScenario != null) {
                ActiveRunBanner(stringResource(activeScenario.title), state.verdict, state.elapsedSeconds, onRunLog)
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(FallbackScenario.entries, key = { it.name }) { scenario ->
                    ScenarioRow(
                        number = scenario.ordinal + 1,
                        title = stringResource(scenario.title),
                        status = scenarioStatus(scenario == activeScenario, state.verdict, state.statuses[scenario.name]),
                        precondition = scenario.precondition?.let { stringResource(it) },
                        onRun = { viewModel.onRun(scenario) },
                    ) {
                        MonoText(stringResource(R.string.qa_label_main) + "     " + scenario.mainUrl.host())
                        MonoText(stringResource(R.string.qa_label_fallback) + " " + scenario.fallbackUrl.host())
                        Text(
                            text = stringResource(R.string.qa_label_expected) + ": " + stringResource(scenario.expected),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun String.host(): String = substringAfter("://").substringBefore('/').ifEmpty { "—" }

private const val STOP_TIMEOUT_MILLIS = 5_000L
