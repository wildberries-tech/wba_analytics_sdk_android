package ru.wildanalytics.pub.analytics.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class ConfigRepositoryImpl : ConfigRepository {

    override val config: MutableStateFlow<WildAnalyticsConfig> = MutableStateFlow(WildAnalyticsConfig.Default)

    override fun updateConfig(update: WildAnalyticsConfig.() -> WildAnalyticsConfig) {
        config.update(update)
    }
}
