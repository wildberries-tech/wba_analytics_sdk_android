package ru.wildanalytics.pub.analytics.sdk.demo.qa

import androidx.annotation.StringRes
import ru.wildanalytics.pub.analytics.sdk.demo.qa.fallback.FallbackScenario
import ru.wildanalytics.pub.analytics.sdk.demo.qa.loss.LossScenario

@StringRes
fun QaSuite.scenarioTitle(name: String): Int? = when (this) {
    QaSuite.FALLBACK -> FallbackScenario.entries.firstOrNull { it.name == name }?.title
    QaSuite.LOSS -> LossScenario.entries.firstOrNull { it.name == name }?.title
}
