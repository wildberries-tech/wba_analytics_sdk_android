package ru.wildanalytics.pub.analytics.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService

internal fun interface NetworkSnapshotProvider {
    fun snapshot(): NetworkSnapshot
}

internal class NetworkSnapshotProviderImpl(private val context: Context) : NetworkSnapshotProvider {

    override fun snapshot(): NetworkSnapshot = try {
        val connectivityManager = context.getSystemService<ConnectivityManager>()
        val capabilities = connectivityManager?.activeNetwork?.let(connectivityManager::getNetworkCapabilities)
        if (capabilities == null) {
            NetworkSnapshot(transports = NONE, validated = null)
        } else {
            NetworkSnapshot(
                transports = TRANSPORTS.filter { (transport, _) -> capabilities.hasTransport(transport) }
                    .joinToString(separator = ",") { (_, name) -> name }
                    .ifEmpty { OTHER },
                validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            )
        }
    } catch (_: Exception) {
        UNKNOWN_NETWORK
    }

    private companion object {
        const val NONE = "none"
        const val OTHER = "other"

        val TRANSPORTS = listOf(
            NetworkCapabilities.TRANSPORT_WIFI to "wifi",
            NetworkCapabilities.TRANSPORT_CELLULAR to "cellular",
            NetworkCapabilities.TRANSPORT_ETHERNET to "ethernet",
            NetworkCapabilities.TRANSPORT_VPN to "vpn",
        )
    }
}

internal val UNKNOWN_NETWORK = NetworkSnapshot(transports = "unknown", validated = null)
