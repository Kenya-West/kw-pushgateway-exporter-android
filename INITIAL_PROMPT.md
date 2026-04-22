Design and implement a native Android application whose minimum and target platform contract is Android 5.0 Lollipop / API level 21. The app is a Prometheus Pushgateway-based device metrics exporter. It does not expose an HTTP /metrics endpoint on the phone. Instead, it periodically collects device and app telemetry from public Android APIs available on API 21 and pushes Prometheus text-format metrics to a Prometheus Pushgateway over HTTP(S), using the Push model.

High-level goal
Build a small, reliable, production-minded Android app that acts like a host/device exporter for Android, but using Pushgateway instead of pull scraping. The app must be designed around official Android API 21 capabilities and official Pushgateway semantics. Do not rely on hidden APIs, root, adb-only access, private SDKs, or OEM-specific undocumented behavior.

Important architecture constraints
1. Target API contract is 21. Everything core must work on Android 5.0 with public APIs available there.
2. Choose Android Native Jetpack compose stack.
3. Use JobScheduler for background periodic execution because it exists on API 21. Use a boot receiver plus persisted jobs if appropriate.
4. Respect Pushgateway semantics:
   - Pushed metrics are grouped by grouping key labels whose first label must be job.
   - Use Pushgateway endpoints under /metrics/job/<JOB_NAME>{/<LABEL_NAME>/<LABEL_VALUE>}.
   - Support PUT semantics for full replacement of a metric group.
   - Optionally support POST semantics if partial metric-name replacement is useful, but default to PUT for deterministic exports.
   - Support DELETE for lifecycle cleanup of stale grouping keys when appropriate.
   - Send Prometheus text exposition format with LF line endings.
   - Design the app so Prometheus can scrape the Pushgateway with honor_labels: true.
5. Because Pushgateway retains metrics until deleted, design explicit stale-series lifecycle handling. This is mandatory.

What the app must do
A. Configuration
Provide a simple configuration UI and local persistence for:
- Pushgateway base URL, including support for HTTP and HTTPS
- Optional basic authentication or custom headers if the chosen stack supports it
- Export interval / schedule
- Job name
- Grouping key strategy
- Whether to include device-scoped labels
- Timeout / retry policy
- Enable/disable specific metric families
- Manual “push now” action
- View last push result, last success time, last failure time, and last error
- Export dry-run / preview mode that shows the metrics payload before sending

B. Identity and grouping key strategy
Implement a safe grouping key scheme that works well with Pushgateway retention behavior.
Requirements:
- Always include job as the first grouping key label
- Do NOT use privileged or sensitive persistent identifiers such as IMEI, IMSI, subscriber ID, or hardware serial as the primary instance identity
- Prefer an app-generated installation UUID persisted locally, or a carefully chosen ANDROID_ID-based fallback if needed
- Grouping key should be configurable, but provide a sane default such as:
  {job="android_device_exporter", app_instance="<installation_uuid>"}
- Additional stable labels can be attached in the body rather than all in the grouping key
- Design a cleanup strategy for stale groups if the installation UUID changes or if the user explicitly resets identity
- Be very careful not to generate unbounded label cardinality

C. Scheduling and delivery
- Schedule periodic collection and push using JobScheduler APIs available on API 21
- Allow constraints such as:
  - any network
  - unmetered network only
  - only while charging
- Persist jobs across reboot if supported and configured
- Handle device boot via RECEIVE_BOOT_COMPLETED if persisted scheduling or startup re-registration is used
- Implement exponential backoff / bounded retry
- Prevent overlapping pushes
- Keep collection + serialization + HTTP push idempotent and robust
- If the device is offline, queue only minimal state locally; do not build an unbounded backlog
- Prefer replacing the whole metric group with a fresh PUT payload each run

D. Metric collection scope
Collect only data available from public Android APIs on API 21 and clearly separate:
- static metadata
- slowly changing metadata
- dynamic gauges/counters

Implement the following metric families.

1. Exporter self-metrics
These describe the exporter app itself.
Examples:
- android_exporter_info{version_name, version_code, package_name, sdk_int, target_sdk, build_type} 1
- android_exporter_last_collect_success_unixtime
- android_exporter_last_push_success_unixtime
- android_exporter_last_push_failure_unixtime
- android_exporter_collect_duration_seconds
- android_exporter_push_duration_seconds
- android_exporter_payload_size_bytes
- android_exporter_metrics_count
- android_exporter_push_attempts_total
- android_exporter_push_failures_total
- android_exporter_job_scheduled 0/1

Populate app metadata from PackageInfo / PackageManager where available:
- packageName
- versionName
- versionCode
- firstInstallTime
- lastUpdateTime

2. Device / OS / build metadata
Expose an info metric with constant value 1 and labels for stable build/device properties that are appropriate for Prometheus metadata.
Candidate labels:
- manufacturer
- model
- brand
- device
- product
- hardware
- fingerprint
- build_id
- display_id
- build_type
- build_tags
- android_release
- android_codename
- android_incremental
- sdk_int
- supported_abis (serialize safely, e.g. comma-separated label only if cardinality is controlled)
- supported_32_bit_abis
- supported_64_bit_abis

Do not parse opaque release strings in fragile ways.
Do not use deprecated CPU_ABI on API 21; use SUPPORTED_ABIS.

3. Time / uptime metrics
Use SystemClock:
- android_device_elapsed_realtime_milliseconds
- android_device_uptime_milliseconds
- android_device_boot_time_seconds (derive from wall clock minus elapsedRealtime carefully)

4. Memory metrics
Use ActivityManager + ActivityManager.MemoryInfo:
- android_memory_available_bytes
- android_memory_total_bytes
- android_memory_threshold_bytes
- android_memory_low 0/1
- android_device_low_ram 0/1 from ActivityManager.isLowRamDevice()

Do not overclaim exact free RAM semantics; preserve Android naming and meaning.

5. Storage / filesystem metrics
Use Environment + StatFs for relevant mount points:
- internal data directory
- cache directory if useful
- external storage directory if present and mounted

Metrics per storage scope/path label:
- android_storage_total_bytes
- android_storage_available_bytes
- android_storage_free_bytes
- android_storage_block_size_bytes
- android_storage_state info/enum label if applicable
- android_storage_present 0/1
- android_storage_emulated 0/1 if obtainable
- android_storage_removable 0/1 if obtainable

Be defensive about unavailable or unmounted external storage.

6. Battery / power metrics
Use ACTION_BATTERY_CHANGED via registerReceiver() plus BatteryManager on API 21:
- battery level and scale
- derived battery percent
- plugged state
- charge status
- health
- present
- technology
- temperature
- voltage

Use BatteryManager.getIntProperty()/getLongProperty() where supported:
- BATTERY_PROPERTY_CAPACITY
- BATTERY_PROPERTY_CHARGE_COUNTER
- BATTERY_PROPERTY_CURRENT_NOW
- BATTERY_PROPERTY_CURRENT_AVERAGE
- BATTERY_PROPERTY_ENERGY_COUNTER

Suggested metrics:
- android_battery_level_ratio
- android_battery_level_percent
- android_battery_scale
- android_battery_status
- android_battery_health
- android_battery_plugged
- android_battery_present 0/1
- android_battery_temperature_celsius
- android_battery_voltage_millivolts
- android_battery_charge_counter_microampere_hours
- android_battery_current_now_microamperes
- android_battery_current_average_microamperes
- android_battery_energy_nano_watt_hours

Represent enum-like status/health safely, either numeric with documented mapping or info-style labeled metric.

7. Connectivity / network topology metrics
Use ConnectivityManager APIs available on API 21:
- getActiveNetworkInfo()
- getAllNetworks()
- getNetworkCapabilities(network)
- getLinkProperties(network)

For each currently known network:
- connected state
- type/subtype where available
- roaming state
- failover state if available from NetworkInfo
- metered state if available
- transport classification where available
- interface name from LinkProperties
- DNS servers from LinkProperties
- search domains from LinkProperties
- link addresses / IPs
- routes count
- proxy presence / proxy host+port if configured on the link

Suggested metrics:
- android_network_up{network_id,...} 0/1
- android_network_roaming{network_id,...} 0/1
- android_network_metered{network_id,...} 0/1
- android_network_dns_servers{network_id,count}
- android_network_routes{network_id} <count>
- android_network_link_addresses{network_id,count}
- android_network_info{network_id,type,subtype,type_name,subtype_name,iface,...} 1

Avoid high-cardinality labels where IP addresses or DNS servers would explode series count. Prefer either:
- small bounded labels in an info metric only, or
- counts as gauges, or
- a separate opt-in “verbose networking metadata” mode

8. Wi-Fi metrics
Use WifiManager when Wi-Fi information is available:
- Wi-Fi enabled state
- connection info
- SSID/BSSID only if user explicitly enables sensitive-network labels
- RSSI
- link speed
- network ID
- supplicant / Wi-Fi state if relevant
- DHCP info if available on API 21

Default behavior:
- Do NOT export SSID/BSSID unless an explicit privacy toggle is enabled
- Safe defaults should export only non-sensitive aggregate/link metrics

Suggested metrics:
- android_wifi_enabled 0/1
- android_wifi_connected 0/1
- android_wifi_rssi_dbm
- android_wifi_link_speed_mbps
- android_wifi_network_id
- android_wifi_dhcp_gateway_ipv4 as info-style label only if privacy mode allows; otherwise omit
- android_wifi_info{supplicant_state,...} 1

9. Telephony metrics
Use TelephonyManager only for public, non-privileged, non-sensitive device-state metrics.
Allowed examples:
- operator name
- network operator code if available
- network country ISO
- SIM country ISO
- phone type
- network type
- data state
- call state
- whether telephony feature exists

Do NOT use or require IMEI, MEID, IMSI, subscriber ID, line number, or other privileged persistent identifiers.

Suggested metrics:
- android_telephony_present 0/1
- android_telephony_operator_info{operator_name,operator_numeric,country_iso} 1
- android_telephony_network_type
- android_telephony_data_state
- android_telephony_call_state
- android_telephony_phone_type

If READ_PHONE_STATE is not granted or not desired, degrade gracefully and collect only feature-level / non-protected data that remains available.

10. Traffic counters
Use TrafficStats:
- total RX/TX bytes
- total RX/TX packets
- mobile RX/TX bytes
- mobile RX/TX packets
- app UID RX/TX bytes and packets for the exporter app itself

Suggested metrics:
- android_network_total_receive_bytes
- android_network_total_transmit_bytes
- android_network_total_receive_packets
- android_network_total_transmit_packets
- android_network_mobile_receive_bytes
- android_network_mobile_transmit_bytes
- android_network_mobile_receive_packets
- android_network_mobile_transmit_packets
- android_exporter_uid_receive_bytes
- android_exporter_uid_transmit_bytes
- android_exporter_uid_receive_packets
- android_exporter_uid_transmit_packets

11. Display metrics
Use Display / DisplayMetrics:
- widthPixels
- heightPixels
- densityDpi
- xdpi
- ydpi
- rotation
- display name if useful and stable

Suggested metrics:
- android_display_width_pixels
- android_display_height_pixels
- android_display_density_dpi
- android_display_xdpi
- android_display_ydpi
- android_display_rotation

12. Hardware/software feature inventory
Use PackageManager.getSystemAvailableFeatures() and FeatureInfo:
- enumerate device features
- include OpenGL ES version
- optionally enumerate important boolean features as individual metrics

Suggested approaches:
- one info metric per feature, value 1
- or one family like android_feature_present{name="<feature_name>",version="<feature_version>"} 1
- expose reqGlEsVersion / supported GLES version safely

13. Sensor inventory
Use SensorManager.getSensorList(Sensor.TYPE_ALL).
Collect sensor metadata, not continuous high-frequency readings by default.
For each sensor:
- name
- vendor
- type
- version
- maximum range
- resolution
- power
- min delay
- wake-up support if available on API 21
- FIFO sizes if available on API 21

Suggested metrics:
- android_sensor_present{name,vendor,type,version,...} 1
- android_sensor_power_milliamps{...}
- android_sensor_resolution{...}
- android_sensor_max_range{...}
- android_sensor_min_delay_microseconds{...}

Optional mode:
Allow opt-in periodic sampling of a very small subset of low-risk sensors, but default OFF to avoid battery drain and excessive churn.

What the app must NOT do
- No hidden APIs
- No root-only metrics
- No scraping /proc or /sys as a primary contract unless explicitly isolated as a best-effort optional plugin and clearly marked non-contractual
- No privileged identifiers by default
- No unbounded label cardinality
- No continuous sensor streaming by default
- No assumptions that every device supports every BatteryManager property or every Telephony/Wi-Fi API
- No crashing when an API returns null, unsupported, or sentinel values
- No dependence on modern APIs above 21 for the core path

Privacy and security requirements
- Default to privacy-preserving metrics
- Sensitive labels such as SSID, BSSID, exact IP addresses, DNS addresses, operator numeric codes, ANDROID_ID-derived values, and installation UUID must be behind explicit user-controlled toggles where appropriate
- Store secrets securely as far as the chosen stack allows
- Support HTTPS and certificate validation by default
- Do not disable TLS verification unless the user explicitly enables an “insecure/test only” mode
- Clearly document which metrics may reveal device/network identity

Pushgateway integration requirements
Implement correct Pushgateway behavior:
- Construct grouping-key URL path correctly
- Correctly URL-encode labels; handle special characters safely
- Send Prometheus text exposition format with final LF line endings
- Prefer a deterministic metric ordering for reproducibility
- Use PUT for a full group refresh
- Optionally offer DELETE for explicit cleanup
- Interpret HTTP 200/202/400 responses correctly
- Surface Pushgateway error bodies in logs/UI
- Support gzip request compression if practical, but keep it optional
- Be aware that Pushgateway adds push_time_seconds and push_failure_time_seconds per group
- Do not push timestamps in samples unless there is a very strong reason; rely on scrape time

Prometheus metric design rules
- Use Prometheus naming conventions rigorously
- Use base units in metric names:
  - _bytes
  - _seconds
  - _celsius
  - _volts or _millivolts
  - _amperes / _microamperes
- Separate metadata info metrics from numeric gauges/counters
- Counters should only ever increase between device boots/process restarts
- Gauges may go up/down
- Include HELP and TYPE lines
- Keep label sets small and stable
- Document each metric family in generated docs

Failure handling
- If one collector fails, other collectors should still run and push partial metrics
- Emit exporter self-metrics for collector failures
- Do not fail the whole export because one subsystem is unavailable
- Handle unsupported Android APIs and unsupported hardware cleanly
- Use sentinel omission rather than fake values wherever possible
- If BatteryManager property returns unsupported/sentinel, omit that metric instead of pushing misleading zeros unless the official API contract for the app’s target behavior requires otherwise

Deliverables
Produce:
1. Source code
2. Architecture explanation
3. AndroidManifest permissions list with rationale for each permission
4. Metric catalog with HELP/TYPE/units/labels
5. Pushgateway integration notes
6. Prometheus scrape configuration example for scraping Pushgateway with honor_labels: true
7. Example Grafana dashboard ideas
8. Test plan for API 21 emulator/device and at least one newer Android version
9. Notes about limitations of public API 21 telemetry
10. Migration notes if later retargeting to newer Android APIs

Expected permissions to consider
At minimum evaluate and justify:
- INTERNET
- ACCESS_NETWORK_STATE
- ACCESS_WIFI_STATE
- RECEIVE_BOOT_COMPLETED
Optionally, and only if truly needed:
- READ_PHONE_STATE
Do not request dangerous permissions unless the implementation actually needs them.

Implementation guidance
- Design collector modules, serializer, scheduler, transport client, config store, and UI as separate components
- Make each collector independently testable
- Make Pushgateway payload generation deterministic and unit-testable
- Include a “sample payload” fixture for tests
- Include graceful degradation for devices lacking telephony, Wi-Fi, battery properties, external storage, or sensors
- Favor low battery impact over aggressive polling
- Make metric collection intervals configurable with sensible defaults

Also generate these pages in the app:
- a proposed package/module structure
- a sample metric payload
- a sample Pushgateway request
- a sample Prometheus scrape config for the Pushgateway

User have an option to configure prometheus pushgateway endpoint, the verbosity of the log level, and the frequency of the metrics collection and pushing. The app should also provide a simple UI to view the last push result, last success time, last failure time, and last error message if any. 