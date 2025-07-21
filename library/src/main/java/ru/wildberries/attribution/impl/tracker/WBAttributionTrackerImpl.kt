package ru.wildberries.attribution.impl.tracker

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import ru.wildberries.analytics.WBAnalytics2
import ru.wildberries.attribution.api.AttributionData
import ru.wildberries.attribution.api.WBAttributionLogger
import ru.wildberries.attribution.api.WBAttributionTracker
import ru.wildberries.attribution.impl.data.AttributionDataSource
import ru.wildberries.attribution.impl.data.toDto

internal class WBAttributionTrackerImpl(
    private val log: WBAttributionLogger,
    private val attributionDataSource: AttributionDataSource,
) : WBAttributionTracker {

    override suspend fun checkAttribution(
        analytics: WBAnalytics2,
        onResult: suspend (AttributionData?) -> Unit
    ) {
        try {
            if (attributionDataSource.isAttributionChecked()) {
                log.logDebug { "Attribution data has already been received" }
            } else {
                val attributionData = attributionDataSource.getAttributionData()
                onResult(attributionData)
                analytics.sendAppInstallEvent(attributionData)
                log.logDebug {
                    if (attributionData != null) {
                        "Attribution data $attributionData was received successfully"
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
    private fun WBAnalytics2.sendAppInstallEvent(data: AttributionData?) {
        val parameters = data
            ?.toDto()
            ?.let { Json.encodeToJsonElement(it) as? JsonObject }
            ?: JsonObject(emptyMap())
        logEvent("app_install", parameters)
    }
}
