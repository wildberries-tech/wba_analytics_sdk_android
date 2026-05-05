package ru.wildberries.analytics

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import ru.wildberries.analytics.WBAnalytics2ServiceLocator.Builder
import ru.wildberries.analytics.batch.BatchRepository
import ru.wildberries.analytics.batch.BatchRepositoryImpl
import ru.wildberries.analytics.config.ConfigRepository
import ru.wildberries.analytics.config.ConfigRepositoryImpl
import ru.wildberries.analytics.db.Migrations
import ru.wildberries.analytics.db.RoomTransactionRunner
import ru.wildberries.analytics.db.TransactionRunner
import ru.wildberries.analytics.db.WBAnalytics2Database
import ru.wildberries.analytics.db.WBAnalytics2Database_Impl
import ru.wildberries.analytics.device.GoogleAdIdProvider
import ru.wildberries.analytics.device.MetadataCollector
import ru.wildberries.analytics.device.WBDeviceInfoProvider
import ru.wildberries.analytics.device.WBDeviceInfoProviderImpl
import ru.wildberries.analytics.event.EventsRepository
import ru.wildberries.analytics.event.EventsRepositoryImpl
import ru.wildberries.analytics.network.NetworkAvailabilitySource
import ru.wildberries.analytics.send.SendAllAnalyticEventsOperation
import ru.wildberries.analytics.send.SendOperationScheduler
import ru.wildberries.analytics.send.SendStrategyProvider
import ru.wildberries.analytics.send.WBAnalytics2SenderService
import java.time.Clock
import kotlin.concurrent.Volatile

@OptIn(ExperimentalCoroutinesApi::class)
private fun buildInstance(context: Context): WBAnalytics2ServiceLocator = Builder().build {
    bindInstance(context.applicationContext)
    bindInstance(Clock.systemDefaultZone())
    bindInstance<WBAnalytics2Logger>(WBAnalytics2LoggerImpl())
    bindInstance<CoroutineScopeFactory>(CoroutineScopeFactoryImpl())

    bind<WBDeviceInfoProvider> {
        WBDeviceInfoProviderImpl(
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
    bind<WBAnalytics2Database> {
        Room.databaseBuilder(
            context = it.get(),
            name = "ru.wildberries.analytics.db",
            factory = { WBAnalytics2Database_Impl() }
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
        WBAnalytics2SenderService(
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
