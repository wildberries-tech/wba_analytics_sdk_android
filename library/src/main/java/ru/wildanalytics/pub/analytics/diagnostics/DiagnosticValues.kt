package ru.wildanalytics.pub.analytics.diagnostics

import ru.wildanalytics.pub.analytics.network.NetworkSnapshot

internal fun NetworkSnapshot.toDiagnosticAttributes(): Map<String, String> = buildMap {
    put(DiagnosticAttributes.NETWORK, transports)
    validated?.let { put(DiagnosticAttributes.VALIDATED, it.toString()) }
}

/** Значение атрибута с ограничением длины, чтобы маркер не разрастался из-за текста исключения. */
internal fun String.toAttributeValue(): String = take(MAX_ATTRIBUTE_LENGTH)

private const val MAX_ATTRIBUTE_LENGTH = 200
