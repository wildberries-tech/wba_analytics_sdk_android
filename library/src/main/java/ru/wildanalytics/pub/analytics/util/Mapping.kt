package ru.wildanalytics.pub.analytics.util

import kotlinx.serialization.InternalSerializationApi
import ru.wildanalytics.pub.analytics.api.Event
import ru.wildanalytics.pub.analytics.api.Meta
import ru.wildanalytics.pub.analytics.db.EventEntity
import ru.wildanalytics.pub.analytics.domain.MetaInfo
import java.time.format.DateTimeFormatter

private val zoneFormatter = DateTimeFormatter.ofPattern("Z")

@OptIn(InternalSerializationApi::class)
internal fun EventEntity.toServerModel(eventNumber: Long): Event {
    return Event(
        name = name,
        time = time,
        eventNumber = eventNumber.toULong(),
        data = extras,
    )
}

@OptIn(InternalSerializationApi::class)
internal fun MetaInfo.toServerModel(batchNumber: Long): Meta = Meta(
    locale = locale,
    device = device,
    sdkVersion = sdkVersion,
    model = model,
    mobileDeviceType = mobileDeviceType,
    product = product,
    osBuild = osBuild,
    manufacturer = manufacturer,
    fingerprint = fingerprint,
    tzOffset = zoneFormatter.format(localTime),
    netType = netType.serializedName,
    appId = appId,
    appVersion = appVersion,
    analyticsSdkVersion = analyticsSdkVersion,
    deviceId = deviceId.id,
    localTime = localTime,
    isUserNew = if (isUserNew) 1 else 0,
    batchNumber = batchNumber.toULong(),
    resolutionWidth = resolutionWidth,
    resolutionHeight = resolutionHeight,
    deviceAdId = deviceAdId,
)
