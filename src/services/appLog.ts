export interface LogEntry {
  id: string;
  timestamp: number;
  formattedTime: string;
  level: 'I' | 'W' | 'E' | 'D';
  tag: string;
  message: string;
  payloadHex?: string;
}

const MAX_LOG_ENTRIES = 200;
let logListeners: ((logs: LogEntry[]) => void)[] = [];
let logs: LogEntry[] = [
  {
    id: 'log-0',
    timestamp: Date.now(),
    formattedTime: new Date().toLocaleTimeString(),
    level: 'I',
    tag: 'GalaxyRing',
    message: 'Galaxy Ring BleManager initialized with RWFit frame decoder',
  },
];

export const AppLog = {
  i(tag: string, message: string, payloadHex?: string) {
    AppLog.log('I', tag, message, payloadHex);
  },
  d(tag: string, message: string, payloadHex?: string) {
    AppLog.log('D', tag, message, payloadHex);
  },
  w(tag: string, message: string, payloadHex?: string) {
    AppLog.log('W', tag, message, payloadHex);
  },
  e(tag: string, message: string, payloadHex?: string) {
    AppLog.log('E', tag, message, payloadHex);
  },
  log(level: 'I' | 'W' | 'E' | 'D', tag: string, message: string, payloadHex?: string) {
    const entry: LogEntry = {
      id: `${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
      timestamp: Date.now(),
      formattedTime: new Date().toLocaleTimeString(),
      level,
      tag,
      message,
      payloadHex,
    };
    logs = [entry, ...logs.slice(0, MAX_LOG_ENTRIES - 1)];
    logListeners.forEach((fn) => fn([...logs]));
  },
  getLogs(): LogEntry[] {
    return [...logs];
  },
  clear() {
    logs = [];
    logListeners.forEach((fn) => fn([]));
  },
  subscribe(fn: (logs: LogEntry[]) => void) {
    logListeners.push(fn);
    fn([...logs]);
    return () => {
      logListeners = logListeners.filter((listener) => listener !== fn);
    };
  },
};
