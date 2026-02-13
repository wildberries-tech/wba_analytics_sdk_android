package ru.wildberries.attribution.impl.data

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import ru.wildberries.attribution.impl.fingerprint.DeviceFingerprintDto

@InternalSerializationApi
@Serializable
internal data class AttributionResultDto(
    @SerialName("fingerprint_gathered")
    val fingerprintGathered: JsonObject?,
    @SerialName("user_attributes")
    val userAttributes: DeviceFingerprintDto
)
