package ru.wildanalytics.pub.analytics.device

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.os.ConfigurationCompat
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import ru.wildanalytics.pub.analytics.logDebug
import java.util.Locale

internal class SystemLocaleProviderImpl(
    private val context: Context,
    private val log: WildAnalyticsLogger,
) : SystemLocaleProvider {

    override val systemLocaleTag: String
        get() {
            val tag = systemLocale()?.toLanguageTag().orEmpty()
            log.logDebug { "System locale resolved: $tag" }
            return tag
        }

    /**
     * Язык и регион ОС.
     *
     * Контекст, переданный при инициализации SDK, может быть обёрнут
     * локалью приложения, поэтому спрашиваем систему напрямую.
     */
    private fun systemLocale(): Locale? {
        val localeManagerLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            localeManagerLocale(context)
        } else {
            null
        }
        return localeManagerLocale ?: systemResourcesLocale()
    }

    private fun systemResourcesLocale(): Locale? =
        ConfigurationCompat.getLocales(Resources.getSystem().configuration).get(0)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun localeManagerLocale(context: Context): Locale? {
    val systemLocales = context.getSystemService(LocaleManager::class.java)?.systemLocales
    return if (systemLocales == null || systemLocales.isEmpty) null else systemLocales[0]
}
