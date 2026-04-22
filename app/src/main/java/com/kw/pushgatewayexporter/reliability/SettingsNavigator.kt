package com.kw.pushgatewayexporter.reliability

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.kw.pushgatewayexporter.reliability.oem.CandidateIntent

/**
 * Launches system settings pages and OEM-specific settings activities with robust
 * fallback. Never throws — all failures return a [Result.Failure] and log the attempt.
 *
 * Design rules:
 *  - Always build a full intent first, then call [PackageManager.resolveActivity] before
 *    trying [Context.startActivity].
 *  - For every "official" target we also keep a generic fallback chain so the user never
 *    dead-ends.
 *  - Background context: callers launch from an Activity (recommended). If we ever launch
 *    from an Application context we add [Intent.FLAG_ACTIVITY_NEW_TASK] to avoid crashes.
 */
class SettingsNavigator(
    private val context: Context,
    private val log: ReliabilityLog
) {

    sealed class Result {
        data class Success(val label: String) : Result()
        data class Failure(val label: String, val reason: String) : Result()
    }

    private val packageName: String get() = context.packageName

    // -------------------------------------------------------------------------
    // Official Android entry points
    // -------------------------------------------------------------------------

    /** Opens the app details settings page for this app. Works on API 21+. */
    fun openAppDetails(): Result {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        return launch("app_details", intent)
    }

    /**
     * Opens the global "Ignore battery optimization" list (API 23+). On earlier APIs this
     * constant does not exist at runtime and the call falls back to app details.
     */
    fun openIgnoreBatteryOptimizationList(): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            log.w(TAG, "Battery optimization list unavailable on API ${Build.VERSION.SDK_INT}; falling back to app details")
            return openAppDetails()
        }
        val action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        val intent = Intent(action)
        return launch("ignore_battery_optimization_list", intent)
    }

    /**
     * Opens the "request" dialog that asks the user to exempt this app from battery
     * optimization. This intent is technically restricted to apps that legitimately need
     * continuous background work — for an exporter, that gate is satisfied.
     *
     * On API < 23 this falls back to app details.
     */
    @android.annotation.SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimization(): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return openAppDetails()
        }
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        return launch("request_ignore_battery_optimization", intent)
    }

    /** Opens the main settings app — last-resort fallback. */
    fun openGenericSettings(): Result =
        launch("generic_settings", Intent(Settings.ACTION_SETTINGS))

    /**
     * Attempts the platform's "Battery" settings page if it exists. Falls back to app details.
     * This target is widely supported but not guaranteed.
     */
    fun openBatterySaverSettings(): Result {
        val intent = Intent("android.settings.BATTERY_SAVER_SETTINGS")
        return if (resolves(intent)) launch("battery_saver_settings", intent)
        else openAppDetails()
    }

    // -------------------------------------------------------------------------
    // OEM / candidate-based navigation
    // -------------------------------------------------------------------------

    /**
     * Try the given candidate intents in order. Returns the first success, or a composite
     * failure describing what was attempted.
     *
     * If every candidate fails and [finalFallbackToAppDetails] is true (default), opens the
     * generic app details page as a last resort.
     */
    fun tryCandidates(
        candidates: List<CandidateIntent>,
        finalFallbackToAppDetails: Boolean = true
    ): Result {
        val attempts = StringBuilder()
        for (candidate in candidates) {
            val intent = buildIntent(candidate)
            if (intent == null) {
                attempts.append("[${candidate.label}: malformed] ")
                continue
            }
            if (!resolves(intent)) {
                log.d(TAG, "OEM candidate not resolvable: ${candidate.label}")
                attempts.append("[${candidate.label}: not resolvable] ")
                continue
            }
            val result = launch("oem:${candidate.label}", intent)
            if (result is Result.Success) return result
            attempts.append("[${candidate.label}: ${(result as Result.Failure).reason}] ")
        }
        log.w(TAG, "All OEM candidates failed: $attempts")
        if (finalFallbackToAppDetails) {
            val fallback = openAppDetails()
            return when (fallback) {
                is Result.Success -> Result.Success("app_details (fallback)")
                is Result.Failure -> Result.Failure("all_candidates", "attempts: $attempts; fallback: ${fallback.reason}")
            }
        }
        return Result.Failure("all_candidates", "attempts: $attempts")
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private fun buildIntent(c: CandidateIntent): Intent? {
        val intent = when {
            c.componentPackage != null && c.componentClass != null -> Intent().apply {
                component = ComponentName(c.componentPackage, c.componentClass)
                if (c.action != null) action = c.action
            }
            c.action != null -> Intent(c.action)
            else -> return null
        }
        if (!c.uri.isNullOrBlank()) {
            runCatching { intent.data = Uri.parse(c.uri) }
        }
        for ((k, v) in c.extras) intent.putExtra(k, v)
        return intent
    }

    private fun resolves(intent: Intent): Boolean {
        return try {
            context.packageManager.resolveActivity(intent, 0) != null
        } catch (t: Throwable) {
            false
        }
    }

    private fun launch(label: String, intent: Intent): Result {
        // Ensure we can launch from any context.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!resolves(intent)) {
            log.w(TAG, "No activity resolves for $label")
            return Result.Failure(label, "no activity resolves")
        }
        return try {
            context.startActivity(intent)
            log.i(TAG, "Launched settings: $label")
            Result.Success(label)
        } catch (e: ActivityNotFoundException) {
            log.w(TAG, "ActivityNotFound for $label: ${e.message}")
            Result.Failure(label, "ActivityNotFoundException")
        } catch (e: SecurityException) {
            // Some OEM activities are not exported publicly and will SecurityException us.
            log.w(TAG, "SecurityException launching $label: ${e.message}")
            Result.Failure(label, "SecurityException")
        } catch (t: Throwable) {
            log.e(TAG, "Unexpected error launching $label", t)
            Result.Failure(label, t.javaClass.simpleName)
        }
    }

    companion object {
        private const val TAG = "SettingsNavigator"
    }
}
