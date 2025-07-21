package ru.wildberries.analytics

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.testCoroutineScheduler
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.InternalSerializationApi
import ru.wildberries.analytics.api.BatchModel
import ru.wildberries.analytics.api.Event
import ru.wildberries.analytics.batch.BatchData
import ru.wildberries.analytics.batch.BatchRepository
import ru.wildberries.analytics.config.ConfigRepository
import ru.wildberries.analytics.config.WBA2Config
import ru.wildberries.analytics.domain.ApiData
import ru.wildberries.analytics.event.EventsRepository
import ru.wildberries.analytics.network.NetworkAvailabilitySource
import ru.wildberries.analytics.service.WBAnalytics2SenderService
import ru.wildberries.analytics.transport.Transport

@OptIn(
    ExperimentalCoroutinesApi::class,
    ExperimentalStdlibApi::class,
    InternalSerializationApi::class
)
@Suppress("Unused")
internal class WBAnalytics2SenderServiceTest : BehaviorSpec() {

    init {
        // Почему-то тестовый скоуп работат только если его включть так.
        coroutineTestScope = true

        lateinit var log: WBAnalytics2Logger
        lateinit var apiUrl: String
        lateinit var apiKey: String
        lateinit var events: List<Event>
        lateinit var batchRepository: BatchRepository
        lateinit var batchData: BatchData
        lateinit var transport: Transport

        Given("Dao and events") {
            apiKey = "someApiKey"
            apiUrl = "someApiUrl"
            val apiData = ApiData(apiUrl, apiKey)
            events = listOf(
                Event(
                    name = "event1",
                    time = testDate(),
                    data = mapOf("param1" to "value1").toJsonObject(),
                    eventNumber = 1u
                )
            )
            batchData = BatchData(
                contentLength = Long.MAX_VALUE,
                model = BatchModel(events = events, meta = testMeta()),
                apiData = apiData,
                eventIds = events.indices.toList(),
                heaviestEvent = "" to 0,
            )

            val networkAvailabilitySource = mockk<NetworkAvailabilitySource> {
                every { isAvailableFlow } returns flowOf(true)
            }

            val eventsRepository = mockk<EventsRepository>(relaxUnitFun = true) {
                coEvery { getFirstEventApiDataOrNull() } returns null
                coEvery { awaitFirstApiData() } returns apiData
            }

            transport = mockk<Transport> {
                coEvery { send(any(), any<BatchModel>(), any(), any()) } returns 200
            }
            val config = WBA2Config.Companion.Default.copy(transport = transport)
            val configRepository = mockk<ConfigRepository> {
                every { this@mockk.config } returns MutableStateFlow(config)
            }
            // Мокаем RoomDatabase.withTransaction extension метод
            MockKAnnotations.init(this)
            mockkStatic("androidx.room.RoomDatabaseKt")

            log = mockk(relaxed = true)
            batchRepository = mockk(relaxUnitFun = true) {
                coEvery { getBatch(any(), any()) } coAnswers { batchData }
            }
            `when`("Start service and send 1 test event") {
                WBAnalytics2SenderService(
                    log = log,
                    batchRepository = batchRepository,
                    eventsRepository = eventsRepository,
                    configRepository = configRepository,
                    coroutineScopeFactory = TestScopeFactory(parentScope = this),
                    networkAvailabilitySource = networkAvailabilitySource,
                )
                testCoroutineScheduler.advanceTimeBy(15_000)
                coroutineContext.cancelChildren()
                then("Api receives 1 send request") {
                    coVerifyOrder {
                        batchRepository.getBatch(eq(ApiData(apiUrl, apiKey)), any())
                        transport.send(
                            url = apiUrl,
                            body = batchData.model,
                            strategy = BatchModel.serializer(),
                            headers = any(),
                        )
                        batchRepository.batchSent(eventIds = batchData.eventIds)
                    }
                }
            }
        }
    }
}