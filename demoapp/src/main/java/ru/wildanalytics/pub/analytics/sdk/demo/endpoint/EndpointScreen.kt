package ru.wildanalytics.pub.analytics.sdk.demo.endpoint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wildanalytics.pub.analytics.sdk.demo.R
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.DemoScaffold
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.DemoTextField
import ru.wildanalytics.pub.analytics.sdk.demo.core.formatTime

@Composable
fun EndpointScreen(viewModel: EndpointViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DemoScaffold(
        title = stringResource(R.string.endpoint_title),
        onBack = onBack,
        message = state.message,
        onMessageShown = viewModel::onMessageShown,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val switchedAt = state.status?.switchedAtMillis
            Text(
                text = switchedAt?.let { stringResource(R.string.endpoint_now_fallback, formatTime(it)) }
                    ?: stringResource(R.string.endpoint_now_main),
                style = MaterialTheme.typography.titleSmall,
                color = if (switchedAt != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(text = stringResource(R.string.endpoint_hint), style = MaterialTheme.typography.bodySmall)
            DemoTextField(
                value = state.mainUrl,
                onValueChange = viewModel::onMainChanged,
                label = stringResource(R.string.label_main_url),
                keyboardType = KeyboardType.Uri,
                error = stringResource(R.string.error_invalid_url).takeIf { state.mainError },
                modifier = Modifier.fillMaxWidth(),
            )
            DemoTextField(
                value = state.fallbackUrl,
                onValueChange = viewModel::onFallbackChanged,
                label = stringResource(R.string.label_fallback_url),
                keyboardType = KeyboardType.Uri,
                error = stringResource(R.string.error_invalid_url).takeIf { state.fallbackError },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = viewModel::onApply, enabled = state.canApply, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.btn_apply_endpoint))
            }
            OutlinedButton(onClick = viewModel::onPreset, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.endpoint_preset))
            }
        }
    }
}
