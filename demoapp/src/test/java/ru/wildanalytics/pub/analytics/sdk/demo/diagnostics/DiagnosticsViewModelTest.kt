package ru.wildanalytics.pub.analytics.sdk.demo.diagnostics

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import ru.wildanalytics.pub.analytics.sdk.demo.MainDispatcherExtension
import ru.wildanalytics.pub.analytics.sdk.demo.logEntry

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(UnconfinedTestDispatcher())

    private val entries = MutableStateFlow(
        listOf(
            logEntry(3, "[wba_send_failed] a code=500"),
            logEntry(2, "plain"),
            logEntry(1, "[wba_queue_eviction] b removed=1"),
        )
    )
    private var cleared = 0
    private val vm by lazy { DiagnosticsViewModel(entries, onClear = { cleared++ }) }

    @Test
    fun sdkFilterIsDefaultAndDropsPlain() {
        vm.state.value.items.map { it.id } shouldBe listOf(3L, 1L)
    }

    @Test
    fun markerFilterKeepsOnlyMarker() {
        vm.onFilter(DiagnosticsFilter.Marker("wba_queue_eviction"))

        vm.state.value.items.map { it.id } shouldBe listOf(1L)
    }

    @Test
    fun filtersCarryCounts() {
        vm.state.value.filters shouldBe listOf(
            DiagnosticsFilter.All to 3,
            DiagnosticsFilter.Sdk to 2,
            DiagnosticsFilter.Marker("wba_queue_eviction") to 1,
            DiagnosticsFilter.Marker("wba_send_failed") to 1,
        )
    }

    @Test
    fun toggleExpandsAndCopyListsVisibleEntries() {
        vm.onToggle(3)

        vm.state.value.expandedIds shouldBe setOf(3L)
        vm.copyText().lines().first().endsWith("wba_send_failed a") shouldBe true
    }
}
