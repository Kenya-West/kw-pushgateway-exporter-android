package com.kw.pushgatewayexporter.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kw.pushgatewayexporter.PushgatewayExporterApp

/**
 * Receives BOOT_COMPLETED broadcast to re-schedule the periodic push job and, when
 * enabled, restart the foreground reliability service.
 *
 * The heavy lifting lives in [com.kw.pushgatewayexporter.reliability.BootRecoveryCoordinator]
 * so that boot recovery and cold-start recovery share the same path.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        try {
            val app = context.applicationContext as? PushgatewayExporterApp ?: return
            Log.i(TAG, "BOOT_COMPLETED received")
            app.bootRecoveryCoordinator.runAfterBoot()
        } catch (e: Exception) {
            Log.e(TAG, "Boot recovery failed", e)
        }
    }
}
