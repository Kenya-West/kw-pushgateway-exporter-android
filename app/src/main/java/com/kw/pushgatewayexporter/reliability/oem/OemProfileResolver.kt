package com.kw.pushgatewayexporter.reliability.oem

import android.os.Build

/**
 * Resolves the current device to an [OemProfile] using [Build] heuristics.
 *
 * Pure function of its inputs so it can be unit-tested without Android runtime.
 */
object OemProfileResolver {

    /**
     * Resolve the best-matching profile from manufacturer / brand / fingerprint hints.
     *
     * Matching is case-insensitive. Returns [OemProfileRegistry.genericProfile] when no
     * profile's [OemProfile.matches] substring appears in any hint.
     */
    fun resolve(
        manufacturer: String? = Build.MANUFACTURER,
        brand: String? = Build.BRAND,
        fingerprint: String? = Build.FINGERPRINT,
        hardware: String? = Build.HARDWARE
    ): OemProfile {
        val haystack = buildString {
            listOf(manufacturer, brand, fingerprint, hardware).forEach {
                if (!it.isNullOrBlank()) {
                    append(it.lowercase())
                    append('|')
                }
            }
        }
        if (haystack.isEmpty()) return OemProfileRegistry.genericProfile()

        return OemProfileRegistry.knownProfiles().firstOrNull { profile ->
            profile.matches.any { needle -> haystack.contains(needle) }
        } ?: OemProfileRegistry.genericProfile()
    }
}
