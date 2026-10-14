# Wiring checklist (remaining work)

The data layer, parser, tests and new self-contained UI pieces are already in this branch.
The edits below touch large existing files and still have to be applied there.
Do NOT rework connect/init/051A steps/0609 HR-SpO2/0505 paging/Sync/keep-alive.

## What is already available
- `SleepRecordParser`, `SleepPageStore`, `SleepSegment`, `SleepSession.isCompleted()`, `SleepSessionEntity.toSegments()` (data/SleepRecordParser.kt)
- `saveSleepSession` now stores real segments in `stagesJson`
- `SleepHypnogram(segments)` (ui/screens/SleepHypnogram.kt)
- `MetricPoint`, `MetricStats`, `HeartRateEntity/OxygenSaturationEntity/HrvEntity/StressEntity.toMetricPoint()` (data/MetricDetail.kt)
- `MetricDetailPanel(...)` (ui/screens/MetricDetailPanel.kt)
- `MonitorMetric`, `MonitorConfig`, `MonitoringSchedule`, `MonitoringSettings(context)`, `ScreenBrightness` (data/MonitoringSettings.kt)
- `BrightnessProbe` (ble/BrightnessProbe.kt)

## 1. SleepScreen.kt (P0)
- Where `sessionToAnalyze` (or similar) is built from a `SleepSessionEntity`, pass the real stages:
  `val segments = entity.toSegments()` and
  `SleepSession(entity.startTime, entity.endTime, stages = segments.map { SleepStageRecord(it.stage, it.start, it.end) })`.
- Replace the current hypnogram with `SleepHypnogram(segments)`.
- Delete every "now"-anchored or sliding daytime window; the X-axis is `startTime..endTime` of the saved night (e.g. 23:35-05:33).
- Show "Mid-Night" only when `SleepRecordParser.crossesLocalMidnight(start, end)`.
- Nights saved before this change have empty `stagesJson`: show "No sleep stage detail from the ring" (no synthetic stages) until the ring is re-synced.
- Then remove the synthetic 22/25/7% fallback in `SleepAnalyzer` (stages empty -> zeros, not invented values).

## 2. HR / SpO2 / HRV / Stress detail (P1)
- For each metric screen, collect the selected day from `RingHealthRepository`
  (`getHeartRateForDay`, `getSpo2ForDay`, `getHrvForDay`, `getStressForDay`), map with `toMetricPoint()`, and show `MetricDetailPanel`.
- Wire the toggle/interval with `MonitoringSettings` (`config`, `setEnabled`, `setIntervalMin`) using the matching `MonitorMetric`.
- Defaults: HR 30 min, SpO2 60, HRV 60, Stress 60.

## 3. Settings: Health Monitoring + brightness (SettingsScreen.kt)
- Health Monitoring section with 4 rows (All-day Heart Rate interval; Regular Blood oxygen, Regular HRV, Scheduled Stress each toggle + interval), driven by `MonitoringSettings`.
- "Screen brightness": Off | Dim light | Soft light | Strong light (`ScreenBrightness.values()`), stored in `MonitoringSettings.screenBrightness`.

## 4. Brightness over BLE (BleRepository / GalaxyRingBLEManager)
- On selection, iterate `BrightnessProbe.orderedCandidates(settings.brightnessKey, settings.brightnessShape)`;
  for each candidate AppLog `BrightnessProbe.describe(...)` (TX), send `BrightnessProbe.buildCommand(...)` through the existing write path,
  log any RX, and when a frame with `BrightnessProbe.isResponseFor(frame, candidate)` arrives, cache `brightnessKey` / `brightnessShape` and stop.
- If nothing answers, log "no RX for any brightness candidate" (no fake success).
- "Off" is also the screen-off value for the optional sleep-mode feature.

## 5. Scheduler (RingScheduledService only, no second service)
- Wake every `MonitoringSchedule.shortestIntervalMin(settings.allConfigs())` minutes.
- On each wake measure only `settings.dueNow()` metrics and call `settings.markRun(metric)` after each.
- One Room row per completed manual/scheduled measure (existing +-60 s de-dup stays); Health Connect write when permitted.
- HRV/Stress: keep the existing 0609 type probe (`Protocol.HRV_PROBE_TYPES`, `getStressCandidateTypes`) and AppLog every TX/RX key; if the ring never answers, log clearly instead of storing data.

## 6. UI strings
- UI says "Galaxy Ring" only; remove any "SR16" text shown to the user.
