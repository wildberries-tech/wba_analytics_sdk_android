package ru.wildanalytics.pub.analytics.sdk.demo.qa.loss

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wildanalytics.pub.analytics.sdk.demo.R
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.DemoScaffold
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.DemoTextField
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.FilterChips
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.OverflowMenu
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.ScenarioRow
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.scenarioStatus
import ru.wildanalytics.pub.analytics.sdk.demo.qa.ActiveRunBanner
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.TransportMode

@Composable
fun LossQaScreen(viewModel: LossQaViewModel, onBack: () -> Unit, onRunLog: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DemoScaffold(
        title = stringResource(R.string.qa_suite_loss),
        onBack = onBack,
        message = state.message,
        onMessageShown = viewModel::onMessageShown,
        actions = { OverflowMenu(listOf(R.string.qa_clear_results to viewModel::onClearResults)) },
    ) { padding ->
        val activeScenario = state.active?.let { run -> LossScenario.entries.firstOrNull { it.name == run.scenario } }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item { Status(state) }
            activeScenario?.let { scenario ->
                item { ActiveRunBanner(stringResource(scenario.title), state.verdict, state.elapsedSeconds, onRunLog) }
            }
            items(LossScenario.entries, key = { it.name }) { scenario ->
                ScenarioRow(
                    number = scenario.ordinal + 1,
                    title = stringResource(scenario.title),
                    status = scenarioStatus(scenario == activeScenario, state.verdict, state.statuses[scenario.name]),
                    onRun = { viewModel.onRun(scenario) },
                ) {
                    Text(text = stringResource(scenario.description), style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = stringResource(R.string.qa_label_expected) + ": " + stringResource(scenario.expected),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                HorizontalDivider()
            }
            item { ManualActions(state, viewModel) }
        }
    }
}

@Composable
private fun Status(state: LossQaUiState) {
    val queue = state.queue?.let { stringResource(R.string.home_queue_value, it.total, it.important) } ?: "—"
    val retries = stringResource(if (state.sdkConfig?.isRetryEnabled != false) R.string.on else R.string.off)
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.qa_loss_status, queue, state.sdkConfig?.maxEventsInCache?.toString() ?: "—", retries),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.isDatabaseClosed) {
            Text(
                text = stringResource(R.string.qa_loss_db_closed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ManualActions(state: LossQaUiState, viewModel: LossQaViewModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(R.string.qa_loss_manual) + if (expanded) " ▾" else " ▸")
        }
        if (!expanded) return@Column
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoTextField(
                value = state.floodCount,
                onValueChange = viewModel::onFloodCountChanged,
                label = stringResource(R.string.qa_loss_flood_count),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = viewModel::onFlood) { Text(stringResource(R.string.qa_loss_flood)) }
        }
        Text(text = stringResource(R.string.qa_loss_transport), style = MaterialTheme.typography.labelLarge)
        FilterChips(
            options = TransportMode.entries.map { it.title },
            selected = state.transportMode.ordinal,
            onSelect = { viewModel.onTransport(TransportMode.entries[it]) },
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
        OutlinedButton(onClick = viewModel::onDrain, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.qa_loss_drain))
        }
        OutlinedButton(onClick = viewModel::onReset, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.qa_loss_reset)) }
        OutlinedButton(
            onClick = viewModel::onRestart,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.qa_loss_restart)) }
    }
}
