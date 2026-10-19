package ru.wildanalytics.pub.analytics.send.fallback

import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import ru.wildanalytics.pub.analytics.batch.BatchData
import ru.wildanalytics.pub.analytics.diagnostics.EndpointFallbackReporter
import ru.wildanalytics.pub.analytics.network.NetworkSnapshotProvider

/** Создаёт [EndpointRoute] для каждого батча. */
internal class EndpointRouter(
    private val endpointFallback: EndpointFallback,
    private val networkSnapshotProvider: NetworkSnapshotProvider,
    private val reporter: EndpointFallbackReporter,
    private val log: WildAnalyticsLogger,
) {
    fun routeFor(batch: BatchData) = EndpointRoute(batch, endpointFallback, networkSnapshotProvider, reporter, log)
}
