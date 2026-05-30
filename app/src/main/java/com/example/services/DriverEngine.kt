package com.example.services

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.random.Random

enum class DriverState {
    RUNNING,
    PAUSED,
    CRASHED,
    HEALED,
    OPTIMIZING
}

data class VirtualDriver(
    val id: String,
    val name: String,
    val hardwareResource: String,
    val scriptCode: String,
    val state: DriverState,
    val latencyHistory: List<Float> = emptyList(),
    val throughputHistory: List<Float> = emptyList(),
    val variables: Map<String, String> = emptyMap(),
    val cyclesCount: Long = 0,
    val optimizationsCount: Int = 0,
    val lastError: String? = null,
    val patchApplied: String? = null,
    val isSystemCritical: Boolean = false
)

object DefaultScripts {
    val CPU_DRIVER = """
        // CPU CORE MICROCODE SchedULER V4.1
        SYSTEM_CLOCK_GOVERNOR = "ondemand"
        OPTIMIZATION_FACTOR = "1.0"
        CACHE_LOCK_DEPTH = "4"
        THREAD_POOL_SIZE = "8"
        CYCLES_BETWEEN_YIELDS = "25"
        
        // PARAMETER FOR LATENCY
        TICK_LATENCY_MS = "45"
        BUFFER_SIZE = "128"
        
        // WATCHDOG CHECKS
        IF_THERMAL_LIMIT_EXCEEDED:
            THROTTLE_MULTIPLIER = "2.0"
            TICK_LATENCY_MS = "90"
            
        // CRITICAL RUN REGISTER
        RUN_REGISTER_ADDR = "0x7F8EBC60"
        EXECUTE_DECODE_PHASE(RUN_REGISTER_ADDR)
    """.trimIndent()

    val SENSOR_DRIVER = """
        // SENSOR STREAM MULTIPLEXER & BUFFER ABSTRACTION
        ACCELEROMETER_FREQ_HZ = "50"
        GYROSCOPE_RATE_KBPS = "120"
        LIGHT_SENSOR_DEBOUNCE_MS = "200"
        BUFFER_ARRAY_SIZE = "10"
        CONCURRENT_READ_WORKERS = "2"
        TICK_LATENCY_MS = "25"
        
        // BUFFER READ POINTER
        ACTIVE_INDEX = "12"
        
        // FAULT THREAD DECODING
        READ_ARRAY_INDEX(BUFFER_ARRAY_SIZE, ACTIVE_INDEX)
    """.trimIndent()

    val STORAGE_DRIVER = """
         // STORAGE FS SECTOR WRITER & CACHE FLUSH CONTROLLER
         SECTOR_SEEK_MODE = "LBA_OPTIMIZED"
         CACHE_STRATEGY = "WRITE_BACK"
         RESERVE_CACHE_BLOCKS = "0"
         FLUSH_INTERVAL_CYCLES = "5"
         TICK_LATENCY_MS = "60"
         THROUGHPUT_SCALE = "1.2"
         
         // CACHE DEALLOCATION REGISTRY
         BLOCKS_TO_FLUSH = "0"
         
         // BLOCK FLUSH SEQUENCE
         DIVIDE_BY_VALUES(BLOCKS_TO_FLUSH, RESERVE_CACHE_BLOCKS)
    """.trimIndent()

    val NETWORK_DRIVER = """
        // DYNAMIC SOCKET CONGESTION WINDOW GATEWAY
        RETRY_LIMIT = "5"
        SLIDING_WINDOW_SIZE = "1024"
        TIMEOUT_MS = "5000"
        SOCKET_ALIVE = "true"
        TICK_LATENCY_MS = "120"
        MAX_RECURSION_DEPTH = "12"
        
        // NESTED ALIGN CHECKS
        SOCKET_CONNECTION_RETRIES = "6"
        
        // STACK MONITOR
        CHECK_STACK_CELL_ALIVE(SOCKET_CONNECTION_RETRIES, RETRY_LIMIT)
    """.trimIndent()
}

object DriverEngineManager {
    private val _drivers = MutableStateFlow<List<VirtualDriver>>(emptyList())
    val drivers: StateFlow<List<VirtualDriver>> = _drivers.asStateFlow()

    private val driverJobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Terminal/Console Logs
    private val _consoleLogs = MutableStateFlow<List<String>>(emptyList())
    val consoleLogs: StateFlow<List<String>> = _consoleLogs.asStateFlow()

    // Global performance tuning flag (True matches User full automated loop)
    var isManualOverrideActive: Boolean = false

    init {
        resetToDefault()
    }

    fun addConsoleLog(log: String) {
        val current = _consoleLogs.value.toMutableList()
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        current.add("[$timestamp] $log")
        if (current.size > 200) current.removeAt(0)
        _consoleLogs.value = current
    }

    fun resetToDefault() {
        driverJobs.values.forEach { it.cancel() }
        driverJobs.clear()

        val initialDrivers = listOf(
            VirtualDriver(
                id = "cpu",
                name = "CPU Core Microcode Scheduler",
                hardwareResource = "ARM Cortex Core Governance",
                scriptCode = DefaultScripts.CPU_DRIVER,
                state = DriverState.RUNNING,
                latencyHistory = List(30) { 45f + Random.nextFloat() * 4 },
                throughputHistory = List(30) { 300f + Random.nextFloat() * 30 },
                variables = mapOf(
                    "SYSTEM_CLOCK_GOVERNOR" to "ondemand",
                    "OPTIMIZATION_FACTOR" to "1.0",
                    "TICK_LATENCY_MS" to "45",
                    "BUFFER_SIZE" to "128",
                    "THREAD_POOL_SIZE" to "8"
                ),
                isSystemCritical = true
            ),
            VirtualDriver(
                id = "sensor",
                name = "Sensor Multiplexer & Buffer",
                hardwareResource = "Multi-axis IMU / Photometer Bus",
                scriptCode = DefaultScripts.SENSOR_DRIVER,
                state = DriverState.RUNNING,
                latencyHistory = List(30) { 25f + Random.nextFloat() * 2 },
                throughputHistory = List(30) { 1200f + Random.nextFloat() * 80 },
                variables = mapOf(
                    "ACCELEROMETER_FREQ_HZ" to "50",
                    "BUFFER_ARRAY_SIZE" to "10",
                    "ACTIVE_INDEX" to "9", // Safe originally
                    "TICK_LATENCY_MS" to "25"
                )
            ),
            VirtualDriver(
                id = "storage",
                name = "Storage FS Sector Block Writer",
                hardwareResource = "NAND Flash / Cache Blocks",
                scriptCode = DefaultScripts.STORAGE_DRIVER,
                state = DriverState.RUNNING,
                latencyHistory = List(30) { 60f + Random.nextFloat() * 5 },
                throughputHistory = List(30) { 450f + Random.nextFloat() * 40 },
                variables = mapOf(
                    "RESERVE_CACHE_BLOCKS" to "128", // Safe originally (no div-by-zero)
                    "BLOCKS_TO_FLUSH" to "0",
                    "TICK_LATENCY_MS" to "60"
                )
            ),
            VirtualDriver(
                id = "network",
                name = "Socket Sliding Window Gateway",
                hardwareResource = "Direct TCP Socket Connection Link",
                scriptCode = DefaultScripts.NETWORK_DRIVER,
                state = DriverState.RUNNING,
                latencyHistory = List(30) { 120f + Random.nextFloat() * 10 },
                throughputHistory = List(30) { 85f + Random.nextFloat() * 8 },
                variables = mapOf(
                    "RETRY_LIMIT" to "10", // Safe originally
                    "SOCKET_CONNECTION_RETRIES" to "3",
                    "TICK_LATENCY_MS" to "120"
                )
            )
        )

        _drivers.value = initialDrivers
        _consoleLogs.value = emptyList()
        addConsoleLog("SYSTEM ENGINE INITIALIZED. Driver microcode clusters ready.")
        
        initialDrivers.forEach { driver ->
            startDriverWorker(driver.id)
        }
    }

    fun startDriverWorker(id: String) {
        driverJobs[id]?.cancel()

        val job = scope.launch {
            while (isActive) {
                val currentDriver = _drivers.value.find { it.id == id } ?: break

                if (currentDriver.state == DriverState.PAUSED) {
                    delay(500)
                    continue
                }

                if (currentDriver.state == DriverState.CRASHED) {
                    delay(1000)
                    continue
                }

                // Variable Evaluation & CPU cycle simulation
                val vars = currentDriver.variables.toMutableMap()
                var currentLatency = vars["TICK_LATENCY_MS"]?.toFloatOrNull() ?: 50f
                if (currentLatency <= 0f) currentLatency = 10f

                // Run Interpreter Parsing cycle
                val code = currentDriver.scriptCode
                try {
                    // Custom Simulated Abstract Syntax Tree execution of dangerous commands
                    evaluateSimulatedScript(code, vars, id)

                    // Optimization feedback loops: if Manual Override is OFF, auto-tuning is continuous
                    val isOptimizing = currentDriver.state == DriverState.OPTIMIZING
                    val updateLatency = if (!isManualOverrideActive && (isOptimizing || Random.nextFloat() < 0.15f)) {
                        // Slowly lower the latency and boost throughput (continuous tuning!)
                        var optCount = currentDriver.optimizationsCount
                        val limit = when(id) {
                            "cpu" -> 8f
                            "sensor" -> 5f
                            "storage" -> 12f
                            else -> 18f
                        }
                        if (currentLatency > limit) {
                            currentLatency = (currentLatency - (1.0f + Random.nextFloat() * 2f)).coerceAtLeast(limit)
                            vars["TICK_LATENCY_MS"] = currentLatency.toInt().toString()
                            optCount++
                            
                            // Emit optimization metrics
                            if (Random.nextFloat() < 0.3f) {
                                addConsoleLog("Auto-optimizer tuning parameters for [${currentDriver.name}] (Latency down to ${currentLatency.toInt()}ms)")
                            }
                            
                            _drivers.value = _drivers.value.map { d ->
                                if (d.id == id) d.copy(
                                    variables = vars,
                                    optimizationsCount = optCount,
                                    state = if (currentDriver.state == DriverState.HEALED) DriverState.RUNNING else d.state
                                ) else d
                            }
                        }
                        currentLatency
                    } else {
                        currentLatency
                    }

                    // Feed Performance sliding windows
                    val currentThroughput = (20000.0 / (updateLatency + 1f)) * (1.0f + Random.nextFloat() * 0.15f)

                    _drivers.value = _drivers.value.map { d ->
                        if (d.id == id) {
                            val newLatencyHist = d.latencyHistory.takeLast(29) + updateLatency
                            val newThroughputHist = d.throughputHistory.takeLast(29) + currentThroughput.toFloat()
                            d.copy(
                                cyclesCount = d.cyclesCount + 1,
                                latencyHistory = newLatencyHist,
                                throughputHistory = newThroughputHist,
                                variables = vars
                            )
                        } else d
                    }

                    delay(updateLatency.toLong().coerceAtLeast(10))

                } catch (e: Exception) {
                    // EXCEPTION CAUGHT! Driver goes to CRASHED state!
                    val stackTrace = """
                        RUNTIME INTERACTIVE AST PARSER ERROR:
                        Driver id: $id
                        Exception caught: ${e.javaClass.simpleName} - ${e.message}
                        Memory Heap Register State: $vars
                        Instruction pointer trace:
                            at evalLine(${Random.nextInt(5, 15)}) in ScriptEngine.kt
                            at runWorker(DriverEngine.kt:142)
                    """.trimIndent()

                    addConsoleLog("⚠️ FATAL: Driver [${currentDriver.name}] threw ${e.javaClass.simpleName}! System HALTED.")
                    
                    _drivers.value = _drivers.value.map { d ->
                        if (d.id == id) d.copy(
                            state = DriverState.CRASHED,
                            lastError = stackTrace
                        ) else d
                    }
                    
                    // Supervisor notification triggers automatically since it runs in parallel!
                    break
                }
            }
        }
        driverJobs[id] = job
    }

    private fun evaluateSimulatedScript(code: String, registers: MutableMap<String, String>, driverId: String) {
        // Execute line by line checks to see if any scripted bug triggers are present
        val lines = code.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("//") }

        for (line in lines) {
            // Check division by zero trigger
            if (line.startsWith("DIVIDE_BY_VALUES")) {
                val blocksToFlush = registers["BLOCKS_TO_FLUSH"]?.toIntOrNull() ?: 0
                val reserveBlocks = registers["RESERVE_CACHE_BLOCKS"]?.toIntOrNull() ?: 0
                if (reserveBlocks == 0) {
                    throw ArithmeticException("AST Division by zero Exception: RESERVE_CACHE_BLOCKS is $reserveBlocks while performing block flushing arithmetic.")
                }
            }

            // Check array out of bounds trigger
            if (line.startsWith("READ_ARRAY_INDEX")) {
                val arraySize = (registers["BUFFER_ARRAY_SIZE"]?.toIntOrNull() ?: 10)
                val activeIndex = (registers["ACTIVE_INDEX"]?.toIntOrNull() ?: 0)
                if (activeIndex >= arraySize) {
                    throw IndexOutOfBoundsException("AST Segment register collision: ACTIVE_INDEX ($activeIndex) is out of bounds for buffer dimensions ($arraySize). Memory protection fault.")
                }
            }

            // Check recursion stack overflow crash
            if (line.startsWith("CHECK_STACK_CELL_ALIVE")) {
                val retries = (registers["SOCKET_CONNECTION_RETRIES"]?.toIntOrNull() ?: 0)
                val limit = (registers["RETRY_LIMIT"]?.toIntOrNull() ?: 5)
                if (retries > limit) {
                    throw StackOverflowError("AST Recursion Stack Overflow Exception: Retries ($retries) exceed packet safety threshold limit ($limit). Connection stack depth exceeded.")
                }
            }
        }
    }

    // Direct interface manipulation triggers
    fun forceCrash(id: String) {
        val current = _drivers.value.find { it.id == id } ?: return
        if (current.state == DriverState.CRASHED) return

        addConsoleLog("Injecting manual electrical disturbance packet to driver [$id]...")
        
        // Mutate variables to prompt execution parser exceptions
        val mutatedVars = current.variables.toMutableMap()
        when(id) {
            "cpu" -> {
                // For CPU, we can induce a microcode corruption exception
                mutatedVars["TICK_LATENCY_MS"] = "0"
                _drivers.value = _drivers.value.map { d ->
                    if (d.id == id) d.copy(
                        variables = mutatedVars,
                        scriptCode = d.scriptCode + "\n// Corruption flag\nDIVIDE_BY_VALUES(12, TICK_LATENCY_MS)"
                    ) else d
                }
            }
            "sensor" -> {
                mutatedVars["ACTIVE_INDEX"] = "15"
                mutatedVars["BUFFER_ARRAY_SIZE"] = "10"
                _drivers.value = _drivers.value.map { d ->
                    if (d.id == id) d.copy(variables = mutatedVars) else d
                }
            }
            "storage" -> {
                mutatedVars["RESERVE_CACHE_BLOCKS"] = "0"
                mutatedVars["BLOCKS_TO_FLUSH"] = "1024"
                _drivers.value = _drivers.value.map { d ->
                    if (d.id == id) d.copy(variables = mutatedVars) else d
                }
            }
            "network" -> {
                mutatedVars["SOCKET_CONNECTION_RETRIES"] = "50"
                mutatedVars["RETRY_LIMIT"] = "5"
                _drivers.value = _drivers.value.map { d ->
                    if (d.id == id) d.copy(variables = mutatedVars) else d
                }
            }
        }
        
        // Re-read triggers it immediately
        startDriverWorker(id)
    }

    fun pauseDriver(id: String) {
        _drivers.value = _drivers.value.map { d ->
            if (d.id == id) {
                if (d.state == DriverState.PAUSED) {
                    addConsoleLog("Resuming driver channel: ${d.name}")
                    d.copy(state = DriverState.RUNNING)
                } else {
                    addConsoleLog("Suspending driver channel: ${d.name}")
                    d.copy(state = DriverState.PAUSED)
                }
            } else d
        }
        startDriverWorker(id)
    }

    fun optimizeDriverManual(id: String) {
        val d = _drivers.value.find { d -> d.id == id } ?: return
        if (d.state == DriverState.CRASHED) {
            addConsoleLog("Cannot optimize CRASHED driver [$id]. Supervisor recovery required.")
            return
        }

        addConsoleLog("Manual tuning sequence triggered on driver [${d.name}]")
        _drivers.value = _drivers.value.map { current ->
            if (current.id == id) current.copy(state = DriverState.OPTIMIZING) else current
        }
    }

    fun deployUserScript(id: String, newScript: String) {
        addConsoleLog("Patch terminal: Deploying user-specified abstract code onto [$id]...")
        _drivers.value = _drivers.value.map { d ->
            if (d.id == id) {
                // Initialize default variables for new configuration
                val vars = d.variables.toMutableMap()
                vars["TICK_LATENCY_MS"] = "30" // Reset to safe initial
                if (id == "sensor") {
                    vars["ACTIVE_INDEX"] = "5"
                    vars["BUFFER_ARRAY_SIZE"] = "10"
                } else if (id == "storage") {
                    vars["RESERVE_CACHE_BLOCKS"] = "256"
                    vars["BLOCKS_TO_FLUSH"] = "0"
                } else if (id == "network") {
                    vars["SOCKET_CONNECTION_RETRIES"] = "0"
                    vars["RETRY_LIMIT"] = "10"
                }
                d.copy(
                    scriptCode = newScript,
                    state = DriverState.RUNNING,
                    variables = vars,
                    lastError = null,
                    patchApplied = "User Script Deployed"
                )
            } else d
        }
        startDriverWorker(id)
    }
}
