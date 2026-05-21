package ru.wildanalytics.pub.analytics

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal interface CoroutineScopeFactory {

    fun create(debugName: String): CoroutineScope
}

internal class CoroutineScopeFactoryImpl : CoroutineScopeFactory {

    override fun create(debugName: String): CoroutineScope {
        return CoroutineScope(
            SupervisorJob() + CoroutineName(debugName) + Dispatchers.Default
        )
    }
}
