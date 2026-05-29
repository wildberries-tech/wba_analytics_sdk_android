package ru.wildanalytics.pub.attribution.impl.tracker

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import ru.wildanalytics.pub.analytics.WildAnalytics
import ru.wildanalytics.pub.attribution.api.AttributionData
import ru.wildanalytics.pub.attribution.api.WildAttributionLogger
import ru.wildanalytics.pub.attribution.api.WildAttributionTracker
import ru.wildanalytics.pub.attribution.impl.data.AttributionDataSource
import ru.wildanalytics.pub.attribution.impl.data.AttributionResultDto

@OptIn(InternalSerializationApi::class)
internal class WildAttributionTrackerImpl(
    private val log: WildAttributionLogger,
    private val attributionDataSource: AttributionDataSource,
) : WildAttributionTracker {

    override suspend fun checkAttribution(
        analytics: WildAnalytics,
        onResult: suspend (AttributionData?) -> Unit
    ) {
        try {
            if (attributionDataSource.isAttributionChecked()) {
                log.logDebug { "Attribution data has already been received" }
            } else {
                val attributionResult = attributionDataSource.getAttributionResult()
                if(attributionResult?.fingerprintGathered != null) {
                    onResult(attributionDataSource.decodeAttributionDataJson(attributionResult.fingerprintGathered))
                }
                analytics.sendAppInstallEvent(attributionResult)
                log.logDebug {
                    if (attributionResult?.fingerprintGathered != null) {
                        "Attribution data ${attributionResult.fingerprintGathered} was received successfully"
                    } else {
                        "No attribution data received for device"
                    }
                }
                attributionDataSource.setAttributionChecked()
            }
        } catch (throwable: Throwable) {
            log.logError(throwable) { "Failed to receive attribution data" }
        }
    }

    @OptIn(InternalSerializationApi::class)
    private fun WildAnalytics.sendAppInstallEvent(result: AttributionResultDto?) {
        val parameters = result
            ?.let { Json.encodeToJsonElement(it) as? JsonObject }
            ?: JsonObject(emptyMap())
        logEvent("app_install", parameters)
    }
}
