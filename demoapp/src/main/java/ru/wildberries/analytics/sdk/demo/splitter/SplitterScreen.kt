package ru.wildberries.analytics.sdk.demo.splitter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.wildberries.analytics.sdk.demo.analytics.ui.DemoTextField

@Composable
fun SplitterScreen(
    viewModel: SplitterViewModel = viewModel()
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val properties by viewModel.properties.collectAsStateWithLifecycle()

    var tempApiKey by remember(config) { mutableStateOf(config.apiKey) }
    var tempFetchUrl by remember(config) { mutableStateOf(config.fetchUrl) }
    var tempUserId by remember(config) { mutableStateOf(config.userId) }
    var tempClientId by remember(config) { mutableStateOf(config.clientId.orEmpty()) }
    var tempAppVersion by remember(config) { mutableStateOf(config.appVersion.orEmpty()) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Config",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item { DemoTextField(tempApiKey, { tempApiKey = it }, "apiKey") }
            item { DemoTextField(tempFetchUrl, { tempFetchUrl = it }, "fetchUrl") }
            item { DemoTextField(tempUserId, { tempUserId = it }, "userId") }
            item { DemoTextField(tempClientId, { tempClientId = it }, "clientId") }
            item { DemoTextField(tempAppVersion, { tempAppVersion = it }, "appVersion") }

            item {
                Text(
                    text = "Experiments",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            properties.forEach { (type, props) ->
                item {
                    Text(
                        text = "Type: $type",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = props.entries.joinToString("\n") { (key, value) -> "${key}: ${value}" },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
        Button(
            onClick = {
                viewModel.updateConfig(
                    tempApiKey,
                    tempFetchUrl,
                    tempUserId,
                    tempClientId,
                    tempAppVersion
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Применить конфиг")
        }
    }
}
