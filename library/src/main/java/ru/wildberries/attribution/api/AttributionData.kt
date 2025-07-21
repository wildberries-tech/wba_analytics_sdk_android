package ru.wildberries.attribution.api

import kotlinx.serialization.json.JsonElement

public data class AttributionData(
    val counterId: String? = null,
    val link: String? = null,
    val otherFields: Map<String, JsonElement>? = null
)