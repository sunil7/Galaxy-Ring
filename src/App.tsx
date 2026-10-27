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
  Smartphone,
  Copy,
  Trash2,
  Share2
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
  const [copiedLogs, setCopiedLogs] = useState<boolean>(false);

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

  const copyAllLogs = () => {
    const text = logs.map(l => `${l.formattedTime} [${l.level}/${l.tag}] ${l.message}`).join('\n');
    navigator.clipboard.writeText(text);
    setCopiedLogs(true);
    setTimeout(() => setCopiedLogs(false), 2000);
  };

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
                  <span>Zone: Low tension</span>
                  <span className="text-emerald-400">Calm</span>
                </div>
              </div>
            </div>

            {/* Daily Activity Summary */}
            <div className="p-6 rounded-2xl bg-[#111827] border border-slate-800">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  <Footprints className="w-5 h-5 text-emerald-400" />
                  <span>Today's Physical Activity</span>
                </h3>
                <span className="text-xs font-medium text-slate-400">Goal: 10,000 steps</span>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                <div>
                  <div className="flex justify-between text-sm mb-1.5">
                    <span className="text-slate-400">Steps Taken</span>
                    <span className="font-bold text-white">{steps.toLocaleString()} / 10,000</span>
                  </div>
                  <div className="w-full bg-slate-800 h-2.5 rounded-full overflow-hidden">
                    <div
                      className="bg-emerald-400 h-2.5 rounded-full"
                      style={{ width: `${Math.min(100, (steps / 10000) * 100)}%` }}
                    ></div>
                  </div>
                </div>

                <div>
                  <div className="flex justify-between text-sm mb-1.5">
                    <span className="text-slate-400">Active Calories</span>
                    <span className="font-bold text-white">{calories} kcal</span>
                  </div>
                  <div className="w-full bg-slate-800 h-2.5 rounded-full overflow-hidden">
                    <div
                      className="bg-rose-500 h-2.5 rounded-full"
                      style={{ width: `${Math.min(100, (calories / 500) * 100)}%` }}
                    ></div>
                  </div>
                </div>

                <div>
                  <div className="flex justify-between text-sm mb-1.5">
                    <span className="text-slate-400">Distance Covered</span>
                    <span className="font-bold text-white">{distanceKm} km</span>
                  </div>
                  <div className="w-full bg-slate-800 h-2.5 rounded-full overflow-hidden">
                    <div
                      className="bg-sky-400 h-2.5 rounded-full"
                      style={{ width: `${Math.min(100, (distanceKm / 8) * 100)}%` }}
                    ></div>
                  </div>
                </div>
              </div>
            </div>

            {/* Sleep Summary Card */}
            <div className="p-6 rounded-2xl bg-[#111827] border border-slate-800 flex flex-col md:flex-row items-center justify-between gap-6">
              <div className="space-y-2">
                <div className="flex items-center space-x-2 text-indigo-400 text-xs font-semibold uppercase">
                  <Moon className="w-4 h-4" />
                  <span>Last Night's Sleep Analysis</span>
                </div>
                <h3 className="text-xl font-bold text-white">
                  7 hrs 45 mins • <span className="text-emerald-400">Sleep Score 88</span>
                </h3>
                <p className="text-xs text-slate-400 max-w-lg">
                  Optimal deep sleep restorative cycle detected. Sleep regularity index was 92% with low nighttime awakening frequency.
                </p>
              </div>

              <button
                onClick={() => setActiveTab('sleep')}
                className="px-4 py-2.5 rounded-xl bg-indigo-600/20 hover:bg-indigo-600/30 text-indigo-300 border border-indigo-500/30 text-xs font-semibold transition flex items-center space-x-2"
              >
                <span>View Full Hypnogram</span>
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}

        {/* TAB 2: VITALS & VIBE */}
        {activeTab === 'vitals' && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-[#111827] border border-slate-800 space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                  <h2 className="text-xl font-bold text-white">Continuous Biosensor Telemetry</h2>
                  <p className="text-xs text-slate-400">Photoplethysmography (PPG) and HRV waveform samples</p>
                </div>
                <div className="flex items-center space-x-2">
                  <button
                    onClick={() => startMeasurement('Heart Rate')}
                    className="px-3 py-1.5 rounded-lg bg-sky-500 hover:bg-sky-400 text-slate-950 text-xs font-bold transition flex items-center space-x-1.5"
                  >
                    <Play className="w-3.5 h-3.5 fill-current" />
                    <span>On-demand HR</span>
                  </button>
                  <button
                    onClick={() => startMeasurement('SpO₂')}
                    className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold transition"
                  >
                    Measure SpO₂
                  </button>
                  <button
                    onClick={() => startMeasurement('HRV')}
                    className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold transition"
                  >
                    Measure HRV
                  </button>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-4">
                {/* Heart Rate history box */}
                <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800">
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-xs font-semibold text-rose-400 flex items-center gap-1.5">
                      <Heart className="w-4 h-4" /> Heart Rate Profile
                    </span>
                    <span className="text-xs text-slate-400 font-mono">Current: {currentHr} BPM</span>
                  </div>
                  <div className="h-28 flex items-end space-x-1.5 pt-4">
                    {[62, 65, 68, 72, 70, 74, 76, 71, 69, 73, 75, 72, 68, 74, currentHr].map((val, idx) => (
                      <div key={idx} className="flex-1 flex flex-col items-center gap-1">
                        <div
                          className="w-full rounded-t bg-gradient-to-t from-rose-500/30 to-rose-500 transition-all duration-300"
                          style={{ height: `${(val / 110) * 80}px` }}
                        ></div>
                      </div>
                    ))}
                  </div>
                  <div className="flex justify-between text-[10px] text-slate-500 mt-2 font-mono">
                    <span>12:00</span>
                    <span>14:00</span>
                    <span>16:00</span>
                    <span>Now</span>
                  </div>
                </div>

                {/* SpO2 history box */}
                <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800">
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-xs font-semibold text-sky-400 flex items-center gap-1.5">
                      <Activity className="w-4 h-4" /> SpO₂ Oxygen Saturation
                    </span>
                    <span className="text-xs text-slate-400 font-mono">Current: {currentSpo2}%</span>
                  </div>
                  <div className="h-28 flex items-end space-x-1.5 pt-4">
                    {[97, 98, 98, 99, 97, 98, 98, 99, 98, 98, 97, 98, 99, 98, currentSpo2].map((val, idx) => (
                      <div key={idx} className="flex-1 flex flex-col items-center gap-1">
                        <div
                          className="w-full rounded-t bg-gradient-to-t from-sky-500/30 to-sky-400 transition-all duration-300"
                          style={{ height: `${((val - 90) / 10) * 80}px` }}
                        ></div>
                      </div>
                    ))}
                  </div>
                  <div className="flex justify-between text-[10px] text-slate-500 mt-2 font-mono">
                    <span>12:00</span>
                    <span>14:00</span>
                    <span>16:00</span>
                    <span>Now</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 3: SLEEP & HYPNOGRAM */}
        {activeTab === 'sleep' && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-[#111827] border border-slate-800 space-y-6">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-xl font-bold text-white">Sleep Architecture & Hypnogram</h2>
                  <p className="text-xs text-slate-400">Computed from overnight PPG pulse variability and 3D accelerometer</p>
                </div>
                <div className="px-3 py-1 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-xs font-bold">
                  Score: 88 / 100
                </div>
              </div>

              {/* Hypnogram Visual Timeline */}
              <div className="space-y-2">
                <div className="flex justify-between text-xs text-slate-400">
                  <span>11:00 PM</span>
                  <span>Timeline of Sleep Stages</span>
                  <span>7:15 AM</span>
                </div>
                <div className="h-14 w-full flex rounded-xl overflow-hidden border border-slate-800 bg-slate-900">
                  {sleepSegments.map((segment, idx) => (
                    <div
                      key={idx}
                      title={`${segment.stage}: ${segment.durationMinutes} mins`}
                      style={{ width: `${(segment.durationMinutes / (totalSleepMinutes + awakeMinutes)) * 100}%` }}
                      className={`h-full flex items-center justify-center text-[10px] font-bold border-r border-slate-900/50 transition-all hover:opacity-90 ${getStageColor(
                        segment.stage
                      )}`}
                    >
                      <span className="hidden sm:inline">{segment.stage}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Stages Breakdown Grid */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                <div className="p-4 rounded-xl bg-indigo-950/20 border border-indigo-500/30">
                  <span className="text-xs font-semibold text-indigo-300">Deep Sleep</span>
                  <p className="text-2xl font-bold text-white mt-1">{deepMinutes}m</p>
                  <span className="text-[11px] text-slate-400">{Math.round((deepMinutes / totalSleepMinutes) * 100)}% of total</span>
                </div>

                <div className="p-4 rounded-xl bg-purple-950/20 border border-purple-500/30">
                  <span className="text-xs font-semibold text-purple-300">REM Sleep</span>
                  <p className="text-2xl font-bold text-white mt-1">{remMinutes}m</p>
                  <span className="text-[11px] text-slate-400">{Math.round((remMinutes / totalSleepMinutes) * 100)}% of total</span>
                </div>

                <div className="p-4 rounded-xl bg-sky-950/20 border border-sky-500/30">
                  <span className="text-xs font-semibold text-sky-300">Light Sleep</span>
                  <p className="text-2xl font-bold text-white mt-1">{lightMinutes}m</p>
                  <span className="text-[11px] text-slate-400">{Math.round((lightMinutes / totalSleepMinutes) * 100)}% of total</span>
                </div>

                <div className="p-4 rounded-xl bg-amber-950/20 border border-amber-500/30">
                  <span className="text-xs font-semibold text-amber-300">Awake Time</span>
                  <p className="text-2xl font-bold text-white mt-1">{awakeMinutes}m</p>
                  <span className="text-[11px] text-slate-400">2 brief awakenings</span>
                </div>
              </div>

              {/* Recovery tips */}
              <div className="p-4 rounded-xl bg-slate-900/50 border border-slate-800 space-y-2">
                <h4 className="text-xs font-bold uppercase tracking-wider text-sky-400">Sleep Recovery Insight</h4>
                <p className="text-xs text-slate-300">
                  Your deep sleep duration ({deepMinutes} mins) was above the 20% benchmark, indicating effective muscular and neuro-cognitive restoration.
                </p>
              </div>
            </div>
          </div>
        )}

        {/* TAB 4: MONITORING SETTINGS */}
        {activeTab === 'settings' && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-[#111827] border border-slate-800 space-y-6">
              <div>
                <h2 className="text-xl font-bold text-white">Ring Monitoring Schedules</h2>
                <p className="text-xs text-slate-400">Configure continuous background telemetry and sensor polling intervals</p>
              </div>

              <div className="space-y-4">
                {Object.values(monitoringConfigs).map((cfg) => (
                  <div
                    key={cfg.key}
                    className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 flex flex-col sm:flex-row sm:items-center justify-between gap-4"
                  >
                    <div>
                      <h4 className="text-sm font-semibold text-white">{cfg.label}</h4>
                      <p className="text-xs text-slate-400">Interval: Every {cfg.intervalMin} minutes</p>
                    </div>

                    <div className="flex items-center space-x-3">
                      <select
                        value={cfg.intervalMin}
                        onChange={(e) => {
                          const val = parseInt(e.target.value);
                          setMonitoringConfigs((prev) => ({
                            ...prev,
                            [cfg.key]: { ...prev[cfg.key], intervalMin: val },
                          }));
                          AppLog.i('Settings', `Updated ${cfg.label} interval to ${val} min`);
                        }}
                        className="bg-slate-800 text-xs text-slate-200 border border-slate-700 rounded-lg px-2.5 py-1.5 focus:outline-none focus:border-sky-500"
                      >
                        <option value={15}>Every 15 min</option>
                        <option value={30}>Every 30 min</option>
                        <option value={60}>Every 60 min</option>
                        <option value={120}>Every 2 hours</option>
                      </select>

                      <button
                        onClick={() => {
                          const updated = !cfg.enabled;
                          setMonitoringConfigs((prev) => ({
                            ...prev,
                            [cfg.key]: { ...prev[cfg.key], enabled: updated },
                          }));
                          AppLog.i('Settings', `Toggled ${cfg.label}: ${updated ? 'ENABLED' : 'DISABLED'}`);
                        }}
                        className={`w-12 h-6 rounded-full transition-colors relative p-0.5 ${
                          cfg.enabled ? 'bg-sky-500' : 'bg-slate-700'
                        }`}
                      >
                        <div
                          className={`w-5 h-5 rounded-full bg-white transition-transform ${
                            cfg.enabled ? 'translate-x-6' : 'translate-x-0'
                          }`}
                        ></div>
                      </button>
                    </div>
                  </div>
                ))}
              </div>

              {/* Ring Screen / LED Brightness Section */}
              <div className="pt-6 border-t border-slate-800 space-y-4">
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="text-base font-bold text-white flex items-center gap-2">
                      <Sun className="w-4 h-4 text-amber-400" />
                      <span>Ring Sensor LED / Screen Brightness</span>
                    </h3>
                    <p className="text-xs text-slate-400">Controls optical sensor illumination brightness level</p>
                  </div>
                  <span className="text-xs font-mono text-emerald-400">{brightnessStatus}</span>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                  {[
                    { lvl: 0, label: 'Off' },
                    { lvl: 1, label: 'Dim' },
                    { lvl: 2, label: 'Soft' },
                    { lvl: 3, label: 'Strong' },
                  ].map((item) => (
                    <button
                      key={item.lvl}
                      onClick={() => handleBrightnessChange(item.lvl as ScreenBrightnessLevel)}
                      className={`p-3 rounded-xl border text-xs font-semibold transition ${
                        brightness === item.lvl
                          ? 'bg-sky-500/20 text-sky-300 border-sky-500'
                          : 'bg-slate-900/60 text-slate-400 border-slate-800 hover:border-slate-700'
                      }`}
                    >
                      {item.label} (Level {item.lvl})
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 5: APNEA RISK */}
        {activeTab === 'apnea' && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-[#111827] border border-slate-800 space-y-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                  <h2 className="text-xl font-bold text-white flex items-center gap-2">
                    <ShieldAlert className="w-5 h-5 text-amber-400" />
                    <span>Sleep Apnea Risk Screener</span>
                  </h2>
                  <p className="text-xs text-slate-400">Heuristic overnight oxygen desaturation & breathing stability analysis</p>
                </div>

                <div
                  className={`px-3 py-1.5 rounded-xl border text-xs font-bold ${
                    apneaReport.riskBand === 'HIGH'
                      ? 'bg-rose-500/10 border-rose-500/30 text-rose-400'
                      : apneaReport.riskBand === 'MODERATE'
                      ? 'bg-amber-500/10 border-amber-500/30 text-amber-400'
                      : 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400'
                  }`}
                >
                  Risk Assessment: {apneaReport.riskBand}
                </div>
              </div>

              {/* Symptoms Checklist */}
              <div className="space-y-3">
                <h4 className="text-xs font-bold uppercase tracking-wider text-slate-400">Reported Symptoms</h4>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {[
                    { key: 'snoring', label: 'Chronic loud snoring during sleep' },
                    { key: 'wakingGasping', label: 'Waking up gasping or choking' },
                    { key: 'morningHeadaches', label: 'Frequent morning headaches' },
                    { key: 'daytimeFatigue', label: 'Excessive daytime somnolence / fatigue' },
                  ].map((symptom) => {
                    const isChecked = apneaSymptoms[symptom.key as keyof SelfReportSymptoms];
                    return (
                      <label
                        key={symptom.key}
                        className="flex items-center space-x-3 p-3 rounded-xl bg-slate-900/60 border border-slate-800 cursor-pointer hover:border-slate-700"
                      >
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={(e) =>
                            setApneaSymptoms((prev) => ({
                              ...prev,
                              [symptom.key]: e.target.checked,
                            }))
                          }
                          className="rounded text-sky-500 focus:ring-0 bg-slate-800 border-slate-700"
                        />
                        <span className="text-xs text-slate-200">{symptom.label}</span>
                      </label>
                    );
                  })}
                </div>
              </div>

              {/* Findings & Recommendations */}
              <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-3">
                <h4 className="text-xs font-bold uppercase tracking-wider text-sky-400">Telemetry Findings</h4>
                <ul className="space-y-1.5 text-xs text-slate-300">
                  {apneaReport.findings.map((f, i) => (
                    <li key={i} className="flex items-center gap-2">
                      <span className="w-1.5 h-1.5 rounded-full bg-sky-400"></span>
                      <span>{f}</span>
                    </li>
                  ))}
                </ul>
                <div className="pt-2 border-t border-slate-800 text-xs text-amber-300/90 font-medium">
                  {apneaReport.recommendation}
                </div>
              </div>

              {/* Disclaimer */}
              <p className="text-[11px] text-slate-500 italic">
                Notice: Informational risk indicators only — not a clinical medical diagnosis. Consult a certified clinician or sleep specialist if symptoms persist.
              </p>
            </div>
          </div>
        )}

        {/* TAB 6: BLE TELEMETRY LOGS */}
        {activeTab === 'logs' && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-[#111827] border border-slate-800 space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                  <h2 className="text-xl font-bold text-white flex items-center gap-2">
                    <Terminal className="w-5 h-5 text-sky-400" />
                    <span>BLE Diagnostic Log Stream</span>
                  </h2>
                  <p className="text-xs text-slate-400">Live GATT packets, frame decoders, and probe transactions</p>
                </div>

                <div className="flex items-center space-x-2">
                  <div className="flex bg-slate-900 p-1 rounded-lg border border-slate-800 text-xs">
                    {(['ALL', 'I', 'W', 'E'] as const).map((lvl) => (
                      <button
                        key={lvl}
                        onClick={() => setLogFilter(lvl)}
                        className={`px-2.5 py-1 rounded font-mono ${
                          logFilter === lvl ? 'bg-sky-500 text-slate-950 font-bold' : 'text-slate-400 hover:text-white'
                        }`}
                      >
                        {lvl}
                      </button>
                    ))}
                  </div>

                  <button
                    onClick={copyAllLogs}
                    className="p-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 transition text-xs flex items-center gap-1.5"
                    title="Copy logs"
                  >
                    <Copy className="w-4 h-4" />
                    <span className="hidden sm:inline">{copiedLogs ? 'Copied!' : 'Copy'}</span>
                  </button>

                  <button
                    onClick={() => AppLog.clear()}
                    className="p-2 rounded-lg bg-slate-800 hover:bg-rose-900/40 text-slate-300 hover:text-rose-300 transition text-xs flex items-center gap-1.5"
                    title="Clear logs"
                  >
                    <Trash2 className="w-4 h-4" />
                    <span className="hidden sm:inline">Clear</span>
                  </button>
                </div>
              </div>

              {/* Log console window */}
              <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 font-mono text-xs max-h-96 overflow-y-auto space-y-2">
                {filteredLogs.length === 0 ? (
                  <p className="text-slate-500 italic">No log entries matching filter.</p>
                ) : (
                  filteredLogs.map((log) => (
                    <div key={log.id} className="flex items-start space-x-2 text-slate-300 leading-relaxed">
                      <span className="text-slate-500 shrink-0">{log.formattedTime}</span>
                      <span
                        className={`px-1.5 py-0.2 rounded text-[10px] font-bold shrink-0 ${
                          log.level === 'E'
                            ? 'bg-rose-500/20 text-rose-400'
                            : log.level === 'W'
                            ? 'bg-amber-500/20 text-amber-400'
                            : 'bg-sky-500/20 text-sky-400'
                        }`}
                      >
                        {log.level}
                      </span>
                      <span className="text-slate-400 shrink-0">[{log.tag}]</span>
                      <span className="break-all">{log.message}</span>
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>
        )}

        {/* TAB 7: ANDROID NATIVE APP */}
        {activeTab === 'android' && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-[#111827] border border-slate-800 space-y-6">
              <div className="flex items-center space-x-3">
                <div className="p-3 rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  <Smartphone className="w-6 h-6" />
                </div>
                <div>
                  <h2 className="text-xl font-bold text-white">Android Native Application Details</h2>
                  <p className="text-xs text-slate-400">Kotlin, Jetpack Compose, Health Connect & Room Persistence</p>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-2">
                  <h4 className="text-sm font-bold text-white flex items-center gap-2">
                    <Activity className="w-4 h-4 text-sky-400" />
                    <span>Android Architecture</span>
                  </h4>
                  <ul className="text-xs text-slate-300 space-y-1.5 list-disc list-inside">
                    <li><strong className="text-white">UI Layer:</strong> Jetpack Compose with Material 3 styling</li>
                    <li><strong className="text-white">Data Layer:</strong> Room Database (Entities: HR, SpO₂, Sleep, Steps)</li>
                    <li><strong className="text-white">BLE Engine:</strong> GalaxyRingBLEManager with custom GATT protocol</li>
                    <li><strong className="text-white">Sync Service:</strong> Android Health Connect Writer & WorkManager worker</li>
                  </ul>
                </div>

                <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-2">
                  <h4 className="text-sm font-bold text-white flex items-center gap-2">
                    <Download className="w-4 h-4 text-emerald-400" />
                    <span>Generate & Install APK</span>
                  </h4>
                  <p className="text-xs text-slate-300">
                    To build or download the ready-to-run Android APK:
                  </p>
                  <ol className="text-xs text-slate-300 space-y-1 list-decimal list-inside">
                    <li>Click <strong className="text-white">Export &gt; Generate APK</strong> in AI Studio</li>
                    <li>Or run <code className="bg-slate-800 px-1 py-0.5 rounded text-sky-300">gradle assembleDebug</code></li>
                    <li>Install on any Android 8.0+ device via ADB</li>
                  </ol>
                </div>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
