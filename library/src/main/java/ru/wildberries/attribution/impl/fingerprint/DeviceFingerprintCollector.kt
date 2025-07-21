package ru.wildberries.attribution.impl.fingerprint

import android.content.Context
import android.content.res.Resources
import android.graphics.Point
import android.os.Build
import android.os.Build.VERSION_CODES
import android.view.WindowManager
import androidx.core.os.ConfigurationCompat
import kotlinx.serialization.InternalSerializationApi
import java.util.Locale
import java.util.TimeZone

internal class DeviceFingerprintCollector(private val context: Context) {

    @OptIn(InternalSerializationApi::class)
    fun collect(): DeviceFingerprintDto {
        val screen = getScreen()
        val locale = getLocale()
        val language = locale.toLanguageTag()
        val timezone = TimeZone.getDefault().id
        return DeviceFingerprintDto(
            screen = screen,
            platform = "Android",
            language = language,
            timezone = timezone,
        )
    }

    private fun getLocale(): Locale = ConfigurationCompat.getLocales(context.resources.configuration).get(0)
        ?: Locale.getDefault()

    private fun getScreen(): String = try {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            "${bounds.width()}x${bounds.height()}"
        } else {
            val display = windowManager.defaultDisplay
            val size = Point()
            display.getRealSize(size)
            "${size.x}x${size.y}"
        }
    } catch (_: Throwable) {
        Resources.getSystem()
            .displayMetrics
            .run { "${widthPixels}x${heightPixels}" }
    }
}
