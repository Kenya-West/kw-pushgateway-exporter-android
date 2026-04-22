package com.kw.pushgatewayexporter.reliability.oem

/**
 * Static registry of known OEM profiles. Data-driven so routes can be updated without
 * rewriting the logic that consumes them. Order of routes inside a profile is the
 * order the UI presents steps.
 *
 * Everything here is best-effort. Component names and packages change between firmware
 * revisions — we always pair them with manual steps and a generic fallback.
 */
object OemProfileRegistry {

    // -------------------------------------------------------------------------
    // Route ids — stable keys usable as checklist ids / analytics tags.
    // -------------------------------------------------------------------------
    const val ROUTE_AUTOSTART = "autostart"
    const val ROUTE_PROTECTED_APPS = "protected_apps"
    const val ROUTE_BATTERY_MANAGER = "battery_manager"
    const val ROUTE_BACKGROUND_ACTIVITY = "background_activity"
    const val ROUTE_POWER_MANAGER = "power_manager"
    const val ROUTE_STARTUP_MANAGER = "startup_manager"
    const val ROUTE_SLEEPING_APPS = "sleeping_apps"
    const val ROUTE_RECENTS_LOCK = "recents_lock"
    const val ROUTE_UNRESTRICTED_BATTERY = "unrestricted_battery"

    // -------------------------------------------------------------------------
    // Generic fallback — used when no other profile matches.
    // -------------------------------------------------------------------------
    private val GENERIC = OemProfile(
        id = "generic",
        displayName = "Android (generic)",
        matches = emptyList(),
        overview = "This device does not match a known OEM with aggressive background restrictions. " +
            "Keep the exporter exempted from battery optimization (Android 6+) and use the " +
            "foreground service mode if you need strictly uninterrupted pushes.",
        generalTips = listOf(
            "Open the app details page and set battery usage to \"Unrestricted\" if offered.",
            "Consider enabling the persistent foreground service for continuous operation."
        ),
        routes = emptyList()
    )

    private val XIAOMI = OemProfile(
        id = "xiaomi",
        displayName = "Xiaomi / Redmi / POCO (MIUI / HyperOS)",
        matches = listOf("xiaomi", "redmi", "poco"),
        overview = "MIUI / HyperOS aggressively freezes background apps. Three controls typically " +
            "need to be flipped for the exporter: autostart permission, a lock in Recents, and " +
            "\"No restrictions\" battery usage.",
        generalTips = listOf(
            "Open Recents and long-press the exporter card to lock it against \"Clear all\".",
            "In Security app → Battery → App battery saver, set the exporter to \"No restrictions\".",
            "Avoid \"Battery saver\" modes that force-stop apps after screen-off."
        ),
        routes = listOf(
            OemRoute(
                id = ROUTE_AUTOSTART,
                title = "Allow autostart",
                rationale = "Without autostart, MIUI prevents the exporter from starting itself " +
                    "(including after boot, after Wi-Fi reconnect, or from JobScheduler).",
                candidates = listOf(
                    CandidateIntent(
                        label = "MIUI Autostart Management",
                        componentPackage = "com.miui.securitycenter",
                        componentClass = "com.miui.permcenter.autostart.AutoStartManagementActivity",
                        confidence = RouteConfidence.LIKELY
                    ),
                    CandidateIntent(
                        label = "MIUI Security Center main",
                        componentPackage = "com.miui.securitycenter",
                        componentClass = "com.miui.securitycenter.Main",
                        confidence = RouteConfidence.BEST_EFFORT
                    )
                ),
                manualSteps = listOf(
                    "Open the Security app.",
                    "Tap Permissions → Autostart.",
                    "Enable autostart for Pushgateway Exporter."
                )
            ),
            OemRoute(
                id = ROUTE_BATTERY_MANAGER,
                title = "Set battery usage to \"No restrictions\"",
                rationale = "MIUI's per-app battery saver throttles or freezes background apps " +
                    "unless this is set to \"No restrictions\".",
                candidates = listOf(
                    CandidateIntent(
                        label = "MIUI Power / hidden apps",
                        componentPackage = "com.miui.powerkeeper",
                        componentClass = "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
                        confidence = RouteConfidence.BEST_EFFORT
                    ),
                    CandidateIntent(
                        label = "MIUI Battery saver settings",
                        componentPackage = "com.miui.securitycenter",
                        componentClass = "com.miui.powercenter.PowerSettings",
                        confidence = RouteConfidence.BEST_EFFORT
                    )
                ),
                manualSteps = listOf(
                    "Open Settings → Battery & performance → App battery saver " +
                        "(or Security app → Battery).",
                    "Find Pushgateway Exporter and set it to \"No restrictions\"."
                )
            ),
            OemRoute(
                id = ROUTE_RECENTS_LOCK,
                title = "Lock the app in Recents",
                rationale = "MIUI clears unlocked apps from Recents aggressively, which also kills " +
                    "their processes. Locking prevents the swipe-away auto-kill.",
                candidates = emptyList(),
                manualSteps = listOf(
                    "Open the exporter, then open Recents (the multitasking switcher).",
                    "Long-press the exporter card and tap the lock / padlock icon.",
                    "The card now shows a lock and will no longer be cleared by \"Clear all\"."
                )
            )
        )
    )

    private val HUAWEI = OemProfile(
        id = "huawei",
        displayName = "Huawei / Honor (EMUI / HarmonyOS / MagicOS)",
        matches = listOf("huawei", "honor", "hihonor"),
        overview = "EMUI / HarmonyOS use \"Protected apps\" / \"Launch management\" as a gate for " +
            "background execution. Battery-optimization exemption alone is not enough.",
        generalTips = listOf(
            "If the exporter stops after a while, check Phone Manager → Startup manager — the " +
                "device may have auto-revoked the permission.",
            "Avoid Huawei \"Power plan: Ultra\"; it overrides per-app exemptions."
        ),
        routes = listOf(
            OemRoute(
                id = ROUTE_PROTECTED_APPS,
                title = "Add to Protected apps / Launch manager",
                rationale = "\"Protected apps\" keeps the exporter running when the screen is off " +
                    "and after memory pressure cleanup.",
                candidates = listOf(
                    CandidateIntent(
                        label = "Huawei Protected Apps",
                        componentPackage = "com.huawei.systemmanager",
                        componentClass = "com.huawei.systemmanager.optimize.process.ProtectActivity",
                        confidence = RouteConfidence.LIKELY
                    ),
                    CandidateIntent(
                        label = "Huawei Startup Manager (newer EMUI)",
                        componentPackage = "com.huawei.systemmanager",
                        componentClass = "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                        confidence = RouteConfidence.BEST_EFFORT
                    ),
                    CandidateIntent(
                        label = "Huawei Phone Manager home",
                        componentPackage = "com.huawei.systemmanager",
                        componentClass = "com.huawei.systemmanager.mainscreen.MainScreenActivity",
                        confidence = RouteConfidence.BEST_EFFORT
                    )
                ),
                manualSteps = listOf(
                    "Open Phone Manager (or Optimizer).",
                    "Tap App launch (or Startup manager).",
                    "Disable \"Manage automatically\" for Pushgateway Exporter.",
                    "Enable all three toggles: Auto-launch, Secondary launch, Run in background."
                )
            )
        )
    )

    private val SAMSUNG = OemProfile(
        id = "samsung",
        displayName = "Samsung (One UI)",
        matches = listOf("samsung"),
        overview = "One UI ships a \"Device care\" battery stack that puts unused apps into " +
            "\"Sleeping\" or \"Deep sleeping\" buckets. The exporter must be excluded and set to " +
            "\"Unrestricted\" background battery usage.",
        generalTips = listOf(
            "If the app lands in \"Deep sleeping\", it will not run at all until opened manually.",
            "Do not enable \"Put unused apps to sleep\" while using the exporter."
        ),
        routes = listOf(
            OemRoute(
                id = ROUTE_SLEEPING_APPS,
                title = "Remove from Sleeping / Deep sleeping apps",
                rationale = "Samsung Device care suspends apps in these lists; sleeping apps do " +
                    "not run JobScheduler work reliably.",
                candidates = listOf(
                    CandidateIntent(
                        label = "Samsung Battery settings",
                        componentPackage = "com.samsung.android.lool",
                        componentClass = "com.samsung.android.sm.ui.battery.BatteryActivity",
                        confidence = RouteConfidence.BEST_EFFORT
                    ),
                    CandidateIntent(
                        label = "Samsung Device care (SmartManager)",
                        componentPackage = "com.samsung.android.sm_cn",
                        componentClass = "com.samsung.android.sm.ui.battery.BatteryActivity",
                        confidence = RouteConfidence.BEST_EFFORT
                    )
                ),
                manualSteps = listOf(
                    "Open Settings → Device care → Battery → Background usage limits.",
                    "Open Sleeping apps and remove Pushgateway Exporter if listed.",
                    "Open Deep sleeping apps and remove Pushgateway Exporter if listed.",
                    "Open Never sleeping apps → Add → select Pushgateway Exporter."
                )
            ),
            OemRoute(
                id = ROUTE_UNRESTRICTED_BATTERY,
                title = "Set battery usage to Unrestricted",
                rationale = "Even exempted apps are throttled unless set to Unrestricted.",
                candidates = emptyList(),
                manualSteps = listOf(
                    "Open Settings → Apps → Pushgateway Exporter → Battery.",
                    "Tap \"Allow background activity\" and switch to \"Unrestricted\"."
                )
            )
        )
    )

    private val ONEPLUS = OemProfile(
        id = "oneplus",
        displayName = "OnePlus (OxygenOS / ColorOS)",
        matches = listOf("oneplus"),
        overview = "Recent OxygenOS builds share the ColorOS background policy: apps must have " +
            "battery optimization disabled and usually benefit from locking in Recents.",
        generalTips = listOf(
            "Newer OnePlus devices hide background controls under \"Battery optimization\".",
            "Disable \"Advanced optimization\" or \"Deep optimization\" if your build offers it."
        ),
        routes = listOf(
            OemRoute(
                id = ROUTE_BATTERY_MANAGER,
                title = "Disable battery optimization for this app",
                rationale = "OxygenOS applies aggressive doze beyond stock AOSP.",
                candidates = listOf(
                    CandidateIntent(
                        label = "OxygenOS battery optimization list",
                        componentPackage = "com.oneplus.security",
                        componentClass = "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
                        confidence = RouteConfidence.BEST_EFFORT
                    )
                ),
                manualSteps = listOf(
                    "Open Settings → Apps → Pushgateway Exporter → Battery.",
                    "Set to \"Don't optimize\" / \"Unrestricted\"."
                )
            ),
            OemRoute(
                id = ROUTE_RECENTS_LOCK,
                title = "Lock in Recents",
                rationale = "Clearing Recents on OnePlus typically force-stops the process.",
                candidates = emptyList(),
                manualSteps = listOf(
                    "Open Recents, long-press the exporter card, tap the lock icon."
                )
            )
        )
    )

    private val OPPO_REALME = OemProfile(
        id = "oppo",
        displayName = "Oppo / Realme (ColorOS / RealmeUI)",
        matches = listOf("oppo", "realme"),
        overview = "ColorOS exposes three toggles that together determine whether an app can push " +
            "continuously: auto-launch, allow other apps to start it, and run in background.",
        generalTips = listOf(
            "Enabling one toggle is rarely enough — enable all three.",
            "Avoid the \"Super power saving\" mode; it whitelists only the OEM's own apps."
        ),
        routes = listOf(
            OemRoute(
                id = ROUTE_STARTUP_MANAGER,
                title = "Enable auto-launch",
                rationale = "Without auto-launch, JobScheduler callbacks cannot wake the process.",
                candidates = listOf(
                    CandidateIntent(
                        label = "ColorOS Startup Manager",
                        componentPackage = "com.coloros.safecenter",
                        componentClass = "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                        confidence = RouteConfidence.LIKELY
                    ),
                    CandidateIntent(
                        label = "ColorOS Startup Manager (alt)",
                        componentPackage = "com.coloros.safecenter",
                        componentClass = "com.coloros.safecenter.startupapp.StartupAppListActivity",
                        confidence = RouteConfidence.BEST_EFFORT
                    ),
                    CandidateIntent(
                        label = "Oppo Safe main",
                        componentPackage = "com.oppo.safe",
                        componentClass = "com.oppo.safe.permission.startup.StartupAppListActivity",
                        confidence = RouteConfidence.BEST_EFFORT
                    )
                ),
                manualSteps = listOf(
                    "Open Security Center (Phone Manager).",
                    "Privacy Permissions → Startup manager.",
                    "Enable auto-launch for Pushgateway Exporter."
                )
            ),
            OemRoute(
                id = ROUTE_BACKGROUND_ACTIVITY,
                title = "Allow background activity",
                rationale = "Background activity is gated separately from auto-launch on ColorOS.",
                candidates = emptyList(),
                manualSteps = listOf(
                    "Settings → Battery → App battery management → Pushgateway Exporter.",
                    "Enable \"Allow background activity\" and \"Allow foreground activity\".",
                    "Disable \"Optimise battery use\" for this app."
                )
            )
        )
    )

    private val VIVO = OemProfile(
        id = "vivo",
        displayName = "Vivo / iQOO (FuntouchOS / OriginOS)",
        matches = listOf("vivo", "iqoo"),
        overview = "Vivo's i-Manager / iManager controls background activity. Several toggles are " +
            "hidden under High power consumption / Background power consumption settings.",
        routes = listOf(
            OemRoute(
                id = ROUTE_BACKGROUND_ACTIVITY,
                title = "Allow high background power consumption",
                rationale = "Vivo suspends apps flagged as high-drain regardless of battery settings.",
                candidates = listOf(
                    CandidateIntent(
                        label = "iManager background power settings",
                        componentPackage = "com.iqoo.secure",
                        componentClass = "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
                        confidence = RouteConfidence.BEST_EFFORT
                    ),
                    CandidateIntent(
                        label = "Vivo App power management",
                        componentPackage = "com.vivo.permissionmanager",
                        componentClass = "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                        confidence = RouteConfidence.BEST_EFFORT
                    )
                ),
                manualSteps = listOf(
                    "Open iManager / i-Manager.",
                    "App Manager → Autostart manager → enable for Pushgateway Exporter.",
                    "Battery → High background power consumption → enable for this app."
                )
            )
        )
    )

    private val ASUS = OemProfile(
        id = "asus",
        displayName = "Asus (ZenUI)",
        matches = listOf("asus"),
        overview = "ZenUI ships \"Auto-start Manager\" inside Mobile Manager / PowerMaster. Any app " +
            "not explicitly allowed is auto-killed.",
        routes = listOf(
            OemRoute(
                id = ROUTE_AUTOSTART,
                title = "Allow auto-start",
                rationale = "PowerMaster revokes background execution by default.",
                candidates = listOf(
                    CandidateIntent(
                        label = "Asus Auto-start Manager",
                        componentPackage = "com.asus.mobilemanager",
                        componentClass = "com.asus.mobilemanager.autostart.AutoStartActivity",
                        confidence = RouteConfidence.BEST_EFFORT
                    ),
                    CandidateIntent(
                        label = "Asus Mobile Manager",
                        componentPackage = "com.asus.mobilemanager",
                        componentClass = "com.asus.mobilemanager.MainActivity",
                        confidence = RouteConfidence.BEST_EFFORT
                    )
                ),
                manualSteps = listOf(
                    "Open Mobile Manager / PowerMaster.",
                    "Auto-start Manager → enable for Pushgateway Exporter.",
                    "In the same app, disable \"Clean up in background\" for this app."
                )
            )
        )
    )

    private val MOTOROLA = OemProfile(
        id = "motorola",
        displayName = "Motorola / Lenovo (Moto / ZUI)",
        matches = listOf("motorola", "moto", "lenovo"),
        overview = "Motorola's stock-ish UI mostly honors standard Android battery optimization. " +
            "Some newer builds add a \"Background restriction\" switch under app info.",
        routes = listOf(
            OemRoute(
                id = ROUTE_UNRESTRICTED_BATTERY,
                title = "Remove background restriction",
                rationale = "Moto's per-app background restriction will silently block pushes.",
                candidates = emptyList(),
                manualSteps = listOf(
                    "Settings → Apps → Pushgateway Exporter → App battery usage.",
                    "Select \"Unrestricted\" (or equivalent on your build)."
                )
            )
        )
    )

    private val TECNO = OemProfile(
        id = "tecno",
        displayName = "Tecno / Infinix / Itel (HIOS / XOS)",
        matches = listOf("tecno", "infinix", "itel", "transsion"),
        overview = "Transsion devices bundle PowerMaster and \"Smart Panel\" / DuraSpeed utilities " +
            "that kill background apps on screen-off and block push notifications.",
        generalTips = listOf(
            "Disable DuraSpeed entirely if available, or at least whitelist this app.",
            "Avoid \"Screen off cleanup\" / \"Power saving mode\" with this app."
        ),
        routes = listOf(
            OemRoute(
                id = ROUTE_POWER_MANAGER,
                title = "Disable DuraSpeed / Smart Panel for this app",
                rationale = "These vendor utilities kill background apps regardless of OS settings.",
                candidates = emptyList(),
                manualSteps = listOf(
                    "Settings → DuraSpeed → find Pushgateway Exporter → disable acceleration.",
                    "Settings → Battery → App battery usage → select \"Unrestricted\".",
                    "Open Recents, long-press the exporter card, and lock it."
                )
            )
        )
    )

    private val BLACKVIEW = OemProfile(
        id = "blackview",
        displayName = "Blackview / Ulefone / Doogee / rugged ODMs",
        matches = listOf("blackview", "ulefone", "doogee", "oukitel", "cubot"),
        overview = "Low-cost rugged OEMs often reuse MediaTek reference firmware with custom " +
            "\"Power\" or \"Background\" utilities. There are rarely public component names to " +
            "deep-link into, so manual steps are the main path.",
        routes = listOf(
            OemRoute(
                id = ROUTE_POWER_MANAGER,
                title = "Whitelist in the built-in power app",
                rationale = "MTK reference UIs block background pushes when the screen is off.",
                candidates = emptyList(),
                manualSteps = listOf(
                    "Look for an app called Power / PowerSaver / BatteryManager.",
                    "Open it and add Pushgateway Exporter to the protected / whitelisted apps.",
                    "Also open Settings → Apps → Pushgateway Exporter → Battery → Unrestricted."
                )
            )
        )
    )

    private val MEIZU = OemProfile(
        id = "meizu",
        displayName = "Meizu (Flyme)",
        matches = listOf("meizu"),
        overview = "Flyme has \"Security\" → Permissions → Background management that must be set " +
            "to allow for push-style apps.",
        routes = listOf(
            OemRoute(
                id = ROUTE_BACKGROUND_ACTIVITY,
                title = "Allow background management",
                rationale = "Flyme defaults to \"Smart\" which will stop the app when idle.",
                candidates = listOf(
                    CandidateIntent(
                        label = "Meizu Background manager",
                        componentPackage = "com.meizu.safe",
                        componentClass = "com.meizu.safe.permission.PermissionMainActivity",
                        confidence = RouteConfidence.BEST_EFFORT
                    )
                ),
                manualSteps = listOf(
                    "Open Phone Manager / Security.",
                    "Permissions → Background management → Pushgateway Exporter → Allow."
                )
            )
        )
    )

    private val SONY = OemProfile(
        id = "sony",
        displayName = "Sony (Xperia)",
        matches = listOf("sony"),
        overview = "Sony ships close to AOSP but adds \"STAMINA\" and \"Battery optimization\" modes " +
            "that must be bypassed for continuous pushes.",
        routes = listOf(
            OemRoute(
                id = ROUTE_BATTERY_MANAGER,
                title = "Exempt from STAMINA / battery optimization",
                rationale = "STAMINA defers background jobs indefinitely.",
                candidates = emptyList(),
                manualSteps = listOf(
                    "Settings → Battery → STAMINA mode → disable, or add the app as an exception.",
                    "Settings → Apps → Pushgateway Exporter → Battery → Unrestricted."
                )
            )
        )
    )

    private val NOKIA = OemProfile(
        id = "nokia",
        displayName = "Nokia (HMD)",
        matches = listOf("nokia", "hmd"),
        overview = "Nokia / HMD ship Android One or near-stock Android — standard battery " +
            "optimization exemption is usually enough.",
        routes = emptyList()
    )

    // -------------------------------------------------------------------------
    // Ordered list for detection. More specific matches first.
    // -------------------------------------------------------------------------
    private val ALL_PROFILES: List<OemProfile> = listOf(
        XIAOMI, HUAWEI, SAMSUNG, ONEPLUS, OPPO_REALME, VIVO,
        ASUS, MOTOROLA, TECNO, BLACKVIEW, MEIZU, SONY, NOKIA
    )

    /** Returns all known profiles (excluding the generic fallback). */
    fun knownProfiles(): List<OemProfile> = ALL_PROFILES

    /** The generic fallback profile. */
    fun genericProfile(): OemProfile = GENERIC

    /** Find a profile by id, or null. */
    fun byId(id: String): OemProfile? =
        ALL_PROFILES.firstOrNull { it.id == id } ?: if (id == GENERIC.id) GENERIC else null
}
