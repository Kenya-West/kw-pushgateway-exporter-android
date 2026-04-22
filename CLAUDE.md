# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project purpose

Native Android app that acts as a Prometheus **Pushgateway** exporter for device/OS telemetry. It does **not** expose an HTTP `/metrics` endpoint. Instead, it periodically collects device state via public Android APIs and **pushes** Prometheus text-format metrics to a user-configured Pushgateway URL. Prometheus then scrapes the Pushgateway (designed for `honor_labels: true`).

The canonical specification for scope, metric families, and privacy rules lives in [INITIAL_PROMPT.md](INITIAL_PROMPT.md) — treat it as authoritative when the spec and code diverge.

## Build & run

Single-module Gradle project (`:app`). Built with AGP 8.5.2 / Kotlin 2.0.21 / JVM target 17 / Compose BOM 2024.09.03.

Note: the Gradle wrapper JAR and `gradlew` scripts are **not committed** (only `gradle/wrapper/gradle-wrapper.properties` exists). Use a system Gradle or regenerate the wrapper with `gradle wrapper` before first build.

```powershell
# Windows / pwsh — assume Gradle on PATH or a regenerated wrapper
gradle :app:assembleDebug        # debug APK at app/build/outputs/apk/debug/
gradle :app:assembleRelease      # release (minified via proguard-rules.pro)
gradle :app:installDebug         # install on connected device/emulator
gradle :app:lint                 # Android Lint
gradle :app:compileDebugKotlin   # quickest type-check loop
```

There is **no test source set yet** (`app/src/test/`, `app/src/androidTest/` do not exist). Adding tests is in-scope if requested — the collectors, serializer, and URL builder are all designed to be independently unit-testable.

Min/target/compile SDK: **21 / 34 / 34**. The API 21 floor is a hard contract — see the "API 21 contract" section below.

## Architecture

The app is a pipeline: **Collectors → Serializer → Transport**, driven by either the **Scheduler** (periodic, background) or the **UI** (manual push / dry run / preview). State is persisted in SharedPreferences.

```
┌──────────────┐    ┌──────────────────┐    ┌───────────────────┐    ┌───────────────────┐
│  Collectors  │───▶│ CollectorRegistry│───▶│ PrometheusSerializer├──▶│ PushgatewayClient │
│  (13 impls)  │    │ (per-collector   │    │ (text format, LF,  │    │ (OkHttp, PUT/POST/│
│              │    │  try/catch)      │    │  sorted, escaped) │    │  DELETE, retries) │
└──────────────┘    └──────────────────┘    └───────────────────┘    └───────────────────┘
        ▲                     ▲                                                 │
        │                     │                                                 ▼
        │             ┌───────┴────────┐                                 ┌──────────────┐
        │             │ MetricsJobSvc  │◀────────── JobScheduler ────────│ Pushgateway  │
        │             │ (bg thread)    │                                 └──────────────┘
        │             └────────────────┘
        │                     ▲
        │                     │
┌───────┴────────┐    ┌───────┴────────┐    ┌──────────────────┐
│ AppConfig      │◀───│SchedulerManager│    │ BootReceiver     │
│ ConfigRepo     │    │ (JOB_ID=1001)  │    │ (re-schedule)    │
│ InstanceIdent. │    └────────────────┘    └──────────────────┘
└────────────────┘
```

**Component map** — all under `com.kw.pushgatewayexporter`:

- `PushgatewayExporterApp` — `Application` subclass; lazily constructs and exposes `configRepository`, `instanceIdentity`, `schedulerManager` as singletons; reschedules the job on process start if enabled.
- [model/](app/src/main/java/com/kw/pushgatewayexporter/model/) — `MetricFamily`, `MetricSample`, `MetricType`, `PushResult`. `MetricFamily` enforces that all samples share the family name prefix.
- [collector/](app/src/main/java/com/kw/pushgatewayexporter/collector/) — `Collector` interface + 13 implementations + `CollectorRegistry`. Each collector is independently enabled by an `AppConfig.enableXxx` flag; a failure in one is caught by the registry and does not block the others.
- [serializer/PrometheusSerializer.kt](app/src/main/java/com/kw/pushgatewayexporter/serializer/PrometheusSerializer.kt) — stateless `object`; sorts families by name for reproducibility, uses LF line endings, escapes HELP and label values, integer-collapses whole-number doubles. Also owns `urlEncodeLabelValue` (shared with the transport).
- [transport/PushgatewayClient.kt](app/src/main/java/com/kw/pushgatewayexporter/transport/PushgatewayClient.kt) — OkHttp 4 client. Builds the grouping-key path `/metrics/job/<job>/app_instance/<uuid>{/<k>/<v>}`. Defaults to **PUT** (full-group replace); POST and DELETE supported. Manual exponential backoff retry (it disables OkHttp's built-in retry). Optional insecure-TLS mode for testing. Basic auth + custom headers.
- [scheduler/](app/src/main/java/com/kw/pushgatewayexporter/scheduler/) — `MetricsJobService` (`JobService`, runs work on a raw `Thread`, uses a `@Volatile` flag to prevent overlap), `SchedulerManager` (wraps `JobScheduler`, job id `1001`), `BootReceiver` (reschedules on `BOOT_COMPLETED` when `persistAcrossReboot` is on).
- [config/](app/src/main/java/com/kw/pushgatewayexporter/config/) — `AppConfig` (immutable data class, single source of truth for all user settings) and `ConfigRepository` (SharedPreferences-backed; also stores last-push result and running counters).
- [identity/InstanceIdentity.kt](app/src/main/java/com/kw/pushgatewayexporter/identity/InstanceIdentity.kt) — lazily generates and persists a random UUID; supports rotation which saves the prior UUID so the job can `DELETE` the stale Pushgateway group on the next push.
- [ui/](app/src/main/java/com/kw/pushgatewayexporter/ui/) — Jetpack Compose. `AppNavigation` has 5 routes: `main`, `config`, `preview`, `catalog`, `samples`. `MainViewModel` mirrors `ConfigRepository` state as `MainUiState` and triggers manual pushes.

### Key architectural invariants

1. **Collectors never throw.** The `Collector.collect()` contract is to return `emptyList()` on any failure. `CollectorRegistry.collectAll()` additionally wraps each call in try/catch as a backstop and returns `(families, errors)`. Partial exports are expected and correct.

2. **Prefer sentinel omission over fake values.** When an Android API returns unsupported/sentinel data (e.g. `BatteryManager` properties on a device that doesn't implement them), omit the metric rather than pushing a misleading `0`.

3. **Serialization must be deterministic.** `PrometheusSerializer.serialize` sorts families by name and preserves declaration order within a family. Do not introduce nondeterminism (e.g. iterating a `HashMap` of samples) without fixing the ordering downstream.

4. **PUT is the default push method** — it fully replaces the grouping key's metric set each cycle, which is what Pushgateway retention behavior requires for a "fresh snapshot" exporter. Only use POST if partial replacement is explicitly desired.

5. **Stale-group lifecycle is mandatory.** Pushgateway retains groups forever until deleted. Identity rotation saves the previous UUID so `MetricsJobService` can issue a `DELETE` on the next push. Preserve this when editing either component.

6. **Grouping key = `job` + `app_instance=<installation_uuid>` (+ optional extras).** The first label must be `job`. Never add unbounded-cardinality labels (IPs, timestamps, SSIDs) to the grouping key — put them in the body or gate them behind privacy toggles.

7. **No timestamps in samples.** Pushgateway injects `push_time_seconds` itself; scrape time is the source of truth.

## API 21 contract (hard constraint)

Core functionality must work on **Android 5.0 Lollipop / API level 21** using **public, non-privileged, non-hidden** APIs. Before using any API in the core path, verify its availability at API 21. Specifically:

- Scheduling is **`JobScheduler`**, not WorkManager, not AlarmManager-as-primary. Boot persistence via `setPersisted(true)` + `BootReceiver`.
- Use `Build.SUPPORTED_ABIS`, not the deprecated `Build.CPU_ABI`.
- Do not assume every `BatteryManager.BATTERY_PROPERTY_*` is implemented; several were added later or are OEM-optional.
- Telephony / Wi-Fi / sensor APIs must degrade gracefully when the feature or permission is absent.

Newer APIs may be used only via properly guarded `Build.VERSION.SDK_INT` checks **and** must not be on the critical path.

## Privacy & permissions

Manifest permissions ([AndroidManifest.xml](app/src/main/AndroidManifest.xml)): `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `RECEIVE_BOOT_COMPLETED`. `READ_PHONE_STATE` is intentionally commented out — telephony collectors must degrade when it's absent.

Privacy-sensitive labels (SSID, BSSID, IP addresses, DNS servers, operator numeric codes) are gated on explicit opt-in flags (`AppConfig.enableSensitiveWifiLabels`, `enableSensitiveNetworkLabels`) and default to **off**. Never add a new sensitive label without a corresponding toggle.

Identity must never be sourced from IMEI, IMSI, subscriber ID, or hardware serial. Use the installation UUID from `InstanceIdentity`.

`android:usesCleartextTraffic="true"` is set because users may run a Pushgateway on a LAN-only HTTP endpoint. HTTPS with real certificate validation is the default; `insecureTls` is an explicit opt-in for test environments only.

## Adding a new collector

1. Implement `Collector` under `collector/`. The `collect()` method must not throw.
2. Add an `enableXxx: Boolean = true` field to [AppConfig](app/src/main/java/com/kw/pushgatewayexporter/config/AppConfig.kt), a `KEY_ENABLE_XXX` constant and its read/write pair in [ConfigRepository](app/src/main/java/com/kw/pushgatewayexporter/config/ConfigRepository.kt).
3. Register the `(config.enableXxx to YourCollector(...))` pair in [CollectorRegistry.buildCollectors](app/src/main/java/com/kw/pushgatewayexporter/collector/CollectorRegistry.kt).
4. If the collector needs a new permission, justify it in the manifest comment block.
5. If the UI exposes the toggle, wire it in `ConfigScreen` and `ConfigViewModel`.

Metric names must follow Prometheus conventions: `_bytes`, `_seconds`, `_celsius`, `_volts`/`_millivolts`, `_amperes`/`_microamperes` base units; counters monotonic; gauges free to move; `_info` metrics carry stable labels with value `1`.
