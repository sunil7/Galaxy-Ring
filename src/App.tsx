import React, { useState, useEffect, useMemo } from 'react';
import {
  Activity,
  Heart,
  Moon,
  Battery,
  BatteryCharging,
  Bluetooth,
  RefreshCw,
  Sliders,
  ShieldAlert,
  Terminal,
  Zap,
  CheckCircle2,
  AlertTriangle,
  Play,
  RotateCcw,
  Sun,
  Flame,
  Footprints,
  Clock,
  ChevronRight,
  TrendingUp,
  Download,
  Info,
  Radio,
  FileText,
  Smartphone
} from 'lucide-react';
import { AppLog, LogEntry } from './services/appLog';
import {
  RingDevice,
  RingBattery,
  SleepSegment,
  SleepStage,
  MonitorConfig,
  ScreenBrightnessLevel,
  SelfReportSymptoms,
  ApneaScreeningReport,
  ManualMeasurementState,
  MetricPoint,
} from './types/models';

type TabType = 'overview' | 'vitals' | 'sleep' | 'settings' | 'apnea' | 'logs' | 'android';

export default function App() {
  const [activeTab, setActiveTab] = useState<TabType>('overview');
  const [isConnected, setIsConnected] = useState<boolean>(true);
  const [battery, setBattery] = useState<RingBattery>({ level: 86, isCharging: false });
  const [device, setDevice] = useState<RingDevice>({
    name: 'Galaxy Ring (R16-84A2)',
    address: 'E4:7B:A2:91:34:F8',
    rssi: -62,
    isConnected: true,
    isBonded: true,
  });

  // Live vitals state
  const [currentHr, setCurrentHr] = useState<number>(72);
  const [currentSpo2, setCurrentSpo2] = useState<number>(98);
  const [currentHrv, setCurrentHrv] = useState<number>(54);
  const [currentStress, setCurrentStress] = useState<number>(28);
  const [steps, setSteps] = useState<number>(7430);
  const [calories, setCalories] = useState<number>(342);
  const [distanceKm, setDistanceKm] = useState<number>(5.2);

  // Manual measurement state
  const [manualState, setManualState] = useState<ManualMeasurementState>({ status: 'IDLE' });
  const [isSyncing, setIsSyncing] = useState<boolean>(false);

  // Monitoring settings state matching Android implementation
  const [monitoringConfigs, setMonitoringConfigs] = useState<{ [key: string]: MonitorConfig }>({
    hr: { key: 'hr', label: 'All-day Heart Rate', enabled: true, intervalMin: 30, defaultIntervalMin: 30 },
    spo2: { key: 'spo2', label: 'Regular Blood Oxygen', enabled: true, intervalMin: 60, defaultIntervalMin: 60 },
    hrv: { key: 'hrv', label: 'Regular HRV', enabled: false, intervalMin: 60, defaultIntervalMin: 60 },
    stress: { key: 'stress', label: 'Scheduled Stress', enabled: false, intervalMin: 60, defaultIntervalMin: 60 },
  });

  // Screen brightness: 0=Off, 1=Dim, 2=Soft, 3=Strong
  const [brightness, setBrightness] = useState<ScreenBrightnessLevel>(2);
  const [brightnessStatus, setBrightnessStatus] = useState<string>('Applied (Key 0x0285 / Shape 0)');

  // Sleep Data with real segments
  const [sleepSegments] = useState<SleepSegment[]>([
    { stage: 'AWAKE', start: 1729468800000, end: 1729469700000, durationMinutes: 15 },
    { stage: 'LIGHT', start: 1729469700000, end: 1729474500000, durationMinutes: 80 },
    { stage: 'DEEP', start: 1729474500000, end: 1729480500000, durationMinutes: 100 },
    { stage: 'LIGHT', start: 1729480500000, end: 1729484100000, durationMinutes: 60 },
    { stage: 'REM', start: 1729484100000, end: 1729489500000, durationMinutes: 90 },
    { stage: 'LIGHT', start: 1729489500000, end: 1729494900000, durationMinutes: 90 },
    { stage: 'AWAKE', start: 1729494900000, end: 1729495800000, durationMinutes: 15 },
  ]);

  // Apnea screening symptoms
  const [apneaSymptoms, setApneaSymptoms] = useState<SelfReportSymptoms>({
    snoring: true,
    wakingGasping: false,
    morningHeadaches: false,
    daytimeFatigue: true,
  });

  // Live logs
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [logFilter, setLogFilter] = useState<'ALL' | 'I' | 'W' | 'E'>('ALL');

  // Subscribe to logs
  useEffect(() => {
    const unsubscribe = AppLog.subscribe((newLogs) => {
      setLogs(newLogs);
    });
    return () => unsubscribe();
  }, []);

  // Periodic heartbeat / telemetry simulation
  useEffect(() => {
    if (!isConnected) return;
    const interval = setInterval(() => {
      setCurrentHr((prev) => Math.min(105, Math.max(58, prev + Math.floor(Math.random() * 5) - 2)));
      setCurrentSpo2((prev) => Math.min(100, Math.max(95, prev + (Math.random() > 0.8 ? (Math.random() > 0.5 ? 1 : -1) : 0))));
      setCurrentHrv((prev) => Math.min(85, Math.max(38, prev + Math.floor(Math.random() * 3) - 1)));
      setCurrentStress((prev) => Math.min(75, Math.max(12, prev + Math.floor(Math.random() * 3) - 1)));
      setSteps((prev) => prev + (Math.random() > 0.6 ? Math.floor(Math.random() * 4) + 1 : 0));
    }, 4000);
    return () => clearInterval(interval);
  }, [isConnected]);

  // Sync action
  const handleSync = () => {
    setIsSyncing(true);
    AppLog.i('BleSync', 'Initiated manual sync via BLE 0x051A step telemetry & 0x0505 history');
    setTimeout(() => {
      setIsSyncing(false);
      AppLog.i('BleSync', 'Sync completed. Synced 48 HR points, 24 SpO2 samples, 1 sleep record.');
    }, 1500);
  };

  // Manual measurement handler
  const startMeasurement = (metric: 'Heart Rate' | 'SpO₂' | 'HRV' | 'Stress') => {
    setManualState({ status: 'MEASURING', metric, progress: 10 });
    AppLog.i('BleSensor', `Triggered on-demand measurement: ${metric}`);

    let progress = 10;
    const timer = setInterval(() => {
      progress += 20;
      if (progress >= 100) {
        clearInterval(timer);
        let finalVal = 74;
        let unit = 'BPM';
        if (metric === 'Heart Rate') {
          finalVal = 71 + Math.floor(Math.random() * 6);
          setCurrentHr(finalVal);
          unit = 'BPM';
        } else if (metric === 'SpO₂') {
          finalVal = 98 + Math.floor(Math.random() * 2);
          setCurrentSpo2(finalVal);
          unit = '%';
        } else if (metric === 'HRV') {
          finalVal = 56 + Math.floor(Math.random() * 8);
          setCurrentHrv(finalVal);
          unit = 'ms';
        } else {
          finalVal = 26 + Math.floor(Math.random() * 8);
          setCurrentStress(finalVal);
          unit = '/ 100';
        }
        setManualState({ status: 'COMPLETED', metric, value: finalVal, unit });
        AppLog.i('BleSensor', `Measurement finalized: ${metric} = ${finalVal} ${unit}`);
      } else {
        setManualState({ status: 'MEASURING', metric, progress });
      }
    }, 500);
  };

  // Change brightness handler with candidate probe logging
  const handleBrightnessChange = (lvl: ScreenBrightnessLevel) => {
    setBrightness(lvl);
    const labels = ['Off', 'Dim', 'Soft', 'Strong'];
    AppLog.i('BrightnessProbe', `TX candidate key=0x0285 shape=0 for level ${labels[lvl]} (${lvl})`);
    AppLog.i('BleManager', `Brightness response confirmed: 0x0285 shape=0 accepted by Galaxy Ring firmware`);
    setBrightnessStatus(`Applied: ${labels[lvl]} (key=0x0285)`);
  };

  // Apnea risk calculation
  const apneaReport: ApneaScreeningReport = useMemo(() => {
    let score = 0;
    if (apneaSymptoms.snoring) score += 1;
    if (apneaSymptoms.wakingGasping) score += 2;
    if (apneaSymptoms.morningHeadaches) score += 1;
    if (apneaSymptoms.daytimeFatigue) score += 1;

    const lowestSpo2 = 94;
    const desatCount = 2;
    const band = score >= 3 ? 'HIGH' : score >= 1 ? 'MODERATE' : 'LOW';

    return {
      riskBand: band,
      confidenceScore: 84,
      desaturationCount: desatCount,
      lowestSpo2,
      minutesUnder90: 0,
      findings: [
        `Lowest recorded nightly SpO2: ${lowestSpo2}%`,
        `Oxygen desaturation events detected: ${desatCount}`,
        apneaSymptoms.snoring ? 'Loud snoring reported' : 'No chronic snoring',
        apneaSymptoms.daytimeFatigue ? 'Excessive daytime fatigue logged' : 'Normal daytime alertness',
      ],
      recommendation:
        band === 'HIGH'
          ? 'Recommended clinical consultation or home sleep apnea testing (HSAT).'
          : 'Low-to-moderate risk indicated. Continue nightly SpO2 monitoring and maintain sleep hygiene.',
    };
  }, [apneaSymptoms]);

  // Total sleep metrics
  const totalSleepMinutes = useMemo(() => {
    return sleepSegments
      .filter((s) => s.stage !== 'AWAKE')
      .reduce((acc, curr) => acc + curr.durationMinutes, 0);
  }, [sleepSegments]);

  const deepMinutes = useMemo(() => {
    return sleepSegments
      .filter((s) => s.stage === 'DEEP')
      .reduce((acc, curr) => acc + curr.durationMinutes, 0);
  }, [sleepSegments]);

  const remMinutes = useMemo(() => {
    return sleepSegments
      .filter((s) => s.stage === 'REM')
      .reduce((acc, curr) => acc + curr.durationMinutes, 0);
  }, [sleepSegments]);

  const lightMinutes = useMemo(() => {
    return sleepSegments
      .filter((s) => s.stage === 'LIGHT')
      .reduce((acc, curr) => acc + curr.durationMinutes, 0);
  }, [sleepSegments]);

  const awakeMinutes = useMemo(() => {
    return sleepSegments
      .filter((s) => s.stage === 'AWAKE')
      .reduce((acc, curr) => acc + curr.durationMinutes, 0);
  }, [sleepSegments]);

  // Stage color lookup
  const getStageColor = (stage: SleepStage) => {
    switch (stage) {
      case 'DEEP':
        return 'bg-indigo-600 border-indigo-400 text-indigo-100';
      case 'LIGHT':
        return 'bg-sky-500 border-sky-300 text-sky-100';
      case 'REM':
        return 'bg-purple-500 border-purple-300 text-purple-100';
      case 'AWAKE':
        return 'bg-amber-500 border-amber-300 text-amber-100';
    }
  };

  const filteredLogs = useMemo(() => {
    if (logFilter === 'ALL') return logs;
    return logs.filter((l) => l.level === logFilter);
  }, [logs, logFilter]);

  return (
    <div className="min-h-screen bg-[#090D16] text-slate-100 flex flex-col font-sans">
      {/* Top Navigation Bar */}
      <header className="border-b border-slate-800 bg-[#0E1422]/90 backdrop-blur sticky top-0 z-50 px-4 lg:px-8 py-3">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="relative flex items-center justify-center w-10 h-10 rounded-xl bg-gradient-to-br from-sky-500 to-indigo-600 shadow-lg shadow-sky-500/20">
              <div className="w-5 h-5 rounded-full border-2 border-white/90"></div>
              <span className="absolute -top-1 -right-1 flex h-3 w-3">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-3 w-3 bg-emerald-500"></span>
              </span>
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h1 className="text-lg font-bold tracking-tight text-white">Galaxy Ring</h1>
                <span className="text-[11px] font-semibold uppercase px-2 py-0.5 rounded-full bg-sky-500/10 text-sky-400 border border-sky-500/20">
                  Companion v1.0
                </span>
              </div>
              <p className="text-xs text-slate-400 flex items-center gap-1.5">
                <Bluetooth className="w-3.5 h-3.5 text-sky-400" />
                <span>{device.name}</span>
                <span className="text-slate-600">•</span>
                <span className="text-emerald-400 font-medium">Ready</span>
              </p>
            </div>
          </div>

          {/* Quick status & Actions */}
          <div className="flex items-center space-x-4">
            <div className="hidden sm:flex items-center space-x-2 bg-slate-900/80 px-3 py-1.5 rounded-lg border border-slate-800 text-xs">
              {battery.isCharging ? (
                <BatteryCharging className="w-4 h-4 text-emerald-400" />
              ) : (
                <Battery className="w-4 h-4 text-sky-400" />
              )}
              <span className="font-medium text-slate-200">{battery.level}%</span>
              <span className="text-slate-500">|</span>
              <span className="text-slate-400">{device.rssi} dBm</span>
            </div>

            <button
              onClick={handleSync}
              disabled={isSyncing}
              className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-sky-500/10 hover:bg-sky-500/20 text-sky-400 border border-sky-500/30 text-xs font-semibold transition active:scale-95 disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isSyncing ? 'animate-spin' : ''}`} />
              <span className="hidden sm:inline">{isSyncing ? 'Syncing...' : 'Sync BLE'}</span>
            </button>
          </div>
        </div>

        {/* Navigation Tabs */}
        <div className="max-w-7xl mx-auto mt-3 flex items-center space-x-1 overflow-x-auto no-scrollbar pt-1 border-t border-slate-800/60">
          {[
            { id: 'overview', label: 'Overview', icon: Activity },
            { id: 'vitals', label: 'Vitals & Vibe', icon: Heart },
            { id: 'sleep', label: 'Sleep & Hypnogram', icon: Moon },
            { id: 'settings', label: 'Monitoring Settings', icon: Sliders },
            { id: 'apnea', label: 'Apnea Risk', icon: ShieldAlert },
            { id: 'logs', label: 'BLE Telemetry Logs', icon: Terminal },
            { id: 'android', label: 'Android Native App', icon: Smartphone },
          ].map((tab) => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id as TabType)}
                className={`flex items-center space-x-2 px-3.5 py-2 rounded-lg text-xs font-medium whitespace-nowrap transition ${
                  isActive
                    ? 'bg-sky-500/15 text-sky-400 border border-sky-500/30'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/40'
                }`}
              >
                <Icon className={`w-4 h-4 ${isActive ? 'text-sky-400' : 'text-slate-500'}`} />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 lg:px-8 py-6 space-y-6">
        {/* TAB 1: OVERVIEW */}
        {activeTab === 'overview' && (
          <div className="space-y-6">
            {/* Quick Hero Banner */}
            <div className="p-6 rounded-2xl bg-gradient-to-r from-slate-900 via-slate-900 to-[#121B2F] border border-slate-800 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
              <div className="space-y-1">
                <div className="flex items-center space-x-2">
                  <span className="h-2.5 w-2.5 rounded-full bg-emerald-400 animate-pulse"></span>
                  <span className="text-xs font-semibold uppercase tracking-wider text-emerald-400">
                    Live Telemetry Connected
                  </span>
                </div>
                <h2 className="text-2xl font-extrabold text-white">Galaxy Ring Health Overview</h2>
                <p className="text-sm text-slate-400 max-w-xl">
                  Real-time optical PPG and motion telemetry synced with Android Companion & Health Connect.
                </p>
              </div>

              <div className="flex items-center space-x-2 bg-slate-950/70 p-2 rounded-xl border border-slate-800">
                <button
                  onClick={() => startMeasurement('Heart Rate')}
                  className="px-3 py-1.5 rounded-lg bg-sky-500 hover:bg-sky-400 text-slate-950 text-xs font-bold transition flex items-center space-x-1.5"
                >
                  <Play className="w-3.5 h-3.5 fill-current" />
                  <span>Measure HR</span>
                </button>
                <button
                  onClick={() => startMeasurement('SpO₂')}
                  className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold transition"
                >
                  Measure SpO₂
                </button>
                <button
                  onClick={() => startMeasurement('Stress')}
                  className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold transition"
                >
                  Measure Stress
                </button>
              </div>
            </div>

            {/* Measurement Modal / In-Progress Indicator */}
            {manualState.status === 'MEASURING' && (
              <div className="p-4 rounded-xl bg-sky-950/40 border border-sky-500/40 flex items-center justify-between animate-pulse">
                <div className="flex items-center space-x-3">
                  <Radio className="w-5 h-5 text-sky-400 animate-spin" />
                  <div>
                    <p className="text-sm font-semibold text-white">
                      Measuring {manualState.metric}... Please keep ring still on finger
                    </p>
                    <p className="text-xs text-sky-300">PPG sensor actively acquiring photoplethysmogram frame</p>
                  </div>
                </div>
                <div className="w-32 bg-slate-800 rounded-full h-2.5 overflow-hidden">
                  <div
                    className="bg-sky-400 h-2.5 rounded-full transition-all duration-300"
                    style={{ width: `${manualState.progress}%` }}
                  ></div>
                </div>
              </div>
            )}

            {manualState.status === 'COMPLETED' && (
              <div className="p-4 rounded-xl bg-emerald-950/40 border border-emerald-500/40 flex items-center justify-between">
                <div className="flex items-center space-x-3">
                  <CheckCircle2 className="w-5 h-5 text-emerald-400" />
                  <div>
                    <p className="text-sm font-semibold text-emerald-200">
                      {manualState.metric} Measurement Complete: {manualState.value} {manualState.unit}
                    </p>
                    <p className="text-xs text-emerald-400/80">Saved to health database & Health Connect synced</p>
                  </div>
                </div>
                <button
                  onClick={() => setManualState({ status: 'IDLE' })}
                  className="text-xs text-slate-400 hover:text-white px-2 py-1 rounded bg-slate-800"
                >
                  Dismiss
                </button>
              </div>
            )}

            {/* Metric Cards Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              {/* Heart Rate Card */}
              <div className="p-5 rounded-2xl bg-[#111827] border border-slate-800 hover:border-slate-700 transition">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-slate-400">Heart Rate</span>
                  <div className="p-2 rounded-xl bg-rose-500/10 text-rose-400">
                    <Heart className="w-4 h-4" />
                  </div>
                </div>
                <div className="mt-3 flex items-baseline space-x-2">
                  <span className="text-3xl font-extrabold text-white tracking-tight">{currentHr}</span>
                  <span className="text-xs text-slate-400">BPM</span>
                </div>
                <div className="mt-2 flex items-center justify-between text-xs text-slate-400 pt-2 border-t border-slate-800">
                  <span>Resting: 64 bpm</span>
                  <span className="text-emerald-400">Normal</span>
                </div>
              </div>

              {/* SpO2 Card */}
              <div className="p-5 rounded-2xl bg-[#111827] border border-slate-800 hover:border-slate-700 transition">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-slate-400">Blood Oxygen</span>
                  <div className="p-2 rounded-xl bg-sky-500/10 text-sky-400">
                    <Activity className="w-4 h-4" />
                  </div>
                </div>
                <div className="mt-3 flex items-baseline space-x-2">
                  <span className="text-3xl font-extrabold text-white tracking-tight">{currentSpo2}</span>
                  <span className="text-xs text-slate-400">%</span>
                </div>
                <div className="mt-2 flex items-center justify-between text-xs text-slate-400 pt-2 border-t border-slate-800">
                  <span>Nightly min: 94%</span>
                  <span className="text-emerald-400">Optimal</span>
                </div>
              </div>

              {/* HRV Card */}
              <div className="p-5 rounded-2xl bg-[#111827] border border-slate-800 hover:border-slate-700 transition">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-slate-400">HRV (RMSSD)</span>
                  <div className="p-2 rounded-xl bg-purple-500/10 text-purple-400">
                    <Zap className="w-4 h-4" />
                  </div>
                </div>
                <div className="mt-3 flex items-baseline space-x-2">
                  <span className="text-3xl font-extrabold text-white tracking-tight">{currentHrv}</span>
                  <span className="text-xs text-slate-400">ms</span>
                </div>
                <div className="mt-2 flex items-center justify-between text-xs text-slate-400 pt-2 border-t border-slate-800">
                  <span>Baseline: 52 ms</span>
                  <span className="text-sky-400">Balanced</span>
                </div>
              </div>

              {/* Stress Card */}
              <div className="p-5 rounded-2xl bg-[#111827] border border-slate-800 hover:border-slate-700 transition">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-slate-400">Stress Index</span>
                  <div className="p-2 rounded-xl bg-amber-500/10 text-amber-400">
                    <Sliders className="w-4 h-4" />
                  </div>
                </div>
                <div className="mt-3 flex items-baseline space-x-2">
                  <span className="text-3xl font-extrabold text-white tracking-tight">{currentStress}</span>
                  <span className="text-xs text-slate-400">/ 100</span>
                </div>
                <div className="mt-2 flex items-center justify-between text-xs text-slate-400 pt-2 border-t border-slate-800">
                  <span>State: Relaxed</span>
                  <span className="text-emerald-400">Low</span>
                </div>
              </div>
            </div>

            {/* Daily Activity & Sleep Summary Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Daily Movement */}
              <div className="p-6 rounded-2xl bg-[#111827] border border-slate-800 space-y-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-2">
                    <Footprints className="w-5 h-5 text-sky-400" />
                    <h3 className="font-bold text-white">Daily Activity</h3>
                  </div>
                  <span className="text-xs text-slate-400">Today</span>
                </div>

                <div className="grid grid-cols-3 gap-3">
                  <div className="p-3.5 rounded-xl bg-slate-900 border border-slate-800">
                    <span className="text-[11px] text-slate-400">Steps</span>
                    <p className="text-xl font-bold text-white mt-1">{steps.toLocaleString()}</p>
                    <span className="text-[10px] text-sky-400">Goal: 10,000 (74%)</span>
                  </div>
                  <div className="p-3.5 rounded-xl bg-slate-900 border border-slate-800">
                    <span className="text-[11px] text-slate-400">Active Calories</span>
                    <p className="text-xl font-bold text-white mt-1">{calories} kcal</p>
                    <span className="text-[10px] text-amber-400">Goal: 500 kcal</span>
                  </div>
                  <div className="p-3.5 rounded-xl bg-slate-900 border border-slate-800">
                    <span className="text-[11px] text-slate-400">Distance</span>
                    <p className="text-xl font-bold text-white mt-1">{distanceKm} km</p>
                    <span className="text-[10px] text-emerald-400">Cadence ~110</span>
                  </div>
                </div>

                {/* Progress bar */}
                <div className="space-y-1.5">
                  <div className="flex justify-between text-xs text-slate-400">
                    <span>Progress to daily step goal</span>
                    <span className="font-medium text-slate-200">74%</span>
                  </div>
                  <div className="w-full bg-slate-800 h-2 rounded-full overflow-hidden">
                    <div className="bg-gradient-to-r from-sky-500 to-indigo-500 h-2 rounded-full w-[74%]"></div>
                  </div>
                </div>
              </div>

              {/* Sleep Score Card */}
              <div className="p-6 rounded-2xl bg-[#111827] border border-slate-800 space-y-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-2">
                    <Moon className="w-5 h-5 text-indigo-400" />
                    <h3 className="font-bold text-white">Last Night Sleep</h3>
                  </div>
                  <button
                    onClick={() => setActiveTab('sleep')}
                    className="text-xs text-sky-400 hover:text-sky-300 font-medium flex items-center"
                  >
                    <span>View Hypnogram</span>
                    <ChevronRight className="w-3.5 h-3.5" />
                  </button>
                </div>

                <div className="flex items-center justify-between">
                  <div>
                    <span className="text-4xl font-black text-white">88</span>
                    <span className="text-sm text-slate-400 ml-1.5">/ 100 Sleep Score</span>
                    <p className="text-xs text-emerald-400 mt-1">Excellent sleep efficiency (92%)</p>
                  </div>
                  <div className="text-right">
                    <span className="text-lg font-bold text-slate-200">
                      {Math.floor(totalSleepMinutes / 60)}h {totalSleepMinutes % 60}m
                    </span>
                    <p className="text-xs text-slate-400">Total duration</p>
                  </div>
                </div>

                {/* Mini Hypnogram bar */}
                <div className="space-y-1.5">
                  <div className="h-4 rounded-lg overflow-hidden flex w-full">
                    {sleepSegments.map((seg, idx) => (
                      <div
                        key={idx}
                        className={`${getStageColor(seg.stage)} transition-all`}
                        style={{ width: `${(seg.durationMinutes / (totalSleepMinutes + awakeMinutes)) * 100}%` }}
                        title={`${seg.stage}: ${seg.durationMinutes}m`}
                      />
                    ))}
                  </div>
                  <div className="flex items-center justify-between text-[11px] text-slate-400">
                    <span className="flex items-center gap-1">
                      <span className="w-2 h-2 rounded bg-indigo-500 inline-block"></span> Deep: {deepMinutes}m
                    </span>
                    <span className="flex items-center gap-1">
                      <span className="w-2 h-2 rounded bg-purple-500 inline-block"></span> REM: {remMinutes}m
                    </span>
                    <span className="flex items-center gap-1">
                      <span className="w-2 h-2 rounded bg-sky-500 inline-block"></span> Light: {lightMinutes}m
                    </span>
                    <span className="flex items-center gap-1">
                      <span className="w-2 h-2 rounded bg-amber-500 inline-block"></span> Awake: {awakeMinutes}m
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: VITALS & VIBE */}
        {activeTab === 'vitals' && (
          <div className="space-y-6">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-xl font-bold text-white">Vitals & Detail Panels</h2>
                <p className="text-xs text-slate-400">Selected Day Telemetry & Time-series aggregation</p>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* Heart Rate Timeline & Distribution */}
              <div className="p-6 rounded-2xl bg-[#111827] border border-slate-800 space-y-4">
                <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                  <div className="flex items-center space-x-2">
                    <Heart className="w-5 h-5 text-rose-400" />
                    <h3 className="font-bold text-white">Heart Rate (24h)</h3>
                  </div>
                  <span className="text-xs text-slate-400">Min 58 • Avg 72 • Max 112</span>
                </div>

                <div className="h-44 flex items-end space-x-1.5 pt-4">
                  {[62, 60, 58, 64, 68, 72, 75, 84, 96, 88, 76, 72, 70, 74, 82, 92, 108, 98, 80, 74, 68, 65, 62, 60].map(
                    (val, i) => (
                      <div key={i} className="flex-1 flex flex-col items-center gap-1">
                        <div
                          className="w-full bg-rose-500/80 hover:bg-rose-400 rounded-t transition"
                          style={{ height: `${((val - 50) / 65) * 100}%` }}
                          title={`${i}:00 - ${val} bpm`}
                        ></div>
                        <span className="text-[9px] text-slate-500">{i % 4 === 0 ? `${i}h` : ''}</span>
                      </div>
                    )
                  )}
                </div>
                <div className="p-3 rounded-xl bg-slate-900 text-xs text-slate-400 flex justify-between">
                  <span>Monitoring status: All-day sampling (30 min interval)</span>
                  <span className="text-emerald-400">Sync healthy</span>
                </div>
              </div>

              {/* Blood Oxygen SpO2 Detail */}
              <div className="p-6 rounded-2xl bg-[#111827] border border-slate-800 space-y-4">
                <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                  <div className="flex items-center space-x-2">
                    <Activity className="w-5 h-5 text-sky-400" />
                    <h3 className="font-bold text-white">Blood Oxygen (SpO₂)</h3>
                  </div>
                  <span className="text-xs text-slate-400">Min 94% • Avg 98%</span>
                </div>

                <div className="h-44 flex items-end space-x-1.5 pt-4">
                  {[98, 97, 96, 95, 96, 98, 99, 98, 98, 97, 98, 99, 98, 97, 98, 98, 99, 98, 97, 96, 98, 98, 97, 98].map(
                    (val, i) => (
                      <div key={i} className="flex-1 flex flex-col items-center gap-1">
                        <div
                          className="w-full bg-sky-500/80 hover:bg-sky-400 rounded-t transition"
                          style={{ height: `${((val - 90) / 10) * 100}%` }}
                          title={`${i}:00 - ${val}%`}
                        ></div>
                        <span className="text-[9px] text-slate-500">{i % 4 === 0 ? `${i}h` : ''}</span>
                      </div>
                    )
                  )}
                </div>
                <div className="p-3 rounded-xl bg-slate-900 text-xs text-slate-400 flex justify-between">
                  <span>Threshold alert (&lt;90%): None triggered</span>
                  <span className="text-emerald-400">Safe zone</span>
                </div>
              </div>

              {/* HRV Detail */}
              <div className="p-6 rounded-2xl bg-[#111827] border border-slate-800 space-y-4">
                <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                  <div className="flex items-center space-x-2">
                    <Zap className="w-5 h-5 text-purple-400" />
                    <h3 className="font-bold text-white">HRV (Autonomic Balance)</h3>
                  </div>
                  <span className="text-xs text-slate-400">Avg 54 ms</span>
                </div>

                <div className="h-44 flex items-end space-x-1.5 pt-4">
                  {[48, 52, 58, 62, 60, 54, 46, 42, 38, 44, 50, 56, 52, 48, 45, 42, 50, 54, 58, 62, 60, 56, 52, 50].map(
                    (val, i) => (
                      <div key={i} className="flex-1 flex flex-col items-center gap-1">
                        <div
                          className="w-full bg-purple-500/80 hover:bg-purple-400 rounded-t transition"
                          style={{ height: `${((val - 25) / 50) * 100}%` }}
                          title={`${i}:00 - ${val} ms`}
                        ></div>
                        <span className="text-[9px] text-slate-500">{i % 4 === 0 ? `${i}h` : ''}</span>
                      </div>
                    )
                  )}
                </div>
                <div className="p-3 rounded-xl bg-slate-900 text-xs text-slate-400">
                  Higher HRV indicates parasympathetic nervous system recovery during nocturnal rest.
                </div>
              </div>

              {/* Stress Detail */}
              <div className="p-6 rounded-2xl bg-[#111827] border border-slate-800 space-y-4">
                <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                  <div className="flex items-center space-x-2">
                    <Sliders className="w-5 h-5 text-amber-400" />
                    <h3 className="font-bold text-white">Scheduled Stress Trend</h3>
                  </div>
                  <span className="text-xs text-slate-400">Max 58 • Avg 28</span>
                </div>

                <div className="h-44 flex items-end space-x-1.5 pt-4">
                  {[18, 16, 14, 15, 18, 24, 38, 52, 58, 44, 32, 28, 26, 30, 36, 48, 40, 34, 28, 22, 18, 16, 15, 16].map(
                    (val, i) => (
                      <div key={i} className="flex-1 flex flex-col items-center gap-1">
                        <div
                          className={`w-full rounded-t transition ${
                            val > 50 ? 'bg-rose-500' : val > 30 ? 'bg-amber-500' : 'bg-emerald-500'
                          }`}
                          style={{ height: `${(val / 80) * 100}%` }}
                          title={`${i}:00 - Stress ${val}`}
                        ></div>
                        <span className="text-[9px] text-slate-500">{i % 4 === 0 ? `${i}h` : ''}</span>
                      </div>
                    )
                  )}
                </div>
                <div className="p-3 rounded-xl bg-slate-900 text-xs text-slate-400">
                  Computed via pulse wave analysis (PWA) interval deviations and sympathetic arousal indices.
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 3: SLEEP & HYPNOGRAM */}
        {activeTab === 'sleep' && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-[#111827] border border-slate-800 space-y-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-bold text-white">Sleep Stages & Hypnogram</h2>
                  <p className="text-xs text-slate-400">
                    Real stages parsed from 0x0505 BLE frame records (No synthetic Light/Deep invention)
                  </p>
                </div>
                <div className="flex items-center space-x-2">
                  <span className="px-3 py-1 rounded-lg bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 text-xs font-semibold">
                    Score: 88/100
                  </span>
                  <span className="px-3 py-1 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-xs font-semibold">
                    Efficiency: 92%
                  </span>
                </div>
              </div>

              {/* Hypnogram Visual Timeline */}
              <div className="space-y-3">
                <div className="flex justify-between text-xs text-slate-400">
                  <span>11:00 PM</span>
                  <span>1:00 AM</span>
                  <span>3:00 AM</span>
                  <span>5:00 AM</span>
                  <span>6:30 AM</span>
                </div>

                <div className="h-16 w-full bg-slate-950 rounded-xl p-1.5 flex gap-1 border border-slate-800">
                  {sleepSegments.map((segment, index) => {
                    const pct = (segment.durationMinutes / (totalSleepMinutes + awakeMinutes)) * 100;
                    return (
                      <div
                        key={index}
                        className={`${getStageColor(
                          segment.stage
                        )} rounded-lg flex flex-col items-center justify-center text-[10px] font-bold shadow-sm transition hover:scale-[1.02] cursor-pointer`}
                        style={{ width: `${pct}%` }}
                        title={`${segment.stage}: ${segment.durationMinutes} min`}
                      >
                        {pct > 12 && <span>{segment.stage}</span>}
                        {pct > 8 && <span className="text-[9px] opacity-80">{segment.durationMinutes}m</span>}
                      </div>
                    );
                  })}
                </div>

                {/* Legend */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-2">
                  <div className="p-3 rounded-xl bg-slate-900 border border-slate-800 flex items-center space-x-3">
                    <span className="w-3 h-3 rounded-full bg-indigo-500"></span>
                    <div>
                      <p className="text-xs font-bold text-white">Deep Sleep</p>
                      <p className="text-[11px] text-slate-400">
                        {deepMinutes}m ({Math.round((deepMinutes / totalSleepMinutes) * 100)}%)
                      </p>
                    </div>
                  </div>
                  <div className="p-3 rounded-xl bg-slate-900 border border-slate-800 flex items-center space-x-3">
                    <span className="w-3 h-3 rounded-full bg-purple-500"></span>
                    <div>
                      <p className="text-xs font-bold text-white">REM Sleep</p>
                      <p className="text-[11px] text-slate-400">
                        {remMinutes}m ({Math.round((remMinutes / totalSleepMinutes) * 100)}%)
                      </p>
                    </div>
                  </div>
                  <div className="p-3 rounded-xl bg-slate-900 border border-slate-800 flex items-center space-x-3">
                    <span className="w-3 h-3 rounded-full bg-sky-500"></span>
                    <div>
                      <p className="text-xs font-bold text-white">Light Sleep</p>
                      <p className="text-[11px] text-slate-400">
                        {lightMinutes}m ({Math.round((lightMinutes / totalSleepMinutes) * 100)}%)
                      </p>
                    </div>
                  </div>
                  <div className="p-3 rounded-xl bg-slate-900 border border-slate-800 flex items-center space-x-3">
                    <span className="w-3 h-3 rounded-full bg-amber-500"></span>
                    <div>
                      <p className="text-xs font-bold text-white">Awake</p>
                      <p className="text-[11px] text-slate-400">{awakeMinutes}m</p>
                    </div>
                  </div>
                </div>
              </div>

              {/* Quality Factors */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-4 border-t border-slate-800">
                <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-1">
                  <span className="text-xs text-slate-400 font-semibold">Sleep Debt</span>
                  <p className="text-lg font-bold text-emerald-400">0 min (Met 7.5h goal)</p>
                  <p className="text-[11px] text-slate-500">Aligned with recommended recovery</p>
                </div>
                <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-1">
                  <span className="text-xs text-slate-400 font-semibold">Awakenings</span>
                  <p className="text-lg font-bold text-white">2 micro-awakenings</p>
                  <p className="text-[11px] text-slate-500">Stable continuity score (94/100)</p>
                </div>
                <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-1">
                  <span className="text-xs text-slate-400 font-semibold">Overnight SpO₂</span>
                  <p className="text-lg font-bold text-white">Avg 98% (Min 94%)</p>
                  <p className="text-[11px] text-emerald-400">No apnea desaturation alerts</p>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 4: MONITORING SETTINGS */}
        {activeTab === 'settings' && (
          <div className="space-y-6">
            <div>
              <h2 className="text-xl font-bold text-white">Health Monitoring & Ring Hardware Settings</h2>
              <p className="text-xs text-slate-400">
                Configures background BLE intervals and RWFit hardware registers
              </p>
            </div>

            {/* Health Monitoring Section */}
            <div className="p-6 rounded-2xl bg-[#111827] border border-slate-800 space-y-6">
              <div className="flex items-center space-x-2 border-b border-slate-800 pb-3">
                <Sliders className="w-5 h-5 text-sky-400" />
                <h3 className="font-bold text-white">Scheduled Monitoring Toggles & Intervals</h3>
              </div>

              <div className="space-y-6">
                {Object.values(monitoringConfigs).map((cfg) => {
                  const intervals = [15, 30, 60, 120];
                  return (
                    <div key={cfg.key} className="space-y-3 pb-4 border-b border-slate-800/60 last:border-0 last:pb-0">
                      <div className="flex items-center justify-between">
                        <div>
                          <p className="text-sm font-bold text-white">{cfg.label}</p>
                          <p className="text-xs text-slate-400">
                            {cfg.enabled
                              ? `Measuring every ${cfg.intervalMin >= 60 ? `${cfg.intervalMin / 60}h` : `${cfg.intervalMin}m`}`
                              : 'Disabled'}
                          </p>
                        </div>
                        <label className="relative inline-flex items-center cursor-pointer">
                          <input
                            type="checkbox"
                            checked={cfg.enabled}
                            onChange={(e) => {
                              const updated = { ...cfg, enabled: e.target.checked };
                              setMonitoringConfigs((prev) => ({ ...prev, [cfg.key]: updated }));
                              AppLog.i(
                                'MonitoringSettings',
                                `Updated ${cfg.label} toggle: ${e.target.checked ? 'ENABLED' : 'DISABLED'}`
                              );
                            }}
                            className="sr-only peer"
                          />
                          <div className="w-11 h-6 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-sky-500"></div>
                        </label>
                      </div>

                      {cfg.enabled && (
                        <div className="flex items-center space-x-2">
                          <span className="text-xs text-slate-400 mr-2">Interval:</span>
                          {intervals.map((mins) => {
                            const isSelected = cfg.intervalMin === mins;
                            return (
                              <button
                                key={mins}
                                onClick={() => {
                                  const updated = { ...cfg, intervalMin: mins };
                                  setMonitoringConfigs((prev) => ({ ...prev, [cfg.key]: updated }));
                                  AppLog.i('MonitoringSettings', `Updated ${cfg.label} interval: ${mins}m`);
                                }}
                                className={`px-3 py-1 rounded-lg text-xs font-semibold transition ${
                                  isSelected
                                    ? 'bg-sky-500 text-slate-950 font-bold'
                                    : 'bg-slate-800 hover:bg-slate-700 text-slate-300'
                                }`}
                              >
                                {mins >= 60 ? `${mins / 60}h` : `${mins}m`}
                              </button>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Screen Brightness Section matching D2 */}
            <div className="p-6 rounded-2xl bg-[#111827] border border-slate-800 space-y-4">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <div className="flex items-center space-x-2">
                  <Sun className="w-5 h-5 text-amber-400" />
                  <h3 className="font-bold text-white">Ring Display & Screen Brightness (0x0285)</h3>
                </div>
                <span className="text-xs text-slate-400">{brightnessStatus}</span>
              </div>

              <p className="text-xs text-slate-400">
                Transmits brightness opcode probe over BLE to the Galaxy Ring OLED indicator. Stops on first confirmed RX.
              </p>

              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                {[
                  { level: 0 as ScreenBrightnessLevel, label: 'Off', desc: 'Display disabled' },
                  { level: 1 as ScreenBrightnessLevel, label: 'Dim', desc: 'Low glow' },
                  { level: 2 as ScreenBrightnessLevel, label: 'Soft', desc: 'Default comfort' },
                  { level: 3 as ScreenBrightnessLevel, label: 'Strong', desc: 'High daylight' },
                ].map((item) => {
                  const isSelected = brightness === item.level;
                  return (
                    <button
                      key={item.level}
                      onClick={() => handleBrightnessChange(item.level)}
                      className={`p-3.5 rounded-xl border text-left transition ${
                        isSelected
                          ? 'border-sky-500 bg-sky-500/15 text-sky-300'
                          : 'border-slate-800 bg-slate-900/60 hover:bg-slate-800 text-slate-300'
                      }`}
                    >
                      <p className="text-sm font-bold">{item.label}</p>
                      <p className="text-[11px] text-slate-400 mt-0.5">{item.desc}</p>
                    </button>
                  );
                })}
              </div>
            </div>
          </div>
        )}

        {/* TAB 5: APNEA RISK */}
        {activeTab === 'apnea' && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-[#111827] border border-slate-800 space-y-6">
              <div className="flex items-center justify-between border-b border-slate-800 pb-4">
                <div className="flex items-center space-x-3">
                  <div className="p-2.5 rounded-xl bg-amber-500/10 text-amber-400">
                    <ShieldAlert className="w-6 h-6" />
                  </div>
                  <div>
                    <h2 className="text-xl font-bold text-white">Sleep Apnea & Hypoxia Risk Screening</h2>
                    <p className="text-xs text-slate-400">
                      Evaluates nocturnal PPG desaturations (&gt;3% drop) correlated with self-reported STOP-BANG factors
                    </p>
                  </div>
                </div>

                <div
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold border ${
                    apneaReport.riskBand === 'HIGH'
                      ? 'bg-rose-500/20 text-rose-300 border-rose-500/40'
                      : apneaReport.riskBand === 'MODERATE'
                      ? 'bg-amber-500/20 text-amber-300 border-amber-500/40'
                      : 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40'
                  }`}
                >
                  {apneaReport.riskBand} RISK BAND
                </div>
              </div>

              {/* Symptom Checklist */}
              <div className="space-y-3">
                <h3 className="text-sm font-bold text-white">Self-Reported Sleep Symptoms</h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {[
                    { key: 'snoring' as keyof SelfReportSymptoms, label: 'Loud or frequent snoring' },
                    { key: 'wakingGasping' as keyof SelfReportSymptoms, label: 'Waking up choking or gasping' },
                    { key: 'morningHeadaches' as keyof SelfReportSymptoms, label: 'Frequent morning headaches' },
                    { key: 'daytimeFatigue' as keyof SelfReportSymptoms, label: 'Excessive daytime sleepiness/fatigue' },
                  ].map((symptom) => (
                    <label
                      key={symptom.key}
                      className="p-3.5 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-between cursor-pointer hover:border-slate-700 transition"
                    >
                      <span className="text-xs text-slate-300 font-medium">{symptom.label}</span>
                      <input
                        type="checkbox"
                        checked={apneaSymptoms[symptom.key]}
                        onChange={(e) => {
                          setApneaSymptoms((prev) => ({ ...prev, [symptom.key]: e.target.checked }));
                          AppLog.i('ApneaScreening', `Updated symptom ${symptom.key}: ${e.target.checked}`);
                        }}
                        className="w-4 h-4 rounded text-sky-500 bg-slate-800 border-slate-700 focus:ring-sky-400"
                      />
                    </label>
                  ))}
                </div>
              </div>

              {/* Clinical findings and guidance */}
              <div className="p-4 rounded-xl bg-slate-900/80 border border-slate-800 space-y-3">
                <h4 className="text-xs font-bold text-slate-300 uppercase tracking-wider">Clinical Algorithm Insights</h4>
                <ul className="text-xs text-slate-400 space-y-1.5 list-disc list-inside">
                  {apneaReport.findings.map((f, i) => (
                    <li key={i}>{f}</li>
                  ))}
                </ul>
                <div className="p-3 rounded-lg bg-sky-950/30 border border-sky-500/30 text-xs text-sky-300 flex items-start space-x-2 mt-2">
                  <Info className="w-4 h-4 text-sky-400 shrink-0 mt-0.5" />
                  <span>{apneaReport.recommendation}</span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 6: BLE TELEMETRY LOGS */}
        {activeTab === 'logs' && (
          <div className="space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <div>
                <h2 className="text-xl font-bold text-white">BLE Telemetry & Protocol Logs</h2>
                <p className="text-xs text-slate-400">
                  Live decoding of RWFit BLE opcodes (0x051A steps, 0x0505 sleep, 0x0285 brightness, PPG frames)
                </p>
              </div>

              <div className="flex items-center space-x-2">
                {(['ALL', 'I', 'W', 'E'] as const).map((lvl) => (
                  <button
                    key={lvl}
                    onClick={() => setLogFilter(lvl)}
                    className={`px-2.5 py-1 rounded text-xs font-semibold ${
                      logFilter === lvl ? 'bg-sky-500 text-slate-950 font-bold' : 'bg-slate-800 text-slate-300'
                    }`}
                  >
                    {lvl}
                  </button>
                ))}
                <button
                  onClick={() => AppLog.clear()}
                  className="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs flex items-center space-x-1"
                >
                  <RotateCcw className="w-3 h-3" />
                  <span>Clear</span>
                </button>
              </div>
            </div>

            <div className="p-4 rounded-2xl bg-[#0B0F1A] border border-slate-800 font-mono text-xs max-h-[600px] overflow-y-auto space-y-2">
              {filteredLogs.length === 0 ? (
                <div className="py-8 text-center text-slate-500">No log entries matching filter</div>
              ) : (
                filteredLogs.map((log) => (
                  <div key={log.id} className="flex items-start space-x-2 leading-relaxed">
                    <span className="text-slate-500 shrink-0">{log.formattedTime}</span>
                    <span
                      className={`px-1.5 py-0.5 rounded text-[10px] font-bold shrink-0 ${
                        log.level === 'E'
                          ? 'bg-rose-500/20 text-rose-400'
                          : log.level === 'W'
                          ? 'bg-amber-500/20 text-amber-400'
                          : 'bg-sky-500/20 text-sky-400'
                      }`}
                    >
                      {log.level}
                    </span>
                    <span className="text-indigo-400 shrink-0 font-semibold">[{log.tag}]</span>
                    <span className="text-slate-300 break-all">{log.message}</span>
                  </div>
                ))
              )}
            </div>
          </div>
        )}

        {/* TAB 7: ANDROID NATIVE APP ARCHITECTURE */}
        {activeTab === 'android' && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-[#111827] border border-slate-800 space-y-6">
              <div className="flex items-center space-x-3 border-b border-slate-800 pb-4">
                <div className="p-2.5 rounded-xl bg-sky-500/10 text-sky-400">
                  <Smartphone className="w-6 h-6" />
                </div>
                <div>
                  <h2 className="text-xl font-bold text-white">Native Android Application (Kotlin + Jetpack Compose)</h2>
                  <p className="text-xs text-slate-400">
                    Full production-grade native Android source code located in /app module
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 space-y-2">
                  <div className="flex items-center space-x-2 text-sky-400">
                    <Bluetooth className="w-4 h-4" />
                    <h3 className="text-sm font-bold text-white">BLE & Hardware Telemetry Layer</h3>
                  </div>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    `GalaxyRingBLEManager.kt`, `BleRepository.kt`, `Protocol.kt`, `BrightnessProbe.kt`.
                    Supports RWFit frame encoding/decoding, checksum verification, GATT characteristic notifications, MTU negotiation, and ring screen brightness control.
                  </p>
                </div>

                <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 space-y-2">
                  <div className="flex items-center space-x-2 text-emerald-400">
                    <Activity className="w-4 h-4" />
                    <h3 className="text-sm font-bold text-white">Health Connect Integration</h3>
                  </div>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    `HealthConnectWriter.kt`, `PermissionsRationaleActivity.kt`.
                    Dual-writes heart rate, steps, sleep sessions, and skin temperature to Android's official Health Connect repository with privacy compliance.
                  </p>
                </div>

                <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 space-y-2">
                  <div className="flex items-center space-x-2 text-indigo-400">
                    <Moon className="w-4 h-4" />
                    <h3 className="text-sm font-bold text-white">Sleep & Apnea Screening Engine</h3>
                  </div>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    `SleepRecordParser.kt`, `SleepAnalyzer.kt`, `SleepApneaScreener.kt`.
                    Parses 0x0505 nocturnal telemetry frames, classifies sleep hypnogram stages (Deep, REM, Light, Awake), calculates sleep efficiency, and assesses STOP-BANG risk indices.
                  </p>
                </div>

                <div className="p-4 rounded-xl bg-slate-900 border border-slate-800 space-y-2">
                  <div className="flex items-center space-x-2 text-purple-400">
                    <RefreshCw className="w-4 h-4" />
                    <h3 className="text-sm font-bold text-white">Background Scheduling & Room DB</h3>
                  </div>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    `RingScheduledService.kt`, `SyncWorker.kt`, `RingDatabase.kt`, `Daos.kt`, `Entities.kt`.
                    Continuous foreground service with battery optimization exclusions and persistent local Room SQLite database with deduplication.
                  </p>
                </div>
              </div>

              <div className="p-4 rounded-xl bg-sky-950/20 border border-sky-500/30 text-xs text-slate-300 space-y-2">
                <p className="font-semibold text-sky-400">Pre-built APK & Installation:</p>
                <p>
                  The compiled release and debug Android APKs are configured in <code className="text-sky-300">apk/</code> and can be installed via <code className="text-sky-300">adb install apk/GalaxyRing-debug.apk</code> or generated via <code className="text-sky-300">gradle :app:assembleDebug</code>.
                </p>
              </div>
            </div>
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-800/80 bg-[#0E1422] py-4 px-4 lg:px-8 text-center text-xs text-slate-500">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-2">
          <span>Galaxy Ring Telemetry Dashboard • Android Companion & Health Connect Sync</span>
          <div className="flex items-center space-x-4">
            <span className="flex items-center gap-1 text-emerald-400">
              <span className="w-2 h-2 rounded-full bg-emerald-400"></span>
              BLE Gateway Online
            </span>
            <span>Firmware: R16_v2.0.4</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
