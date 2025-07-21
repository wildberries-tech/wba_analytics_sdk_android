package ru.wildberries.analytics.config

import kotlinx.coroutines.flow.StateFlow

internal interface ConfigRepository {

    val config: StateFlow<WBA2Config>

    fun updateConfig(update: WBA2Config.() -> WBA2Config)
}
