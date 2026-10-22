# Galaxy Ring Companion & Health Connect Sync

An open companion Android application for the **Samsung Galaxy Ring**, designed to capture real-time biometrics over Bluetooth Low Energy (BLE) and synchronize them seamlessly into **Android Health Connect**.

---

## ✨ Features

- **Real-time Bluetooth Low Energy (BLE)**:
  - BLE Device Scanner for Galaxy Ring and compatible RWFit wearables.
  - Standard GATT parsing for Battery Level (`0x2A19`), Heart Rate (`0x2A37`), and Skin Temperature (`0x2A1C`).
  - Custom Galaxy Ring telemetry framing with XOR checksum validation and two-way commands (`PING`, `REQUEST_SYNC`, `FIND_MY_RING`).
- **Comprehensive Biometric Monitoring**:
  - **Live Vitals**: Continuous and on-demand Heart Rate, Blood Oxygen (SpO₂), Heart Rate Variability (HRV / RMSSD), and Stress index tracking.
  - **Daily Activity**: Step count, active calorie expenditure, and estimated distance.
  - **Sleep Stages & Hypnogram**: Real sleep session parsing for Deep, REM, Light, and Awake phases with continuity and efficiency scores.
  - **Sleep Apnea Screener**: Symptom and nocturnal oxygen desaturation analysis with clinical risk stratification.
  - **Hardware Controls**: Ring screen brightness opcode probe (`0x0285`) and scheduled monitoring interval configuration.
- **Android Health Connect Integration**:
  - Direct synchronization of `HeartRateRecord`, `StepsRecord`, `SleepSessionRecord`, `OxygenSaturationRecord`, and `BodyTemperatureRecord`.
  - Built-in `PermissionsRationaleActivity` compliant with Android and Google Play policies.
- **Background Periodic Sync**:
  - `RingScheduledService` & WorkManager background sync workers for reliable background telemetry capture.
- **Admin Diagnostics & Telemetry Logs**:
  - Detailed live BLE frame logs with level filtering (INFO, WARN, ERROR), clipboard export, and GATT connection inspection.

---

## 🛠 Project Structure

```
├── app/
│   ├── build.gradle.kts                     # App module build configuration & dependencies
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml          # BLE, Foreground Service & Health Connect permissions
│       │   ├── java/com/galaxy/ring/
│       │   │   ├── GalaxyRingApp.kt         # Application class & repository initialization
│       │   │   ├── MainActivity.kt          # Main dashboard & permission request flows
│       │   │   ├── ble/
│       │   │   │   ├── BleRepository.kt     # BLE scanner, GATT manager & simulation fallback
│       │   │   │   ├── BrightnessProbe.kt   # Hardware screen brightness command probing
│       │   │   │   ├── GalaxyRingBLEManager.kt # Core BLE GATT connection manager
│       │   │   │   └── Protocol.kt          # Telemetry frame encoders, decoders & checksums
│       │   │   ├── data/
│       │   │   │   ├── Entities.kt & Daos.kt# Room Database persistence for biometric records
│       │   │   │   ├── RingDatabase.kt      # Local SQLite database definition
│       │   │   │   ├── SleepAnalyzer.kt     # Sleep stages, scores, and efficiency metrics
│       │   │   │   ├── SleepApneaScreener.kt# Apnea screening report generator
│       │   │   │   └── Models.kt            # Ring telemetry & state models
│       │   │   ├── debug/
│       │   │   │   └── AppLog.kt            # In-app logging system for BLE frames & events
│       │   │   ├── health/
│       │   │   │   ├── HealthConnectWriter.kt # Health Connect client & data insertion
│       │   │   │   └── PermissionsRationaleActivity.kt # Permissions rationale screen
│       │   │   ├── sync/
│       │   │   │   ├── RingScheduledService.kt # Foreground service for keep-connected BLE
│       │   │   │   └── SyncWorker.kt        # Periodic WorkManager background worker
│       │   │   └── ui/
│       │   │       ├── screens/
│       │   │       │   ├── MainScreen.kt    # Home dashboard, status pill & quick metrics
│       │   │       │   ├── SleepScreen.kt   # Hypnogram & sleep stage breakdown
│       │   │       │   ├── HeartScreen.kt   # 24-hour heart rate & SpO2 distribution
│       │   │       │   ├── ActivityScreen.kt# Daily steps, workouts & calorie progress
│       │   │       │   ├── SettingsScreen.kt# Monitoring intervals & ring brightness
│       │   │       │   └── AdminScreen.kt   # Telemetry log viewer & GATT debug
│       │   │       └── theme/
│       │   │           ├── Color.kt & Theme.kt # Cyber titanium dark theme
│       │   │           └── Components.kt    # Metric cards & empty states
│       │   └── res/                         # Adaptive launcher icons, drawables & strings
│       └── test/java/com/galaxy/ring/       # JVM Robolectric & unit tests
├── gradle/
│   └── libs.versions.toml                   # Version catalog
└── settings.gradle.kts
```

---

## 🔒 Permissions & Security

- **`BLUETOOTH_SCAN` & `BLUETOOTH_CONNECT`**: Scans and connects with your Galaxy Ring.
- **`FOREGROUND_SERVICE_CONNECTED_DEVICE`**: Keeps ring connection active for background synchronization.
- **Health Connect (`android.permission.health.*`)**: Reads and writes heart rate, steps, sleep, oxygen saturation, and body temperature to your local Health Connect database. All metrics remain private and on-device.
