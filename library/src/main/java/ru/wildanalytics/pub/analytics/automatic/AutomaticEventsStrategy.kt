package ru.wildanalytics.pub.analytics.automatic

import kotlinx.coroutines.CoroutineScope
import ru.wildanalytics.pub.analytics.ApiKey
import ru.wildanalytics.pub.analytics.WildAnalytics

internal sealed class AutomaticEventsStrategy {

    abstract fun start(wba: WildAnalytics, scope: CoroutineScope)
    abstract fun stop()

    class Track(
        private val apiKey: ApiKey,
        private val registry: AutomaticEventsApiKeyRegistry,
        trackerFactory: AutomaticEventsTracker.Factory,
    ) : AutomaticEventsStrategy() {

        private val tracker: AutomaticEventsTracker = trackerFactory.create()

        override fun start(wba: WildAnalytics, scope: CoroutineScope) {
            registry.register(apiKey)
            tracker.start(wba, scope)
        }

        override fun stop() {
            tracker.stop()
            registry.unregister(apiKey)
        }
    }

    object DoNotTrack : AutomaticEventsStrategy() {

        override fun start(wba: WildAnalytics, scope: CoroutineScope) = Unit
        override fun stop() = Unit
    }
}
