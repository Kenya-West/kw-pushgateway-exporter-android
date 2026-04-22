package com.kw.pushgatewayexporter.reliability.oem

/**
 * Confidence that a candidate intent / step actually exists on the current device.
 * OEM deep links are unofficial — we never guarantee them, we only try them.
 */
enum class RouteConfidence {
    /** Documented or widely observed to exist on this OEM family at some firmware revision. */
    LIKELY,

    /** Reported on forums / community knowledge; no guarantees. */
    BEST_EFFORT,

    /** Last-resort generic fallback (package details / app info). */
    FALLBACK
}

/**
 * A single candidate for opening a settings / OEM page.
 * Consumers try these in order and stop at the first one that resolves.
 *
 * One of [componentPackage]+[componentClass], [action], or [uri] must be populated.
 */
data class CandidateIntent(
    /** Human label for logs and diagnostics. */
    val label: String,

    /** Explicit package for a component-based intent, or an action that needs package targeting. */
    val componentPackage: String? = null,

    /** Explicit class name for a component-based intent. */
    val componentClass: String? = null,

    /** Android intent action (e.g. [android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS]). */
    val action: String? = null,

    /** Optional URI string for the intent (e.g. "package:<package>"). */
    val uri: String? = null,

    /** Extras to attach. String-valued only — kept simple to stay serializable. */
    val extras: Map<String, String> = emptyMap(),

    /** Confidence this works on the current device. */
    val confidence: RouteConfidence = RouteConfidence.BEST_EFFORT
)

/**
 * A named navigation target inside an OEM profile — e.g. "autostart", "battery_manager",
 * "background_activity". Holds ordered candidate intents and human-readable fallback steps.
 */
data class OemRoute(
    val id: String,
    val title: String,
    val rationale: String,
    val candidates: List<CandidateIntent>,
    val manualSteps: List<String>
)

/**
 * Aggregate OEM profile. Keyed by manufacturer / brand heuristics.
 *
 * Always pair route attempts with human-readable [manualSteps] — if every candidate fails,
 * we display the manual steps and move on. Never dead-end.
 */
data class OemProfile(
    /** Short identifier used in logs and preferences (e.g. "xiaomi", "huawei", "generic"). */
    val id: String,

    /** Human name for UI headings. */
    val displayName: String,

    /** Lower-cased manufacturer / brand / rom substrings this profile matches. */
    val matches: List<String>,

    /** Routes specific to this OEM (autostart, protected apps, background activity, etc.). */
    val routes: List<OemRoute>,

    /** General guidance shown at the top of the OEM section. */
    val overview: String,

    /** Extra misc tips (lock in recents, disable cleanup, etc.). */
    val generalTips: List<String> = emptyList()
)
