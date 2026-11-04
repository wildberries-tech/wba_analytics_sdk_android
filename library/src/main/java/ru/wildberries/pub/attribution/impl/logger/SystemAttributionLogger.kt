package ru.wildberries.pub.attribution.impl.logger

import android.util.Log
import ru.wildberries.pub.attribution.api.WBAttributionLogger

internal data class SystemAttributionLogger(private val tag: String?) : WBAttributionLogger {

    override fun logDebug(error: Throwable?, lazyMessage: () -> String) {
        Log.d(tag, lazyMessage(), error)
    }

    override fun logError(error: Throwable?, lazyMessage: () -> String) {
        Log.e(tag, lazyMessage(), error)
    }
}
