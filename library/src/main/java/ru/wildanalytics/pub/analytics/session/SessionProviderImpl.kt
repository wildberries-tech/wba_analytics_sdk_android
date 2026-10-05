package ru.wildanalytics.pub.analytics.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.wildanalytics.pub.analytics.util.IdGenerator
import ru.wildanalytics.pub.analytics.util.ProcessForegroundDataProvider
import java.util.UUID

internal class SessionProviderImpl(
    private val idGenerator: IdGenerator,
    processForegroundDataProvider: ProcessForegroundDataProvider,
    scope: CoroutineScope,
) : SessionProvider {

    private var _currentSessionValue: ULong = idGenerator.generateId().toSessionValue()

    override val currentSessionValue: ULong
        get() = _currentSessionValue

    init {
        processForegroundDataProvider.processForegroundData
            .onEach {
                _currentSessionValue = idGenerator.generateId().toSessionValue()
            }
            .launchIn(scope)
    }
}

private fun UUID.toSessionValue() = leastSignificantBits.toULong()
