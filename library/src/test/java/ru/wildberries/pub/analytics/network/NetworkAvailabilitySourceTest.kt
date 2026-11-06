package ru.wildberries.pub.analytics.network

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.wildberries.pub.analytics.WBAnalytics2Logger
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
internal class NetworkAvailabilitySourceTest {

    @Test
    fun closingFlowAfterFailedRegistrationDoesNotThrow() = runTest {
        val connectivityManager = mockk<ConnectivityManager> {
            every { registerNetworkCallback(any(), any<ConnectivityManager.NetworkCallback>()) } throws
                SecurityException("Package android does not belong to 10135")
            every { unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) } throws
                IllegalArgumentException("NetworkCallback was not registered")
        }
        val context = mockk<Context> {
            every { getSystemService(ConnectivityManager::class.java) } returns connectivityManager
            every { getSystemServiceName(ConnectivityManager::class.java) } returns Context.CONNECTIVITY_SERVICE
            every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        }

        val log = mockk<WBAnalytics2Logger>(relaxed = true) {
            every { isEnabled } returns true
        }

        val values = mutableListOf<Boolean>()
        val job = launch { NetworkAvailabilitySource(context, log).availabilityFlow.collect { values += it } }
        advanceTimeBy(2.seconds)
        job.cancel()
        advanceUntilIdle()

        assertEquals(listOf(false), values)
        verify(exactly = 2) { connectivityManager.registerNetworkCallback(any(), any<ConnectivityManager.NetworkCallback>()) }
        verify(exactly = 1) { log.logWarn(match<String> { it.contains("SecurityException") }) }
        verify { connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) }
    }

    @Test
    fun successfulRegistrationIsNotLogged() = runTest {
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        val context = mockk<Context> {
            every { getSystemService(ConnectivityManager::class.java) } returns connectivityManager
            every { getSystemServiceName(ConnectivityManager::class.java) } returns Context.CONNECTIVITY_SERVICE
            every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        }
        val log = mockk<WBAnalytics2Logger>(relaxed = true) {
            every { isEnabled } returns true
        }

        NetworkAvailabilitySource(context, log).availabilityFlow.first()

        verify(exactly = 0) { log.logWarn(any<String>()) }
        verify(exactly = 1) { connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) }
    }
}
