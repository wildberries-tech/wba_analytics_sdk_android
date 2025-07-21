package ru.wildberries.attribution.impl.tracker

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.scopes.BehaviorSpecWhenContainerScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.wildberries.analytics.WBAnalytics2
import ru.wildberries.attribution.api.AttributionData
import ru.wildberries.attribution.impl.data.AttributionDataSource
import ru.wildberries.attribution.impl.logger.NoOpAttributionLogger

@OptIn(InternalSerializationApi::class)
@Suppress("Unused")
internal class WBAttributionTrackerTests : BehaviorSpec({
    Given("Attribution not checked") {
        val logger = NoOpAttributionLogger()
        val dataSource = spyk<TestAttributionDataSource>(TestAttributionDataSource()) {
            coEvery { setAttributionChecked() } returns Unit
        }
        dataSource.isAttributionReceivedField = false
        val wba = mockk<WBAnalytics2>(relaxed = true)
        val subject = WBAttributionTrackerImpl(log = logger, attributionDataSource = dataSource)

        And("Attribution data request succeed") {
            val attributionData = AttributionData(
                link = "link",
                otherFields = mapOf("key" to JsonPrimitive("value"))
            )
            dataSource.attributionDataGetter = { attributionData }

            When("Check attribution invoked") {
                val onResult: suspend (AttributionData?) -> Unit = mockk(relaxed = true)
                subject.checkAttribution(wba, onResult)

                Then("onResult invoked with the same attribution data") {
                    coVerify(exactly = 1) {
                        onResult.invoke(refEq(attributionData))
                    }
                }

                Then("Analytics logged app_install event") {
                    coVerify(exactly = 1) {
                        wba.logEvent(eq("app_install"), any<JsonObject>())
                    }
                }

                Then("Attribution invocation was persisted") {
                    coVerify(exactly = 1) {
                        dataSource.setAttributionChecked()
                    }
                }
            }
        }
    }

    Given("Attribution not checked") {
        val logger = NoOpAttributionLogger()
        val dataSource = spyk<TestAttributionDataSource>(TestAttributionDataSource()) {
            coEvery { setAttributionChecked() } returns Unit
        }
        dataSource.isAttributionReceivedField = false
        val wba = mockk<WBAnalytics2>(relaxed = true)
        val subject = WBAttributionTrackerImpl(log = logger, attributionDataSource = dataSource)

        And("Attribution data request failed") {
            dataSource.attributionDataGetter = { throw RuntimeException() }

            When("Check attribution invoked") {
                thenAttributionNotHappened(subject, wba, dataSource)
            }
        }
    }

    Given("Attribution already checked") {
        val logger = NoOpAttributionLogger()
        val dataSource = spyk<TestAttributionDataSource>(TestAttributionDataSource())
        dataSource.isAttributionReceivedField = true
        val wba = mockk<WBAnalytics2>()
        val subject = WBAttributionTrackerImpl(log = logger, attributionDataSource = dataSource)

        When("Check attribution invoked") {
            thenAttributionNotHappened(subject, wba, dataSource)
        }
    }
})

private suspend fun BehaviorSpecWhenContainerScope.thenAttributionNotHappened(
    subject: WBAttributionTrackerImpl,
    wba: WBAnalytics2,
    dataSource: TestAttributionDataSource
) {
    val onResult: suspend (AttributionData?) -> Unit = mockk()
    subject.checkAttribution(wba, onResult)

    Then("onResult never invoked") {
        coVerify(inverse = true) {
            onResult.invoke(any())
        }
    }

    Then("Analytics not logged app_install event") {
        coVerify(inverse = true) {
            wba.logEvent(eq("app_install"), any<JsonObject>())
        }
    }

    Then("Attribution invocation wasn't persisted") {
        coVerify(inverse = true) {
            dataSource.setAttributionChecked()
        }
    }
}

private class TestAttributionDataSource : AttributionDataSource {

    var attributionDataGetter: () -> AttributionData? = { AttributionData() }
    var isAttributionReceivedField = false

    override suspend fun getAttributionData(): AttributionData? = attributionDataGetter.invoke()

    override suspend fun isAttributionChecked(): Boolean = isAttributionReceivedField

    override suspend fun setAttributionChecked() {
        isAttributionReceivedField = true
    }
}
