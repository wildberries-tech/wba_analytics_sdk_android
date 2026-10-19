package ru.wildanalytics.pub.analytics.sdk.demo.events

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.wildanalytics.pub.analytics.sdk.demo.R
import ru.wildanalytics.pub.analytics.sdk.demo.config.InputValidator
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsEventConfig
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsRepository

/** Шаблон события и общие параметры; правки сохраняются в репозиторий с задержкой. */
@OptIn(FlowPreview::class)
class EventsViewModel(
    private val repository: AnalyticsRepository,
    private val validator: InputValidator = InputValidator(),
) : ViewModel() {

    private val _state = MutableStateFlow(repository.eventsConfig.toUiState())
    val state: StateFlow<EventsUiState> = _state.asStateFlow()

    init {
        _state.drop(1)
            .debounce(SAVE_DEBOUNCE_MILLIS)
            .map { it.toDomain() }
            .distinctUntilChanged()
            .onEach(repository::updateSendEventsConfig)
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        val pending = _state.value.toDomain()
        if (pending != repository.eventsConfig) repository.updateSendEventsConfig(pending)
    }

    fun onCounterIdChanged(value: String) {
        val id = validator.validateCounterId(value)
        _state.update { it.copy(counterId = id, apiKey = it.toDomain().copy(counterId = id).apiKey) }
    }

    fun onNameChanged(value: String) = _state.update { it.copy(name = validator.validateEventName(value)) }

    fun onBodyChanged(value: String) = _state.update { it.copy(body = value, bodyError = null) }

    fun onCountChanged(value: String) = _state.update { it.copy(count = validator.validateEventsPerSendCount(value)) }

    fun onImportantChanged(value: Boolean) = _state.update { it.copy(isImportant = value) }

    fun onPeriodicChanged(value: String) =
        _state.update { it.copy(periodicSeconds = validator.validatePeriodicLoggingInterval(value)) }

    fun onAddParameter() = _state.update { it.copy(parameters = it.parameters + ParameterUiState()) }

    fun onRemoveParameter(id: String) = _state.update { state -> state.copy(parameters = state.parameters.filter { it.id != id }) }

    fun onParameterChanged(id: String, key: String, value: String) = _state.update { state ->
        val sanitized = validator.validateParameterKey(key)
        state.copy(parameters = state.parameters.map { if (it.id == id) it.copy(key = sanitized, value = value) else it })
    }

    fun onSend() {
        val config = _state.value.toDomain()
        viewModelScope.launch {
            repository.logEvents(config).onFailure { e ->
                Log.e(TAG, "Failed to log events", e)
                _state.update { it.copy(bodyError = R.string.error_invalid_json) }
            }
        }
    }
}

private fun AnalyticsEventConfig.toUiState() = EventsUiState(
    counterId = counterId,
    apiKey = apiKey,
    name = name,
    body = value,
    count = count,
    isImportant = isImportant,
    parameters = commonParameters.map { (key, value) -> ParameterUiState(key = key, value = value) },
    periodicSeconds = periodicLoggingIntervalSeconds,
)

private fun EventsUiState.toDomain() = AnalyticsEventConfig(
    counterId = counterId,
    name = name,
    value = body,
    count = count,
    isImportant = isImportant,
    commonParameters = parameters.filter { it.key.isNotBlank() }.associate { it.key to it.value },
    periodicLoggingIntervalSeconds = periodicSeconds,
)

private const val TAG = "WildAnalytics"
private const val SAVE_DEBOUNCE_MILLIS = 300L
