package ru.wildberries.attribution.impl.data

import ru.wildberries.attribution.api.AttributionData

internal interface AttributionDataSource {
    suspend fun getAttributionData(): AttributionData?
    suspend fun isAttributionChecked(): Boolean
    suspend fun setAttributionChecked()
}
