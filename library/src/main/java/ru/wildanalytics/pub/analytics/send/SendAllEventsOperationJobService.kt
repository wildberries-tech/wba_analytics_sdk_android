package ru.wildanalytics.pub.analytics.send

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.wildanalytics.pub.analytics.CoroutineScopeFactory
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import ru.wildanalytics.pub.analytics.WildAnalyticsServiceLocator
import ru.wildanalytics.pub.analytics.logDebug

internal class SendAllEventsOperationJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val wildAnalyticsLocator = WildAnalyticsServiceLocator.getInstance(this)
        val operation = wildAnalyticsLocator.get<SendAllAnalyticEventsOperation>()
        val scope = wildAnalyticsLocator.get<CoroutineScopeFactory>()
            .create(SendAllEventsOperationJobService::class.simpleName.orEmpty())
        val log = wildAnalyticsLocator.get<WildAnalyticsLogger>()
        log.logDebug { "${SendAllAnalyticEventsOperation.NAME} job started" }
        scope.launch {
            try {
                operation.execute()
                log.logDebug { "${SendAllAnalyticEventsOperation.NAME} job finished" }
                jobFinished(params, false)
            } catch (exception: CancellationException) {
                throw exception
            } catch (error: Error) {
                log.logError(error, emptyMap())
            } catch (exception: Exception) {
                log.logException(exception)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        return true
    }
}