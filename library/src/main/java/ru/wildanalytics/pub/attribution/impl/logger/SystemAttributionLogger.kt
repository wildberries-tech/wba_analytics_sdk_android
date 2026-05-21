package ru.wildanalytics.pub.attribution.impl.logger

import android.util.Log
import ru.wildanalytics.pub.attribution.api.WildAttributionLogger

internal data class SystemAttributionLogger(private val tag: String?) : WildAttributionLogger {

    override fun logDebug(error: Throwable?, lazyMessage: () -> String) {
        Log.d(tag, lazyMessage(), error)
    }

    override fun logError(error: Throwable?, lazyMessage: () -> String) {
        Log.e(tag, lazyMessage(), error)
    }
}
