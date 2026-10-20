# Agent Queue

[x] A1 ui/theme: colors/type + composables MetricCard, EmptyState, SectionHeader only
[x] A2 Bottom nav: Home|Sleep|Heart|Activity|More wiring to existing screens (no data rewrite)
[x] A3 Restyle Home only: status pill + cards for steps, last HR, last SpO2, last sleep (real DB or empty)
[x] B1 SleepScreen: entity.toSegments() + SleepHypnogram(segments); no Light→Deep→REM invention; empty stagesJson → EmptyState
[x] B2 SleepAnalyzer: stages empty → all stage minutes 0 (delete 22/25/7% fake split)
[x] C1 Protocol: constants + buildHistoryRead for 0x0503/0509/050A/050D (encodeFrame + READ 0x10 only)
[x] C2 Sync path: request HRV+Stress history; parse real samples → Room; log if empty (no fakes)
[x] C3 Heart day UI: MetricDetailPanel + timed HR list for selected day
[x] C4 SpO2 day UI: same pattern
[x] C5 HRV day UI: same pattern
[ ] C6 Stress day UI: same pattern
[ ] D1 Settings: MonitoringSettings toggles/intervals (HR 30m default; SpO2/HRV/Stress 60m)
[ ] D2 Settings brightness Off|Dim|Soft|Strong; BLE 0x0285 + BrightnessProbe; cache key on RX; no fake success
[ ] D3 RingScheduledService: wake shortestIntervalMin; measure settings.dueNow() only; markRun after
[ ] E1 Remove user-visible "SR16"; move Admin under More
[ ] E2 Sync docs/WIRING.md checkboxes to match repo
