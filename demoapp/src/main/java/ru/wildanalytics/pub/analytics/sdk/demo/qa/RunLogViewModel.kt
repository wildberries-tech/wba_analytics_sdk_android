package ru.wildanalytics.pub.analytics.sdk.demo.qa

import android.os.Build
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
import ru.wildanalytics.pub.analytics.sdk.demo.core.RequestRecord
import ru.wildanalytics.pub.analytics.sdk.demo.core.formatDuration

/** Лог активного прогона набора: вердикт, таймер и все запросы и маркеры с момента старта. */
class RunLogViewModel(
    private val tracker: RunTracker,
    entries: Flow<List<LogEntry>>,
    requests: Flow<List<RequestRecord>>,
    private val actions: RunActions,
) : ViewModel() {

    private val filter = MutableStateFlow(RunLogFilter.ALL)
    private val expanded = MutableStateFlow<Set<String>>(emptySet())

    private val items = combine(tracker.active, entries, requests) { run, entries, requests ->
        run?.let { RunLog.build(it, entries, requests, actions.aliases()) }.orEmpty()
    }

    val state: StateFlow<RunLogUiState> = combine(
        combine(tracker.active, tracker.verdict, tracker.elapsedSeconds, ::Triple),
        items,
        filter,
        expanded,
    ) { (run, verdict, elapsed), items, filter, expanded ->
        RunLogUiState(
            run = run,
            verdict = verdict,
            elapsedSeconds = elapsed,
            items = items.filter(filter::accepts),
            requestCount = items.count { it.kind != RunLogItem.Kind.MARKER },
            markerCount = items.count { it.kind == RunLogItem.Kind.MARKER },
            filter = filter,
            expandedIds = expanded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RunLogUiState())

    fun onFilter(value: RunLogFilter) {
        filter.value = value
    }

    fun onToggle(id: String) = expanded.update { if (id in it) it - id else it + id }

    fun onSendMore() = actions.sendMore()

    fun onRerun() = actions.rerun()

    fun onStop() = tracker.stop()

    fun report(title: String, verdictText: String?): String = buildString {
        val state = state.value
        appendLine("WBA QA · ${tracker.suite} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
        appendLine(listOfNotNull(title, verdictText, state.elapsedSeconds.formatDuration()).joinToString(" · "))
        state.items.forEach { item ->
            appendLine("+${item.offsetSeconds}s ${item.kind.glyph} ${item.text}")
            item.attributes.forEach { (key, value) -> appendLine("    $key=$value") }
        }
    }.trimEnd()
}

/** Действия набора, которые зависят от его сценариев. */
class RunActions(
    val sendMore: () -> Unit,
    val rerun: () -> Unit,
    val aliases: () -> Map<String, String>,
)

data class RunLogUiState(
    val run: ActiveRun? = null,
    val verdict: Verdict? = null,
    val elapsedSeconds: Long = 0,
    val items: List<RunLogItem> = emptyList(),
    val requestCount: Int = 0,
    val markerCount: Int = 0,
    val filter: RunLogFilter = RunLogFilter.ALL,
    val expandedIds: Set<String> = emptySet(),
)

enum class RunLogFilter {
    ALL,
    REQUESTS,
    MARKERS,
    ;

    fun accepts(item: RunLogItem): Boolean = when (this) {
        ALL -> true
        REQUESTS -> item.kind != RunLogItem.Kind.MARKER
        MARKERS -> item.kind == RunLogItem.Kind.MARKER
    }
}

val RunLogItem.Kind.glyph: String
    get() = when (this) {
        RunLogItem.Kind.SUCCESS -> "✓"
        RunLogItem.Kind.FAILURE -> "✗"
        RunLogItem.Kind.MOCK -> "≈"
        RunLogItem.Kind.MARKER -> "◆"
    }

private const val STOP_TIMEOUT_MILLIS = 5_000L
