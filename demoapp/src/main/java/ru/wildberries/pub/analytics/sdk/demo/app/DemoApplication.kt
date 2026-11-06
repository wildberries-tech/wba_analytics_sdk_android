package ru.wildberries.pub.analytics.sdk.demo.app

import android.app.Application
import ru.wildberries.pub.analytics.sdk.demo.qa.QaSuite
import ru.wildberries.pub.analytics.wbAnalytics2CustomLogger

class DemoApplication : Application() {

    lateinit var container: AppContainer
        private set

    private var resumedSuite: QaSuite? = null

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        wbAnalytics2CustomLogger = container.logger
        container.periodicLogger
        resumedSuite = container.resumePending()
    }

    /** Набор, прогон которого продолжился после перезапуска; отдаётся один раз. */
    fun takeResumedSuite(): QaSuite? = resumedSuite.also { resumedSuite = null }
}
