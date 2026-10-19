package ru.wildanalytics.pub.analytics.sdk.demo.config

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import ru.wildanalytics.pub.analytics.sdk.demo.FakeAnalyticsRepository
import ru.wildanalytics.pub.analytics.sdk.demo.MainDispatcherExtension

class SdkConfigViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val repository = FakeAnalyticsRepository()
    private val vm = SdkConfigViewModel(repository)

    @Test
    fun unchangedConfigCannotBeApplied() {
        vm.state.value.canApply shouldBe false
    }

    @Test
    fun invalidFieldBlocksApply() {
        vm.onFieldChanged(ConfigField.MAX_EVENTS_IN_CACHE, "0")

        vm.state.value.fields.getValue(ConfigField.MAX_EVENTS_IN_CACHE).isError shouldBe true
        vm.state.value.canApply shouldBe false
        vm.onApply()
        repository.sdkUpdates.shouldBeEmpty()
    }

    @Test
    fun applyCallsRepositoryOnce() {
        vm.onFieldChanged(ConfigField.MAX_EVENTS_IN_CACHE, "50")
        vm.onRetriesChanged(false)
        vm.onApply()

        val expected = FakeAnalyticsRepository.DEFAULT_SDK_CONFIG.copy(maxEventsInCache = 50, isRetryEnabled = false)
        repository.sdkUpdates.single() shouldBe expected
        vm.state.value.canApply shouldBe false
    }

    @Test
    fun nonDigitsAreDropped() {
        vm.onFieldChanged(ConfigField.MAX_EVENTS_IN_BATCH, "1a2")

        vm.state.value.fields.getValue(ConfigField.MAX_EVENTS_IN_BATCH).value shouldBe "12"
    }
}
