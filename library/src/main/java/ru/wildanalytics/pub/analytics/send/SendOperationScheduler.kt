package ru.wildanalytics.pub.analytics.send

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import ru.wildanalytics.pub.analytics.logDebug

private const val SEND_OPERATION_JOB_ID = 137138139

internal class SendOperationScheduler(
    private val context: Context,
    private val log: WildAnalyticsLogger
) {

    fun schedule() {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if (scheduler.getPendingJob(SEND_OPERATION_JOB_ID) != null) {
            return
        }
        val jobInfo = JobInfo.Builder(
            SEND_OPERATION_JOB_ID,
            ComponentName(context, SendAllEventsOperationJobService::class.java)
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING)
            .build()
        val result = scheduler.schedule(jobInfo)
        log.logDebug { "schedule ${SendAllAnalyticEventsOperation.NAME} job (scheduled = ${result == 1})" }
    }

    fun cancel() {
        log.logDebug { "cancel ${SendAllAnalyticEventsOperation.NAME} job" }
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        scheduler.cancel(SEND_OPERATION_JOB_ID)
    }
}