package ru.wildberries.analytics.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withContext
import ru.wildberries.analytics.WBAnalytics2Logger
import kotlin.time.Duration.Companion.milliseconds

internal class NetworkAvailabilitySource(
    private val context: Context,
    private val logger: WBAnalytics2Logger
) {

    val isAvailableFlow: Flow<Boolean> =
        combine(isRestrictedFlow(), isNetworkAvailable()) { isRestricted, isNetworkAvailable ->
            if (logger.isEnabled) {
                logger.logDebug("network isAvailable - $isNetworkAvailable, isRestricted - $isRestricted")
            }
            isNetworkAvailable && !isRestricted
        }
            .distinctUntilChanged()

    private val connectivityManager get() = context.getSystemService<ConnectivityManager>()!!

    private fun ConnectivityManager.isBackgroundRestricted() =
        restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED

    private fun isRestrictedFlow(): Flow<Boolean> {
        val connectManager = connectivityManager
        val lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle
        val isRestrictedState =
            MutableStateFlow(!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && connectManager.isBackgroundRestricted())

        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isRestrictedState.value = false
            }

            override fun onStop(owner: LifecycleOwner) {
                isRestrictedState.value = connectManager.isBackgroundRestricted()
            }
        }

        return isRestrictedState.onSubscription {
            withContext(Dispatchers.Main) {
                lifecycle.addObserver(observer)
            }
        }.onCompletion {
            withContext(Dispatchers.Main) {
                lifecycle.removeObserver(observer)
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun isNetworkAvailable(): Flow<Boolean> = callbackFlow {
        val callback = object : NetworkCallback() {
            private val availableNetworks = mutableSetOf<Network>()

            override fun onAvailable(network: Network) {
                availableNetworks.add(network)
                trySendBlocking(true)
            }

            override fun onLost(network: Network) {
                availableNetworks.remove(network)
                trySendBlocking(availableNetworks.isNotEmpty())
            }
        }

        // https://issuetracker.google.com/issues/175055271
        // фикс есть для android s+,
        // на остальных версиях - ждать апдейтов и юзать try catch
        val request =
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        val connectManager = connectivityManager
        try {
            connectManager.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            try {
                delay(1000.milliseconds)
                connectManager.registerNetworkCallback(request, callback)
            } catch (_: Exception) {
            }
        }
        awaitClose { connectManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
