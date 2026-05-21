package ru.wildanalytics.pub.analytics

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import ru.wildanalytics.pub.analytics.WildAnalyticsServiceLocator.Builder
import ru.wildanalytics.pub.analytics.batch.BatchRepository
import ru.wildanalytics.pub.analytics.batch.BatchRepositoryImpl
import ru.wildanalytics.pub.analytics.config.ConfigRepository
import ru.wildanalytics.pub.analytics.config.ConfigRepositoryImpl
import ru.wildanalytics.pub.analytics.db.Migrations
import ru.wildanalytics.pub.analytics.db.RoomTransactionRunner
import ru.wildanalytics.pub.analytics.db.TransactionRunner
import ru.wildanalytics.pub.analytics.db.WildAnalyticsDatabase
import ru.wildanalytics.pub.analytics.db.WildAnalyticsDatabase_Impl
import ru.wildanalytics.pub.analytics.device.GoogleAdIdProvider
import ru.wildanalytics.pub.analytics.device.MetadataCollector
import ru.wildanalytics.pub.analytics.device.WildDeviceInfoProvider
import ru.wildanalytics.pub.analytics.device.WildDeviceInfoProviderImpl
import ru.wildanalytics.pub.analytics.event.EventsRepository
import ru.wildanalytics.pub.analytics.event.EventsRepositoryImpl
import ru.wildanalytics.pub.analytics.network.NetworkAvailabilitySource
import ru.wildanalytics.pub.analytics.send.SendAllAnalyticEventsOperation
import ru.wildanalytics.pub.analytics.send.SendOperationScheduler
import ru.wildanalytics.pub.analytics.send.SendStrategyProvider
import ru.wildanalytics.pub.analytics.send.WildAnalyticsSenderService
import java.time.Clock
import kotlin.concurrent.Volatile

@OptIn(ExperimentalCoroutinesApi::class)
private fun buildInstance(context: Context): WildAnalyticsServiceLocator = Builder().build {
    bindInstance(context.applicationContext)
    bindInstance(Clock.systemDefaultZone())
    bindInstance<WildAnalyticsLogger>(WildAnalyticsLoggerImpl())
    bindInstance<CoroutineScopeFactory>(CoroutineScopeFactoryImpl())

    bind<WildDeviceInfoProvider> {
        WildDeviceInfoProviderImpl(
            context = it.get()
        )
    }
    bind {
        GoogleAdIdProvider(
            context = it.get(),
            log = it.get(),
            scopeFactory = it.get(),
        )
    }
    bind<WildAnalyticsDatabase> {
        Room.databaseBuilder(
            context = it.get(),
            name = "ru.wildanalytics.pub.analytics.db",
            factory = { WildAnalyticsDatabase_Impl() }
        )
            .setQueryCoroutineContext(Dispatchers.IO.limitedParallelism(4))
            .fallbackToDestructiveMigration(true)
            .addMigrations(*Migrations().all)
            .build()
    }
    bind {
        MetadataCollector(
            deviceInfoProvider = it.get(),
            googleAdIdProvider = it.get(),
            context = it.get(),
            clock = it.get(),
        )
    }
    bind<TransactionRunner> {
        RoomTransactionRunner(db = it.get<WildAnalyticsDatabase>())
    }
    bind {
        it.get<WildAnalyticsDatabase>().eventsDao()
    }
    bind {
        it.get<WildAnalyticsDatabase>().sentInfoDao()
    }
    bind<ConfigRepository> {
        ConfigRepositoryImpl()
    }
    bind<EventsRepository> {
        EventsRepositoryImpl(
            eventsDao = it.get(),
            db = it.get(),
            log = it.get(),
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
        )
    }
    bind {
        WildAnalyticsSenderService(
            log = it.get(),
            configRepository = it.get(),
            coroutineScopeFactory = it.get(),
            sendStrategyProvider = it.get(),
        )
    }
    bind { NetworkAvailabilitySource(context = it.get()) }
    bind { SendOperationScheduler(context = it.get(), log = it.get()) }
    bind {
        SendAllAnalyticEventsOperation(
            eventsRepository = it.get(),
            batchRepository = it.get(),
            configRepository = it.get(),
            log = it.get(),
        )
    }
}

public class WildAnalyticsServiceLocator(private val providers: Map<Class<*>, (WildAnalyticsServiceLocator) -> Any>) {

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

        private val providers = mutableMapOf<Class<*>, (WildAnalyticsServiceLocator) -> Any>()

        fun <T> bind(clazz: Class<T>, provider: (WildAnalyticsServiceLocator) -> T) {
            @Suppress("UNCHECKED_CAST")
            providers[clazz] = provider as (WildAnalyticsServiceLocator) -> Any
        }

        fun <T> bindInstance(clazz: Class<T>, instance: T) {
            bind(clazz) { instance }
        }

        inline fun <reified T> bind(noinline provider: (WildAnalyticsServiceLocator) -> T) {
            bind(T::class.java, provider)
        }

        inline fun <reified T> bindInstance(instance: T) {
            bindInstance(T::class.java, instance)
        }

        fun build(body: Builder.() -> Unit): WildAnalyticsServiceLocator {
            body()
            return WildAnalyticsServiceLocator(providers)
        }
    }

    public companion object {

        @Volatile
        private var Instance: WildAnalyticsServiceLocator? = null

        public fun getInstance(context: Context): WildAnalyticsServiceLocator = Instance
            ?: synchronized(this) {
                Instance
                    ?: buildInstance(context)
                        .also { Instance = it }
            }
    }
}
