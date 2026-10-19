package ru.wildanalytics.pub.analytics

import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * Придерживает обработку событий, пока каждый из [requiredKeys] не будет хотя бы раз
 * присвоен через [WildAnalytics.setCommonParameter] (значение `null` тоже считается
 * присвоением). По истечении [timeout] открывается принудительно.
 *
 * Открывается один раз и навсегда: последующее удаление ключей его не закрывает.
 */
internal class CommonParametersGate(
    private val requiredKeys: Set<String>,
    private val timeout: Duration,
    private val log: WildAnalyticsLogger,
) {

    private val assignedKeys = MutableStateFlow(persistentHashSetOf<String>())

    @Volatile
    private var isOpen = requiredKeys.isEmpty()

    fun onAssigned(keys: Collection<String>) {
        if (isOpen) {
            return
        }

        assignedKeys.update { it.addAll(keys) }
    }

    suspend fun await() {
        if (isOpen) {
            return
        }

        val assigned = withTimeoutOrNull(timeout) {
            assignedKeys.first { it.containsAll(requiredKeys) }
        }
        if (assigned == null) {
            log.logWarn {
                val missing = requiredKeys - assignedKeys.value
                "required common parameters $missing were not set within $timeout, events proceed without them"
            }
        }
        isOpen = true
    }
}
