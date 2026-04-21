package com.kw.pushgatewayexporter.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kw.pushgatewayexporter.PushgatewayExporterApp

/**
 * Receives BOOT_COMPLETED broadcast to re-schedule the periodic push job
 * after device reboot, if the user had scheduling enabled.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        try {
            val app = context.applicationContext as? PushgatewayExporterApp ?: return
            val config = app.configRepository.getConfig()

            if (config.schedulingEnabled && config.persistAcrossReboot) {
                Log.i(TAG, "Device booted, re-scheduling push job")
                app.schedulerManager.schedulePeriodicJob(config)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to re-schedule after boot", e)
        }
    }
}
