# Background reliability subsystem

This document describes the `com.kw.pushgatewayexporter.reliability` package: what it
does, why it exists, and how to maintain it.

## Why this exists

Pushgateway exporter is a *push-style* exporter — if the device stops running the app in
the background, metrics go silent. On stock AOSP that is rarely a problem, but:

- Android 6 (API 23) introduced Doze, which defers background work when the device is idle.
- OEM skins (MIUI, EMUI, OneUI, ColorOS, FuntouchOS, …) add their own layers that
  **freeze** or **force-stop** apps the OS itself would leave running.

A non-privileged app cannot fully override these behaviors. The best it can do is:

1. Ask the user to complete the device-specific settings dance.
2. Use every official API that does help (foreground service, battery-optimization
   exemption, boot persistence, JobScheduler).
3. Empirically verify the result and keep reminding the user if anything regresses.

That is what this subsystem implements.

## Module map

```
reliability/
├── oem/
│   ├── OemProfile.kt                Data model
│   ├── OemProfileRegistry.kt        Static registry of known OEM routes + manual steps
│   └── OemProfileResolver.kt        Build.MANUFACTURER/BRAND → profile
├── BatteryOptimizationHelper.kt     API 23+ exemption check (pure classify() for tests)
├── SettingsNavigator.kt             Launch system + OEM settings with robust fallback
├── ReliabilityLog.kt                In-process ring buffer persisted to SharedPrefs
├── ReliabilityPreferences.kt        Durable user-facing reliability state
├── ReliabilityChecklist.kt          Step model + pure status classification
├── ReliabilityManager.kt            Top-level facade wired up by the Application class
├── ExporterForegroundService.kt     Persistent foreground service push loop
├── ForegroundServiceController.kt   Start/stop/isRunning
├── BootRecoveryCoordinator.kt       Post-boot & cold-start recovery + inconsistency scan
├── ReliabilityTestRunner.kt         Self-test framework
└── DiagnosticsReporter.kt           Plain-text diagnostics bundle
```

UI:

```
ui/screens/
├── ReliabilityScreen.kt             Checklist wizard
├── ReliabilityTestScreen.kt         Self-test launcher
└── DiagnosticsScreen.kt             Copyable diagnostics report

ui/viewmodel/
└── ReliabilityViewModel.kt          Shared VM for the three screens
```

Entry points from the rest of the app:

- `PushgatewayExporterApp.reliabilityManager` — constructed once in `onCreate`.
- `BootReceiver` → `BootRecoveryCoordinator.runAfterBoot()`.
- `MetricsJobService` calls `reliabilityPreferences.markPushTimestamp()` on every
  successful push so reminder heuristics and the self-test can see run progress.

## API 21 vs API 23+ behavior

| Concern                     | API 21–22                              | API 23+                                          |
|-----------------------------|----------------------------------------|--------------------------------------------------|
| Doze / app standby          | Does not exist                         | Present; exemption required                      |
| `isIgnoringBatteryOptimizations` | API does not exist               | Used directly; wrapped in `BatteryOptimizationHelper.status` |
| `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` | Absent                 | Opens the system list                            |
| `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Absent                 | Prompts the user; app must have legitimate use case |
| JobScheduler                | Available (baseline)                   | Available                                        |
| Boot persistence            | `setPersisted(true)` + `BOOT_COMPLETED` | Same                                            |
| Foreground service          | `Service.startForeground`              | Same; API 26+ requires `startForegroundService` then `startForeground`; API 29+ typed; API 34+ requires `foregroundServiceType` declaration |
| Notification channel        | N/A                                    | API 26+ required                                 |

All version-specific paths are gated with `Build.VERSION.SDK_INT` or `tools:targetApi`
in the manifest. `BatteryOptimizationHelper.classify` is intentionally a pure function
(injected `sdkInt`) so the behavior is unit-tested for both pre-M and post-M paths.

## What is verifiable vs. best-effort

The `VerificationKind` enum classifies each checklist step:

- **DIRECT** — the OS tells us. Battery optimization (API 23+), foreground service running,
  scheduling persistence flag, JobScheduler registration.
- **INDIRECT** — we infer from side effects. The self-test counts observed push runs
  against expected runs.
- **USER_CONFIRMED** — no public read API exists. OEM autostart toggles, protected-app
  lists, vendor battery manager allowlists. We show the user a "Mark as done" chip and
  invalidate the confirmation if the detected OEM profile changes.
- **NOT_APPLICABLE** — API level / device does not expose the concept.

**We never claim OEM steps are "verified".** The diagnostics report shows the verification
kind per step so users (and maintainers) can tell the difference.

## Updating OEM routes

OEM component names change between firmware revisions. Routes are **data**, not code —
update `OemProfileRegistry.kt` and ship.

When you learn a new candidate works on firmware X:

1. Add it to the route's `candidates` list.
2. Set `confidence = LIKELY` only if you've confirmed on at least one recent device;
   otherwise `BEST_EFFORT`.
3. Always keep `manualSteps` current — the user sees those when every candidate fails.
4. Do **not** remove old candidates unless you've verified they crash or are gone;
   `PackageManager.resolveActivity` simply skips unresolvable ones.
5. Add the profile id to `OemProfileRegistryTest` to keep the duplicate-id invariant.

New OEM profiles:

1. Add an `OemProfile` constant to `OemProfileRegistry`.
2. Append to `ALL_PROFILES` (ordering matters — put more specific matches first if any
   prefix collisions exist).
3. Ensure `matches` uses lowercase substrings from `Build.MANUFACTURER` /
   `Build.BRAND` / `Build.FINGERPRINT`.
4. Add a resolver test.

## Self-test semantics

`ReliabilityTestRunner` runs one of five modes and records:

- `expectedRuns` — duration ÷ interval.
- `observedRuns` — delta of `configRepository.getPushAttemptsTotal()` during the window.
- `outcome` — `PASSED` if ≥ expected, `PARTIAL` if ≥ 50 %, `FAILED` otherwise.

The runner temporarily overrides the exporter interval (e.g. 1 minute for SHORT mode) and
restores the user's original config on completion. If the runner crashes or the user
cancels, the original config must still be restored — that's why `cancel()` and `finish()`
both run through the same prefs/scheduler restoration path.

**Limitations** — a self-test run is one device, one firmware, one moment in time.
Users should re-run it after any system update.

## Foreground service design

- Channel `exporter_foreground`, importance LOW (minimal pings).
- Service is `START_STICKY`; Android restarts it on process death (OEM permitting).
- Single-threaded worker loop with a `@Volatile stopRequested` flag and `Thread.sleep`
  for the interval. Kotlin coroutines were deliberately **not** used here — the loop
  needs to survive without a scope that might get cancelled by process restart.
- When the user enables foreground mode, the JobScheduler job is **not** cancelled — it
  remains as a belt-and-braces backup. The service pulls its own interval from the same
  config, so both mechanisms target the same Pushgateway with consistent grouping.
- On API 34+ the manifest declares `foregroundServiceType="dataSync"`. This is the
  correct type for periodic external syncs; if Google ever requires a stricter type,
  update the manifest and any runtime `startForeground(id, notification, type)` calls.

## Diagnostics

The diagnostics report includes the OEM profile id, device build info, per-step status,
and the last ~100 log entries. It deliberately **excludes** the Pushgateway URL,
credentials, and any network-identifier labels — users often paste these into public
issue trackers.

If you add a new piece of reliability state that's useful for debugging, add it to
`DiagnosticsReporter.build()` rather than logging it ad-hoc.

## Testing

- JVM unit tests under `app/src/test/`:
  - `OemProfileResolverTest` — detection matrix.
  - `OemProfileRegistryTest` — well-formedness invariants (every route has a launch
    hint or manual fallback, ids are unique, etc.).
  - `BatteryOptimizationHelperTest` — classify() across API levels.
  - `ReliabilityChecklistTest` — status classification + step composition.
  - `SettingsNavigatorFallbackTest` — data-model invariants.
- There are no integration tests yet. When you add Robolectric, the priority targets
  are `SettingsNavigator.launch` with a mocked `PackageManager.resolveActivity` and
  `BootRecoveryCoordinator.detectInconsistencies` with a fake app.

Run `gradle :app:testDebugUnitTest`.

## Honest limits

- On severely-locked OEM firmware, the user **must** complete the OEM checklist — there
  is no programmatic workaround.
- The self-test only tells you about *this* boot. After a reboot, an OS update, or a
  user-installed battery saver app, retest.
- We never silently restart background work indefinitely. When foreground mode is on,
  the persistent notification tells the user the app is active and why.
