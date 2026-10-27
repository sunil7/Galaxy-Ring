export type SleepStage = 'DEEP' | 'LIGHT' | 'REM' | 'AWAKE';

export interface RingDevice {
  name: string;
  address: string;
  rssi?: number;
  isConnected?: boolean;
  isBonded?: boolean;
}

export interface RingBattery {
  level: number;
  isCharging: boolean;
}

export type ConnectionState =
  | { status: 'DISCONNECTED' }
  | { status: 'SCANNING' }
  | { status: 'CONNECTING'; device: RingDevice }
  | { status: 'READY'; device: RingDevice; battery?: RingBattery }
  | { status: 'ERROR'; message: string };

export interface SleepStageRecord {
  stage: SleepStage;
  startTime: number;
  endTime: number;
}

export interface SleepSegment {
  stage: SleepStage;
  start: number;
  end: number;
  durationMinutes: number;
}

export interface SleepSession {
  startTime: number;
  endTime: number;
  stages: SleepStageRecord[];
}

export interface SleepAnalysis {
  score: number;
  totalSleepMinutes: number;
  targetDurationMinutes: number;
  durationScore: number;
  deepPercent: number;
  deepScore: number;
  remPercent: number;
  remScore: number;
  lightPercent: number;
  awakeMinutes: number;
  awakeningsCount: number;
  continuityScore: number;
  consistencyScore?: number;
  sleepDebtMinutes?: number;
  qualityTips: string[];
}

export interface HeartRateEntity {
  id?: number;
  timestamp: number;
  bpm: number;
}

export interface OxygenSaturationEntity {
  id?: number;
  timestamp: number;
  percentage: number;
}

export interface HrvEntity {
  id?: number;
  timestamp: number;
  hrvMs: number;
}

export interface StressEntity {
  id?: number;
  timestamp: number;
  stressScore: number;
}

export interface DailyStepsEntity {
  date: string;
  timestamp: number;
  steps: number;
  caloriesKcal: number;
  distanceMeters: number;
}

export interface SleepSessionEntity {
  id?: number;
  date: string;
  startTime: number;
  endTime: number;
  durationMinutes: number;
  deepMinutes: number;
  lightMinutes: number;
  remMinutes: number;
  awakeMinutes: number;
  sleepScore: number;
  syncedToHealthConnect: boolean;
}

export interface MonitorConfig {
  key: string;
  label: string;
  enabled: boolean;
  intervalMin: number;
  defaultIntervalMin: number;
}

export type ScreenBrightnessLevel = 0 | 1 | 2 | 3;

export interface SelfReportSymptoms {
  snoring: boolean;
  wakingGasping: boolean;
  morningHeadaches: boolean;
  daytimeFatigue: boolean;
}

export interface ApneaScreeningReport {
  riskBand: 'LOW' | 'MODERATE' | 'HIGH';
  confidenceScore: number;
  desaturationCount: number;
  lowestSpo2: number;
  minutesUnder90: number;
  findings: string[];
  recommendation: string;
}

export interface ManualMeasurementState {
  status: 'IDLE' | 'MEASURING' | 'COMPLETED' | 'ERROR';
  metric?: string;
  progress?: number;
  value?: number;
  unit?: string;
  errorMessage?: string;
}

export interface MetricPoint {
  time: string;
  value: number;
}
