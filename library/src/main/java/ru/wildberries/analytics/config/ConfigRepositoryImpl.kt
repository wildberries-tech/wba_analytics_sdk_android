package ru.wildberries.analytics.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class ConfigRepositoryImpl : ConfigRepository {

    override val config: MutableStateFlow<WBA2Config> = MutableStateFlow(WBA2Config.Default)

    override fun updateConfig(update: WBA2Config.() -> WBA2Config) {
        config.update(update)
    }
}
