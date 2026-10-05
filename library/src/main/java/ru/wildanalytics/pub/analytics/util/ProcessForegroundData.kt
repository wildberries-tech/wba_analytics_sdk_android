package ru.wildanalytics.pub.analytics.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

/**
 * @param isInForeground - находится ли процесс на переднем плане (т.е. есть ли какая-то видимая [android.app.Activity])
 * @param isFromForeground - был ли процесс до этого на переднем плане
 * */
internal data class ProcessForegroundData(
    val isInForeground: Boolean,
    val isFromForeground: Boolean,
)

internal class ProcessForegroundDataProvider(scope: CoroutineScope) {

    /**
     * Данные о нахождении процесса на переднем плане
     * @see ProcessForegroundData
     * */
    val processForegroundData: StateFlow<ProcessForegroundData> = ProcessLifecycleOwner.get()
        .lifecycle
        .currentStateFlow
        .map { it.isInForeground }
        .distinctUntilChanged()
        .scan<Boolean, ProcessForegroundData?>(null) { prev, isInForeground ->
            ProcessForegroundData(
                isInForeground = isInForeground,
                isFromForeground = prev?.isInForeground == true
            )
        }
        .filterNotNull()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
            initialValue = ProcessForegroundData(
                isInForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isInForeground,
                isFromForeground = false,
            )
        )

    private val Lifecycle.State.isInForeground: Boolean get() = isAtLeast(Lifecycle.State.STARTED)
}