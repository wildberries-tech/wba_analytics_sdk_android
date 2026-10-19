package ru.wildanalytics.pub.analytics.sdk.demo.endpoint

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import ru.wildanalytics.pub.analytics.sdk.demo.R
import ru.wildanalytics.pub.analytics.sdk.demo.config.InputValidator
import ru.wildanalytics.pub.analytics.sdk.demo.core.LogEntry
import ru.wildanalytics.pub.analytics.sdk.demo.home.EndpointStatus
import ru.wildanalytics.pub.analytics.sdk.demo.home.endpointStatus
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsEndpointConfig
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsRepository
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.DEFAULT_FALLBACK_URL
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.DEFAULT_MAIN_URL

class EndpointViewModel(
    private val repository: AnalyticsRepository,
    entries: Flow<List<LogEntry>>,
    private val validator: InputValidator = InputValidator(),
) : ViewModel() {

    private val form = MutableStateFlow(repository.endpointConfig.toForm())
    private val message = MutableStateFlow<Int?>(null)

    val state: StateFlow<EndpointUiState> = combine(
        form,
        repository.endpointConfigFlow,
        entries,
        message
    ) { form, saved, entries, message ->
        form.toUiState(saved, endpointStatus(saved, entries), message)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, form.value.toUiState(repository.endpointConfig, null, null))

    fun onMainChanged(value: String) = form.update { it.copy(mainUrl = value) }

    fun onFallbackChanged(value: String) = form.update { it.copy(fallbackUrl = value) }

    fun onApply() {
        val config = form.value.toConfig()
        if (!config.isValid()) return
        repository.updateEndpointConfig(config)
        message.value = R.string.endpoint_applied
    }

    fun onPreset() {
        form.value = AnalyticsEndpointConfig(DEFAULT_MAIN_URL, DEFAULT_FALLBACK_URL).toForm()
        onApply()
    }

    fun onMessageShown() {
        message.value = null
    }

    private fun AnalyticsEndpointConfig.isValid() =
        validator.isValidOptionalUrl(mainUrl) && validator.isValidOptionalHttpsUrl(fallbackUrl)

    private fun Form.toUiState(saved: AnalyticsEndpointConfig, status: EndpointStatus?, @StringRes message: Int?): EndpointUiState {
        val config = toConfig()
        return EndpointUiState(
            mainUrl = mainUrl,
            mainError = !validator.isValidOptionalUrl(config.mainUrl),
            fallbackUrl = fallbackUrl,
            fallbackError = !validator.isValidOptionalHttpsUrl(config.fallbackUrl),
            canApply = config.isValid() && config != saved,
            status = status,
            message = message,
        )
    }

    private data class Form(val mainUrl: String, val fallbackUrl: String) {
        fun toConfig() = AnalyticsEndpointConfig(mainUrl.trim(), fallbackUrl.trim())
    }

    private fun AnalyticsEndpointConfig.toForm() = Form(mainUrl, fallbackUrl)
}

data class EndpointUiState(
    val mainUrl: String,
    val mainError: Boolean,
    val fallbackUrl: String,
    val fallbackError: Boolean,
    val canApply: Boolean,
    val status: EndpointStatus?,
    @StringRes val message: Int?,
)
