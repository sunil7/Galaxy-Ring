# Galaxy Ring Companion & Health Connect Sync

An open companion Android application for the **Samsung Galaxy Ring**, designed to capture real-time biometrics over Bluetooth Low Energy (BLE), manage ring device configurations, and synchronize data seamlessly into **Android Health Connect** and a private on-device **Room database**.

---

## 📱 Generating & Downloading the APK

### Option A: 1-Click in AI Studio
1. Open the project settings / overflow menu in the top right of the AI Studio window.
2. Click **Generate APK** (or **Export > Generate APK / AAB**).
3. The platform will compile and sign the APK and provide the direct download link.

### Option B: Automated GitHub Actions CI/CD
When pushed to GitHub, the workflow in [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml) compiles the APK automatically on every push or manual dispatch. The resulting APK is published as a downloadable artifact (`galaxy-ring-debug-apk`).

### Option C: Build via Local Gradle
```bash
# Build the debug APK:
gradle assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk

# Install via ADB:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## ✨ Features & Architecture

- **Real-time Bluetooth Low Energy (BLE)**:
  - BLE Device Scanner for Galaxy Ring and standard fitness devices with permission contracts.
  - Standard GATT parsing for Battery Level (`0x2A19`), Heart Rate (`0x2A37`), and Skin Temperature (`0x2A1C`).
  - Custom Galaxy Ring telemetry framing with XOR checksum validation and bidirectional commands (`PING`, `REQUEST_SYNC`, `FIND_MY_RING`, manual measurement triggers).
- **Interactive Health Dashboard (Home)**:
  - Live biometrics: Heart Rate, Blood Oxygen (SpO₂), HRV (RMSSD), Stress Index, Daily Steps, and Sleep Score.
  - On-demand measurement triggers (HR, SpO₂, HRV, Stress) with animated PPG acquisition indicator and live completion feedback.
  - Ring Status Pill with connection status, battery percentage, RSSI signal gauge, and quick sync action.
- **Sleep & Hypnogram Analysis**:
  - Visual sleep stage hypnogram timeline showing Deep, REM, Light, and Awake segments.
  - Detailed stage breakdowns with target deltas and sleep debt calculations.
  - Stage quality and recovery tips based on physiological thresholds.
  - Overnight physiological telemetry (overnight min/avg HR, lowest SpO₂).
  - Heuristic Sleep Apnea risk screening module evaluating oxygen desaturations, breathing stability, and self-reported symptoms with clinical disclaimers.
- **Heart & Cardiovascular Metrics**:
  - Continuous heart rate sparkline and resting heart rate monitoring.
  - Heart Rate Variability (RMSSD) and autonomous nervous system stress scores.
- **Activity & Workout Tracking**:
  - Daily steps counter with calories burned and distance tracking.
  - Workout record inspection and detailed workout drilldown.
- **Ring Configuration & Monitoring Settings**:
  - Granular interval scheduling for all-day Heart Rate, regular SpO₂, regular HRV, and scheduled Stress.
  - Ring LED / screen brightness probe (levels Off, Dim, Soft, Strong) with candidate key probing (`0x0285`).
  - Battery optimization management and keep-connected background settings.
- **Diagnostic Admin Console (AppLog)**:
  - High-performance in-memory and file-backed circular ring buffer log.
  - Log filtering by level (INFO, WARN, ERROR, DEBUG), one-tap clipboard copy, and diagnostic export.
  - Raw BLE frame transmitter and GATT command testing console.
- **Android Health Connect Integration**:
  - Direct synchronization of `HeartRateRecord`, `StepsRecord`, `SleepSessionRecord`, and `BodyTemperatureRecord`.
  - Built-in `PermissionsRationaleActivity` compliant with Android and Google Play policies.
- **Local Persistence & Background Service**:
  - Strict privacy-first on-device Room database storing health snapshots, sleep sessions, and workout logs.
  - Foreground service (`RingScheduledService`) and periodic `WorkManager` background sync worker.

---

## 🛠 Project Structure

```
app/
├── build.gradle.kts                     # App module build configuration & dependencies
└── src/
    ├── main/
    │   ├── AndroidManifest.xml          # BLE & Health Connect permissions
    │   ├── java/com/galaxy/ring/
    │   │   ├── GalaxyRingApp.kt         # Application singleton & service wiring
    │   │   ├── MainActivity.kt          # Main dashboard & permissions flow
    │   │   ├── ble/
    │   │   │   ├── BleRepository.kt     # BLE scanner, GATT manager & simulation
    │   │   │   ├── BrightnessProbe.kt   # Screen brightness candidate probing
    │   │   │   ├── GalaxyRingBLEManager.kt # Low-level BLE connection manager
    │   │   │   └── Protocol.kt          # Telemetry frame encoders & decoders
    │   │   ├── data/
    │   │   │   ├── Daos.kt              # Room DAOs for health records
    │   │   │   ├── Entities.kt          # Room entities (HR, SpO2, Sleep, Steps)
    │   │   │   ├── Models.kt            # Ring telemetry & state domain models
    │   │   │   ├── MonitoringSettings.kt# User preferences for intervals & brightness
    │   │   │   ├── RingDatabase.kt      # Room database configuration
    │   │   │   ├── RingHealthRepository.kt # Main data repository layer
    │   │   │   ├── SleepAnalyzer.kt     # Sleep staging and score computations
    │   │   │   ├── SleepApneaScreener.kt# Heuristic overnight breathing evaluation
    │   │   │   └── SleepRecordParser.kt # Raw sleep page decoder
    │   │   ├── debug/
    │   │   │   └── AppLog.kt            # Diagnostic circular logging system
    │   │   ├── health/
    │   │   │   ├── HealthConnectWriter.kt # Health Connect client & data insertion
    │   │   │   └── PermissionsRationaleActivity.kt # Permissions rationale screen
    │   │   ├── sync/
    │   │   │   ├── RingScheduledService.kt # Foreground service for continuous sync
    │   │   │   └── SyncWorker.kt        # WorkManager periodic background sync
    │   │   └── ui/
    │   │       ├── screens/             # Jetpack Compose screens (Main, Sleep, Heart, Activity, Settings, Admin)
    │   │       ├── components/          # Reusable MetricCard, StatPill, SectionHeader
    │   │       └── theme/               # Cyber titanium dark M3 theme
    │   └── res/                         # Adaptive launcher icon, vectors, strings
    └── test/java/com/galaxy/ring/       # Comprehensive JVM unit & Robolectric tests
gradle/
└── libs.versions.toml                   # Version catalog
```

---

## 🔒 Permissions & Security

- **`BLUETOOTH_SCAN` & `BLUETOOTH_CONNECT`**: Scans and connects with the Galaxy Ring.
- **Health Connect (`android.permission.health.*`)**: Reads and writes heart rate, steps, sleep, and body temperature to your local Health Connect database. All metrics remain private and on-device.
- **`POST_NOTIFICATIONS` & `FOREGROUND_SERVICE`**: Maintains background connectivity and periodic sync when enabled by user.
