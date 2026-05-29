package ru.wildanalytics.pub.analytics.config

import kotlinx.coroutines.flow.StateFlow

public interface ConfigRepository {

    public val config: StateFlow<WildAnalyticsConfig>

    public fun updateConfig(update: WildAnalyticsConfig.() -> WildAnalyticsConfig)
}
