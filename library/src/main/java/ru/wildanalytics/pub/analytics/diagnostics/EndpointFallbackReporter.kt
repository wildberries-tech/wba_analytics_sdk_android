package ru.wildanalytics.pub.analytics.diagnostics

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.wildanalytics.pub.analytics.send.HttpStatusException
import ru.wildanalytics.pub.analytics.send.fallback.FallbackSwitch

/** Маркер [DiagnosticMarker.ENDPOINT_FALLBACK] о переключении на резервный URL. */
internal class EndpointFallbackReporter(private val reporter: DiagnosticsReporter) {

    fun report(switch: FallbackSwitch) {
        reporter.report(
            marker = DiagnosticMarker.ENDPOINT_FALLBACK,
            message = "Sending switched to fallback URL until process restart",
            attributes = buildMap {
                put(DiagnosticAttributes.PRIMARY_HOST, switch.primaryUrl.toHttpUrlOrNull()?.host.orEmpty())
                put(DiagnosticAttributes.FALLBACK_HOST, switch.fallbackUrl.toHttpUrlOrNull()?.host.orEmpty())
                (switch.cause as? HttpStatusException)?.let { put(DiagnosticAttributes.CODE, it.code.toString()) }
                put(DiagnosticAttributes.EXCEPTION, switch.cause.javaClass.name)
                switch.cause.message?.let { put(DiagnosticAttributes.ERROR, it.toAttributeValue()) }
                put(DiagnosticAttributes.ATTEMPT, switch.attempt.toString())
                put(DiagnosticAttributes.BATCH_NUMBER, switch.batchNumber)
                putAll(switch.network.toDiagnosticAttributes())
            },
            throwable = switch.cause,
        )
    }
}
