package ru.wildanalytics.pub.analytics.sdk.demo.analytics.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.wildanalytics.pub.analytics.sdk.demo.R

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.Factory)
) {
    val eventState by viewModel.eventState.collectAsStateWithLifecycle()
    val config by viewModel.configState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextButton(
                onClick = viewModel::onRestoreDefaults,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(text = stringResource(R.string.btn_restore_defaults))
            }

            EventDetailsCard(eventState, viewModel)

            config?.let {
                SdkConfigCard(it, viewModel)
            }
        }

        LogEventsButton(
            eventsCount = eventState.eventsPerSendCount,
            onClick = viewModel::sendMultipleEvents
        )
    }
}

@Composable
private fun EventDetailsCard(
    eventState: AnalyticsEventConfigUiState,
    viewModel: AnalyticsViewModel
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.title_event_details),
                style = MaterialTheme.typography.titleMedium
            )
            DemoSpacer()
            DemoTextField(
                value = eventState.counterId.toString(),
                onValueChange = viewModel::onCounterIdChanged,
                label = stringResource(
                    R.string.label_counter_id,
                    AnalyticsInputValidator.MIN_COUNTER_ID
                ),
                keyboardType = KeyboardType.Number
            )
            DemoSpacer()
            Text(
                text = stringResource(R.string.label_api_key, eventState.apiKey),
                style = MaterialTheme.typography.bodySmall
            )
            DemoSpacer()
            DemoTextField(
                value = eventState.eventsPerSendCount.toString(),
                onValueChange = viewModel::onEventsCountChanged,
                label = stringResource(
                    R.string.label_events_per_send,
                    AnalyticsInputValidator.MIN_EVENTS_PER_SEND,
                    AnalyticsInputValidator.MAX_EVENTS_PER_SEND
                ),
                keyboardType = KeyboardType.Number
            )
            DemoSpacer()
            ImportanceCheckbox(
                isImportant = eventState.isImportant,
                onChanged = viewModel::onImportanceChanged
            )
            DemoSpacer()
            DemoTextField(
                value = eventState.eventName,
                onValueChange = viewModel::onEventNameChanged,
                label = stringResource(
                    R.string.label_event_name,
                    AnalyticsInputValidator.MAX_EVENT_NAME_LENGTH
                )
            )
            DemoSpacer()
            DemoTextField(
                value = eventState.eventValue,
                onValueChange = viewModel::onEventValueChanged,
                label = stringResource(R.string.label_event_value),
                error = eventState.eventValueError?.let { stringResource(it) }
            )

            DemoSpacer()
            CommonParametersSection(
                parameters = eventState.commonParameters,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun ImportanceCheckbox(
    isImportant: Boolean,
    onChanged: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onChanged(!isImportant) }
            .padding(8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Checkbox(
            checked = isImportant,
            onCheckedChange = null
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.label_important),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun CommonParametersSection(
    parameters: List<ParameterUiState>,
    viewModel: AnalyticsViewModel
) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Text(
        text = stringResource(R.string.title_common_params),
        style = MaterialTheme.typography.titleSmall
    )
    DemoSpacer()

    parameters.forEachIndexed { index, param ->
        if (index > 0) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
        CommonParameterRow(
            param = param,
            viewModel = viewModel
        )
    }

    TextButton(
        onClick = viewModel::onAddCommonParameter
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.btn_add_param))
    }
}

@Composable
private fun CommonParameterRow(
    param: ParameterUiState,
    viewModel: AnalyticsViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            DemoTextField(
                value = param.key,
                onValueChange = { viewModel.onCommonParameterChanged(param.id, it, param.value) },
                label = stringResource(
                    R.string.label_param_key,
                    AnalyticsInputValidator.MAX_PARAM_KEY_LENGTH
                ),
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall,
                isDense = true
            )
            DemoSpacer()
            DemoTextField(
                value = param.value,
                onValueChange = { viewModel.onCommonParameterChanged(param.id, param.key, it) },
                label = stringResource(R.string.label_param_value),
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall,
                isDense = true
            )
        }
        IconButton(onClick = { viewModel.onRemoveCommonParameter(param.id) }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

@Composable
private fun SdkConfigCard(
    config: AnalyticsSdkConfigUiState,
    viewModel: AnalyticsViewModel
) {
    val rotationState by animateFloatAsState(
        targetValue = if (config.isConfigExpanded) 180f else 0f,
        label = "rotation"
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.onToggleConfigExpanded() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.title_sdk_config),
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotationState)
                )
            }

            AnimatedVisibility(visible = config.isConfigExpanded) {
                SdkConfigFields(config, viewModel)
            }
        }
    }
}

@Composable
private fun SdkConfigFields(
    config: AnalyticsSdkConfigUiState,
    viewModel: AnalyticsViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        config.error?.let {
            Text(
                text = stringResource(it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        DemoTextField(
            value = config.maxEventsInBatch.toString(),
            onValueChange = viewModel::onMaxEventsInBatchChanged,
            label = stringResource(
                R.string.label_max_events_in_batch,
                AnalyticsInputValidator.MIN_BATCH_EVENTS,
                AnalyticsInputValidator.MAX_BATCH_EVENTS
            ),
            keyboardType = KeyboardType.Number
        )
        DemoSpacer()
        DemoTextField(
            value = config.maxBatchSizeInBytes.toString(),
            onValueChange = viewModel::onMaxBatchSizeInBytesChanged,
            label = stringResource(
                R.string.label_max_batch_size,
                AnalyticsInputValidator.MIN_BATCH_SIZE_BYTES,
                AnalyticsInputValidator.MAX_BATCH_SIZE_BYTES
            ),
            keyboardType = KeyboardType.Number
        )
        DemoSpacer()
        DemoTextField(
            value = config.delayBetweenBatchesSeconds.toString(),
            onValueChange = viewModel::onDelayBetweenBatchesChanged,
            label = stringResource(
                R.string.label_delay_between_batches,
                AnalyticsInputValidator.MIN_DELAY_SECONDS,
                AnalyticsInputValidator.MAX_DELAY_SECONDS
            ),
            keyboardType = KeyboardType.Number
        )
        DemoSpacer()
        DemoTextField(
            value = config.delayBetweenOperationsSeconds.toString(),
            onValueChange = viewModel::onDelayBetweenOperationsChanged,
            label = stringResource(
                R.string.label_delay_between_ops,
                AnalyticsInputValidator.MIN_DELAY_SECONDS,
                AnalyticsInputValidator.MAX_DELAY_SECONDS
            ),
            keyboardType = KeyboardType.Number
        )
        DemoSpacer()
        DemoTextField(
            value = config.initialDelaySeconds.toString(),
            onValueChange = viewModel::onInitialDelayChanged,
            label = stringResource(
                R.string.label_initial_delay,
                AnalyticsInputValidator.MIN_DELAY_SECONDS,
                AnalyticsInputValidator.MAX_DELAY_SECONDS
            ),
            keyboardType = KeyboardType.Number
        )
        DemoSpacer()
        DemoTextField(
            value = config.maxEventsInCache.toString(),
            onValueChange = viewModel::onMaxEventsInCacheChanged,
            label = stringResource(
                R.string.label_max_events_in_cache,
                AnalyticsInputValidator.MIN_CACHE_EVENTS,
                AnalyticsInputValidator.MAX_CACHE_EVENTS
            ),
            keyboardType = KeyboardType.Number
        )
        DemoSpacer()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    viewModel.onRetryEnabledChanged(!config.isRetryEnabled)
                }
                .padding(8.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Checkbox(
                checked = config.isRetryEnabled,
                onCheckedChange = null
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.label_enable_retries),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun LogEventsButton(
    eventsCount: Int,
    onClick: () -> Unit
) {
    val buttonText = if (eventsCount > 1) {
        stringResource(R.string.btn_log_events_simple)
    } else {
        stringResource(R.string.btn_log_event)
    }

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .imePadding()
    ) {
        Text(text = buttonText)
    }
}
