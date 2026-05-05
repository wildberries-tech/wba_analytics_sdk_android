package ru.wildberries.splitter.impl

import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.wildberries.splitter.api.OnConfigChangedFlags
import ru.wildberries.splitter.api.WBSplitterConfig
import ru.wildberries.splitter.impl.domain.SplitterPropertiesRepository

@Suppress("UnusedFlow")
internal class WBSplitterImplTest {

    private lateinit var repository: SplitterPropertiesRepository
    private lateinit var splitter: WBSplitterImpl
    private val config = WBSplitterConfig(
        apiKey = "apiKey",
        fetchUrl = "fetchUrl",
        userId = "userId"
    )
    private var onConfigChangedFlags: OnConfigChangedFlags = OnConfigChangedFlags()
    private val getOnConfigChangedFlags: (oldConfig: WBSplitterConfig, newConfig: WBSplitterConfig) -> OnConfigChangedFlags =
        { _, _ -> onConfigChangedFlags }

    @BeforeEach
    fun setUp() {
        repository = mockk(relaxed = true)
        splitter = WBSplitterImpl(
            repository,
            config,
            getOnConfigChangedFlags = getOnConfigChangedFlags,
            mockk(relaxed = true)
        )
    }

    @Test
    fun `getProperties calls repository`() = runTest {
        splitter.getProperties("test_type")
        coVerify { repository.getProperties("test_type") }
    }

    @Test
    fun `getProperty calls repository`() = runTest {
        splitter.getProperty("test_type", "test_key")
        coVerify { repository.getProperty("test_type", "test_key") }
    }

    @Test
    fun `getPropertyFromMemoryCache calls repository`() {
        splitter.getPropertyFromMemoryCache("test_type", "test_key")
        verify { repository.getPropertyFromMemoryCache("test_type", "test_key") }
    }

    @Test
    fun `observePropertiesForType calls repository`() {
        splitter.observePropertiesForType("test_type")
        verify { repository.observeProperties("test_type") }
    }

    @Test
    fun `observeProperties calls repository`() {
        splitter.observeProperties()
        verify { repository.observeProperties() }
    }

    @Test
    fun `observeProperty calls repository`() {
        splitter.observeProperty("test_type", "test_key")
        verify { repository.observeProperty("test_type", "test_key") }
    }

    @Test
    fun `requestPropertiesFetch calls repository`() {
        splitter.requestPropertiesFetch()
        verify { repository.fetch(config) }
    }

    @Test
    fun `updateConfig with updateCacheFromLocalSource flag does call updateInMemoryCacheFromLocalSource`() {
        onConfigChangedFlags = OnConfigChangedFlags(updateCacheFromLocalSource = true)
        val newConfig = config.copy(apiKey = "newApiKey")

        splitter.updateConfig { newConfig }

        verify { repository.updateInMemoryCacheFromLocalSource(newConfig.apiKey) }
    }

    @Test
    fun `updateConfig with forceFetch flag does call fetch with force`() {
        onConfigChangedFlags = OnConfigChangedFlags(forceFetch = true)
        val newConfig = config.copy(fetchUrl = "newFetchUrl")

        splitter.updateConfig { newConfig }

        verify { repository.fetch(config = newConfig, force = true) }
    }

    @Test
    fun `updateConfig with softFetch flag does call fetch without force`() {
        onConfigChangedFlags = OnConfigChangedFlags(softFetch = true)
        val newConfig = config.copy(userId = "newUserId")

        splitter.updateConfig { newConfig }

        verify { repository.fetch(config = newConfig, force = false) }
    }

    @Test
    fun `updateConfig with no flags does not call fetch or updateInMemoryCacheFromLocalSource`() {
        onConfigChangedFlags = OnConfigChangedFlags()
        val newConfig = config.copy(apiKey = "newApiKey")

        splitter.updateConfig { newConfig }

        verify(exactly = 0) { repository.updateInMemoryCacheFromLocalSource(newConfig.apiKey) }
        verify(exactly = 0) { repository.fetch(newConfig, any()) }
    }

    @Test
    fun `updateConfig with all flags calls all methods`() {
        onConfigChangedFlags = OnConfigChangedFlags(
            updateCacheFromLocalSource = true,
            forceFetch = true,
            softFetch = true
        )
        val newConfig = config.copy(apiKey = "newApiKey", fetchUrl = "newFetchUrl")

        splitter.updateConfig { newConfig }

        verify { repository.updateInMemoryCacheFromLocalSource(newConfig.apiKey) }
        verify { repository.fetch(config = newConfig, force = true) }
        verify(exactly = 0) { repository.fetch(config = newConfig, force = false) }
    }
}
