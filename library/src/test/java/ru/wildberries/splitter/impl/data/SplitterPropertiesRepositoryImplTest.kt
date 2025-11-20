package ru.wildberries.splitter.impl.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.wildberries.splitter.api.WBSplitterConfig
import ru.wildberries.splitter.impl.SplitterLogger
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
internal class SplitterPropertiesRepositoryImplTest {

    private val logger: SplitterLogger = mockk(relaxed = true)
    private val dao: SplitterPropertyDao = mockk(relaxed = true)
    private val remoteDataSource: SplitterRemoteDataSource = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var repository: SplitterPropertiesRepositoryImpl

    private val config = WBSplitterConfig(
        apiKey = "apiKey",
        fetchUrl = "fetchUrl",
        userId = "userId"
    )

    @BeforeEach
    fun setUp() {
        repository = SplitterPropertiesRepositoryImpl(
            logger = logger,
            localDataSource = dao,
            remoteDataSource = remoteDataSource,
            scope = testScope
        )
    }

    @Test
    fun `fetch throttled`(): Unit = testScope.runTest {
        SplitterPropertiesRepositoryImpl.updateFetchTimestampForApiKey(
            config.apiKey,
            System.currentTimeMillis()
        )

        repository.fetch(config)
        advanceTimeBy(100)

        coVerify(exactly = 0) { remoteDataSource.getExperiments(any()) }
    }

    @Test
    fun `fetch success`(): Unit = testScope.runTest {
        val experiments = listOf(
            SplitterGroupDto(
                "type",
                listOf(SplitterPropertyDto("key", "value"))
            )
        )
        coEvery { remoteDataSource.getExperiments(config) } returns experiments
        coEvery { dao.getProperties(config.apiKey) } returns emptyList()
        SplitterPropertiesRepositoryImpl.updateFetchTimestampForApiKey(config.apiKey, null)
        repository.fetch(config)
        advanceUntilIdle()

        val properties = repository.getProperties("type")
        assertEquals(mapOf("key" to "value"), properties)

        coVerify { dao.replaceProperties(config.apiKey, any()) }
    }

    @Test
    fun `fetch force`(): Unit = testScope.runTest {
        val experiments = listOf(
            SplitterGroupDto(
                "type",
                listOf(SplitterPropertyDto("key", "value"))
            )
        )
        coEvery { remoteDataSource.getExperiments(config) } returns experiments
        SplitterPropertiesRepositoryImpl.updateFetchTimestampForApiKey(
            config.apiKey,
            System.currentTimeMillis()
        )

        repository.fetch(config, force = true)
        advanceUntilIdle()

        coVerify { remoteDataSource.getExperiments(config) }
    }

    @Test
    fun `getProperties returns cached properties`(): Unit = testScope.runTest {
        val experiments = listOf(
            SplitterGroupDto(
                "type",
                listOf(SplitterPropertyDto("key", "value"))
            )
        )
        coEvery { remoteDataSource.getExperiments(config) } returns experiments
        SplitterPropertiesRepositoryImpl.updateFetchTimestampForApiKey(config.apiKey, null)

        repository.fetch(config)
        advanceUntilIdle()

        val properties = repository.getProperties("type")

        assertEquals(mapOf("key" to "value"), properties)
    }

    @Test
    fun `observeProperties emits updates`(): Unit = testScope.runTest {
        val experiments = listOf(
            SplitterGroupDto(
                "type",
                listOf(SplitterPropertyDto("key", "value"))
            )
        )
        coEvery { remoteDataSource.getExperiments(config) } returns experiments
        SplitterPropertiesRepositoryImpl.updateFetchTimestampForApiKey(config.apiKey, null)


        val flow = repository.observeProperties("type")
        val initialProperties = flow.first()
        assertEquals(emptyMap(), initialProperties)

        repository.fetch(config)
        advanceUntilIdle()

        val updatedProperties = flow.first()
        assertEquals(mapOf("key" to "value"), updatedProperties)
    }

    @Test
    fun `cache is initialized from db`(): Unit = testScope.runTest {
        val dbProperties =
            listOf(
                SplitterPropertyEntity(
                    config.apiKey,
                    "type",
                    "key",
                    "value"
                )
            )
        coEvery { dao.getProperties(config.apiKey) } returns dbProperties

        SplitterPropertiesRepositoryImpl.updateFetchTimestampForApiKey(
            config.apiKey,
            System.currentTimeMillis()
        )
        repository.updateInMemoryCacheFromLocalSource(config.apiKey)

        val properties = repository.getProperties("type")
        assertEquals(mapOf("key" to "value"), properties)
    }
}
