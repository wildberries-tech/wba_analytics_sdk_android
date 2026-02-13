package ru.wildberries.analytics.config

import kotlinx.coroutines.flow.StateFlow

public interface ConfigRepository {

    public val config: StateFlow<WBA2Config>

    public fun updateConfig(update: WBA2Config.() -> WBA2Config)
}
