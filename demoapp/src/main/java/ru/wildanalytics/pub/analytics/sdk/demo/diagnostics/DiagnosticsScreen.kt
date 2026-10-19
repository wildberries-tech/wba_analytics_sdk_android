package ru.wildanalytics.pub.analytics.sdk.demo.diagnostics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wildanalytics.pub.analytics.sdk.demo.R
import ru.wildanalytics.pub.analytics.sdk.demo.core.LogEntry
import ru.wildanalytics.pub.analytics.sdk.demo.core.MarkerParser
import ru.wildanalytics.pub.analytics.sdk.demo.core.copyToClipboard
import ru.wildanalytics.pub.analytics.sdk.demo.core.formatTime
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.DemoScaffold
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.FilterChips
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.LogItemRow
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.OverflowMenu

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DemoScaffold(
        title = stringResource(R.string.title_diagnostics),
        onBack = onBack,
        actions = {
            OverflowMenu(
                listOf(
                    R.string.action_copy to { context.copyToClipboard(viewModel.copyText()) },
                    R.string.action_clear to viewModel::onClear,
                )
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            FilterChips(
                options = state.filters.map { (filter, count) -> "${filter.label()} $count" },
                selected = state.filters.indexOfFirst { it.first == state.filter },
                onSelect = { viewModel.onFilter(state.filters[it].first) },
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            )
            if (state.items.isEmpty()) {
                Text(
                    text = stringResource(R.string.label_diagnostics_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(state.items, key = { it.id }) { entry ->
                    LogItemRow(
                        prefix = formatTime(entry.timestampMillis),
                        glyph = entry.level.glyph,
                        glyphColor = entry.level.color(),
                        text = listOfNotNull(entry.marker?.let(MarkerParser::shortName), entry.text.ifBlank { null }).joinToString(" "),
                        attributes = entry.attributes + listOfNotNull(entry.cause?.let { "cause" to it }),
                        expanded = entry.id in state.expandedIds,
                        onToggle = { viewModel.onToggle(entry.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsFilter.label(): String = when (this) {
    DiagnosticsFilter.All -> stringResource(R.string.diag_all)
    DiagnosticsFilter.Sdk -> stringResource(R.string.diag_sdk)
    is DiagnosticsFilter.Marker -> MarkerParser.shortName(name)
}

@Composable
private fun LogEntry.Level.color() =
    if (this == LogEntry.Level.WARN) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error

private val LogEntry.Level.glyph: String
    get() = when (this) {
        LogEntry.Level.WARN -> "W"
        LogEntry.Level.ERROR -> "E"
        LogEntry.Level.EXCEPTION -> "X"
    }
