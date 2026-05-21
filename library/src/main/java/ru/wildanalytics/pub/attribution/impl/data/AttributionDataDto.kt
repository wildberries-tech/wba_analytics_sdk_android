package ru.wildanalytics.pub.attribution.impl.data

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import ru.wildanalytics.pub.attribution.api.AttributionData

@InternalSerializationApi
@Serializable(AttributionDataSerializer::class)
internal data class AttributionDataDto(
    override val counterId: String? = null,
    override val link: String? = null,
    override val otherFields: Map<String, JsonElement>? = null,
) : AttributionData

@InternalSerializationApi
internal object AttributionDataSerializer : KSerializer<AttributionDataDto> {

    private const val COUNTER_ID_KEY = "counterId"
    private const val LINK_KEY = "link"

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor(serialName = "ru.wildanalytics.pub.attribution.data.AttributionDataDto")

    override fun serialize(
        encoder: Encoder,
        value: AttributionDataDto
    ) {
        encoder as JsonEncoder
        val jsonObject = buildJsonObject {
            value.counterId?.let { put(COUNTER_ID_KEY, it) }
            value.link?.let { put(LINK_KEY, it) }
            value.otherFields?.forEach { key, value -> put(key, value) }
        }
        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): AttributionDataDto {
        decoder as JsonDecoder
        val fields = (decoder.decodeJsonElement() as? JsonObject).orEmpty().toMutableMap()
        return AttributionDataDto(
            counterId = fields.remove(COUNTER_ID_KEY)?.jsonPrimitive?.content,
            link = fields.remove(LINK_KEY)?.jsonPrimitive?.content,
            otherFields = fields.takeIf { it.isNotEmpty() }
        )
    }
}
