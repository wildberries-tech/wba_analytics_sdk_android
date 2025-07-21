package ru.wildberries.analytics

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.serialization.InternalSerializationApi
import ru.wildberries.analytics.device.DeviceId
import ru.wildberries.analytics.domain.MetaInfo
import ru.wildberries.analytics.domain.NetworkType
import ru.wildberries.analytics.util.toServerModel
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.coroutines.coroutineContext

internal fun testDate() = OffsetDateTime.of(2020, 9, 11, 13, 30, 45, 0, ZoneOffset.of("+4"))

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun virtualTime() = coroutineContext[TestCoroutineScheduler]!!.currentTime

internal class TestCoroutineLogger {

    val logLines = mutableListOf<String>()

    suspend fun log(message: String) {
        val time = virtualTime().toString().padStart(5, '_')
        logLines += "$time: $message"
    }
}

internal class TestScopeFactory(private val parentScope: CoroutineScope) : CoroutineScopeFactory {

    override fun create(debugName: String): CoroutineScope {
        return parentScope +  SupervisorJob(parentScope.coroutineContext[Job]) + CoroutineName(debugName)
    }
}

@OptIn(InternalSerializationApi::class)
internal fun testMeta(batchNumber: Long = 1L) = testMetaInfo().toServerModel(batchNumber)

internal fun testMetaInfo() = MetaInfo(
    locale = "locale",
    device = "device",
    sdkVersion = "sdkVersion",
    model = "model",
    mobileDeviceType = "mobileDeviceType",
    product = "product",
    osBuild = "osBuild",
    manufacturer = "manufacturer",
    fingerprint = "fingerprint",
    netType = NetworkType.Ethernet,
    appId = "appId",
    appVersion = "appVersion",
    analyticsSdkVersion = "analyticsSdkVersion",
    deviceId = DeviceId("deviceId"),
    localTime = testDate(),
    isUserNew = false,
    resolutionWidth = 560,
    resolutionHeight = 1000,
)
