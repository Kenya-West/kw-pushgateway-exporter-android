# 📱 Android Prometheus Pushgateway Exporter

![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg?style=flat)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Android-success)
![Gradle](https://img.shields.io/badge/Gradle-8.5.2-02303A)

A native Android application that acts as a **Prometheus Pushgateway** exporter for device and OS telemetry. 

Unlike traditional host exporters, this app **does not** expose an HTTP `/metrics` endpoint. Instead, it periodically collects device state via public Android APIs and **pushes** Prometheus text-format metrics to a user-configured Pushgateway URL. 

Prometheus then scrapes the Pushgateway (designed for `honor_labels: true`), creating a seamless bridge between your device fleets and your monitoring stack.

---

## Features

- **Push-Model Architecture:** Securely pushes metrics outward instead of requiring incoming network exposure.
- **Robust Background Execution:** Uses `JobScheduler` for periodic, reliable data collection that persists across reboots.
- **Pushgateway Lifecycle Semantics:** Automatically manages grouping keys (e.g., `job` and `app_instance`), with intelligent `PUT` replacement rules and `DELETE` hooks for cleaning up stale instance identifiers.
- **Deterministic Output:** Generates perfectly sorted, LF-terminated Prometheus exposition formats to ensure metric reproducibility and system compatibility.
- **Extensive Collectors:** Offers 13 extensible collectors profiling hardware and software states, without relying on root access, unlisted APIs, or ADB:
  - Battery & Power (capacity, health, temperature, voltage, charging state)
  - Connectivity & Wi-Fi (active networks, link speeds, signal strength)
  - Telephony (operator info, network types)
  - Storage & Memory Total/Available bytes
  - Network Traffic counters (TX/RX across mobile, total, and the app UID)
  - Device/OS Metadata (manufacturer, build IDs, uptime, SDK version)
  - Self-metrics (push duration, payload size, failure rates)
- **Strict Privacy Controls:** Does *not* extract or depend on proprietary IDs (IMEI, IMSI, or MAC tracking). Sensitive network info (SSIDs, BSSIDs, IPs, DNS) are hard-gated by strict opt-in user configurations. Generates its own application UUID for safely tracking app instances.

## 🏗 Architecture

The app is built as a sequential processing pipeline driven by background jobs or standard UI interaction:

**`Collectors ➔ Serializer ➔ Transport`**

*   **Collectors:** 13 individually configurable implementations. A failure in one collector never interrupts the rest of the flow.
*   **Serializer:** Transforms internal data models into strictly formatted Prometheus text format.
*   **Transport (`PushgatewayClient`):** Handles HTTP(S) communication, custom grouping key URL pathing, basic auth, and retry backoff.
*   **UI:** Built entirely in **Jetpack Compose** allowing users to modify configurations, preview metric payloads instantly over a local dry run, and trigger manual pushes.

## Build & Run

Ensure you have Android SDK 34 installed. Because the binary wrapper is uncommitted, build environments should utilize a system Gradle fallback or generate the wrapper first. 

### CLI Commands

```powershell
# Generate wrapper (if absent)
gradle wrapper

# Build a Debug APK (Outputs to app/build/outputs/apk/debug/)
./gradlew :app:assembleDebug

# Build a minified Release APK
./gradlew :app:assembleRelease

# Install on a connected emulator or test device
./gradlew :app:installDebug

# Run static checks
./gradlew :app:lint
```

## 🔌 API Contract

- **Minimum SDK (minSdk):** API 21 (Android 5.0 Lollipop).
- **Target / Compile SDK:** API 34.
- Core operations purposefully avoid constraints to ensure operation on a broad spectrum of Android distributions (wearables, older phones, embedded boards).

## Permissions

This app gracefully requests minimal permissions to operate. Certain collectors (like telemetry or granular Wi-Fi states) degrade and omit output safely if user permissions are denied:
- `INTERNET` (Required for HTTP exports)
- `ACCESS_NETWORK_STATE` & `ACCESS_WIFI_STATE` (Used by collectors)
- `RECEIVE_BOOT_COMPLETED` (Optional, persists schedules across device restarts)
- *Note:* `READ_PHONE_STATE` is structurally decoupled and optional to maximize user privacy.

## Adding New Collectors

1. Implement the `Collector` interface in the `collector/` package ensuring `collect()` returns `emptyList()` rather than throwing on errors.
2. Embed a toggle flag `enable<Feature>` within `AppConfig` and register it inside `ConfigRepository`.
3. Add the mapping to `CollectorRegistry.buildCollectors`.
4. Ensure exported metric names follow robust Prometheus naming topologies (`_bytes`, `_seconds`, monotonic `_total`, etc.). 

---

**License & Distribution**
Refer to internal tracking and licensing formats for further instructions around deploying this utility outside of local testing environments.
