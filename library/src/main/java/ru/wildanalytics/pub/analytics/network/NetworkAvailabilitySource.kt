package ru.wildanalytics.pub.analytics.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private const val NO_NETWORK_CHECK_DELAY_MS = 50L
private const val NETWORK_CHANGE_DEBOUNCE_MS = 50L

internal class NetworkAvailabilitySource(
    private val context: Context,
) {

    val availabilityFlow: Flow<Boolean> = isNetworkAvailable()

    private val connectivityManager get() = context.getSystemService<ConnectivityManager>()!!

    @OptIn(FlowPreview::class)
    private fun isNetworkAvailable(): Flow<Boolean> = callbackFlow {
        val availableNetworks = mutableSetOf<Network>()

        val callback = object : NetworkCallback() {

            override fun onAvailable(network: Network) {
                availableNetworks.add(network)
                trySendBlocking(true)
            }

            override fun onLost(network: Network) {
                availableNetworks.remove(network)
                trySendBlocking(availableNetworks.isNotEmpty())
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                if (blocked) {
                    availableNetworks.remove(network)
                } else {
                    availableNetworks.add(network)
                }
                trySendBlocking(availableNetworks.isNotEmpty())
            }
        }

        /**
         * спустя [NO_NETWORK_CHECK_DELAY_MS] проверим на наличие доступных сетей
         * своеобразный таймаут([NO_NETWORK_CHECK_DELAY_MS] + [NETWORK_CHANGE_DEBOUNCE_MS])
         * на проверку отсутствия сетей
         */
        launch {
            delay(NO_NETWORK_CHECK_DELAY_MS)
            trySendBlocking(availableNetworks.isNotEmpty())
        }
        // https://issuetracker.google.com/issues/175055271
        // фикс есть для android s+,
        // на остальных версиях - ждать апдейтов и юзать try catch
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
    }.debounce(NETWORK_CHANGE_DEBOUNCE_MS)
        .distinctUntilChanged()
}
