package ru.wildanalytics.pub.analytics.device

@JvmInline
public value class DeviceId(public val id: String)

public interface WildDeviceInfoProvider {

    public fun getDeviceId(): DeviceId

    public fun isUserNew(): Boolean
}
