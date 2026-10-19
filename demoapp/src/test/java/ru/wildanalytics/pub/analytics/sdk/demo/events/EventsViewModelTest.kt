package ru.wildanalytics.pub.analytics.sdk.demo.events

import androidx.lifecycle.ViewModelStore
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import ru.wildanalytics.pub.analytics.sdk.demo.FakeAnalyticsRepository
import ru.wildanalytics.pub.analytics.sdk.demo.MainDispatcherExtension

class EventsViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val repository = FakeAnalyticsRepository()

    @Test
    fun pendingEditIsSavedWhenScreenCloses() {
        val store = ViewModelStore()
        val vm = EventsViewModel(repository)
        @Suppress("RestrictedApi")
        store.put("events", vm)

        vm.onNameChanged("edited_event")
        store.clear()

        repository.eventsConfig.name shouldBe "edited_event"
    }
}
