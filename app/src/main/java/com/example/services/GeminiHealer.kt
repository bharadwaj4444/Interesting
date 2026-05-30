package com.example.services

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiHealer {
    private const val TAG = "GeminiHealer"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Attempts to heal a crashed driver script using Gemini API.
     * If the API Key is invalid or empty, it falls back to the robust heuristic healer.
     */
    suspend fun healScript(
        driverId: String,
        driverName: String,
        scriptCode: String,
        variables: Map<String, String>,
        stackTrace: String
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY

        // Check if API key is present and not the placeholder
        val isKeyConfigured = apiKey.isNotEmpty() && 
                apiKey != "MY_GEMINI_API_KEY" && 
                !apiKey.startsWith("PLACEHOLDER") && 
                apiKey != "GEMINI_API_KEY"

        if (!isKeyConfigured) {
            Log.d(TAG, "Gemini API key not configured. Using local emergency healer fallback.")
            return@withContext performHeuristicHealing(driverId, scriptCode)
        }

        val prompt = """
            You are the AI Supervisor for the Hardware Abstraction and Management System.
            A simulated hardware driver has crashed with a fatal exception.
            
            --- CRASH PROFILE ---
            Driver ID: $driverId
            Driver Name: $driverName
            
            Current Variables Heap Registers:
            ${variables.entries.joinToString("\n") { "${it.key} = \"${it.value}\"" }}
            
            Exception Stack Trace:
            $stackTrace
            
            --- RUNTIME SCRIPT CODE ---
            $scriptCode
            
            --- INSTRUCTIONS ---
            Identify and fix the bug in the RUNTIME SCRIPT CODE. 
            Ensure variables like ACTIVE_INDEX, BLOCKS_TO_FLUSH, RESERVE_CACHE_BLOCKS, or SOCKET_CONNECTION_RETRIES have safe values, or rewrite/remove instructions causing compile exception block errors.
            Do NOT include any explanations, markdown code blocks, or preamble comments.
            Return ONLY the healed, corrected, complete script code in plain text.
        """.trimIndent()

        try {
            val requestBodyJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                }
                put("contents", contentsArray)
                
                // Set system instructions
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "You are an autonomous low-level C/Assembly compiler debugger. You return ONLY the plain text source script patch code without any markdown styling or comments.")
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "Gemini API Error: ${response.code} $errBody")
                    return@withContext performHeuristicHealing(driverId, scriptCode)
                }

                val responseBody = response.body?.string() ?: throw Exception("Empty response body")
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val responseContent = firstCandidate?.optJSONObject("content")
                val parts = responseContent?.optJSONArray("parts")
                val text = parts?.optJSONObject(0)?.optString("text")

                if (!text.isNullOrBlank()) {
                    var cleanedCode = text.trim()
                    // Strip occasionally returned ``` or ```kotlin wrappers
                    if (cleanedCode.startsWith("```")) {
                        cleanedCode = cleanedCode.substringAfter("\n").substringBeforeLast("```").trim()
                    }
                    Log.d(TAG, "Successfully healed script via Gemini API!")
                    return@withContext cleanedCode to "Gemini Autonomous AI Healer Patch v3.5-flash"
                } else {
                    return@withContext performHeuristicHealing(driverId, scriptCode)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call Gemini API: ${e.message}", e)
            return@withContext performHeuristicHealing(driverId, scriptCode)
        }
    }

    /**
     * Heuristic Healing fallback triggered when no network/key exists or error is caught.
     */
    private fun performHeuristicHealing(driverId: String, currentScript: String): Pair<String, String> {
        val healedScript: String
        val diagnosis: String

        when (driverId) {
            "cpu" -> {
                diagnosis = "Local HEURISTIC Core: Cleared invalid clock frequency variable configuration. Safety instruction inserted."
                healedScript = """
                    // CPU CORE MICROCODE SCHEDULER V4.1 - SAFETY PATCHED
                    SYSTEM_CLOCK_GOVERNOR = "performance"
                    OPTIMIZATION_FACTOR = "1.5"
                    CACHE_LOCK_DEPTH = "4"
                    THREAD_POOL_SIZE = "8"
                    CYCLES_BETWEEN_YIELDS = "25"
                    
                    // FIXED OVERCLOCK CRASH CAP
                    TICK_LATENCY_MS = "15"
                    BUFFER_SIZE = "256"
                    
                    // FIXED TEMPERATURE AND INJECTED THREAD SAFETY
                    THROTTLE_MULTIPLIER = "1.0"
                    
                    // REGISTER CLEAR
                    RUN_REGISTER_ADDR = "0x7F8EBC60"
                    EXECUTE_DECODE_PHASE(RUN_REGISTER_ADDR)
                """.trimIndent()
            }
            "sensor" -> {
                diagnosis = "Local HEURISTIC Core: Reset buffer read pointer inside bounds. SENSOR_DEBOUNCE limit applied."
                healedScript = """
                    // SENSOR STREAM MULTIPLEXER & BUFFER ABSTRACTION - CRASH HARDENED
                    ACCELEROMETER_FREQ_HZ = "50"
                    GYROSCOPE_RATE_KBPS = "120"
                    LIGHT_SENSOR_DEBOUNCE_MS = "200"
                    BUFFER_ARRAY_SIZE = "10"
                    CONCURRENT_READ_WORKERS = "2"
                    TICK_LATENCY_MS = "8"
                    
                    // HEALED ACTIVE INDEX TO PREVENT BOUNDS EXCEPTION
                    ACTIVE_INDEX = "5"
                    
                    // INDEX COMPARATOR ACCESS INJECTED
                    READ_ARRAY_INDEX(BUFFER_ARRAY_SIZE, ACTIVE_INDEX)
                """.trimIndent()
            }
            "storage" -> {
                diagnosis = "Local HEURISTIC Core: Allocated safety reserve blocks of space. Zero-division catcher configured."
                healedScript = """
                     // STORAGE FS SECTOR WRITER & CACHE FLUSH CONTROLLER - HEALED
                     SECTOR_SEEK_MODE = "LBA_OPTIMIZED"
                     CACHE_STRATEGY = "WRITE_BACK"
                     
                     // INJECTED BLOCKS PREVENTING DIVISION CRASH
                     RESERVE_CACHE_BLOCKS = "128"
                     FLUSH_INTERVAL_CYCLES = "5"
                     TICK_LATENCY_MS = "12"
                     THROUGHPUT_SCALE = "1.8"
                     
                     BLOCKS_TO_FLUSH = "1024"
                     
                     // DIVISOR GUARD RESOLVED
                     DIVIDE_BY_VALUES(BLOCKS_TO_FLUSH, RESERVE_CACHE_BLOCKS)
                """.trimIndent()
            }
            "network" -> {
                diagnosis = "Local HEURISTIC Core: Throttled stack recursion network retry bounds to safety values."
                healedScript = """
                    // DYNAMIC SOCKET CONGESTION WINDOW GATEWAY - HEALED
                    RETRY_LIMIT = "10"
                    SLIDING_WINDOW_SIZE = "1024"
                    TIMEOUT_MS = "5000"
                    SOCKET_ALIVE = "true"
                    TICK_LATENCY_MS = "18"
                    MAX_RECURSION_DEPTH = "12"
                    
                    // ALIGNED RETRIES UNDER SECURITY RANGE
                    SOCKET_CONNECTION_RETRIES = "2"
                    
                    // VERIFY RECURSIVE SAFETY STACK
                    CHECK_STACK_CELL_ALIVE(SOCKET_CONNECTION_RETRIES, RETRY_LIMIT)
                """.trimIndent()
            }
            else -> {
                diagnosis = "Emergency Local Patch: Script trace reset to default parameters."
                healedScript = currentScript
            }
        }

        return healedScript to "Supervisor Heuristic Auto-Patch ($diagnosis)"
    }
}
