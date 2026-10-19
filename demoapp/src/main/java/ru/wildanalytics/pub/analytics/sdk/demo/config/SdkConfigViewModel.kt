package ru.wildanalytics.pub.analytics.sdk.demo.config

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.wildanalytics.pub.analytics.sdk.demo.R
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsRepository
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsSdkConfig

/** Конфиг SDK: правки копятся в форме и применяются только кнопкой. */
class SdkConfigViewModel(private val repository: AnalyticsRepository) : ViewModel() {

    private var applied: AnalyticsSdkConfig? = repository.sdkConfig
    private val _state = MutableStateFlow(applied.toUiState())
    val state: StateFlow<SdkConfigUiState> = _state.asStateFlow()

    init {
        repository.sdkConfigFlow
            .onEach { config ->
                if (config == applied) return@onEach
                val isUntouched = _state.value.toConfig() == applied
                applied = config
                if (isUntouched) _state.value = config.toUiState() else update { it }
            }
            .launchIn(viewModelScope)
    }

    fun onFieldChanged(field: ConfigField, value: String) {
        val digits = value.filter(Char::isDigit).take(MAX_DIGITS)
        val isError = digits.toIntOrNull()?.let { it !in field.range } ?: true
        update { it.copy(fields = it.fields + (field to FieldState(digits, isError))) }
    }

    fun onRetriesChanged(enabled: Boolean) = update { it.copy(isRetryEnabled = enabled) }

    fun onApply() {
        val config = _state.value.toConfig()?.takeIf { _state.value.canApply } ?: return
        repository.updateSdkConfig(config)
            .onSuccess {
                applied = config
                update { it.copy(message = R.string.config_applied) }
            }
            .onFailure { e ->
                Log.e(TAG, "Failed to update config", e)
                update { it.copy(message = R.string.error_config_update_failed) }
            }
    }

    fun onRestoreDefaults() {
        repository.resetToDefaults()
        applied = repository.sdkConfig
        _state.value = applied.toUiState()
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private fun update(block: (SdkConfigUiState) -> SdkConfigUiState) = _state.update { state ->
        block(state).let { it.copy(canApply = it.fields.values.none(FieldState::isError) && it.toConfig() != applied) }
    }
}

data class SdkConfigUiState(
    val isAvailable: Boolean,
    val fields: Map<ConfigField, FieldState> = emptyMap(),
    val isRetryEnabled: Boolean = true,
    val canApply: Boolean = false,
    @StringRes val message: Int? = null,
)

data class FieldState(
    val value: String,
    val isError: Boolean = false,
)

enum class ConfigField(@StringRes val label: Int, val range: IntRange) {
    MAX_EVENTS_IN_BATCH(R.string.label_max_events_in_batch, InputValidator.MIN_BATCH_EVENTS..InputValidator.MAX_BATCH_EVENTS),
    MAX_BATCH_SIZE(R.string.label_max_batch_size, InputValidator.MIN_BATCH_SIZE_BYTES..InputValidator.MAX_BATCH_SIZE_BYTES),
    DELAY_BETWEEN_BATCHES(R.string.label_delay_between_batches, InputValidator.MIN_DELAY_SECONDS..InputValidator.MAX_DELAY_SECONDS),
    DELAY_BETWEEN_OPERATIONS(R.string.label_delay_between_ops, InputValidator.MIN_DELAY_SECONDS..InputValidator.MAX_DELAY_SECONDS),
    INITIAL_DELAY(R.string.label_initial_delay, InputValidator.MIN_DELAY_SECONDS..InputValidator.MAX_DELAY_SECONDS),
    MAX_EVENTS_IN_CACHE(R.string.label_max_events_in_cache, InputValidator.MIN_CACHE_EVENTS..InputValidator.MAX_CACHE_EVENTS),
}

private fun AnalyticsSdkConfig?.toUiState(): SdkConfigUiState {
    if (this == null) return SdkConfigUiState(isAvailable = false)
    val values = mapOf(
        ConfigField.MAX_EVENTS_IN_BATCH to maxEventsInBatch,
        ConfigField.MAX_BATCH_SIZE to maxBatchSizeInBytes,
        ConfigField.DELAY_BETWEEN_BATCHES to delayBetweenBatchesSeconds,
        ConfigField.DELAY_BETWEEN_OPERATIONS to delayBetweenOperationsSeconds,
        ConfigField.INITIAL_DELAY to initialDelaySeconds,
        ConfigField.MAX_EVENTS_IN_CACHE to maxEventsInCache,
    )
    return SdkConfigUiState(
        isAvailable = true,
        fields = values.mapValues { (_, value) -> FieldState(value.toString()) },
        isRetryEnabled = isRetryEnabled,
    )
}

private fun SdkConfigUiState.toConfig(): AnalyticsSdkConfig? {
    fun value(field: ConfigField) = fields[field]?.value?.toIntOrNull()
    return AnalyticsSdkConfig(
        maxEventsInBatch = value(ConfigField.MAX_EVENTS_IN_BATCH) ?: return null,
        maxBatchSizeInBytes = value(ConfigField.MAX_BATCH_SIZE) ?: return null,
        delayBetweenBatchesSeconds = value(ConfigField.DELAY_BETWEEN_BATCHES) ?: return null,
        delayBetweenOperationsSeconds = value(ConfigField.DELAY_BETWEEN_OPERATIONS) ?: return null,
        initialDelaySeconds = value(ConfigField.INITIAL_DELAY) ?: return null,
        maxEventsInCache = value(ConfigField.MAX_EVENTS_IN_CACHE) ?: return null,
        isRetryEnabled = isRetryEnabled,
    )
}

private const val TAG = "WildAnalytics"
private const val MAX_DIGITS = 9
