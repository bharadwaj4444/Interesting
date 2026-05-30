package com.example.services

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class CrashDiagnosticReport(
    val id: String,
    val driverName: String,
    val timestamp: String,
    val caughtException: String,
    val memoryDump: Map<String, String>,
    val stackTraceExcerpt: String,
    val patchApplied: String,
    val recoveryDurationMs: Long,
    val healerUsed: String
)

enum class SupervisorStatus {
    STANDBY,
    ANALYZING,
    GEN_PATCH,
    REBOOTING
}

object SupervisorDaemon {
    private const val TAG = "SupervisorDaemon"

    private val _status = MutableStateFlow(SupervisorStatus.STANDBY)
    val status: StateFlow<SupervisorStatus> = _status.asStateFlow()

    private val _reports = MutableStateFlow<List<CrashDiagnosticReport>>(emptyList())
    val reports: StateFlow<List<CrashDiagnosticReport>> = _reports.asStateFlow()

    private val _heartbeatCounter = MutableStateFlow<Long>(0)
    val heartbeatCounter: StateFlow<Long> = _heartbeatCounter.asStateFlow()

    private var supervisorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // TTS Reference managed by UI
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    fun initTTS(context: Context) {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                isTtsInitialized = true
                speak("Hardware Abstraction System fail safe watchdog online.")
            }
        }
    }

    fun speak(text: String) {
        if (isTtsInitialized) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun shutdown() {
        supervisorJob?.cancel()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    fun startMonitoring() {
        supervisorJob?.cancel()
        supervisorJob = scope.launch {
            Log.d(TAG, "Supervisor Daemon Watchdog Process Started.")
            DriverEngineManager.addConsoleLog("[WATCHDOG] Guard Service Activated. Isolation: ACTIVE.")
            
            while (isActive) {
                delay(600) // Watchdog polling cycle
                _heartbeatCounter.value = _heartbeatCounter.value + 1

                val currentDrivers = DriverEngineManager.drivers.value
                val crashedDriver = currentDrivers.find { it.state == DriverState.CRASHED }

                if (crashedDriver != null) {
                    if (DriverEngineManager.isManualOverrideActive) {
                        // MANUAL OVERRIDE IS ON - Safety supervisor paused!
                        _status.value = SupervisorStatus.STANDBY
                        if (_heartbeatCounter.value % 10 == 0L) {
                            DriverEngineManager.addConsoleLog("⚠️ WARNING: Driver [${crashedDriver.name}] has CRASHED, but Autonomous Healing is blocked by active MANUAL OVERRIDE.")
                            speak("Emergency warning: ${crashedDriver.name} is offline. Manual override is active.")
                        }
                        continue
                    }

                    // Autonomic recovery loop activated!
                    Log.d(TAG, "Watchdog detected driver crash in module [${crashedDriver.id}]! Responding...")
                    DriverEngineManager.addConsoleLog("🚨 WATCHDOG EVENT: System anomaly in [${crashedDriver.name}]. Isolating logic node...")
                    speak("Alert: System abnormality detected in ${crashedDriver.name}. Isolating microcode register and engaging healing protocol...")

                    val startTime = System.currentTimeMillis()
                    _status.value = SupervisorStatus.ANALYZING
                    delay(800) // Simulate AST/Memory isolation analysis latency in watchdog

                    _status.value = SupervisorStatus.GEN_PATCH
                    DriverEngineManager.addConsoleLog("Supervisor: Analysing variables scope heap and AST instruction footprint...")

                    val rawError = crashedDriver.lastError ?: "Direct instruction register fault"
                    val exceptionLine = rawError.lines().find { it.contains("Exception") } ?: "UnknownException: AST logic fault"

                    // Execute Gemini API healing or local Fallback healing
                    val (healedCode, healerName) = GeminiHealer.healScript(
                        driverId = crashedDriver.id,
                        driverName = crashedDriver.name,
                        scriptCode = crashedDriver.scriptCode,
                        variables = crashedDriver.variables,
                        stackTrace = rawError
                    )

                    _status.value = SupervisorStatus.REBOOTING
                    delay(650) // Code generation & register reboot latch

                    // Deploy healed script
                    DriverEngineManager.deployUserScript(crashedDriver.id, healedCode)
                    
                    val recoveryTime = System.currentTimeMillis() - startTime
                    DriverEngineManager.addConsoleLog("✅ SUPERVISOR: Integrated hot patch successfully onto [${crashedDriver.name}] and re-booted runtime stack.")
                    speak("${crashedDriver.name} has been successfully repaired and reinitialized in $recoveryTime milliseconds.")

                    // Create structured diagnostic report
                    val report = CrashDiagnosticReport(
                        id = UUID.randomUUID().toString().substring(0, 8).uppercase(),
                        driverName = crashedDriver.name,
                        timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                        caughtException = exceptionLine.trim(),
                        memoryDump = crashedDriver.variables,
                        stackTraceExcerpt = rawError,
                        patchApplied = healedCode,
                        recoveryDurationMs = recoveryTime,
                        healerUsed = healerName
                    )

                    val currentReports = _reports.value.toMutableList()
                    currentReports.add(0, report)
                    _reports.value = currentReports

                    // Sync driver details to state HEALED or RUNNING
                    _status.value = SupervisorStatus.STANDBY
                }
            }
        }
    }
}
