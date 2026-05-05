package ru.wildberries.analytics.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import ru.wildberries.analytics.toJsonObject
import java.time.OffsetDateTime

@Serializable
internal data class BatchModel(
    val meta: Meta,
    val events: List<Event>,
)

@Serializable
internal data class Event(
    val name: String,
    @SerialName("event_time")
    @Serializable(with = TimeAsStringSerializer::class)
    val time: OffsetDateTime,
    @SerialName("event_num")
    val eventNumber: ULong,
    val data: JsonObject,
) {

    constructor(
        name: String,
        time: OffsetDateTime,
        eventNumber: ULong,
        data: Map<String, String>,
    ) : this(name, time, eventNumber, data.toJsonObject())

    init {
        require(name.isNotEmpty())
    }
}

@Serializable
internal data class Meta(
    @SerialName("locale")
    val locale: String,

    @SerialName("device")
    val device: String,

    @SerialName("sdk_version")
    val sdkVersion: String,

    @SerialName("model")
    val model: String,

    @SerialName("mobile_device_type")
    val mobileDeviceType: String,

    @SerialName("product")
    val product: String,

    @SerialName("os-build")
    val osBuild: String,

    @SerialName("manufacturer")
    val manufacturer: String,

    @SerialName("fingerprint")
    val fingerprint: String,

    @SerialName("tz_offset")
    val tzOffset: String,

    @SerialName("net_type")
    val netType: String,

    @SerialName("app_id")
    val appId: String,

    @SerialName("app_version")
    val appVersion: String,

    @SerialName("analytics_sdk_version")
    val analyticsSdkVersion: String,

    @SerialName("device_id")
    val deviceId: String,

    @SerialName("local_time")
    @Serializable(with = TimeAsStringSerializer::class)
    val localTime: OffsetDateTime,

    /**
     * 1 - новый юзер (приложение впервые открыто на этом девайсе),
     * 0 - юзер не новый.
     */
    @SerialName("is_new_user")
    val isUserNew: Int,

    /**
     * Номер батча. Какой по счёту батч отправляется с этого клиента.
     * */
    @SerialName("batch_num")
    val batchNumber: ULong,

    @SerialName("resolution_width")
    val resolutionWidth: Int,

    @SerialName("resolution_height")
    val resolutionHeight: Int,

    @SerialName("device_ad_id")
    val deviceAdId: String,
)
