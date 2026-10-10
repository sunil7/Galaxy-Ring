# Galaxy Ring APK Releases

This folder contains the compiled Android application packages (APK) for the Galaxy Ring companion app.

- **Debug Build**: `galaxy-ring-debug.apk` / `app-debug.apk` (25 MB)
  - Features included: 24h Heart Rate & SpO₂ Day Timelines, Hypnogram & Multi-Range Sleep Analysis, Sleep Consistency, Rolling Sleep Debt, and Overnight Sleep Apnea Risk Screening.

> **Note**: If your Git client or export bridge filters large binary `.apk` files during push, you can force-add the APK using:
> ```bash
> git add -f apk/*.apk
> git commit -m "Add compiled APK"
> git push origin main
> ```
> Alternatively, the included GitHub Actions workflow will automatically compile and publish the latest APK to your GitHub Releases on every push.
