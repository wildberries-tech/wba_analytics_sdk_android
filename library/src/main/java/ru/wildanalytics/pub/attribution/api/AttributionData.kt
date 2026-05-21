package ru.wildanalytics.pub.attribution.api

import kotlinx.serialization.json.JsonElement

public interface AttributionData {
    public val counterId: String?
    public val link: String?
    public val otherFields: Map<String, JsonElement>?
}
