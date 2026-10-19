package ru.wildanalytics.pub.analytics.sdk.demo.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wildanalytics.pub.WildAnalytics.BuildConfig
import ru.wildanalytics.pub.analytics.sdk.demo.R
import ru.wildanalytics.pub.analytics.sdk.demo.core.MarkerParser
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.Badge
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.DemoScaffold
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.StatusTile
import ru.wildanalytics.pub.analytics.sdk.demo.core.formatTime
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.title
import ru.wildanalytics.pub.analytics.sdk.demo.qa.QaSuite
import ru.wildanalytics.pub.analytics.sdk.demo.qa.scenarioTitle
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.TransportMode

@Composable
fun HomeScreen(viewModel: HomeViewModel, actions: HomeActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(state = state, actions = actions, onSendEvent = viewModel::onSendEvent)
}

class HomeActions(
    val onEvents: () -> Unit,
    val onConfig: () -> Unit,
    val onEndpoint: () -> Unit,
    val onDiagnostics: () -> Unit,
    val onQa: () -> Unit,
)

@Composable
fun HomeContent(state: HomeUiState, actions: HomeActions, onSendEvent: () -> Unit) {
    DemoScaffold(title = stringResource(R.string.app_name) + " · SDK " + BuildConfig.LIBRARY_VERSION, onBack = null) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Badges(state) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QueueTile(state, actions.onConfig, Modifier.weight(1f))
                    SendTile(state, actions.onDiagnostics, Modifier.weight(1f))
                }
            }
            item { EndpointTile(state.endpoint, actions.onEndpoint) }
            item {
                StatusTile(
                    title = stringResource(R.string.home_tile_diagnostics),
                    value = HOME_MARKERS.take(2).joinToString(" · ") { it.counter(state) },
                    subtitle = HOME_MARKERS.drop(2).joinToString(" · ") { it.counter(state) },
                    onClick = actions.onDiagnostics,
                )
            }
            item { QaTile(state, actions.onQa) }
            item {
                Row {
                    TextButton(onClick = actions.onEvents) { Text(stringResource(R.string.home_link_events) + " ›") }
                    TextButton(onClick = actions.onConfig) { Text(stringResource(R.string.home_link_config) + " ›") }
                }
            }
            item {
                Button(onClick = onSendEvent, enabled = !state.isSending, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_send_event))
                }
            }
        }
    }
}

@Composable
private fun Badges(state: HomeUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.transportMode != TransportMode.REAL) {
            Badge(stringResource(R.string.home_badge_mock, state.transportMode.title), MaterialTheme.colorScheme.error)
        }
        if (state.periodicSeconds > 0) {
            Badge(stringResource(R.string.home_badge_periodic, state.periodicSeconds), MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun QueueTile(state: HomeUiState, onClick: () -> Unit, modifier: Modifier) {
    val queue = state.queue
    StatusTile(
        title = stringResource(R.string.home_tile_queue),
        value = queue?.let { stringResource(R.string.home_queue_value, it.total, it.important) } ?: "—",
        subtitle = state.sdkConfig?.let { stringResource(R.string.home_queue_limit, it.maxEventsInCache) }
            ?: stringResource(R.string.home_queue_no_db).takeIf { queue == null },
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun SendTile(state: HomeUiState, onClick: () -> Unit, modifier: Modifier) {
    val last = state.lastSend
    StatusTile(
        title = stringResource(R.string.home_tile_send),
        value = last?.let { "${it.host} ${it.result}" + if (it.isMock) " mock" else "" } ?: "—",
        subtitle = last?.let { formatTime(it.timestampMillis) } ?: stringResource(R.string.home_send_none),
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun EndpointTile(endpoint: EndpointStatus, onClick: () -> Unit) {
    val switchedAt = endpoint.switchedAtMillis
    StatusTile(
        title = stringResource(R.string.home_tile_endpoint),
        value = if (switchedAt != null) "${endpoint.mainHost} → ${endpoint.fallbackHost}" else endpoint.mainHost,
        subtitle = when {
            switchedAt != null -> stringResource(R.string.home_endpoint_switched, formatTime(switchedAt))
            endpoint.fallbackHost == null -> stringResource(R.string.home_endpoint_fallback_none)
            else -> stringResource(R.string.home_endpoint_main) + " · fallback " + endpoint.fallbackHost
        },
        badge = stringResource(R.string.home_badge_fallback).takeIf { switchedAt != null },
        badgeColor = MaterialTheme.colorScheme.error,
        onClick = onClick,
    )
}

@Composable
private fun QaTile(state: HomeUiState, onClick: () -> Unit) {
    val lines = QaSuite.entries.mapNotNull { suite ->
        state.qa[suite]?.let { stringResource(R.string.home_qa_line, stringResource(suite.title), it.passed, it.total) }
    }
    val running = state.qa.values.firstNotNullOfOrNull { it.active }
    StatusTile(
        title = stringResource(R.string.home_tile_qa),
        value = lines.joinToString("\n"),
        subtitle = running?.let { run -> "⏳ " + (run.suite.scenarioTitle(run.scenario)?.let { stringResource(it) } ?: run.scenario) },
        onClick = onClick,
    )
}

private fun String.counter(state: HomeUiState) = "${MarkerParser.shortName(this)} ${state.markerCounts[this] ?: 0}"
