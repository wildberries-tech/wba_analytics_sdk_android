package ru.wildanalytics.pub.analytics

import android.util.Log
import ru.wildanalytics.pub.WildAnalytics.BuildConfig

internal class WildAnalyticsLoggerImpl : WildAnalyticsLogger {

    private val tag = "WildAnalytics"
    override val isEnabled
        get() = analyticsCustomLogger?.isEnabled
                ?: BuildConfig.DEBUG || Log.isLoggable(tag, Log.DEBUG)

    override fun logDebug(message: String) {
        val customLogger = analyticsCustomLogger
        if (customLogger == null) {
            Log.d(tag, message)
        } else {
            customLogger.logDebug(message)
        }
    }

    override fun logException(e: Exception) {
        val customLogger = analyticsCustomLogger
        if (customLogger == null) {
            Log.w(tag, "", e)
        } else {
            customLogger.logException(e)
        }
    }

    override fun logError(e: Error, details: Map<String, String>) {
        val customLogger = analyticsCustomLogger
        if (customLogger == null) {
            Log.e(tag, "", e)
        } else {
            customLogger.logError(e, details)
        }
    }

    override fun logWarn(message: String) {
        val customLogger = analyticsCustomLogger
        if (customLogger == null) {
            Log.w(tag, message)
        } else {
            customLogger.logWarn(message)
        }
    }
}
