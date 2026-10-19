package ru.wildanalytics.pub.analytics.sdk.demo.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wildanalytics.pub.analytics.sdk.demo.R
import ru.wildanalytics.pub.analytics.sdk.demo.config.InputValidator
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.DemoScaffold
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.DemoTextField

@Composable
fun EventsScreen(viewModel: EventsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DemoScaffold(title = stringResource(R.string.events_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EventFields(state, viewModel)
            HorizontalDivider()
            Parameters(state.parameters, viewModel)
            HorizontalDivider()
            DemoTextField(
                value = if (state.periodicSeconds == 0) "" else state.periodicSeconds.toString(),
                onValueChange = viewModel::onPeriodicChanged,
                label = stringResource(
                    R.string.label_periodic_interval,
                    InputValidator.MIN_PERIODIC_INTERVAL,
                    InputValidator.MAX_PERIODIC_INTERVAL
                ),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = viewModel::onSend, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.events_send, state.count))
            }
        }
    }
}

@Composable
private fun EventFields(state: EventsUiState, viewModel: EventsViewModel) {
    DemoTextField(
        value = state.counterId.toString(),
        onValueChange = viewModel::onCounterIdChanged,
        label = stringResource(R.string.label_counter_id, InputValidator.MIN_COUNTER_ID),
        keyboardType = KeyboardType.Number,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(text = stringResource(R.string.label_api_key, state.apiKey), style = MaterialTheme.typography.bodySmall)
    DemoTextField(
        value = state.name,
        onValueChange = viewModel::onNameChanged,
        label = stringResource(R.string.label_event_name, InputValidator.MAX_EVENT_NAME_LENGTH),
        modifier = Modifier.fillMaxWidth(),
    )
    DemoTextField(
        value = state.body,
        onValueChange = viewModel::onBodyChanged,
        label = stringResource(R.string.label_event_value),
        error = state.bodyError?.let { stringResource(it) },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DemoTextField(
            value = state.count.toString(),
            onValueChange = viewModel::onCountChanged,
            label = stringResource(R.string.label_events_per_send, InputValidator.MIN_EVENTS_PER_SEND, InputValidator.MAX_EVENTS_PER_SEND),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { viewModel.onImportantChanged(!state.isImportant) },
        ) {
            Checkbox(checked = state.isImportant, onCheckedChange = null)
            Text(text = stringResource(R.string.label_important), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Parameters(parameters: List<ParameterUiState>, viewModel: EventsViewModel) {
    Text(text = stringResource(R.string.title_common_params), style = MaterialTheme.typography.titleSmall)
    parameters.forEach { param ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoTextField(
                value = param.key,
                onValueChange = { viewModel.onParameterChanged(param.id, it, param.value) },
                label = stringResource(R.string.label_param_key, InputValidator.MAX_PARAM_KEY_LENGTH),
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            DemoTextField(
                value = param.value,
                onValueChange = { viewModel.onParameterChanged(param.id, param.key, it) },
                label = stringResource(R.string.label_param_value),
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.onRemoveParameter(param.id) }) { Icon(Icons.Default.Delete, contentDescription = null) }
        }
    }
    TextButton(onClick = viewModel::onAddParameter) {
        Icon(Icons.Default.Add, contentDescription = null)
        Text(stringResource(R.string.btn_add_param))
    }
}
