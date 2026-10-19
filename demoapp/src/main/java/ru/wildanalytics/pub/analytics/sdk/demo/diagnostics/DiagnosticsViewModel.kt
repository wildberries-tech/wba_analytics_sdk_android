package ru.wildanalytics.pub.analytics.sdk.demo.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import ru.wildanalytics.pub.analytics.sdk.demo.core.LogEntry
import ru.wildanalytics.pub.analytics.sdk.demo.core.markerCounts
import ru.wildanalytics.pub.analytics.sdk.demo.core.formatTime

class DiagnosticsViewModel(
    entries: Flow<List<LogEntry>>,
    private val onClear: () -> Unit,
) : ViewModel() {

    private val filter = MutableStateFlow<DiagnosticsFilter>(DiagnosticsFilter.Sdk)
    private val expanded = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<DiagnosticsUiState> = combine(entries, filter, expanded) { entries, filter, expanded ->
        val markers = entries.markerCounts().toSortedMap().map { (name, count) -> DiagnosticsFilter.Marker(name) to count }
        DiagnosticsUiState(
            items = entries.filter(filter::accepts),
            filter = filter,
            filters = listOf(DiagnosticsFilter.All to entries.size, DiagnosticsFilter.Sdk to entries.count { it.marker != null }) + markers,
            expandedIds = expanded,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DiagnosticsUiState())

    fun onFilter(value: DiagnosticsFilter) {
        filter.value = value
    }

    fun onToggle(id: Long) = expanded.update { if (id in it) it - id else it + id }

    fun onClear() {
        onClear.invoke()
        expanded.value = emptySet()
    }

    fun copyText(): String = state.value.items.joinToString("\n\n") { entry ->
        val head = "${formatTime(
            entry.timestampMillis
        )} ${entry.level} ${listOfNotNull(entry.marker, entry.text.ifBlank { null }).joinToString(" ")}"
        (listOf(head) + entry.attributes.map { (key, value) -> "  $key=$value" } + listOfNotNull(entry.cause?.let { "  cause=$it" }))
            .joinToString("\n")
    }
}

data class DiagnosticsUiState(
    val items: List<LogEntry> = emptyList(),
    val filter: DiagnosticsFilter = DiagnosticsFilter.Sdk,
    val filters: List<Pair<DiagnosticsFilter, Int>> = emptyList(),
    val expandedIds: Set<Long> = emptySet(),
)

sealed interface DiagnosticsFilter {

    fun accepts(entry: LogEntry): Boolean

    data object All : DiagnosticsFilter {
        override fun accepts(entry: LogEntry) = true
    }

    data object Sdk : DiagnosticsFilter {
        override fun accepts(entry: LogEntry) = entry.marker != null
    }

    data class Marker(val name: String) : DiagnosticsFilter {
        override fun accepts(entry: LogEntry) = entry.marker == name
    }
}
