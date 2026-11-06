package ru.wildberries.pub.analytics.diagnostics

import ru.wildberries.pub.analytics.WBAnalytics2Logger

internal class DiagnosticsReporter(
    private val log: WBAnalytics2Logger,
    private val sdkVersion: String,
) {

    fun report(
        marker: DiagnosticMarker,
        message: String,
        attributes: Map<String, String> = emptyMap(),
        throwable: Throwable? = null,
    ) {
        try {
            if (!log.isEnabled) return
            val text = "[${marker.id}] $message"
            val fullAttributes = attributes + (DiagnosticAttributes.SDK_VERSION to sdkVersion)
            when (marker.level) {
                DiagnosticMarker.Level.ERROR -> log.logError(Error(text, throwable), fullAttributes)
                DiagnosticMarker.Level.WARN -> log.logWarn(
                    "$text " + fullAttributes.entries.joinToString(separator = ", ") { (key, value) -> "$key=$value" }
                )
            }
        } catch (_: Exception) {
        }
    }
}
