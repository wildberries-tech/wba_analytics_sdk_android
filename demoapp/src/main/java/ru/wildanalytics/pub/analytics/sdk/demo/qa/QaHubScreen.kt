package ru.wildanalytics.pub.analytics.sdk.demo.qa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import ru.wildanalytics.pub.analytics.sdk.demo.core.formatDuration
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.DemoScaffold
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.StatusTile
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.title

class QaHubViewModel(
    results: Flow<Map<ResultKey, ScenarioResult>>,
    trackers: List<RunTracker>,
    private val suiteSizes: Map<QaSuite, Int>,
) : ViewModel() {

    val state: StateFlow<List<SuiteRow>> = combine(listOf(results) + trackers.flatMap { listOf(it.active, it.elapsedSeconds) }) { values ->
        @Suppress("UNCHECKED_CAST")
        val resultMap = values[0] as Map<ResultKey, ScenarioResult>
        trackers.mapIndexed { index, tracker ->
            SuiteRow(
                suite = tracker.suite,
                passed = resultMap.count { (key, result) -> key.suite == tracker.suite && result.status == Verdict.Status.PASSED },
                total = suiteSizes.getValue(tracker.suite),
                active = values[1 + index * 2] as? ActiveRun,
                elapsedSeconds = values[2 + index * 2] as Long,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())
}

data class SuiteRow(
    val suite: QaSuite,
    val passed: Int,
    val total: Int,
    val active: ActiveRun?,
    val elapsedSeconds: Long,
)

@Composable
fun QaHubScreen(viewModel: QaHubViewModel, onBack: () -> Unit, onSuite: (QaSuite) -> Unit) {
    val rows by viewModel.state.collectAsStateWithLifecycle()
    DemoScaffold(title = stringResource(R.string.qa_hub_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            rows.forEach { row ->
                StatusTile(
                    title = stringResource(row.suite.title),
                    value = stringResource(R.string.qa_passed_count, row.passed, row.total),
                    subtitle = row.active?.let { run ->
                        val title = row.suite.scenarioTitle(run.scenario)?.let { stringResource(it) } ?: run.scenario
                        stringResource(R.string.qa_running, title, row.elapsedSeconds.formatDuration())
                    },
                    onClick = { onSuite(row.suite) },
                )
            }
        }
    }
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
