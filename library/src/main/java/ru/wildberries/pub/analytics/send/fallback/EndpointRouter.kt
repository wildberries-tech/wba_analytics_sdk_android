package ru.wildberries.pub.analytics.send.fallback

import ru.wildberries.pub.analytics.WBAnalytics2Logger
import ru.wildberries.pub.analytics.batch.BatchData
import ru.wildberries.pub.analytics.diagnostics.EndpointFallbackReporter
import ru.wildberries.pub.analytics.network.NetworkSnapshotProvider

/** Создаёт [EndpointRoute] для каждого батча. */
internal class EndpointRouter(
    private val endpointFallback: EndpointFallback,
    private val networkSnapshotProvider: NetworkSnapshotProvider,
    private val reporter: EndpointFallbackReporter,
    private val log: WBAnalytics2Logger,
) {
    fun routeFor(batch: BatchData) = EndpointRoute(batch, endpointFallback, networkSnapshotProvider, reporter, log)
}
