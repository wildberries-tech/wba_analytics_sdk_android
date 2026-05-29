package ru.wildanalytics.pub.analytics

import io.kotest.common.ExperimentalKotest
import io.kotest.core.config.AbstractProjectConfig

@OptIn(ExperimentalKotest::class)
internal class ProjectConfig : AbstractProjectConfig() {

    init {
        testCoroutineDispatcher = true
    }
}
