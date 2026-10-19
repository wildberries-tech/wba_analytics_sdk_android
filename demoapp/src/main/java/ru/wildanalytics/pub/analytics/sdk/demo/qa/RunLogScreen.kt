package ru.wildanalytics.pub.analytics.sdk.demo.qa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wildanalytics.pub.analytics.sdk.demo.R
import ru.wildanalytics.pub.analytics.sdk.demo.core.copyToClipboard
import ru.wildanalytics.pub.analytics.sdk.demo.core.formatDuration
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.DemoScaffold
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.FilterChips
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.LogItemRow
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.OverflowMenu
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.PASSED_COLOR
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.PASSED_CONTAINER
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.PASSED_CONTENT
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.label
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.title

@Composable
fun RunLogScreen(viewModel: RunLogViewModel, suite: QaSuite, titleOf: (String) -> Int?, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scenarioTitle = state.run?.let { titleOf(it.scenario) }
    val title = scenarioTitle?.let { stringResource(it) } ?: stringResource(suite.title)
    val verdictText = state.verdict?.let { stringResource(it.status.label()) + " · " + stringResource(it.reason) }
    DemoScaffold(
        title = title,
        onBack = onBack,
        actions = { OverflowMenu(listOf(R.string.qa_copy_report to { context.copyToClipboard(viewModel.report(title, verdictText)) })) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (state.run == null) {
                Text(text = stringResource(R.string.qa_log_no_run), modifier = Modifier.padding(16.dp))
                return@Column
            }
            VerdictCard(state, verdictText, viewModel)
            FilterChips(
                options = listOf(R.string.qa_log_all, R.string.qa_log_requests, R.string.qa_log_markers).map { stringResource(it) },
                selected = state.filter.ordinal,
                onSelect = { viewModel.onFilter(RunLogFilter.entries[it]) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (state.items.isEmpty()) Text(text = stringResource(R.string.qa_log_empty), modifier = Modifier.padding(16.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(state.items, key = { it.id }) { item ->
                    LogItemRow(
                        prefix = "+${item.offsetSeconds}s".padStart(OFFSET_WIDTH),
                        glyph = item.kind.glyph,
                        glyphColor = item.kind.color(),
                        text = item.text,
                        attributes = item.attributes,
                        expanded = item.id in state.expandedIds,
                        onToggle = { viewModel.onToggle(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun VerdictCard(state: RunLogUiState, verdictText: String?, viewModel: RunLogViewModel) {
    val (container, content) = when (state.verdict?.status) {
        Verdict.Status.PASSED -> PASSED_CONTAINER to PASSED_CONTENT
        Verdict.Status.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = container, contentColor = content),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = verdictText.orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(
                text = state.elapsedSeconds.formatDuration() + " · " +
                    stringResource(R.string.qa_log_summary, state.requestCount, state.markerCount),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::onSendMore) { Text(stringResource(R.string.qa_send_more, QA_MORE_EVENTS)) }
                OutlinedButton(onClick = viewModel::onRerun) { Text(stringResource(R.string.qa_rerun)) }
                TextButton(onClick = viewModel::onStop) { Text(stringResource(R.string.qa_stop)) }
            }
        }
    }
}

@Composable
private fun RunLogItem.Kind.color(): Color = when (this) {
    RunLogItem.Kind.SUCCESS -> PASSED_COLOR
    RunLogItem.Kind.FAILURE -> MaterialTheme.colorScheme.error
    RunLogItem.Kind.MOCK -> MaterialTheme.colorScheme.tertiary
    RunLogItem.Kind.MARKER -> MaterialTheme.colorScheme.primary
}

const val QA_MORE_EVENTS = 5
private const val OFFSET_WIDTH = 5
