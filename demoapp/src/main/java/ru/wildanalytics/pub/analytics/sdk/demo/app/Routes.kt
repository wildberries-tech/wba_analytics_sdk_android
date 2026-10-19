package ru.wildanalytics.pub.analytics.sdk.demo.app

import kotlinx.serialization.Serializable
import ru.wildanalytics.pub.analytics.sdk.demo.qa.QaSuite

@Serializable
data object HomeRoute

@Serializable
data object EventsRoute

@Serializable
data object SdkConfigRoute

@Serializable
data object EndpointRoute

@Serializable
data object DiagnosticsRoute

@Serializable
data object QaHubRoute

@Serializable
data object FallbackQaRoute

@Serializable
data object LossQaRoute

@Serializable
data class RunLogRoute(val suite: QaSuite)
