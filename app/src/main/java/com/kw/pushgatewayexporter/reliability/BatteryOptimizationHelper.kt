package com.kw.pushgatewayexporter.reliability

import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * Thin wrapper around [PowerManager.isIgnoringBatteryOptimizations] with proper API-level
 * guards. On API 21–22 the underlying API does not exist, so we return
 * [OptimizationStatus.NotApplicable] and the UI collapses the step accordingly.
 *
 * Pure enough to unit-test: [classify] is a static function over injected inputs.
 */
object BatteryOptimizationHelper {

    enum class OptimizationStatus {
        /** API 21–22: the Doze / exemption model does not exist yet. */
        NotApplicable,

        /** API 23+, this app is already in the exempt list. */
        Exempt,

        /** API 23+, this app is subject to standard battery optimization. */
        NotExempt,

        /** API 23+, we could not determine status (PowerManager unavailable, etc.). */
        Unknown
    }

    /** Query the live system. Safe on all API levels. */
    fun status(context: Context): OptimizationStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return OptimizationStatus.NotApplicable
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return OptimizationStatus.Unknown
        return try {
            classify(Build.VERSION.SDK_INT, pm.isIgnoringBatteryOptimizations(context.packageName))
        } catch (t: Throwable) {
            OptimizationStatus.Unknown
        }
    }

    /**
     * Pure classification used by [status] and unit tests. On pre-M, [ignoring] is ignored.
     */
    fun classify(sdkInt: Int, ignoring: Boolean?): OptimizationStatus {
        if (sdkInt < Build.VERSION_CODES.M) return OptimizationStatus.NotApplicable
        return when (ignoring) {
            true -> OptimizationStatus.Exempt
            false -> OptimizationStatus.NotExempt
            null -> OptimizationStatus.Unknown
        }
    }

    /** Convenience: is the status actionable (requires user intervention)? */
    fun needsUserAction(status: OptimizationStatus): Boolean =
        status == OptimizationStatus.NotExempt

    /** Convenience: is the concept applicable on this API level? */
    fun isApplicable(status: OptimizationStatus): Boolean =
        status != OptimizationStatus.NotApplicable
}
