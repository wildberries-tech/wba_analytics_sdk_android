package ru.wildberries.attribution.impl.fingerprint

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@InternalSerializationApi
@Serializable
internal data class DeviceFingerprintDto(
    @SerialName("screen")
    val screen: String,
    @SerialName("platform")
    val platform: String,
    @SerialName("language")
    val language: String,
    @SerialName("timezone")
    val timezone: String,
)
