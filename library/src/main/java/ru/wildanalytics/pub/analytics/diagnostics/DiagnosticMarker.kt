package ru.wildanalytics.pub.analytics.diagnostics

internal enum class DiagnosticMarker(
    val id: String,
    val level: Level,
) {
    QUEUE_EVICTION(id = "wba_queue_eviction", level = Level.ERROR),
    INSERT_FAILED(id = "wba_insert_failed", level = Level.ERROR),
    SEND_FAILED(id = "wba_send_failed", level = Level.WARN),
    ENDPOINT_FALLBACK(id = "wba_endpoint_fallback", level = Level.ERROR),
    SEND_LOOP_RESTARTED(id = "wba_send_loop_restarted", level = Level.WARN),
    ;

    enum class Level { WARN, ERROR }
}
