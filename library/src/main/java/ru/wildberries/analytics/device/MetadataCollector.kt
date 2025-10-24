package ru.wildberries.analytics.device

import android.content.Context
import android.content.res.Resources
import android.graphics.Point
import android.net.ConnectivityManager
import android.os.Build
import android.os.Build.VERSION_CODES
import android.telephony.TelephonyManager
import android.util.Size
import android.view.WindowManager
import androidx.core.content.getSystemService
import androidx.core.os.ConfigurationCompat
import ru.wildberries.analytics.domain.MetaInfo
import ru.wildberries.analytics.domain.NetworkType
import ru.wildberries.analytics.osBuild
import java.time.Clock
import java.time.OffsetDateTime

/**
 * Собирает некоторую общую информацию.
 */
internal class MetadataCollector(
    private val deviceInfoProvider: WBDeviceInfoProvider,
    private val context: Context,
    private val clock: Clock,
) {

    private val displayResolution: Size by lazy { displayResolution() }

    fun collect(): MetaInfo {
        return MetaInfo(
            locale = ConfigurationCompat.getLocales(context.resources.configuration).get(0)!!.toLanguageTag(),
            device = Build.DEVICE,
            sdkVersion = Build.VERSION.SDK_INT.toString(),
            model = Build.DEVICE,
            mobileDeviceType = getDeviceType(),
            product = ANDROID,
            osBuild = osBuild,
            manufacturer = Build.MANUFACTURER,
            fingerprint = Build.FINGERPRINT,
            netType = getNetworkType(),
            appId = context.packageName,
            appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName!!,
            analyticsSdkVersion = "1.0",
            deviceId = deviceInfoProvider.getDeviceId(),
            localTime = OffsetDateTime.now(clock),
            isUserNew = deviceInfoProvider.isUserNew(),
            resolutionWidth = displayResolution.width,
            resolutionHeight = displayResolution.height,
        )
    }

    private fun getDeviceType(): String {
        return if (context.resources.configuration.smallestScreenWidthDp >= TABLET_SMALLEST_SCREEN_WIDTH) TABLET else PHONE
    }

    @Suppress("DEPRECATION")
    private fun getNetworkType(): NetworkType {
        val connectivityManager: ConnectivityManager = context.getSystemService()!!
        val activeNetworkInfo = connectivityManager.activeNetworkInfo ?: return NetworkType.Other
        val type = activeNetworkInfo.type
        val subtype = activeNetworkInfo.subtype
        return when {
            type == ConnectivityManager.TYPE_WIFI -> NetworkType.WiFi
            type == ConnectivityManager.TYPE_ETHERNET -> NetworkType.Ethernet
            type != ConnectivityManager.TYPE_MOBILE -> NetworkType.Other
            subtype == TelephonyManager.NETWORK_TYPE_GPRS ||
                subtype == TelephonyManager.NETWORK_TYPE_EDGE ||
                subtype == TelephonyManager.NETWORK_TYPE_CDMA ||
                subtype == TelephonyManager.NETWORK_TYPE_1xRTT ||
                subtype == TelephonyManager.NETWORK_TYPE_IDEN ||
                subtype == TelephonyManager.NETWORK_TYPE_GSM -> NetworkType.Mobile2G

            subtype == TelephonyManager.NETWORK_TYPE_UMTS ||
                subtype == TelephonyManager.NETWORK_TYPE_EVDO_0 ||
                subtype == TelephonyManager.NETWORK_TYPE_EVDO_A ||
                subtype == TelephonyManager.NETWORK_TYPE_HSDPA ||
                subtype == TelephonyManager.NETWORK_TYPE_HSUPA ||
                subtype == TelephonyManager.NETWORK_TYPE_HSPA ||
                subtype == TelephonyManager.NETWORK_TYPE_EVDO_B ||
                subtype == TelephonyManager.NETWORK_TYPE_EHRPD ||
                subtype == TelephonyManager.NETWORK_TYPE_HSPAP ||
                subtype == TelephonyManager.NETWORK_TYPE_TD_SCDMA -> NetworkType.Mobile3G

            subtype == TelephonyManager.NETWORK_TYPE_LTE ||
                subtype == TelephonyManager.NETWORK_TYPE_IWLAN -> NetworkType.Mobile4G

            subtype == TelephonyManager.NETWORK_TYPE_NR -> NetworkType.Mobile5G

            else -> NetworkType.Other
        }
    }

    private fun displayResolution(): Size = try {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            Size(bounds.width(), bounds.height())
        } else {
            val display = windowManager.defaultDisplay
            val size = Point()
            display.getRealSize(size)
            Size(size.x, size.y)
        }
    } catch (_: Throwable) {
        Resources.getSystem()
            .displayMetrics
            .run { Size(widthPixels, heightPixels) }
    }

    companion object {

        private const val TABLET_SMALLEST_SCREEN_WIDTH = 600
        private const val TABLET = "tablet"
        private const val PHONE = "phone"
        private const val ANDROID = "Android"
    }
}
