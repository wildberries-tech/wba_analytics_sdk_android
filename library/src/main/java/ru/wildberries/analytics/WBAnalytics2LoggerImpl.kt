package ru.wildberries.analytics

import android.util.Log
import ru.wildberries.WBAnalytics2.BuildConfig

internal class WBAnalytics2LoggerImpl : WBAnalytics2Logger {

    private val tag = "WBA2"
    override val isEnabled
        get() = wbAnalytics2CustomLogger?.isEnabled
                ?: BuildConfig.DEBUG || Log.isLoggable(tag, Log.DEBUG)

    override fun logDebug(message: String) {
        val customLogger = wbAnalytics2CustomLogger
        if (customLogger == null) {
            Log.d(tag, message)
        } else {
            customLogger.logDebug(message)
        }
    }

    override fun logException(e: Exception) {
        val customLogger = wbAnalytics2CustomLogger
        if (customLogger == null) {
            Log.w(tag, "", e)
        } else {
            customLogger.logException(e)
        }
    }

    override fun logError(e: Error, details: Map<String, String>) {
        val customLogger = wbAnalytics2CustomLogger
        if (customLogger == null) {
            Log.e(tag, "", e)
        } else {
            customLogger.logError(e, details)
        }
    }

    override fun logWarn(message: String) {
        val customLogger = wbAnalytics2CustomLogger
        if (customLogger == null) {
            Log.w(tag, message)
        } else {
            customLogger.logWarn(message)
        }
    }
}
