package ru.wildanalytics.pub.analytics.sdk.demo.endpoint

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import ru.wildanalytics.pub.analytics.sdk.demo.FakeAnalyticsRepository
import ru.wildanalytics.pub.analytics.sdk.demo.MainDispatcherExtension
import ru.wildanalytics.pub.analytics.sdk.demo.core.LogEntry
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.AnalyticsEndpointConfig
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.DEFAULT_FALLBACK_URL
import ru.wildanalytics.pub.analytics.sdk.demo.sdk.DEFAULT_MAIN_URL

@OptIn(ExperimentalCoroutinesApi::class)
class EndpointViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(UnconfinedTestDispatcher())

    private val repository = FakeAnalyticsRepository()
    private val vm by lazy { EndpointViewModel(repository, MutableStateFlow<List<LogEntry>>(emptyList())) }

    @Test
    fun httpFallbackIsRejected() {
        vm.onFallbackChanged("http://afb.wb.ru/m/batch")

        vm.state.value.fallbackError shouldBe true
        vm.state.value.canApply shouldBe false
        vm.onApply()
        repository.endpointUpdates.shouldBeEmpty()
    }

    @Test
    fun applySavesTrimmedUrls() {
        vm.onFallbackChanged(" https://afb.wb.ru/m/batch ")
        vm.onApply()

        repository.endpointUpdates.single() shouldBe AnalyticsEndpointConfig(DEFAULT_MAIN_URL, DEFAULT_FALLBACK_URL)
    }

    @Test
    fun presetFillsAndApplies() {
        vm.onMainChanged("https://x.example/m/batch")
        vm.onPreset()

        vm.state.value.mainUrl shouldBe DEFAULT_MAIN_URL
        vm.state.value.fallbackUrl shouldBe DEFAULT_FALLBACK_URL
        repository.endpointUpdates.single() shouldBe AnalyticsEndpointConfig(DEFAULT_MAIN_URL, DEFAULT_FALLBACK_URL)
    }
}
