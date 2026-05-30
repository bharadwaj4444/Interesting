package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.services.*
import com.example.ui.components.PerformanceChart
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import androidx.compose.ui.platform.testTag

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize global fail-safe monitor daemon and Speech TTS
        SupervisorDaemon.initTTS(applicationContext)
        SupervisorDaemon.startMonitoring()
        
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    HamsDashboardScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SupervisorDaemon.shutdown()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HamsDashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Service Observables
    val driversState by DriverEngineManager.drivers.collectAsState()
    val consoleLogs by DriverEngineManager.consoleLogs.collectAsState()
    val supervisorStatus by SupervisorDaemon.status.collectAsState()
    val supervisorReports by SupervisorDaemon.reports.collectAsState()
    val heartbeat by SupervisorDaemon.heartbeatCounter.collectAsState()

    // Host Intel Metrics (refreshed dynamically)
    var hostMetrics by remember { mutableStateOf(HostIntrospection.scanDevice(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            hostMetrics = HostIntrospection.scanDevice(context)
        }
    }

    // Interactive UI states
    var activeTab by remember { mutableStateOf(0) } // 0: Introspect, 1: Registers, 2: Supervisor
    var selectedDriverId by remember { mutableStateOf("cpu") }
    val selectedDriver = driversState.find { it.id == selectedDriverId } ?: driversState.firstOrNull()
    var scriptInputText by remember { mutableStateOf("") }
    
    // Sync script editor when selected driver changes
    LaunchedEffect(selectedDriverId, selectedDriver?.scriptCode) {
        if (selectedDriver != null) {
            scriptInputText = selectedDriver.scriptCode
        }
    }

    var isManualOverride by remember { mutableStateOf(DriverEngineManager.isManualOverrideActive) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
    ) {
        // --- 1. HUD BENTO HEADER SECTION ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "H.A.M.S.",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    fontSize = 26.sp
                )
                Text(
                    text = "AUTONOMOUS SUPERVISOR v4.2",
                    fontSize = 9.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }

            // Two Bento Actions Avatar Circles
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Microphone audio test button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(com.example.ui.theme.BentoLightBadgeBg)
                        .clickable {
                            SupervisorDaemon.speak("Global Sentinel check complete. Heartbeat frequency normal.")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎙️", fontSize = 18.sp)
                }

                // Configuration helper settings button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(com.example.ui.theme.BentoCardPurpleMedium)
                        .clickable {
                            SupervisorDaemon.speak("Displaying active system telemetry matrix.")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙️", fontSize = 18.sp)
                }
            }
        }

        // --- BENTO HERO CARD: ACTIVE SUPERVISOR STATISTICS ---
        val throughputOpt = remember(heartbeat) {
            98.0 + (heartbeat % 10) * 0.05
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.BentoHighlightPurple),
            border = BorderStroke(1.dp, com.example.ui.theme.BentoAccentPurple.copy(alpha = 0.12f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Styled Status Badge
                    Box(
                        modifier = Modifier
                            .background(com.example.ui.theme.BentoAccentPurple, RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(com.example.ui.theme.CyberSecondaryGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SUPERVISOR ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontSize = 9.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    Text(
                        text = "HB: %dms".format(100 + (heartbeat % 12) * 5),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = com.example.ui.theme.BentoAccentPurple,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "%.1f%%".format(throughputOpt),
                    fontSize = 32.sp,
                    color = com.example.ui.theme.BentoAccentPurple,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    lineHeight = 36.sp
                )
                Text(
                    text = "System Throughput Optimization",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = Color(0xFF4F378B),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }
        }

        // --- 2. FAIL-SAFE PROTOCOL & OVERRIDE PANEL (BENTO SWITCH) ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag("manual_override_panel"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isManualOverride) com.example.ui.theme.BentoAlertRedBg else com.example.ui.theme.BentoCardPurpleMedium
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (isManualOverride) Color(0xFFF2B8B5) else com.example.ui.theme.BentoOutlineColor
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "FAIL-SAFE PROTOCOL",
                        fontSize = 10.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = if (isManualOverride) com.example.ui.theme.BentoAlertRedText else com.example.ui.theme.BentoTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Manual Override",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = if (isManualOverride) Color(0xFF601410) else com.example.ui.theme.BentoTextPrimary
                    )
                }

                // Rigid physical switch
                Switch(
                    checked = isManualOverride,
                    onCheckedChange = { value ->
                        isManualOverride = value
                        DriverEngineManager.isManualOverrideActive = value
                        if (value) {
                            DriverEngineManager.addConsoleLog("[SECURITY] Manual override toggle activated! Safeguards isolated. Local registers held static.")
                            SupervisorDaemon.speak("Security Alert: Manual override active.")
                        } else {
                            DriverEngineManager.addConsoleLog("[SECURITY] Antigravity co-processor autonomous daemon initialized.")
                            SupervisorDaemon.speak("Autonomic sentinel loop engaged. Real-time driver self-optimization active.")
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = com.example.ui.theme.BentoAlertRedPrimary,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = com.example.ui.theme.BentoOutlineColor
                    ),
                    modifier = Modifier.testTag("manual_override_toggle")
                )
            }
        }

        // --- 3. BENTO SYSTEM TABS ROW ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .background(com.example.ui.theme.BentoCardPurpleLight, RoundedCornerShape(24.dp))
                .border(1.dp, com.example.ui.theme.BentoOutlineColor, RoundedCornerShape(24.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                Triple("System", "📊", 0),
                Triple("Drivers", "💾", 1),
                Triple("Supervisor", "🧬", 2)
            )

            tabs.forEach { (title, emoji, index) ->
                val selected = activeTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) Color(0xFFE8DEF8) else Color.Transparent)
                        .clickable { activeTab = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = emoji,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = if (selected) com.example.ui.theme.BentoTextPrimary else com.example.ui.theme.BentoTextSecondary.copy(alpha = 0.7f),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- 4. EXPANDABLE TAB CONTENT SATELLITES ---
        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                0 -> IntrospectTabContent(metrics = hostMetrics)
                1 -> RegistersTabContent(
                    drivers = driversState,
                    selectedDriver = selectedDriver,
                    scriptInput = scriptInputText,
                    onScriptChange = { scriptInputText = it },
                    onDriverSelect = { selectedDriverId = it },
                    isOverrideActive = isManualOverride,
                    coroutineScope = coroutineScope
                )
                2 -> SupervisorTabContent(
                    status = supervisorStatus,
                    reports = supervisorReports,
                    logs = consoleLogs,
                    heartbeat = heartbeat,
                    coroutineScope = coroutineScope
                )
            }
        }
    }
}

// ==========================================
// TAB 1: HARDWARE DEEP INTROSPECTION MODULE
// ==========================================
@Composable
fun IntrospectTabContent(metrics: HardwareMetrics) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            // General Platform Banner
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, com.example.ui.theme.BentoOutlineColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Architecture Specs",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${metrics.manufacturer.uppercase()} ${metrics.model}",
                            style = MaterialTheme.typography.titleMedium,
                            color = com.example.ui.theme.BentoTextPrimary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = "CPU Abstraction layer mapping core instruction footprints directly across co-processors.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            // Memory & Storage progress metrics side-by-side
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, com.example.ui.theme.BentoOutlineColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dns, contentDescription = "RAM", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RAM CAPACITY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "%.2f GB Avail".format(metrics.availRamGb),
                            style = MaterialTheme.typography.titleMedium,
                            color = com.example.ui.theme.BentoTextPrimary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = "Total System: %.2f GB".format(metrics.totalRamGb),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val ramUsedPct = ((metrics.totalRamGb - metrics.availRamGb) / metrics.totalRamGb).coerceIn(0.0..1.0).toFloat()
                        LinearProgressIndicator(
                            progress = { ramUsedPct },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = com.example.ui.theme.BentoLightBadgeBg
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, com.example.ui.theme.BentoOutlineColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, contentDescription = "Storage", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("FLASH SECTOR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "%.1f GB Free".format(metrics.freeStorageGb),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = "Partition: %.1f GB".format(metrics.totalStorageGb),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val storageUsedPct = ((metrics.totalStorageGb - metrics.freeStorageGb) / metrics.totalStorageGb).coerceIn(0.0..1.0).toFloat()
                        LinearProgressIndicator(
                            progress = { storageUsedPct },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = com.example.ui.theme.BentoLightBadgeBg
                        )
                    }
                }
            }
        }

        item {
            // Real battery environmental monitor
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, com.example.ui.theme.BentoOutlineColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BatteryChargingFull, contentDescription = "Battery", tint = Color(0xFFC5A000), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("POWER DELIVERY REGULATION", style = MaterialTheme.typography.labelSmall, color = com.example.ui.theme.BentoTextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        Text(
                            text = if (metrics.isBatteryCharging) "CHARGING-BUS ON" else "AC DISCHARGED",
                            fontSize = 9.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = if (metrics.isBatteryCharging) Color(0xFF1D5A21) else Color(0xFF9E5400),
                            modifier = Modifier
                                .background(if (metrics.isBatteryCharging) Color(0xFFE8F5E9) else Color(0xFFFFF3E0), RoundedCornerShape(6.dp))
                                .border(1.dp, if (metrics.isBatteryCharging) Color(0xFF81C784) else Color(0xFFFFB74D), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("BUS LEVEL", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${metrics.batteryPercent.roundToInt()}%", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        Column {
                            Text("THERMALS", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${metrics.batteryTemperatureC}°C", style = MaterialTheme.typography.titleLarge, color = com.example.ui.theme.BentoAlertRedPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        Column {
                            Text("CO-REGULATOR VOLTAGE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("%.2f V".format(metrics.batteryVoltageV), style = MaterialTheme.typography.titleLarge, color = com.example.ui.theme.BentoAccentPurple, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            // Network Adapter & Audio DAC Map
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, com.example.ui.theme.BentoOutlineColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("NETWORK GATEWAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(metrics.activeNetworkType, style = MaterialTheme.typography.bodySmall, color = com.example.ui.theme.BentoTextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Active Raw TCP/IP Tunnel", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, com.example.ui.theme.BentoOutlineColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("AUDIO INTERFACE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(metrics.activeOutputDevices, style = MaterialTheme.typography.bodySmall, color = com.example.ui.theme.BentoTextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Audio sync latency: ~14ms", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            // Sensors Array List Title
            Text(
                text = "PHYSICAL TRANSDUCER BUS [${metrics.availableSensors.size} CONNECTED]",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
            )
        }

        item {
            // Scrolling sensors list
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            ) {
                items(metrics.availableSensors) { sensorName ->
                    Box(
                        modifier = Modifier
                            .background(com.example.ui.theme.BentoCardPurpleMedium, RoundedCornerShape(12.dp))
                            .border(1.dp, com.example.ui.theme.BentoOutlineColor, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Sensor connected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = sensorName.take(24),
                                fontSize = 9.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = com.example.ui.theme.BentoTextPrimary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

// ==========================================
// TAB 2: VIRTUAL DRIVERS & REGISTERS WORKER
// ==========================================
@Composable
fun RegistersTabContent(
    drivers: List<VirtualDriver>,
    selectedDriver: VirtualDriver?,
    scriptInput: String,
    onScriptChange: (String) -> Unit,
    onDriverSelect: (String) -> Unit,
    isOverrideActive: Boolean,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    if (selectedDriver == null) return

    Column(modifier = Modifier.fillMaxSize()) {
        // Horizontal slider of registers/drivers in Bento Style
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            items(drivers) { d ->
                val isSelected = d.id == selectedDriver.id
                val driverColor = when (d.state) {
                    DriverState.RUNNING -> Color(0xFF1D5A21)
                    DriverState.OPTIMIZING -> Color(0xFF9E5400)
                    DriverState.CRASHED -> com.example.ui.theme.BentoAlertRedPrimary
                    DriverState.PAUSED -> Color(0xFF49454F)
                    DriverState.HEALED -> Color(0xFF00796B)
                }

                val driverStateBg = when (d.state) {
                    DriverState.RUNNING -> Color(0xFFE8F5E9)
                    DriverState.OPTIMIZING -> Color(0xFFFFF3E0)
                    DriverState.CRASHED -> com.example.ui.theme.BentoAlertRedBg
                    DriverState.PAUSED -> Color(0xFFF1F1F1)
                    DriverState.HEALED -> Color(0xFFE0F2F1)
                }

                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) com.example.ui.theme.BentoHighlightPurple else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onDriverSelect(d.id) }
                        .border(
                            1.dp,
                            if (isSelected) com.example.ui.theme.BentoAccentPurple else com.example.ui.theme.BentoOutlineColor,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(10.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = d.id.uppercase(),
                                fontSize = 10.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = if (isSelected) com.example.ui.theme.BentoAccentPurple else com.example.ui.theme.BentoTextSecondary
                            )

                            // state dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(driverColor)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = d.name.take(20),
                            fontSize = 11.sp,
                            maxLines = 1,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = if (isSelected) com.example.ui.theme.BentoAccentPurple else com.example.ui.theme.BentoTextPrimary
                        )
                    }
                }
            }
        }

        // Selected Driver Details & Controls
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, com.example.ui.theme.BentoOutlineColor)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header status row info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = selectedDriver.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = com.example.ui.theme.BentoTextPrimary
                        )
                        Text(
                            "Resource Bind: ${selectedDriver.hardwareResource}",
                            fontSize = 10.sp,
                            color = com.example.ui.theme.BentoTextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(com.example.ui.theme.BentoLightBadgeBg, RoundedCornerShape(8.dp))
                            .border(1.dp, com.example.ui.theme.BentoOutlineColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "STATE: ${selectedDriver.state.name}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = when (selectedDriver.state) {
                                DriverState.RUNNING -> Color(0xFF1D5A21)
                                DriverState.PAUSED -> Color(0xFF49454F)
                                DriverState.CRASHED -> com.example.ui.theme.BentoAlertRedPrimary
                                DriverState.HEALED -> Color(0xFF004D40)
                                else -> Color(0xFF9E5400)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Performance Custom Charts section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(95.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PerformanceChart(
                        data = selectedDriver.latencyHistory,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        lineColor = com.example.ui.theme.BentoAccentPurple,
                        title = "LATENCY FLUX (MUTEX)",
                        unit = "ms"
                    )

                    PerformanceChart(
                        data = selectedDriver.throughputHistory,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        lineColor = Color(0xFF1D5A21),
                        title = "DECODE THREADING",
                        unit = "hz"
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Inline terminal code editor
                Text(
                    text = "ABSTRACT HARDWARE DRIVER REGISTER DECODE (AST STACK):",
                    fontSize = 9.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = com.example.ui.theme.BentoTextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Editable script field with light theme keyboard styling
                TextField(
                    value = scriptInput,
                    onValueChange = onScriptChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, com.example.ui.theme.BentoOutlineColor, RoundedCornerShape(12.dp)),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = com.example.ui.theme.BentoTextPrimary
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFFEF7FF),
                        unfocusedContainerColor = Color(0xFFFEF7FF),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // MANUAL AND HEALING ACTIONS BAR (BENTO ROUNDED BUTTONS)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Inject deploy button
                    Button(
                        onClick = {
                            DriverEngineManager.deployUserScript(selectedDriver.id, scriptInput)
                            SupervisorDaemon.speak("Microcode compiled successfully and injected into dynamic register address space.")
                        },
                        modifier = Modifier.weight(1.5f).height(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.BentoAccentPurple),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = "Compile", modifier = Modifier.size(11.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("COMPREHENSIVE BUILD", fontSize = 9.sp, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }

                    // TUNE/OPTIMIZE
                    OutlinedButton(
                        onClick = {
                            DriverEngineManager.optimizeDriverManual(selectedDriver.id)
                        },
                        enabled = selectedDriver.state != DriverState.CRASHED,
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = com.example.ui.theme.BentoAccentPurple,
                            disabledContentColor = Color.Gray
                        ),
                        border = BorderStroke(1.dp, if (selectedDriver.state != DriverState.CRASHED) com.example.ui.theme.BentoAccentPurple else Color.Gray),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Tune", modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("TUNE AST", fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }

                    // PAUSE
                    OutlinedButton(
                        onClick = {
                            DriverEngineManager.pauseDriver(selectedDriver.id)
                        },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF49454F)
                        ),
                        border = BorderStroke(1.dp, com.example.ui.theme.BentoOutlineColor),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        val isPaused = selectedDriver.state == DriverState.PAUSED
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = "Pause Toggle",
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(if (isPaused) "RESUME" else "SUSPEND", fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }

                    // INJECT STRESS/FAULT
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                DriverEngineManager.forceCrash(selectedDriver.id)
                            }
                        },
                        enabled = selectedDriver.state != DriverState.CRASHED,
                        modifier = Modifier.weight(1.2f).height(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.BentoAlertRedPrimary),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Force crash", modifier = Modifier.size(11.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("FORCE FAULT", fontSize = 9.sp, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 3: WATCHDOG SUPERVISOR & AI HEAL LOGS
// ==========================================
@Composable
fun SupervisorTabContent(
    status: SupervisorStatus,
    reports: List<CrashDiagnosticReport>,
    logs: List<String>,
    heartbeat: Long,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, com.example.ui.theme.BentoOutlineColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    when (status) {
                                        SupervisorStatus.STANDBY -> Color(0xFF1D5A21)
                                        SupervisorStatus.REBOOTING -> Color(0xFF00796B)
                                        else -> Color(0xFF9E5400)
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "FAIL-SAFE ISOLATOR STATE: ${status.name}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = com.example.ui.theme.BentoAccentPurple
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Automated AI Recovery Engines standing by for AST heap exception traps.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Global telemetry actions
                Box(
                    modifier = Modifier
                        .background(com.example.ui.theme.BentoCardPurpleMedium, RoundedCornerShape(12.dp))
                        .border(1.dp, com.example.ui.theme.BentoOutlineColor, RoundedCornerShape(12.dp))
                        .clickable {
                            DriverEngineManager.resetToDefault()
                            SupervisorDaemon.speak("Global hardware systems rebooted to clean boot microcode registers.")
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = "Clear", modifier = Modifier.size(12.dp), tint = com.example.ui.theme.BentoAccentPurple)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SYSTEM REBOOT", fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = com.example.ui.theme.BentoAccentPurple)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Diagnostics metrics Title
        Text(
            text = "CRASH DIAGNOSTIC REPORTS TRAIL [${reports.size} RECOVERIES]:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = com.example.ui.theme.BentoTextSecondary,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFFFEF7FF), RoundedCornerShape(16.dp))
                .border(1.dp, com.example.ui.theme.BentoOutlineColor, RoundedCornerShape(16.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (reports.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "All Clear", tint = Color(0xFF1D5A21).copy(alpha = 0.5f), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("NO CORE TRACES FAULTED", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = com.example.ui.theme.BentoTextPrimary)
                            Text("All virtual microcode registers currently heartbeat functional.", fontSize = 9.sp, color = com.example.ui.theme.BentoTextSecondary)
                        }
                    }
                }
            } else {
                items(reports) { r ->
                    var isExpanded by remember { mutableStateOf(false) }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, com.example.ui.theme.BentoOutlineColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.BugReport, contentDescription = "Crash", tint = com.example.ui.theme.BentoAlertRedPrimary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "REPORT ${r.id} - ${r.driverName}",
                                        fontSize = 10.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = com.example.ui.theme.BentoAlertRedText
                                    )
                                }
                                Text(r.timestamp, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "TRAPPED Exception: ${r.caughtException}",
                                fontSize = 10.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = Color(0xFF9E5400)
                            )
                            Text(
                                text = "Deployed Healer: ${r.healerUsed} in ${r.recoveryDurationMs}ms",
                                fontSize = 9.sp,
                                color = com.example.ui.theme.BentoAccentPurple,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )

                            if (isExpanded) {
                                Divider(color = com.example.ui.theme.BentoOutlineColor, modifier = Modifier.padding(vertical = 6.dp))
                                Text("MEMORY DUMP REGISTER STATE:", fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = com.example.ui.theme.BentoTextSecondary)
                                Text(
                                    text = r.memoryDump.toString(),
                                    fontSize = 9.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = Color(0xFF21005D),
                                    modifier = Modifier
                                        .background(com.example.ui.theme.BentoLightBadgeBg, RoundedCornerShape(6.dp))
                                        .border(1.dp, com.example.ui.theme.BentoOutlineColor, RoundedCornerShape(6.dp))
                                        .fillMaxWidth()
                                        .padding(6.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("AUTONOMOUS DEPLOYED MICROCODE PATCH:", fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = com.example.ui.theme.BentoTextSecondary)
                                Text(
                                    text = r.patchApplied,
                                    fontSize = 9.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = Color(0xFF004D40),
                                    modifier = Modifier
                                        .background(Color(0xFFE0F2F1), RoundedCornerShape(6.dp))
                                        .border(1.dp, Color(0xFF80CBC4), RoundedCornerShape(6.dp))
                                        .fillMaxWidth()
                                        .padding(6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Live scrolling terminal console log matching Bento output
        Text(
            text = "HAMS SENTINEL PHYSICAL TELEMETRY LOGS:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = com.example.ui.theme.BentoTextSecondary,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(Color(0xFFFEF7FF), RoundedCornerShape(16.dp))
                .border(1.dp, com.example.ui.theme.BentoOutlineColor, RoundedCornerShape(16.dp))
                .padding(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true
            ) {
                items(logs.reversed()) { logLine ->
                    val color = when {
                        logLine.contains("⚠️") || logLine.contains("WARNING") -> Color(0xFF9E5400)
                        logLine.contains("🚨") || logLine.contains("FATAL") || logLine.contains("Security Alert") -> com.example.ui.theme.BentoAlertRedPrimary
                        logLine.contains("✅") || logLine.contains("SUPERVISOR") || logLine.contains("success") -> Color(0xFF1D5A21)
                        else -> com.example.ui.theme.BentoTextSecondary
                    }

                    Text(
                        text = logLine,
                        fontSize = 9.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}
