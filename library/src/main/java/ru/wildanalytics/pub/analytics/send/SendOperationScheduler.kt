package ru.wildanalytics.pub.analytics.send

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import ru.wildanalytics.pub.analytics.logDebug

private const val SEND_OPERATION_JOB_ID = 137138139

internal class SendOperationScheduler(
    private val context: Context,
    private val log: WildAnalyticsLogger
) {

    fun schedule() {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if (scheduler.allPendingJobs.any { it.id == SEND_OPERATION_JOB_ID }) {
            return
        }
        val networkType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            JobInfo.NETWORK_TYPE_NOT_ROAMING
        } else {
            JobInfo.NETWORK_TYPE_ANY
        }
        val jobInfo = JobInfo.Builder(
            SEND_OPERATION_JOB_ID,
            ComponentName(context, SendAllEventsOperationJobService::class.java)
        )
            .setRequiredNetworkType(networkType)
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