package ru.wildberries.analytics.device

@JvmInline
public value class DeviceId(public val id: String)

public interface WBDeviceInfoProvider {

    public fun getDeviceId(): DeviceId

    public fun isUserNew(): Boolean
}
