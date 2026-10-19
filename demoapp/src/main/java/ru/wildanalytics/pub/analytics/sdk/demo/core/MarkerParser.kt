package ru.wildanalytics.pub.analytics.sdk.demo.core

/** Разбор сообщений SDK вида `[wba_marker] текст key=value, key=value`. */
object MarkerParser {

    fun parse(message: String, details: Map<String, String> = emptyMap()): ParsedMarker {
        val marker = MARKER.find(message)
        val body = marker?.let { message.substring(it.range.last + 1) } ?: message
        val attributes = ATTRIBUTE.findAll(body).associate { it.groupValues[1] to it.groupValues[2] } + details
        val text = ATTRIBUTE.find(body)?.let { body.substring(0, it.range.first) } ?: body
        return ParsedMarker(
            name = marker?.groupValues?.get(1),
            text = text.trim().trimEnd(','),
            attributes = attributes,
        )
    }

    fun shortName(marker: String): String = marker.removePrefix(MARKER_PREFIX)
}

data class ParsedMarker(
    val name: String?,
    val text: String,
    val attributes: Map<String, String>,
)

private const val MARKER_PREFIX = "wba_"
private val MARKER = Regex("""^\s*\[(wba_[a-z0-9_]+)]\s*""")
private val ATTRIBUTE = Regex("""\b([a-z_]+)=([^,\s]+)""")
