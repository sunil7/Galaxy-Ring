# Galaxy Ring APK Generation & Downloads

This repository contains the complete Android source code, Gradle build system, and automated CI/CD pipeline to generate the installable Android application package (APK).

---

## 🛠️ How to Generate the APK

### 1. In AI Studio (Fastest — 1-Click)
In the AI Studio platform:
1. Open the project settings / overflow menu in the top-right corner.
2. Select **Generate APK** (or **Export > Generate APK / AAB**).
3. The platform cloud build system will assemble the APK and provide a direct download link.

---

### 2. Via GitHub Actions (Automated CI/CD)
The repository includes a ready-to-run GitHub Actions workflow (`.github/workflows/build-apk.yml`):
1. Push this repository to GitHub or trigger the workflow manually under the **Actions** tab (**"Build Android APK" > "Run workflow"**).
2. The workflow will automatically compile the project with JDK 21 and Gradle 9.3.1.
3. Download the built APK directly from the **Artifacts** section (`GalaxyRing-v1.0.1`) or from the `/apk` folder in the repository (`apk/GalaxyRing-v1.0.1.apk`).

---

### 3. Local CLI or Android Studio
If you clone or export this repository locally:
```bash
# Build the APK using Gradle
gradle assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk

# Install directly to a connected Android phone or ring test device via ADB:
adb install -r apk/GalaxyRing-v1.0.1.apk
```

---

## 📦 APK Specifications
- **File Name**: `GalaxyRing-v1.0.1.apk`
- **Package ID**: `com.aistudio.galaxyring.kjnpqm`
- **Version**: 1.0.1 (`versionCode = 2`)
- **Min SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 15 (API 36)
- **Architectures**: Universal (`arm64-v8a`, `armeabi-v7a`, `x86_64`)
- **Key Capabilities**: Bluetooth Low Energy (BLE) Gatt Client, Health Connect Sync, Foreground Service Telemetry, Room SQLite Database.
