package ru.wildberries.pub.analytics.api

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import ru.wildberries.pub.analytics.toJsonObject
import java.time.OffsetDateTime

@Serializable
@OptIn(InternalSerializationApi::class)
internal data class BatchModel(
    val meta: Meta,
    val events: List<Event>,
)

@Serializable
@OptIn(InternalSerializationApi::class)
internal data class Event(
    val name: String,
    @SerialName("session_value")
    val sessionValue: ULong?,
    @SerialName("event_time")
    @Serializable(with = TimeAsStringSerializer::class)
    val time: OffsetDateTime,
    @SerialName("event_num")
    val eventNumber: ULong,
    val data: JsonObject,
) {

    constructor(
        name: String,
        sessionValue: ULong,
        time: OffsetDateTime,
        eventNumber: ULong,
        data: Map<String, String>,
    ) : this(name, sessionValue, time, eventNumber, data.toJsonObject())

    init {
        require(name.isNotEmpty())
    }
}

@Serializable
@OptIn(InternalSerializationApi::class)
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

    @SerialName("device_ad_id_type")
    val deviceAdIdType: String,

    @SerialName("timezone")
    val timezone: String,

    @SerialName("enable_automatic_events")
    val enableAutomaticEvents: Boolean,

    /**
     * Режим отправки батча (strategy-sourced):
     * 0 - SCHEDULED, 1 - IMMEDIATE.
     */
    @SerialName("send_mode")
    val sendMode: Int,
)
