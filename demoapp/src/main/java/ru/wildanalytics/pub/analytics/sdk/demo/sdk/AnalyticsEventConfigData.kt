package ru.wildanalytics.pub.analytics.sdk.demo.sdk

import kotlinx.serialization.Serializable

@Serializable
data class AnalyticsEventConfigData(
    val counterId: Long = 0,
    val eventName: String = DEFAULT_EVENT_NAME,
    val eventValue: String = DEFAULT_EVENT_VALUE,
    val eventsCount: Int = 1,
    val isImportant: Boolean = false,
    val commonParameters: Map<String, String> = emptyMap(),
    val periodicLoggingIntervalSeconds: Int = 0,
)

private const val DEFAULT_EVENT_NAME = "fake_event"
private val DEFAULT_EVENT_VALUE = """
    {
        "fakeInt":1,
        "fakeString":"string",
        "fakeDouble":0.0
    }
""".trimIndent()
