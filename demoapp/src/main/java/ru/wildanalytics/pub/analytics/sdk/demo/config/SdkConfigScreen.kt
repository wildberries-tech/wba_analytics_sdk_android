package ru.wildanalytics.pub.analytics.sdk.demo.config

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wildanalytics.pub.analytics.sdk.demo.R
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.DemoScaffold
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.DemoTextField

@Composable
fun SdkConfigScreen(viewModel: SdkConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DemoScaffold(
        title = stringResource(R.string.config_title),
        onBack = onBack,
        message = state.message,
        onMessageShown = viewModel::onMessageShown,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!state.isAvailable) {
                Text(text = stringResource(R.string.error_config_update_failed), color = MaterialTheme.colorScheme.error)
                return@Column
            }
            ConfigField.entries.forEach { field ->
                val fieldState = state.fields.getValue(field)
                DemoTextField(
                    value = fieldState.value,
                    onValueChange = { viewModel.onFieldChanged(field, it) },
                    label = stringResource(field.label, field.range.first, field.range.last),
                    keyboardType = KeyboardType.Number,
                    error = stringResource(R.string.config_error_range, field.range.first, field.range.last).takeIf { fieldState.isError },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.onRetriesChanged(!state.isRetryEnabled) }
                    .padding(vertical = 8.dp),
            ) {
                Text(text = stringResource(R.string.label_enable_retries), modifier = Modifier.weight(1f))
                Switch(checked = state.isRetryEnabled, onCheckedChange = viewModel::onRetriesChanged)
            }
            Button(onClick = viewModel::onApply, enabled = state.canApply, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.config_apply))
            }
            OutlinedButton(onClick = viewModel::onRestoreDefaults, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.btn_restore_defaults))
            }
        }
    }
}
