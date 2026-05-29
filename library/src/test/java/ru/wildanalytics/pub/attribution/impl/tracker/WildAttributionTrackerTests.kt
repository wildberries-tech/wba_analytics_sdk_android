package ru.wildanalytics.pub.attribution.impl.tracker

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.scopes.BehaviorSpecWhenContainerScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import ru.wildanalytics.pub.analytics.WildAnalytics
import ru.wildanalytics.pub.attribution.api.AttributionData
import ru.wildanalytics.pub.attribution.impl.data.AttributionDataDto
import ru.wildanalytics.pub.attribution.impl.data.AttributionDataSource
import ru.wildanalytics.pub.attribution.impl.data.AttributionResultDto
import ru.wildanalytics.pub.attribution.impl.fingerprint.DeviceFingerprintDto
import ru.wildanalytics.pub.attribution.impl.logger.NoOpAttributionLogger

@OptIn(InternalSerializationApi::class)
@Suppress("Unused")
internal class WildAttributionTrackerTests : BehaviorSpec({
    Given("Attribution not checked") {
        val logger = NoOpAttributionLogger()
        val dataSource = spyk<TestAttributionDataSource>(TestAttributionDataSource()) {
            coEvery { setAttributionChecked() } returns Unit
        }
        dataSource.isAttributionReceivedField = false
        val analytics = mockk<WildAnalytics>(relaxed = true)
        val subject = WildAttributionTrackerImpl(log = logger, attributionDataSource = dataSource)

        And("Attribution data request succeed") {
            val attributionData = JsonObject(
                mapOf(
                    "link" to JsonPrimitive("link"),
                    "otherFields" to JsonObject(mapOf("key" to JsonPrimitive("value")))
                )
            )
            val expectedAttributionData =
                Json.decodeFromJsonElement<AttributionDataDto>(attributionData)
            dataSource.attributionDataGetter = { attributionData }

            When("Check attribution invoked") {
                val onResult: suspend (AttributionData?) -> Unit = mockk(relaxed = true)
                subject.checkAttribution(analytics, onResult)

                Then("onResult invoked with the same attribution data") {
                    coVerify(exactly = 1) {
                        onResult.invoke(expectedAttributionData)
                    }
                }

                Then("Analytics logged app_install event") {
                    coVerify(exactly = 1) {
                        analytics.logEvent(eq("app_install"), any<JsonObject>())
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
        val analytics = mockk<WildAnalytics>(relaxed = true)
        val subject = WildAttributionTrackerImpl(log = logger, attributionDataSource = dataSource)

        And("Attribution data request failed") {
            dataSource.attributionDataGetter = { throw RuntimeException() }

            When("Check attribution invoked") {
                thenAttributionNotHappened(subject, analytics, dataSource)
            }
        }
    }

    Given("Attribution already checked") {
        val logger = NoOpAttributionLogger()
        val dataSource = spyk<TestAttributionDataSource>(TestAttributionDataSource())
        dataSource.isAttributionReceivedField = true
        val analytics = mockk<WildAnalytics>()
        val subject = WildAttributionTrackerImpl(log = logger, attributionDataSource = dataSource)

        When("Check attribution invoked") {
            thenAttributionNotHappened(subject, analytics, dataSource)
        }
    }
})

private suspend fun BehaviorSpecWhenContainerScope.thenAttributionNotHappened(
    subject: WildAttributionTrackerImpl,
    analytics: WildAnalytics,
    dataSource: TestAttributionDataSource
) {
    val onResult: suspend (AttributionData?) -> Unit = mockk()
    subject.checkAttribution(analytics, onResult)

    Then("onResult never invoked") {
        coVerify(inverse = true) {
            onResult.invoke(any())
        }
    }

    Then("Analytics not logged app_install event") {
        coVerify(inverse = true) {
            analytics.logEvent(eq("app_install"), any<JsonObject>())
        }
    }

    Then("Attribution invocation wasn't persisted") {
        coVerify(inverse = true) {
            dataSource.setAttributionChecked()
        }
    }
}


@OptIn(InternalSerializationApi::class)
private class TestAttributionDataSource : AttributionDataSource {

    var attributionDataGetter: () -> JsonObject? = { JsonObject(emptyMap()) }
    var isAttributionReceivedField = false

    override suspend fun getAttributionResult(): AttributionResultDto? =
        attributionDataGetter.invoke()
            ?.let {
                AttributionResultDto(
                    fingerprintGathered = it,
                    userAttributes = DeviceFingerprintDto(
                        screenResolution = "screenResolution",
                        platform = "platform",
                        language = "language",
                        timezone = "timezone",
                        device = "device",
                        versionOs = "versionOs",
                        pixelRatio = "pixelRatio",
                    )
                )
            }

    override suspend fun isAttributionChecked(): Boolean = isAttributionReceivedField

    override suspend fun setAttributionChecked() {
        isAttributionReceivedField = true
    }

    override fun decodeAttributionDataJson(json: JsonObject): AttributionDataDto? {
        return try {
            Json.decodeFromJsonElement<AttributionDataDto>(json)
        } catch (e: Exception) {
            null
        }
    }
}
