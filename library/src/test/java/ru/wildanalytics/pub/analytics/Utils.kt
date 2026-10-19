package ru.wildanalytics.pub.analytics

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestCoroutineScheduler
import ru.wildanalytics.pub.analytics.device.DeviceId
import ru.wildanalytics.pub.analytics.domain.MetaInfo
import ru.wildanalytics.pub.analytics.domain.NetworkType
import ru.wildanalytics.pub.analytics.util.toServerModel
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal fun testDate() = OffsetDateTime.of(2020, 9, 11, 13, 30, 45, 0, ZoneOffset.of("+4"))

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun virtualTime() = currentCoroutineContext()[TestCoroutineScheduler]!!.currentTime

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
    deviceAdId = "deviceAdId",
    deviceAdIdType = "gaid",
    timezone = "Europe/Moscow",
    enableAutomaticEvents = false,
)

internal fun testNetworkSnapshotProvider(
    network: ru.wildanalytics.pub.analytics.network.NetworkSnapshot =
        ru.wildanalytics.pub.analytics.network.NetworkSnapshot(transports = "wifi", validated = true),
) = ru.wildanalytics.pub.analytics.network.NetworkSnapshotProvider { network }

internal fun testEndpointRouter(
    endpointFallback: ru.wildanalytics.pub.analytics.send.fallback.EndpointFallback =
        ru.wildanalytics.pub.analytics.send.fallback.EndpointFallback(),
    networkSnapshotProvider: ru.wildanalytics.pub.analytics.network.NetworkSnapshotProvider =
        testNetworkSnapshotProvider(),
    diagnostics: ru.wildanalytics.pub.analytics.diagnostics.DiagnosticsReporter =
        ru.wildanalytics.pub.analytics.diagnostics.DiagnosticsReporter(NoOpWildAnalyticsLogger, sdkVersion = "test"),
) = ru.wildanalytics.pub.analytics.send.fallback.EndpointRouter(
    endpointFallback,
    networkSnapshotProvider,
    ru.wildanalytics.pub.analytics.diagnostics.EndpointFallbackReporter(diagnostics),
    NoOpWildAnalyticsLogger,
)

internal fun testBatch(apiUrl: ApiUrl, apiKey: ApiKey, batchNumber: Long = 1L) = ru.wildanalytics.pub.analytics.batch.BatchData(
    contentLength = 1,
    apiData = ru.wildanalytics.pub.analytics.domain.ApiData(apiUrl = apiUrl, apiKey = apiKey),
    model = ru.wildanalytics.pub.analytics.api.BatchModel(meta = testMeta(batchNumber), events = emptyList()),
    eventIds = listOf(1),
    heaviestEvent = "event" to 1L,
)
