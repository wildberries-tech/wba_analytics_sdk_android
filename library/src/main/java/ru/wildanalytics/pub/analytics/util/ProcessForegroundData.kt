package ru.wildanalytics.pub.analytics.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

/**
 * @param isInForeground - находится ли процесс на переднем плане (т.е. есть ли какая-то видимая [android.app.Activity])
 * @param isFromForeground - был ли процесс до этого на переднем плане
 * */
internal data class ProcessForegroundData(
    val isInForeground: Boolean,
    val isFromForeground: Boolean,
)

internal class ProcessForegroundDataProvider {

    /**
     * Данные о нахождении процесса на переднем плане
     * @see ProcessForegroundData
     * */
    val processForegroundData: Flow<ProcessForegroundData>
        get() = ProcessLifecycleOwner.get()
            .lifecycle
            .currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.STARTED) }
            .distinctUntilChanged()
            .scan<Boolean, ProcessForegroundData?>(null) { prev, isInForeground ->
                ProcessForegroundData(
                    isInForeground = isInForeground,
                    isFromForeground = prev?.isInForeground == true
                )
            }
            .filterNotNull()
            .distinctUntilChanged()
}