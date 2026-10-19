package ru.wildanalytics.pub.analytics.sdk.demo.events

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface BackgroundStateProvider {
    val isInBackground: Flow<Boolean>
}

class BackgroundStateProviderImpl : BackgroundStateProvider {
    override val isInBackground: Flow<Boolean> = ProcessLifecycleOwner.get().lifecycle.currentStateFlow
        .map { !it.isAtLeast(Lifecycle.State.STARTED) }
        .distinctUntilChanged()
}
