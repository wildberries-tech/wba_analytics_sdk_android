package ru.wildberries.attribution.impl.data

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.JsonObject

internal interface AttributionDataSource {
    @OptIn(InternalSerializationApi::class)
    suspend fun getAttributionResult(): AttributionResultDto?
    suspend fun isAttributionChecked(): Boolean
    suspend fun setAttributionChecked()

    @OptIn(InternalSerializationApi::class)
    fun decodeAttributionDataJson(json: JsonObject): AttributionDataDto?
}
