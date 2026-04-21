package com.kw.pushgatewayexporter.scheduler

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.kw.pushgatewayexporter.config.AppConfig

/**
 * Manages scheduling of the periodic metrics push job via JobScheduler (API 21+).
 */
class SchedulerManager(private val context: Context) {

    companion object {
        private const val TAG = "SchedulerManager"
        const val JOB_ID = 1001
    }

    private val jobScheduler: JobScheduler
        get() = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    /**
     * Schedule (or reschedule) the periodic push job with the given config.
     */
    fun schedulePeriodicJob(config: AppConfig) {
        cancelJob() // Cancel existing before re-scheduling

        val componentName = ComponentName(context, MetricsJobService::class.java)
        val intervalMs = config.pushIntervalMinutes.toLong() * 60 * 1000

        val builder = JobInfo.Builder(JOB_ID, componentName)
            .setPeriodic(intervalMs)
            .setPersisted(config.persistAcrossReboot)

        if (config.requireUnmeteredNetwork) {
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
        } else {
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        }

        if (config.requireCharging) {
            builder.setRequiresCharging(true)
        }

        val result = jobScheduler.schedule(builder.build())
        if (result == JobScheduler.RESULT_SUCCESS) {
            Log.i(TAG, "Periodic job scheduled: interval=${config.pushIntervalMinutes}min, " +
                "unmetered=${config.requireUnmeteredNetwork}, charging=${config.requireCharging}")
        } else {
            Log.e(TAG, "Failed to schedule periodic job")
        }
    }

    /**
     * Cancel the periodic push job.
     */
    fun cancelJob() {
        jobScheduler.cancel(JOB_ID)
        Log.i(TAG, "Periodic job cancelled")
    }

    /**
     * Check if the job is currently scheduled.
     */
    fun isJobScheduled(): Boolean {
        return jobScheduler.allPendingJobs.any { it.id == JOB_ID }
    }
}
