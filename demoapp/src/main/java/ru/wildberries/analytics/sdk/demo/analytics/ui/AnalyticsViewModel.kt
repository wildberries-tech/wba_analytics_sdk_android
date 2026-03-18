package ru.wildberries.analytics.sdk.demo.analytics.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.wildberries.analytics.sdk.demo.R
import ru.wildberries.analytics.sdk.demo.analytics.data.AnalyticsRepositoryImpl
import ru.wildberries.analytics.sdk.demo.analytics.domain.AnalyticsSdkConfig
import ru.wildberries.analytics.sdk.demo.analytics.domain.AnalyticsEventConfig
import ru.wildberries.analytics.sdk.demo.analytics.domain.AnalyticsRepository

private const val UPDATE_DEBOUNCE = 300L

@OptIn(FlowPreview::class)
class AnalyticsViewModel(
    private val validator: AnalyticsInputValidator,
    private val repository: AnalyticsRepository
) : ViewModel() {

    private val _eventState = MutableStateFlow(repository.eventsConfig.toUiState(null))
    val eventState: StateFlow<AnalyticsEventConfigUiState> = _eventState.asStateFlow()

    private val _configState = MutableStateFlow(repository.sdkConfig?.toUiState())
    val configState: StateFlow<AnalyticsSdkConfigUiState?> = _configState.asStateFlow()

    init {
        observeConfigFromRepository()
        observeEventFromRepository()
        observeConfigChanges()
        observeEventChanges()
    }

    private fun observeConfigFromRepository() {
        repository.sdkConfigFlow
            .onEach { domainConfig ->
                _configState.update { uiState ->
                    domainConfig?.toUiState(isConfigExpanded = uiState?.isConfigExpanded == true)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeEventFromRepository() {
        repository.eventsConfigFlow
            .onEach { domainEvent ->
                _eventState.update { current ->
                    domainEvent.toUiState(current.eventValueError)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeEventChanges() {
        _eventState.debounce(UPDATE_DEBOUNCE)
            .map { it.toDomain() }
            .distinctUntilChanged()
            .onEach { repository.updateSendEventsConfig(it) }
            .launchIn(viewModelScope)
    }

    private fun observeConfigChanges() {
        _configState.debounce(UPDATE_DEBOUNCE)
            .filterNotNull()
            .map { it.toDomain() }
            .distinctUntilChanged()
            .onEach { config ->
                repository.updateSdkConfig(config).onFailure { e ->
                    Log.e("WBA2", "Failed to update config", e)
                    _configState.update {
                        it?.copy(error = R.string.error_config_update_failed)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun onCounterIdChanged(value: String) {
        val id = validator.validateCounterId(value)
        _eventState.update { it.toDomain().copy(counterId = id).toUiState(it.eventValueError) }
    }

    fun onEventNameChanged(name: String) {
        val validatedName = validator.validateEventName(name)
        _eventState.update { it.copy(eventName = validatedName) }
    }

    fun onEventValueChanged(value: String) {
        _eventState.update { it.copy(eventValue = value, eventValueError = null) }
    }

    fun onEventsCountChanged(count: String) {
        val coerced = validator.validateEventsPerSendCount(count)
        _eventState.update { it.copy(eventsPerSendCount = coerced) }
    }

    fun onMaxEventsInBatchChanged(value: String) {
        val count = validator.validateMaxEventsInBatch(value)
        _configState.update { it?.copy(maxEventsInBatch = count, error = null) }
    }

    fun onMaxBatchSizeInBytesChanged(value: String) {
        val size = validator.validateMaxBatchSizeInBytes(value)
        _configState.update { it?.copy(maxBatchSizeInBytes = size, error = null) }
    }

    fun onDelayBetweenBatchesChanged(value: String) {
        val seconds = validator.validateDelaySeconds(value)
        _configState.update { it?.copy(delayBetweenBatchesSeconds = seconds, error = null) }
    }

    fun onDelayBetweenOperationsChanged(value: String) {
        val seconds = validator.validateDelaySeconds(value)
        _configState.update { it?.copy(delayBetweenOperationsSeconds = seconds, error = null) }
    }

    fun onInitialDelayChanged(value: String) {
        val seconds = validator.validateDelaySeconds(value)
        _configState.update { it?.copy(initialDelaySeconds = seconds, error = null) }
    }

    fun onMaxEventsInCacheChanged(value: String) {
        val count = validator.validateMaxEventsInCache(value)
        _configState.update { it?.copy(maxEventsInCache = count, error = null) }
    }

    fun onRetryEnabledChanged(enabled: Boolean) {
        _configState.update { it?.copy(isRetryEnabled = enabled, error = null) }
    }

    fun onToggleConfigExpanded() {
        _configState.update { it?.copy(isConfigExpanded = !(it.isConfigExpanded)) }
    }

    fun onImportanceChanged(isImportant: Boolean) {
        _eventState.update { it.copy(isImportant = isImportant) }
    }

    fun onAddCommonParameter() {
        _eventState.update { state ->
            state.copy(
                commonParameters = state.commonParameters + ParameterUiState()
            )
        }
    }

    fun onRemoveCommonParameter(id: String) {
        _eventState.update { state ->
            state.copy(
                commonParameters = state.commonParameters.filter { it.id != id }
            )
        }
    }

    fun onCommonParameterChanged(id: String, newKey: String, newValue: String) {
        _eventState.update { state ->
            val sanitizedKey = validator.validateParameterKey(newKey)
            state.copy(
                commonParameters = state.commonParameters.map {
                    if (it.id == id) it.copy(key = sanitizedKey, value = newValue) else it
                }
            )
        }
    }

    fun onRestoreDefaults() {
        repository.resetToDefaults()
        _configState.update { it?.copy(error = null) }
    }

    fun sendMultipleEvents() {
        val state = _eventState.value
        viewModelScope.launch {
            repository.logEvents(state.toDomain()).onFailure { e ->
                Log.e("WBA2", "Failed to log events", e)
                _eventState.update {
                    it.copy(eventValueError = R.string.error_invalid_json)
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = requireNotNull(this[APPLICATION_KEY])
                AnalyticsViewModel(
                    validator = AnalyticsInputValidator(),
                    repository = AnalyticsRepositoryImpl(application)
                )
            }
        }
    }
}

private fun AnalyticsSdkConfigUiState.toDomain() = AnalyticsSdkConfig(
    maxEventsInBatch = maxEventsInBatch,
    maxBatchSizeInBytes = maxBatchSizeInBytes,
    delayBetweenBatchesSeconds = delayBetweenBatchesSeconds,
    delayBetweenOperationsSeconds = delayBetweenOperationsSeconds,
    initialDelaySeconds = initialDelaySeconds,
    maxEventsInCache = maxEventsInCache,
    isRetryEnabled = isRetryEnabled
)

private fun AnalyticsSdkConfig.toUiState(
    isConfigExpanded: Boolean = false,
    error: Int? = null
) = AnalyticsSdkConfigUiState(
    maxEventsInBatch = maxEventsInBatch,
    maxBatchSizeInBytes = maxBatchSizeInBytes,
    delayBetweenBatchesSeconds = delayBetweenBatchesSeconds,
    delayBetweenOperationsSeconds = delayBetweenOperationsSeconds,
    initialDelaySeconds = initialDelaySeconds,
    maxEventsInCache = maxEventsInCache,
    isRetryEnabled = isRetryEnabled,
    isConfigExpanded = isConfigExpanded,
    error = error
)

private fun AnalyticsEventConfigUiState.toDomain() = AnalyticsEventConfig(
    counterId = counterId,
    name = eventName,
    value = eventValue,
    count = eventsPerSendCount,
    isImportant = isImportant,
    commonParameters = commonParameters
        .filter { it.key.isNotEmpty() }
        .associate { it.key to it.value }
)

private fun AnalyticsEventConfig.toUiState(eventValueError: Int?) = AnalyticsEventConfigUiState(
    counterId = counterId,
    apiKey = apiKey,
    eventName = name,
    eventValue = value,
    eventsPerSendCount = count,
    isImportant = isImportant,
    commonParameters = commonParameters.map { (k, v) -> ParameterUiState(key = k, value = v) },
    eventValueError = eventValueError
)