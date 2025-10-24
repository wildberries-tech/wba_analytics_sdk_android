package ru.wildberries.attribution.impl.data

import kotlinx.serialization.InternalSerializationApi

internal interface AttributionDataSource {
    @OptIn(InternalSerializationApi::class)
    suspend fun getAttributionResult(): AttributionResultDto?
    suspend fun isAttributionChecked(): Boolean
    suspend fun setAttributionChecked()
}
