# Galaxy Ring Companion & Health Connect Sync

An open companion Android application for the **Samsung Galaxy Ring**, designed to capture real-time biometrics over Bluetooth Low Energy (BLE) and synchronize them seamlessly into **Android Health Connect**.

---

## 📱 Pre-built APK

The compiled and signed debug APK is included directly with this repository:
- **File**: [`apk/GalaxyRing-debug.apk`](apk/GalaxyRing-debug.apk)
- **Target Architecture**: Universal (arm64-v8a, armeabi-v7a, x86_64)
- **Min SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 15 (API 36)

### Quick Install via ADB
```bash
adb install apk/GalaxyRing-debug.apk
```
Or transfer `GalaxyRing-debug.apk` to your Android device and install it using your file manager (ensure "Install unknown apps" is enabled).

---

## ✨ Features

- **Real-time Bluetooth Low Energy (BLE)**:
  - BLE Device Scanner for Galaxy Ring and standard fitness devices.
  - Standard GATT parsing for Battery Level (`0x2A19`), Heart Rate (`0x2A37`), and Skin Temperature (`0x2A1C`).
  - Custom Galaxy Ring telemetry framing with XOR checksum validation and two-way commands (`PING`, `REQUEST_SYNC`, `FIND_MY_RING`).
- **Android Health Connect Integration**:
  - Direct synchronization of `HeartRateRecord`, `StepsRecord`, `SleepSessionRecord`, and `BodyTemperatureRecord`.
  - Built-in `PermissionsRationaleActivity` compliant with Android and Google Play policies.
- **Background Periodic Sync**:
  - `SyncWorker` powered by Android `WorkManager` for scheduled 15-minute background sync intervals.
- **Modern Jetpack Compose UI**:
  - Sleek dark titanium aesthetic with animated pulsing biosensor ring canvas.
  - Live vitals dashboard: Heart rate with sparkline history, daily steps with goal progress, sleep stage analysis (Deep, REM, Light, Awake), skin temperature differential, and ring battery gauge.
  - Interactive "Find My Ring" trigger and instant "Sync Now" action.

---

## 🛠 Project Structure

```
├── apk/
│   └── GalaxyRing-debug.apk                 # Pre-built, ready-to-install APK
├── app/
│   ├── build.gradle.kts                     # App module build configuration
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml          # BLE & Health Connect permissions
│       │   ├── java/com/galaxy/ring/
│       │   │   ├── GalaxyRingApp.kt         # Application class & WorkManager scheduler
│       │   │   ├── MainActivity.kt          # Main dashboard & permission request flow
│       │   │   ├── ble/
│       │   │   │   ├── BleRepository.kt     # BLE scanner, GATT manager & simulation
│       │   │   │   └── Protocol.kt          # Frame decoders & command builders
│       │   │   ├── data/
│       │   │   │   └── Models.kt            # Ring telemetry & state models
│       │   │   ├── health/
│       │   │   │   ├── HealthConnectWriter.kt # Health Connect client & data insertion
│       │   │   │   └── PermissionsRationaleActivity.kt # Permissions rationale screen
│       │   │   ├── sync/
│       │   │   │   └── SyncWorker.kt        # Periodic WorkManager background worker
│       │   │   └── ui/
│       │   │       ├── screens/MainScreen.kt# Jetpack Compose dashboard & dialogs
│       │   │       └── theme/Theme.kt       # Cyber titanium dark theme
│       │   └── res/                         # Vector icons, themes, and strings
│       └── test/java/com/galaxy/ring/ble/
│           └── ProtocolTest.kt              # Unit tests for BLE protocol decoding
├── gradle/
│   └── libs.versions.toml                   # Version catalog
└── settings.gradle.kts
```

---

## 🏗 Building from Source

### Prerequisites
- JDK 17 or higher
- Android SDK with API 36

### Build Commands
```bash
# Run unit tests
gradle :app:testDebugUnitTest

# Assemble debug APK
gradle :app:assembleDebug

# The newly built APK will be located at:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔒 Permissions & Security

- **`BLUETOOTH_SCAN` & `BLUETOOTH_CONNECT`**: Scans and connects with your Galaxy Ring.
- **Health Connect (`android.permission.health.*`)**: Reads and writes heart rate, steps, sleep, and body temperature to your local Health Connect database. All metrics remain private and on-device.
