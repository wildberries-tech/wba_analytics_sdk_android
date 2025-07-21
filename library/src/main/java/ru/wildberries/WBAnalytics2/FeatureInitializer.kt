package ru.wildberries.WBAnalytics2

import android.content.Context
import androidx.room.Room
import androidx.startup.Initializer
import ru.wildberries.analytics.CoroutineScopeFactory
import ru.wildberries.analytics.CoroutineScopeFactoryImpl
import ru.wildberries.analytics.WBAnalytics2Logger
import ru.wildberries.analytics.WBAnalytics2LoggerImpl
import ru.wildberries.analytics.WBAnalytics2ServiceLocator
import ru.wildberries.analytics.batch.BatchRepository
import ru.wildberries.analytics.batch.BatchRepositoryImpl
import ru.wildberries.analytics.config.ConfigRepository
import ru.wildberries.analytics.config.ConfigRepositoryImpl
import ru.wildberries.analytics.db.Migrations
import ru.wildberries.analytics.db.RoomTransactionRunner
import ru.wildberries.analytics.db.TransactionRunner
import ru.wildberries.analytics.db.WBAnalytics2Database
import ru.wildberries.analytics.device.MetadataCollector
import ru.wildberries.analytics.device.WBDeviceInfoProvider
import ru.wildberries.analytics.device.WBDeviceInfoProviderImpl
import ru.wildberries.analytics.event.EventsRepository
import ru.wildberries.analytics.event.EventsRepositoryImpl
import ru.wildberries.analytics.network.NetworkAvailabilitySource
import ru.wildberries.analytics.service.WBAnalytics2SenderService
import ru.wildberries.analytics.wbAnalytics2Locator
import java.time.Clock

@Suppress("unused")
public class FeatureInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        val sl = WBAnalytics2ServiceLocator.Builder().build {
            bindInstance(context)
            bindInstance(Clock.systemDefaultZone())
            bindInstance<WBAnalytics2Logger>(WBAnalytics2LoggerImpl())
            bindInstance<CoroutineScopeFactory>(CoroutineScopeFactoryImpl())

            bind<WBDeviceInfoProvider> {
                WBDeviceInfoProviderImpl(
                    context = it.get()
                )
            }
            bind {
                Room.databaseBuilder(
                    it.get(),
                    WBAnalytics2Database::class.java,
                    "ru.wildberries.analytics.db"
                )
                    .fallbackToDestructiveMigration()
                    .addMigrations(*Migrations().all)
                    .build()
            }
            bind {
                MetadataCollector(
                    deviceInfoProvider = it.get(),
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
                WBAnalytics2SenderService(
                    log = it.get(),
                    batchRepository = it.get(),
                    eventsRepository = it.get(),
                    configRepository = it.get(),
                    coroutineScopeFactory = it.get(),
                    networkAvailabilitySource = it.get(),
                )
            }

            bind { NetworkAvailabilitySource(context = it.get(), logger = it.get()) }
        }

        wbAnalytics2Locator = sl

        sl.get<WBAnalytics2SenderService>() // Запускает сервис.
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
