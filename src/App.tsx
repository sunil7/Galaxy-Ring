import React, { useState } from 'react';
import { Activity, Moon, Heart, Droplets, Battery, Settings, RefreshCw, Zap } from 'lucide-react';

export default function App() {
  const [activeTab, setActiveTab] = useState<'home' | 'sleep' | 'heart' | 'settings'>('home');

  return (
    <div className="min-h-screen bg-[#090D16] text-slate-100 flex flex-col font-sans">
      {/* Top Header */}
      <header className="px-6 py-4 border-b border-slate-800/80 flex items-center justify-between">
        <div className="flex items-center space-x-3">
          <div className="w-9 h-9 rounded-full bg-sky-500/20 border border-sky-500/40 flex items-center justify-center">
            <Zap className="w-5 h-5 text-sky-400" />
          </div>
          <div>
            <h1 className="text-lg font-bold tracking-tight">Galaxy Ring</h1>
            <p className="text-xs text-slate-400">Health Telemetry & Background Sync</p>
          </div>
        </div>

        {/* Status Pill */}
        <div className="flex items-center space-x-2 bg-slate-900 border border-slate-800 rounded-full px-3 py-1.5 text-xs">
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
          <span className="text-slate-300 font-medium">Ready</span>
          <span className="text-slate-600">|</span>
          <div className="flex items-center space-x-1 text-slate-400">
            <Battery className="w-3.5 h-3.5" />
            <span>85%</span>
          </div>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 p-6 max-w-4xl w-full mx-auto space-y-6">
        {/* Navigation Tabs */}
        <div className="flex space-x-2 bg-slate-900/60 p-1 rounded-xl border border-slate-800">
          <button
            onClick={() => setActiveTab('home')}
            className={`flex-1 py-2 px-3 rounded-lg text-sm font-medium transition-all flex items-center justify-center space-x-2 ${
              activeTab === 'home'
                ? 'bg-sky-500/20 text-sky-400 border border-sky-500/30'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Activity className="w-4 h-4" />
            <span>Home</span>
          </button>
          <button
            onClick={() => setActiveTab('sleep')}
            className={`flex-1 py-2 px-3 rounded-lg text-sm font-medium transition-all flex items-center justify-center space-x-2 ${
              activeTab === 'sleep'
                ? 'bg-indigo-500/20 text-indigo-400 border border-indigo-500/30'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Moon className="w-4 h-4" />
            <span>Sleep</span>
          </button>
          <button
            onClick={() => setActiveTab('heart')}
            className={`flex-1 py-2 px-3 rounded-lg text-sm font-medium transition-all flex items-center justify-center space-x-2 ${
              activeTab === 'heart'
                ? 'bg-rose-500/20 text-rose-400 border border-rose-500/30'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Heart className="w-4 h-4" />
            <span>Heart</span>
          </button>
          <button
            onClick={() => setActiveTab('settings')}
            className={`flex-1 py-2 px-3 rounded-lg text-sm font-medium transition-all flex items-center justify-center space-x-2 ${
              activeTab === 'settings'
                ? 'bg-slate-700/40 text-slate-200 border border-slate-600/40'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Settings className="w-4 h-4" />
            <span>Settings</span>
          </button>
        </div>

        {/* Dashboard Grid */}
        {activeTab === 'home' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Steps Card */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-5 space-y-3">
              <div className="flex items-center justify-between text-slate-400 text-sm">
                <span className="flex items-center space-x-2">
                  <Activity className="w-4 h-4 text-emerald-400" />
                  <span>Daily Steps</span>
                </span>
                <span className="text-xs bg-emerald-500/10 text-emerald-400 px-2 py-0.5 rounded-full border border-emerald-500/20">
                  Goal: 10,000
                </span>
              </div>
              <div className="text-3xl font-extrabold tracking-tight text-slate-100">6,420</div>
              <p className="text-xs text-slate-400">4.8 km • 245 kcal burned</p>
            </div>

            {/* Heart Rate Card */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-5 space-y-3">
              <div className="flex items-center justify-between text-slate-400 text-sm">
                <span className="flex items-center space-x-2">
                  <Heart className="w-4 h-4 text-rose-400" />
                  <span>Last Heart Rate</span>
                </span>
                <span className="text-xs text-slate-500">Scheduled (30m)</span>
              </div>
              <div className="text-3xl font-extrabold tracking-tight text-slate-100">
                72 <span className="text-base font-normal text-slate-400">BPM</span>
              </div>
              <p className="text-xs text-slate-400">Resting range: 58 - 84 BPM</p>
            </div>

            {/* SpO2 Card */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-5 space-y-3">
              <div className="flex items-center justify-between text-slate-400 text-sm">
                <span className="flex items-center space-x-2">
                  <Droplets className="w-4 h-4 text-sky-400" />
                  <span>Blood Oxygen (SpO₂)</span>
                </span>
                <span className="text-xs text-slate-500">Scheduled (60m)</span>
              </div>
              <div className="text-3xl font-extrabold tracking-tight text-slate-100">
                98 <span className="text-base font-normal text-slate-400">%</span>
              </div>
              <p className="text-xs text-slate-400">Normal healthy range (95 - 100%)</p>
            </div>

            {/* Sleep Card */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-5 space-y-3">
              <div className="flex items-center justify-between text-slate-400 text-sm">
                <span className="flex items-center space-x-2">
                  <Moon className="w-4 h-4 text-indigo-400" />
                  <span>Last Night Sleep</span>
                </span>
                <span className="text-xs bg-indigo-500/10 text-indigo-400 px-2 py-0.5 rounded-full border border-indigo-500/20">
                  Score: 84
                </span>
              </div>
              <div className="text-3xl font-extrabold tracking-tight text-slate-100">
                7h 24m
              </div>
              <p className="text-xs text-slate-400">Real stages from Galaxy Ring</p>
            </div>
          </div>
        )}

        {/* Settings Tab */}
        {activeTab === 'settings' && (
          <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-6 space-y-6">
            <h2 className="text-base font-semibold text-slate-200">Scheduled Health Monitoring</h2>
            <div className="space-y-4">
              <div className="flex items-center justify-between py-2 border-b border-slate-800/60">
                <div>
                  <div className="text-sm font-medium">All-day Heart Rate</div>
                  <div className="text-xs text-slate-400">Runs every 30 minutes</div>
                </div>
                <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  Active (30m)
                </span>
              </div>

              <div className="flex items-center justify-between py-2 border-b border-slate-800/60">
                <div>
                  <div className="text-sm font-medium">Regular Blood Oxygen (SpO₂)</div>
                  <div className="text-xs text-slate-400">Runs every 60 minutes</div>
                </div>
                <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  Active (60m)
                </span>
              </div>

              <div className="flex items-center justify-between py-2 border-b border-slate-800/60">
                <div>
                  <div className="text-sm font-medium">Screen Brightness</div>
                  <div className="text-xs text-slate-400">Off | Dim | Soft | Strong (BLE 0x0285)</div>
                </div>
                <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-sky-500/10 text-sky-400 border border-sky-500/20">
                  Strong light
                </span>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'sleep' && (
          <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-6 space-y-4">
            <h2 className="text-base font-semibold text-slate-200">Sleep Analysis</h2>
            <p className="text-sm text-slate-400">
              Parsed from ring 0x0505 records (Light, Deep, REM, Awake).
            </p>
          </div>
        )}

        {activeTab === 'heart' && (
          <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-6 space-y-4">
            <h2 className="text-base font-semibold text-slate-200">Heart Rate & SpO₂ Timelines</h2>
            <p className="text-sm text-slate-400">
              MetricDetailPanel 24-hour timeline and automated interval checks.
            </p>
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="px-6 py-4 border-t border-slate-800/80 text-center text-xs text-slate-500">
        Galaxy Ring Health Platform
      </footer>
    </div>
  );
}
