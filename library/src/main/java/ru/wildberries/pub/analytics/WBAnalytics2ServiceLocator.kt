package ru.wildberries.pub.analytics

import android.content.Context
import ru.wildberries.pub.WBAnalytics2.BuildConfig
import ru.wildberries.pub.analytics.WBAnalytics2ServiceLocator.Builder
import ru.wildberries.pub.analytics.automatic.AutomaticEventsApiKeyRegistry
import ru.wildberries.pub.analytics.automatic.AutomaticEventsApiKeyRegistryImpl
import ru.wildberries.pub.analytics.automatic.AutomaticEventsTracker
import ru.wildberries.pub.analytics.automatic.AutomaticEventsTrackerImpl
import ru.wildberries.pub.analytics.batch.BatchRepository
import ru.wildberries.pub.analytics.batch.BatchRepositoryImpl
import ru.wildberries.pub.analytics.config.ConfigRepository
import ru.wildberries.pub.analytics.config.ConfigRepositoryImpl
import ru.wildberries.pub.analytics.db.RoomTransactionRunner
import ru.wildberries.pub.analytics.db.TransactionRunner
import ru.wildberries.pub.analytics.db.WBAnalytics2Database
import ru.wildberries.pub.analytics.db.WBAnalytics2DatabaseFactory
import ru.wildberries.pub.analytics.device.AdIdProvider
import ru.wildberries.pub.analytics.device.DeviceHardwareInfoProvider
import ru.wildberries.pub.analytics.device.DeviceHardwareInfoProviderImpl
import ru.wildberries.pub.analytics.device.IpcServiceConfig
import ru.wildberries.pub.analytics.device.MetadataCollector
import ru.wildberries.pub.analytics.device.SystemLocaleProvider
import ru.wildberries.pub.analytics.device.SystemLocaleProviderImpl
import ru.wildberries.pub.analytics.device.WBDeviceInfoProvider
import ru.wildberries.pub.analytics.device.WBDeviceInfoProviderImpl
import ru.wildberries.pub.analytics.device.WBMarketingModelProvider
import ru.wildberries.pub.analytics.device.WBMarketingModelProviderImpl
import ru.wildberries.pub.analytics.diagnostics.DiagnosticsReporter
import ru.wildberries.pub.analytics.diagnostics.EndpointFallbackReporter
import ru.wildberries.pub.analytics.diagnostics.QueueEvictionReporter
import ru.wildberries.pub.analytics.event.EventsRepository
import ru.wildberries.pub.analytics.event.EventsRepositoryImpl
import ru.wildberries.pub.analytics.network.NetworkAvailabilitySource
import ru.wildberries.pub.analytics.network.NetworkSnapshotProvider
import ru.wildberries.pub.analytics.network.NetworkSnapshotProviderImpl
import ru.wildberries.pub.analytics.send.RestartPolicy
import ru.wildberries.pub.analytics.send.SendAllAnalyticEventsOperation
import ru.wildberries.pub.analytics.send.SendOperationScheduler
import ru.wildberries.pub.analytics.send.SendStrategyProvider
import ru.wildberries.pub.analytics.send.WBAnalytics2SenderService
import ru.wildberries.pub.analytics.send.fallback.EndpointFallback
import ru.wildberries.pub.analytics.send.fallback.EndpointRouter
import ru.wildberries.pub.analytics.session.SessionProvider
import ru.wildberries.pub.analytics.session.SessionProviderImpl
import ru.wildberries.pub.analytics.transport.CustomHeadersRepository
import ru.wildberries.pub.analytics.transport.CustomHeadersRepositoryImpl
import ru.wildberries.pub.analytics.util.DynamicSystemClock
import ru.wildberries.pub.analytics.util.IdGenerator
import ru.wildberries.pub.analytics.util.ProcessForegroundDataProvider
import ru.wildberries.pub.analytics.util.RandomIdGenerator
import java.time.Clock
import kotlin.concurrent.Volatile

@Suppress("LongMethod")
private fun buildInstance(context: Context): WBAnalytics2ServiceLocator = Builder().build {
    bindInstance(context.applicationContext)
    bindInstance<Clock>(DynamicSystemClock())
    bindInstance<WBAnalytics2Logger>(WBAnalytics2LoggerImpl())
    bindInstance<CoroutineScopeFactory>(CoroutineScopeFactoryImpl())
    bind {
        DiagnosticsReporter(
            log = it.get(),
            sdkVersion = BuildConfig.LIBRARY_VERSION,
        )
    }

    bind<IdGenerator> { RandomIdGenerator() }
    bind {
        ProcessForegroundDataProvider(
            scope = it.get<CoroutineScopeFactory>().create("ForegroundData")
        )
    }
    bind<SessionProvider> {
        SessionProviderImpl(
            idGenerator = it.get(),
            processForegroundDataProvider = it.get(),
            scope = it.get<CoroutineScopeFactory>().create("SessionProvider")
        )
    }

    bind<DeviceHardwareInfoProvider> {
        DeviceHardwareInfoProviderImpl(context = it.get())
    }
    bind<AutomaticEventsTracker.Factory> {
        AutomaticEventsTracker.Factory {
            AutomaticEventsTrackerImpl(
                processForegroundDataProvider = it.get(),
                deviceHardwareInfoProvider = it.get(),
                deviceInfoProvider = it.get(),
            )
        }
    }

    bind<WBDeviceInfoProvider> {
        WBDeviceInfoProviderImpl(
            context = it.get(),
            idGenerator = it.get(),
        )
    }
    bind<WBMarketingModelProvider> {
        WBMarketingModelProviderImpl(
            log = it.get(),
        )
    }
    bind<SystemLocaleProvider> {
        SystemLocaleProviderImpl(
            context = it.get(),
            log = it.get(),
        )
    }
    bind {
        AdIdProvider(
            context = it.get(),
            log = it.get(),
            scopeFactory = it.get(),
            configs = listOf(
                IpcServiceConfig(
                    action = "com.google.android.gms.ads.identifier.service.START",
                    packageName = "com.google.android.gms",
                    descriptor = "com.google.android.gms.ads.identifier.internal.IAdvertisingIdService",
                    providerType = "gaid",
                ),
                IpcServiceConfig(
                    action = "com.uodis.opendevice.OPENIDS_SERVICE",
                    packageName = "com.huawei.hwid",
                    descriptor = "com.uodis.opendevice.aidl.OpenDeviceIdentifierService",
                    providerType = "oaid",
                )
            )
        )
    }
    bind<WBAnalytics2Database> {
        WBAnalytics2DatabaseFactory.create(
            context = it.get(),
            coroutineScopeFactory = it.get(),
            logger = it.get(),
        )
    }
    bind<AutomaticEventsApiKeyRegistry> {
        AutomaticEventsApiKeyRegistryImpl()
    }
    bind {
        MetadataCollector(
            deviceInfoProvider = it.get(),
            marketingModelProvider = it.get(),
            systemLocaleProvider = it.get(),
            adIdProvider = it.get(),
            automaticEventsApiKeyRegistry = it.get(),
            context = it.get(),
            clock = it.get(),
        )
    }
    bind<TransactionRunner> {
        RoomTransactionRunner(db = it.get<WBAnalytics2Database>())
    }
    bind {
        it.get<WBAnalytics2Database>().eventsDao()
    }
    bind {
        it.get<WBAnalytics2Database>().sentInfoDao()
    }
    bind<ConfigRepository> {
        ConfigRepositoryImpl()
    }
    bind<CustomHeadersRepository> {
        CustomHeadersRepositoryImpl()
    }
    bind<EventEnricherRegistry> {
        EventEnricherRegistryImpl(log = it.get())
    }
    bind<EventsRepository> {
        EventsRepositoryImpl(
            eventsDao = it.get(),
            db = it.get(),
            transactionRunner = it.get(),
        )
    }
    bind<BatchRepository> {
        BatchRepositoryImpl(
            metadataCollector = it.get(),
            transactionRunner = it.get(),
            eventsRepository = it.get(),
            sentInfoDao = it.get(),
            log = it.get(),
        )
    }

    bind {
        SendStrategyProvider(
            eventsRepository = it.get(),
            log = it.get(),
            networkAvailabilitySource = it.get(),
            sendOperation = it.get(),
            sendOperationScheduler = it.get(),
            processForegroundDataProvider = it.get(),
        )
    }
    bind {
        WBAnalytics2SenderService(
            log = it.get(),
            configRepository = it.get(),
            coroutineScopeFactory = it.get(),
            sendStrategyProvider = it.get(),
            diagnostics = it.get(),
            restartPolicy = RestartPolicy(),
        )
    }
    bind { NetworkAvailabilitySource(context = it.get(), log = it.get()) }
    bind { SendOperationScheduler(context = it.get(), log = it.get()) }
    bind {
        SendAllAnalyticEventsOperation(
            eventsRepository = it.get(),
            batchRepository = it.get(),
            configRepository = it.get(),
            log = it.get(),
            customHeadersRepository = it.get(),
            queueEvictionReporter = it.get(),
            diagnostics = it.get(),
            endpointRouter = it.get(),
        )
    }
    bind { EndpointFallback() }
    bind<NetworkSnapshotProvider> { NetworkSnapshotProviderImpl(context = it.get()) }
    bind { EndpointFallbackReporter(reporter = it.get()) }
    bind {
        EndpointRouter(
            endpointFallback = it.get(),
            networkSnapshotProvider = it.get(),
            reporter = it.get(),
            log = it.get(),
        )
    }
    bind {
        QueueEvictionReporter(
            reporter = it.get(),
            clock = it.get(),
            idGenerator = it.get(),
        )
    }
}

public class WBAnalytics2ServiceLocator(private val providers: Map<Class<*>, (WBAnalytics2ServiceLocator) -> Any>) {

    private val map = mutableMapOf<Class<*>, Any>()

    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> get(clazz: Class<T>): T = synchronized(map) {
        var instance = map[clazz]
        if (instance == null) {
            instance =
                (providers[clazz] ?: error("Not found provider for ${clazz.name}")).invoke(this)
            map[clazz] = instance
        }

        instance as T
    }

    public inline fun <reified T : Any> get(): T {
        return get(T::class.java)
    }

    internal class Builder {

        private val providers = mutableMapOf<Class<*>, (WBAnalytics2ServiceLocator) -> Any>()

        fun <T> bind(clazz: Class<T>, provider: (WBAnalytics2ServiceLocator) -> T) {
            @Suppress("UNCHECKED_CAST")
            providers[clazz] = provider as (WBAnalytics2ServiceLocator) -> Any
        }

        fun <T> bindInstance(clazz: Class<T>, instance: T) {
            bind(clazz) { instance }
        }

        inline fun <reified T> bind(noinline provider: (WBAnalytics2ServiceLocator) -> T) {
            bind(T::class.java, provider)
        }

        inline fun <reified T> bindInstance(instance: T) {
            bindInstance(T::class.java, instance)
        }

        fun build(body: Builder.() -> Unit): WBAnalytics2ServiceLocator {
            body()
            return WBAnalytics2ServiceLocator(providers)
        }
    }

    public companion object {

        @Volatile
        private var Instance: WBAnalytics2ServiceLocator? = null

        public fun getInstance(context: Context): WBAnalytics2ServiceLocator = Instance
            ?: synchronized(this) {
                Instance
                    ?: buildInstance(context)
                        .also { Instance = it }
            }
    }
}
