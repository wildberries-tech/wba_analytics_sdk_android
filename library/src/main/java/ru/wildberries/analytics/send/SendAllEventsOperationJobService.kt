package ru.wildberries.analytics.send

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.wildberries.analytics.CoroutineScopeFactory
import ru.wildberries.analytics.WBAnalytics2Logger
import ru.wildberries.analytics.WBAnalytics2ServiceLocator
import ru.wildberries.analytics.logDebug

internal class SendAllEventsOperationJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val wbAnalytics2Locator = WBAnalytics2ServiceLocator.getInstance(this)
        val operation = wbAnalytics2Locator.get<SendAllAnalyticEventsOperation>()
        val scope = wbAnalytics2Locator.get<CoroutineScopeFactory>()
            .create(SendAllEventsOperationJobService::class.simpleName.orEmpty())
        val log = wbAnalytics2Locator.get<WBAnalytics2Logger>()
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