package ru.wildanalytics.pub.analytics.sdk.demo.app

import android.app.Application
import ru.wildanalytics.pub.analytics.analyticsCustomLogger
import ru.wildanalytics.pub.analytics.sdk.demo.qa.QaSuite

class DemoApplication : Application() {

    lateinit var container: AppContainer
        private set

    private var resumedSuite: QaSuite? = null

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        analyticsCustomLogger = container.logger
        container.periodicLogger
        resumedSuite = container.resumePending()
    }

    /** Набор, прогон которого продолжился после перезапуска; отдаётся один раз. */
    fun takeResumedSuite(): QaSuite? = resumedSuite.also { resumedSuite = null }
}
