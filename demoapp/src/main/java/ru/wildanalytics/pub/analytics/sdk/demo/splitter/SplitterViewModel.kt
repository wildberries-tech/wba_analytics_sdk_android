package ru.wildanalytics.pub.analytics.sdk.demo.splitter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import ru.wildanalytics.pub.splitter.api.WildSplitter
import ru.wildanalytics.pub.splitter.api.WildSplitterConfig

class SplitterViewModel(application: Application) : AndroidViewModel(application) {

    private val _config = MutableStateFlow(
        WildSplitterConfig(
            apiKey = "",
            fetchUrl = "https://splitter.wb.ru/v2/result",
            userId = "",
            clientId = null,
            appVersion = null
        )
    )
    val config: StateFlow<WildSplitterConfig> = _config.asStateFlow()

    private val splitter = WildSplitter.create(
        context = application,
        config = _config.value,
        withSystemLogs = true,
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val properties: StateFlow<Map<String, Map<String, Any?>>> = _config
        .flatMapLatest { newConfig ->
            splitter.updateConfig { newConfig }
            splitter.observeProperties()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun updateConfig(
        apiKey: String,
        fetchUrl: String,
        userId: String,
        clientId: String?,
        appVersion: String?
    ) {
        _config.value = WildSplitterConfig(
            apiKey = apiKey,
            fetchUrl = fetchUrl,
            userId = userId,
            clientId = clientId,
            appVersion = appVersion
        )
    }
}
