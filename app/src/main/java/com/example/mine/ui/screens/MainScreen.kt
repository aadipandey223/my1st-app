package com.example.mine.ui.screens

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings
import android.bluetooth.BluetoothAdapter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mine.viewmodel.SecureChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.text.replaceFirstChar

@Composable
fun ModernMainScreen() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf("start") }
    var connectionType by remember { mutableStateOf<String?>(null) }

    var connectionState by remember { mutableStateOf("disconnected") }
    var isCheckingConnection by remember { mutableStateOf(false) }
    var connectedDeviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }

    // Check connection status when returning to app
    LaunchedEffect(Unit) {
        // Initial connection check
        checkConnectionStatus(
            context = context,
            connectionType = connectionType,
            onUpdateCheckingState = { isCheckingConnection = it },
            onUpdateConnectionState = { connectionState = it },
            onUpdateConnectedDevice = { connectedDeviceInfo = it }
        )
    }

    when (currentScreen) {
        "start" -> StartScreen(
            onDiscoverDevices = { currentScreen = "connection-type" }
        )
        "connection-type" -> ConnectionTypeScreen(
            onBack = { currentScreen = "start" },
            onSelectType = { type ->
                connectionType = type
                currentScreen = "connection-guide"
            }
        )
        "connection-guide" -> ConnectionGuideScreen(
            connectionType = connectionType ?: "bluetooth",
            onBack = { currentScreen = "connection-type" },
            onOpenSettings = { type ->
                openSettings(context, type)
            },
            onCheckConnection = {
                checkConnectionStatus(
                    context = context,
                    connectionType = connectionType,
                    onUpdateCheckingState = { isCheckingConnection = it },
                    onUpdateConnectionState = { connectionState = it },
                    onUpdateConnectedDevice = { connectedDeviceInfo = it }
                )
                currentScreen = "connection-status"
            }
        )
        "connection-status" -> ConnectionStatusScreen(
            connectionType = connectionType ?: "bluetooth",
            connectionState = connectionState,
            connectedDeviceInfo = connectedDeviceInfo,
            isCheckingConnection = isCheckingConnection,
            onBack = { currentScreen = "connection-guide" },
            onRetry = {
                checkConnectionStatus(
                    context = context,
                    connectionType = connectionType,
                    onUpdateCheckingState = { isCheckingConnection = it },
                    onUpdateConnectionState = { connectionState = it },
                    onUpdateConnectedDevice = { connectedDeviceInfo = it }
                )
            },
            onContinue = { currentScreen = "continue" }
        )
        "continue" -> ContinueScreen(
            selectedDevice = connectedDeviceInfo,
            onContinue = { currentScreen = "qr-generation" }
        )
        "qr-generation" -> QRGenerationScreen(
            onContinue = { currentScreen = "checking-connection" }
        )
        "checking-connection" -> CheckingConnectionScreen(
            onConnectionEstablished = { currentScreen = "chat" }
        )
        "chat" -> ChatScreen(
            selectedDevice = connectedDeviceInfo,
            onReset = { currentScreen = "start" }
        )
    }
}

// Extracted function to handle connection checking
fun checkConnectionStatus(
    context: Context,
    connectionType: String?,
    onUpdateCheckingState: (Boolean) -> Unit,
    onUpdateConnectionState: (String) -> Unit,
    onUpdateConnectedDevice: (DeviceInfo?) -> Unit
) {
    onUpdateCheckingState(true)
    // Check connection based on selected type
    CoroutineScope(Dispatchers.Main).launch {
        delay(1000) // Simulate check time

        // Check actual device connection status
        val isConnected = when (connectionType) {
            "bluetooth" -> checkBluetoothConnection(context)
            "wifi" -> checkWifiConnection(context)
            else -> false
        }

        if (isConnected) {
            onUpdateConnectionState("connected")
            // Get connected device info
            onUpdateConnectedDevice(getConnectedDeviceInfo(context, connectionType))
        } else {
            onUpdateConnectionState("disconnected")
            onUpdateConnectedDevice(null)
        }

        onUpdateCheckingState(false)
    }
}

// Real connection checking functions
fun checkBluetoothConnection(context: Context): Boolean {
    return try {
        val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        bluetoothAdapter != null && bluetoothAdapter.isEnabled && bluetoothAdapter.bondedDevices.isNotEmpty()
    } catch (e: Exception) {
        false
    }
}

fun checkWifiConnection(context: Context): Boolean {
    return try {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectionInfo = wifiManager.connectionInfo
        connectionInfo != null && connectionInfo.networkId != -1
    } catch (e: Exception) {
        false
    }
}

fun getConnectedDeviceInfo(context: Context, connectionType: String?): DeviceInfo? {
    return when (connectionType) {
        "bluetooth" -> {
            try {
                val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                    val bondedDevices = bluetoothAdapter.bondedDevices
                    if (bondedDevices.isNotEmpty()) {
                        val device = bondedDevices.first()
                        DeviceInfo(
                            id = device.address,
                            name = device.name ?: "Unknown Bluetooth Device",
                            signal = 85,
                            type = "bluetooth",
                            status = "Connected"
                        )
                    } else null
                } else null
            } catch (e: Exception) {
                null
            }
        }
        "wifi" -> {
            try {
                val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val connectionInfo = wifiManager.connectionInfo
                if (connectionInfo != null && connectionInfo.networkId != -1) {
                    DeviceInfo(
                        id = connectionInfo.bssid ?: "unknown",
                        name = connectionInfo.ssid?.removeSurrounding("\"") ?: "Unknown WiFi Network",
                        signal = WifiManager.calculateSignalLevel(connectionInfo.rssi, 5) * 20, // Convert to percentage
                        type = "wifi",
                        status = "Connected"
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
        else -> null
    }
}

// Function to open settings
fun openSettings(context: Context, connectionType: String) {
    val intent = when (connectionType) {
        "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
        else -> Intent(Settings.ACTION_SETTINGS)
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

// Data classes
data class DeviceInfo(
    val id: String,
    val name: String,
    val signal: Int,
    val type: String,
    val status: String = "available"
)

// Start Screen
@Composable
fun StartScreen(onDiscoverDevices: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E3A8A), // indigo-900
                        Color(0xFF7C3AED), // purple-900
                        Color(0xFF000000)  // black
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Icon
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(48.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF22D3EE), // cyan-400
                                Color(0xFFA855F7)  // purple-500
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Router,
                    contentDescription = "Router Icon",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // App Title
            Text(
                text = "Fusion Node App",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle
            Text(
                text = "Secure device-to-device communication",
                fontSize = 16.sp,
                color = Color(0xFFC4B5FD) // purple-200
            )

            Spacer(modifier = Modifier.height(64.dp))

            // Discover Button
            Button(
                onClick = onDiscoverDevices,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF06B6D4), // cyan-500
                                    Color(0xFF9333EA)  // purple-600
                                )
                            ),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = "Smartphone Icon",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Discover Devices",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// Connection Type Selection Screen
@Composable
fun ConnectionTypeScreen(
    onBack: () -> Unit,
    onSelectType: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E40AF), // blue-900
                        Color(0xFF3730A3), // indigo-900
                        Color(0xFF000000)  // black
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Select Connection Type",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Choose how you want to connect to devices",
                    fontSize = 16.sp,
                    color = Color(0xFFBFDBFE) // blue-200
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Bluetooth Option
            Button(
                onClick = { onSelectType("bluetooth") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF2563EB), // blue-600
                                    Color(0xFF0891B2)  // cyan-600
                                )
                            ),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "Bluetooth Icon",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Bluetooth",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )

                            Text(
                                text = "Connect via Bluetooth Low Energy",
                                fontSize = 14.sp,
                                color = Color(0xFFDBEAFE) // blue-100
                            )

                            Text(
                                text = "Range: ~100m • Power efficient",
                                fontSize = 12.sp,
                                color = Color(0xFFBFDBFE) // blue-200
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Wi-Fi Option
            Button(
                onClick = { onSelectType("wifi") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF9333EA), // purple-600
                                    Color(0xFFDB2777)  // pink-600
                                )
                            ),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Router,
                                contentDescription = "Wi-Fi Icon",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Wi-Fi",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )

                            Text(
                                text = "Connect via Wi-Fi networks",
                                fontSize = 14.sp,
                                color = Color(0xFFFCE7F3) // pink-100
                            )

                            Text(
                                text = "Range: ~300m • High bandwidth",
                                fontSize = 12.sp,
                                color = Color(0xFFFBCFE8) // pink-200
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Back Button
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = Color(0xFF4B5563) // gray-600
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Back",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// Connection Guide Screen
@Composable
fun ConnectionGuideScreen(
    connectionType: String,
    onBack: () -> Unit,
    onOpenSettings: (String) -> Unit,
    onCheckConnection: () -> Unit
) {
    val title = if (connectionType == "bluetooth") "Bluetooth Connection Guide" else "Wi-Fi Connection Guide"
    val icon = if (connectionType == "bluetooth") Icons.Default.Wifi else Icons.Default.Router
    val instructions = if (connectionType == "bluetooth") {
        listOf(
            "1. Go to your phone's Settings",
            "2. Navigate to Bluetooth settings",
            "3. Turn on Bluetooth if not already on",
            "4. Scan for available devices",
            "5. Select and pair with your desired device",
            "6. Return to this app when connected"
        )
    } else {
        listOf(
            "1. Go to your phone's Settings",
            "2. Navigate to Wi-Fi settings",
            "3. Turn on Wi-Fi if not already on",
            "4. Select your desired network",
            "5. Enter password if required",
            "6. Return to this app when connected"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF111827), // gray-900
                        Color(0xFF1E40AF), // blue-900
                        Color(0xFF000000)  // black
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF22D3EE), // cyan-400
                                    Color(0xFFA855F7)  // purple-500
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Connection Icon",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Follow these steps to connect manually",
                    fontSize = 16.sp,
                    color = Color(0xFFBFDBFE) // blue-200
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Instructions Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF374151).copy(alpha = 0.5f) // gray-800/50
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Connection Steps:",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF34D399) // green-400
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    instructions.forEach { instruction ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = instruction,
                                fontSize = 16.sp,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (instruction != instructions.last()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Important Note
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF7C2D12).copy(alpha = 0.3f) // orange-900/30
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = Color(0xFFFB923C) // orange-400
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info Icon",
                        tint = Color(0xFFFB923C), // orange-400
                        modifier = Modifier.size(24.dp)
                    )

                    Text(
                        text = "Important: Make sure you're connected to a device before returning to this app. The app will verify your connection when you return.",
                        fontSize = 14.sp,
                        color = Color(0xFFFED7AA) // orange-200
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Open Settings Button
            Button(
                onClick = { onOpenSettings(connectionType) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFF59E0B), // amber-500
                                    Color(0xFFEF4444)  // red-500
                                )
                            ),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings Icon",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Open ${connectionType.replaceFirstChar { it.uppercase() }} Settings",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Check Connection Button
            Button(
                onClick = onCheckConnection,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF10B981), // green-500
                                    Color(0xFF3B82F6)  // blue-600
                                )
                            ),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Check Icon",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Check Connection Status",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Back Button
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = Color(0xFF4B5563) // gray-600
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Back to Connection Types",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// Device List Screen
@Composable
fun DeviceListScreen(
    connectionType: String,
    onBack: () -> Unit,
    onDeviceSelect: (DeviceInfo) -> Unit
) {
    val devices = remember {
        if (connectionType == "bluetooth") {
            listOf(
                DeviceInfo("BT_001", "AADI Fusion BT", 88, "bluetooth", "Available"),
                DeviceInfo("BT_002", "Relay Node BT", 75, "bluetooth", "Paired"),
                DeviceInfo("BT_003", "Smart Device BT", 92, "bluetooth", "Available")
            )
        } else {
            listOf(
                DeviceInfo("WIFI_001", "FusionNet_5G", 92, "wifi", "Secured"),
                DeviceInfo("WIFI_002", "AADI_Network", 67, "wifi", "Secured"),
                DeviceInfo("WIFI_003", "OpenNetwork", 45, "wifi", "Open")
            )
        }
    }

    val title = if (connectionType == "bluetooth") "Bluetooth Devices" else "Wi-Fi Networks"
    val icon = if (connectionType == "bluetooth") Icons.Default.Wifi else Icons.Default.Router

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF111827), // gray-900
                        Color(0xFF1E40AF), // blue-900
                        Color(0xFF000000)  // black
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF22D3EE), // cyan-400
                                    Color(0xFFA855F7)  // purple-500
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Connection Icon",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Scanning for available $connectionType devices...",
                    fontSize = 16.sp,
                    color = Color(0xFFBFDBFE) // blue-200
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Device List
            devices.forEach { device ->
                DeviceItem(
                    device = device,
                    connectionType = connectionType,
                    onClick = { onDeviceSelect(device) }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Back Button
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = Color(0xFF4B5563) // gray-600
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Back to Connection Types",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun DeviceItem(
    device: DeviceInfo,
    connectionType: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF374151).copy(alpha = 0.5f) // gray-800/50
        ),
        border = BorderStroke(
            width = 1.dp,
            color = Color(0xFF4B5563) // gray-600
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                ) {
                    Icon(
                        imageVector = if (connectionType == "bluetooth") Icons.Default.Wifi else Icons.Default.Router,
                        contentDescription = "Device Icon",
                        tint = if (connectionType == "bluetooth") Color(0xFF60A5FA) else Color(0xFFC084FC),
                        modifier = Modifier.size(24.dp)
                    )

                    // Status indicator
                    if (device.status == "Paired" || device.status == "Secured") {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (device.status == "Paired") Color(0xFF34D399) else Color(0xFFFBBF24),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Column {
                    Text(
                        text = device.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )

                    Text(
                        text = device.status,
                        fontSize = 14.sp,
                        color = Color(0xFF9CA3AF) // gray-400
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${device.signal}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF34D399) // green-400
                )

                Text(
                    text = "Signal",
                    fontSize = 12.sp,
                    color = Color(0xFF9CA3AF) // gray-400
                )
            }
        }
    }
}

// Phone Settings Screen
@Composable
fun PhoneSettingsScreen(
    selectedDevice: DeviceInfo?,
    connectionType: String?,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    isConnecting: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF064E3B), // green-900
                        Color(0xFF134E4A), // teal-900
                        Color(0xFF000000)  // black
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings Icon",
                    tint = Color(0xFF34D399), // green-400
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Phone Settings",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Configure connection settings",
                    fontSize = 16.sp,
                    color = Color(0xFFBBF7D0) // green-200
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Selected Device Info
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF374151).copy(alpha = 0.5f) // gray-800/50
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Selected Device",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF34D399) // green-400
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.2f)), // green-500/20
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (connectionType == "bluetooth") Icons.Default.Wifi else Icons.Default.Router,
                                contentDescription = "Device Icon",
                                tint = if (connectionType == "bluetooth") Color(0xFF60A5FA) else Color(0xFFC084FC),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = selectedDevice?.name ?: "Unknown Device",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )

                            Text(
                                text = "${connectionType?.replaceFirstChar { it.uppercase() } ?: "Unknown"} • ${selectedDevice?.signal ?: 0}% signal",
                                fontSize = 14.sp,
                                color = Color(0xFF9CA3AF) // gray-400
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Connection Settings
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF374151).copy(alpha = 0.5f) // gray-800/50
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Connection Settings",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF34D399) // green-400
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto-connect toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Auto-connect",
                            fontSize = 16.sp,
                            color = Color.White
                        )

                        Switch(
                            checked = true,
                            onCheckedChange = { },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981) // green-500
                            )
                        )
                    }

                    // Encryption toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Encryption",
                            fontSize = 16.sp,
                            color = Color.White
                        )

                        Switch(
                            checked = true,
                            onCheckedChange = { },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981) // green-500
                            )
                        )
                    }

                    // Background sync toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Background sync",
                            fontSize = 16.sp,
                            color = Color.White
                        )

                        Switch(
                            checked = false,
                            onCheckedChange = { },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981), // green-500
                                uncheckedTrackColor = Color(0xFF4B5563) // gray-600
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Connect Button
            Button(
                onClick = onConnect,
                enabled = !isConnecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF10B981), // green-500
                                    Color(0xFF0D9488)  // teal-600
                                )
                            ),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    if (isConnecting) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "Connecting...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    } else {
                        Text(
                            text = "Connect to Device",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Back Button
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = Color(0xFF4B5563) // gray-600
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Back to Device List",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// Continue Screen
@Composable
fun ContinueScreen(
    selectedDevice: DeviceInfo?,
    onContinue: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF064E3B), // green-900
                        Color(0xFF1E40AF), // blue-900
                        Color(0xFF000000)  // black
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Success Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF34D399), // green-400
                                Color(0xFF3B82F6)  // blue-500
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Success Icon",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Success Title
            Text(
                text = "Connection Established!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Device Info Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF374151).copy(alpha = 0.5f) // gray-800/50
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = selectedDevice?.name ?: "Unknown Device",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF34D399) // green-400
                    )

                    Text(
                        text = "Device ID: ${selectedDevice?.id ?: "Unknown"}",
                        fontSize = 14.sp,
                        color = Color(0xFF9CA3AF) // gray-300
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Key Icon",
                            tint = Color(0xFF60A5FA), // blue-400
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "Keys Generated",
                            fontSize = 12.sp,
                            color = Color(0xFF93C5FD) // blue-300
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Continue Button
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF10B981), // green-500
                                    Color(0xFF3B82F6)  // blue-600
                                )
                            ),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Text(
                        text = "Continue to QR Generation",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

// QR Generation Screen
@Composable
fun QRGenerationScreen(onContinue: () -> Unit) {
    var timeLeft by remember { mutableStateOf(600) } // 10 minutes in seconds

    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF581C87), // purple-900
                        Color(0xFF1E40AF), // blue-900
                        Color(0xFF000000)  // black
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "QR Code Icon",
                    tint = Color(0xFFC084FC), // purple-400
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Secure QR Code",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Share this QR code with the other device",
                    fontSize = 16.sp,
                    color = Color(0xFFC4B5FD) // purple-200
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // QR Code Placeholder
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(192.dp)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "QR CODE",
                            fontSize = 12.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "fusion:abc123:device456:${System.currentTimeMillis()}",
                            fontSize = 8.sp,
                            color = Color.Black,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "QR Code",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Timer
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF374151).copy(alpha = 0.5f) // gray-800/50
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Timer Icon",
                            tint = Color(0xFFFB923C), // orange-400
                            modifier = Modifier.size(20.dp)
                        )

                        Text(
                            text = "Time Remaining:",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }

                    Text(
                        text = "${timeLeft / 60}:${(timeLeft % 60).toString().padStart(2, '0')}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFB923C) // orange-400
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Continue Button
            Button(
                onClick = onContinue,
                enabled = timeLeft > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (timeLeft > 0) Color.Transparent else Color(0xFF4B5563)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (timeLeft > 0) {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFA855F7), // purple-500
                                        Color(0xFF3B82F6)  // blue-600
                                    )
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF4B5563), // gray-600
                                        Color(0xFF4B5563)  // gray-600
                                    )
                                )
                            },
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Text(
                        text = "Continue",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

// Checking Connection Screen
@Composable
fun CheckingConnectionScreen(onConnectionEstablished: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        repeat(100) {
            delay(30)
            progress = (it + 1) / 100f
        }
        delay(500)
        onConnectionEstablished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF92400E), // yellow-900
                        Color(0xFFEA580C), // orange-900
                        Color(0xFF000000)  // black
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Loading Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFFBBF24), // yellow-400
                                Color(0xFFFB923C)  // orange-500
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = Color.White,
                    strokeWidth = 4.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = "Establishing Connection",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle
            Text(
                text = "Verifying secure connection between devices...",
                fontSize = 16.sp,
                color = Color(0xFFFED7AA) // yellow-200
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Connection Diagram
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF374151).copy(alpha = 0.5f) // gray-800/50
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = "Phone A",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )

                        Text(
                            text = "···",
                            fontSize = 24.sp,
                            color = Color(0xFFFBBF24) // yellow-400
                        )

                        Icon(
                            imageVector = Icons.Default.Router,
                            contentDescription = "Fusion Node",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )

                        Text(
                            text = "···",
                            fontSize = 24.sp,
                            color = Color(0xFFFBBF24) // yellow-400
                        )

                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = "Phone B",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Phone A ↔ Fusion Node ↔ Relay ↔ Phone B",
                        fontSize = 12.sp,
                        color = Color(0xFF9CA3AF) // gray-300
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Progress Bar
                         LinearProgressIndicator(
                 progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFFFBBF24), // yellow-400
                trackColor = Color(0xFF374151) // gray-800
            )
        }
    }
}

// Chat Screen
@Composable
fun ChatScreen(
    selectedDevice: DeviceInfo?,
    onReset: () -> Unit
) {
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var newMessage by remember { mutableStateOf("") }

    // Sample messages
    LaunchedEffect(Unit) {
        messages = listOf(
            ChatMessage("Hello! How are you?", "you", "12:30 PM", true),
            ChatMessage("I'm doing great! Thanks for asking.", "peer", "12:31 PM", true),
            ChatMessage("This is a secure encrypted message.", "you", "12:32 PM", true)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF111827), // gray-900
                        Color(0xFF000000)  // black
                    )
                )
            )
    ) {
        // Header
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF374151).copy(alpha = 0.5f) // gray-800/50
            ),
            shape = RoundedCornerShape(0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF10B981)), // green-500
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Message,
                            contentDescription = "Chat Icon",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Secure Chat",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = Color(0xFF34D399), // green-400
                                        shape = CircleShape
                                    )
                            )

                            Text(
                                text = "Connected via ${selectedDevice?.name ?: "Unknown Device"}",
                                fontSize = 12.sp,
                                color = Color(0xFF34D399) // green-400
                            )
                        }
                    }
                }

                IconButton(onClick = onReset) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Chat",
                        tint = Color.White
                    )
                }
            }
        }

        // Messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Message,
                            contentDescription = "Empty Chat",
                            tint = Color(0xFF9CA3AF), // gray-400
                            modifier = Modifier
                                .size(48.dp)
                                .padding(bottom = 16.dp)
                        )

                        Text(
                            text = "Start your secure conversation",
                            fontSize = 16.sp,
                            color = Color(0xFF9CA3AF) // gray-400
                        )
                    }
                }
            } else {
                items(messages) { message ->
                    ChatMessageItem(message = message)
                }
            }
        }

        // Message Input
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF374151).copy(alpha = 0.5f) // gray-800/50
            ),
            shape = RoundedCornerShape(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newMessage,
                        onValueChange = { newMessage = it },
                        placeholder = {
                            Text(
                                text = "Type your encrypted message...",
                                color = Color(0xFF9CA3AF) // gray-400
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6), // blue-500
                            unfocusedBorderColor = Color(0xFF4B5563), // gray-600
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    IconButton(
                        onClick = {
                            if (newMessage.isNotBlank()) {
                                messages = messages + ChatMessage(
                                    newMessage,
                                    "you",
                                    "12:30 PM",
                                    true
                                )
                                newMessage = ""
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = if (newMessage.isNotBlank()) Color(0xFF3B82F6) else Color(0xFF4B5563),
                                shape = RoundedCornerShape(10.dp)
                            )
                    ) {
                                                 Icon(
                             imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "End-to-end encrypted via fusion node relay",
                    fontSize = 12.sp,
                    color = Color(0xFF9CA3AF), // gray-400
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

// Chat Message Item
@Composable
fun ChatMessageItem(message: ChatMessage) {
    val isFromUser = message.sender == "you"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isFromUser) Color(0xFF2563EB) else Color(0xFF4B5563) // blue-600 or gray-600
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.encrypted) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Encrypted",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    Text(
                        text = message.timestamp,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// Data class for chat messages
data class ChatMessage(
    val text: String,
    val sender: String,
    val timestamp: String,
    val encrypted: Boolean
)

// Connection Status Screen
@Composable
fun ConnectionStatusScreen(
    connectionType: String,
    connectionState: String,
    connectedDeviceInfo: DeviceInfo?,
    isCheckingConnection: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit
) {
    val title = if (connectionType == "bluetooth") "Bluetooth Connection Status" else "Wi-Fi Connection Status"
    val icon = if (connectionType == "bluetooth") Icons.Default.Wifi else Icons.Default.Router
    val isConnected = connectionState == "connected"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        if (isConnected) Color(0xFF064E3B) else Color(0xFF7C2D12), // green-900 or orange-900
                        if (isConnected) Color(0xFF1E40AF) else Color(0xFFDC2626), // blue-900 or red-600
                        Color(0xFF000000)  // black
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(
                            if (isConnected) {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF34D399), // green-400
                                        Color(0xFF3B82F6)  // blue-500
                                    )
                                )
                            } else {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFFB923C), // orange-400
                                        Color(0xFFEF4444)  // red-500
                                    )
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCheckingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = Color.White,
                            strokeWidth = 4.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = if (isConnected) "Connected" else "Not Connected",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isCheckingConnection) "Checking connection..." else if (isConnected) "Connection verified successfully!" else "No connection detected",
                    fontSize = 16.sp,
                    color = if (isConnected) Color(0xFFBBF7D0) else Color(0xFFFED7AA) // green-200 or orange-200
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Connection Status Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF374151).copy(alpha = 0.5f) // gray-800/50
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Connection Details:",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isConnected) Color(0xFF34D399) else Color(0xFFFB923C) // green-400 or orange-400
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isConnected && connectedDeviceInfo != null) {
                        // Connected device info
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        if (connectionType == "bluetooth") Color(0xFF60A5FA) else Color(0xFFC084FC)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = "Device Icon",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = connectedDeviceInfo.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )

                                Text(
                                    text = "${connectionType.replaceFirstChar { it.uppercase() }} • ${connectedDeviceInfo.signal}% signal",
                                    fontSize = 14.sp,
                                    color = Color(0xFF9CA3AF) // gray-400
                                )

                                Text(
                                    text = "Status: Connected",
                                    fontSize = 14.sp,
                                    color = Color(0xFF34D399) // green-400
                                )
                            }
                        }
                    } else {
                        // Not connected message
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info Icon",
                                tint = Color(0xFFFB923C), // orange-400
                                modifier = Modifier.size(24.dp)
                            )

                            Column {
                                Text(
                                    text = "No device connected",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )

                                Text(
                                    text = "Please connect to a $connectionType device or network first",
                                    fontSize = 14.sp,
                                    color = Color(0xFF9CA3AF) // gray-400
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            if (isConnected) {
                // Continue button when connected
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF10B981), // green-500
                                        Color(0xFF3B82F6)  // blue-600
                                    )
                                ),
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Continue Icon",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "Continue to Next Step",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
            } else {
                // Retry and back buttons when not connected
                Column {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFFFB923C), // orange-500
                                            Color(0xFFEF4444)  // red-600
                                        )
                                    ),
                                    RoundedCornerShape(16.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry Icon",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = "Retry Connection Check",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Warning message
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF7C2D12).copy(alpha = 0.3f) // orange-900/30
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = Color(0xFFFB923C) // orange-400
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Warning Icon",
                                tint = Color(0xFFFB923C), // orange-400
                                modifier = Modifier.size(24.dp)
                            )

                            Text(
                                                                 text = "Please connect to a $connectionType device or network before proceeding. You cannot move forward without an active connection.",
                                fontSize = 14.sp,
                                color = Color(0xFFFED7AA) // orange-200
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Back Button
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = Color(0xFF4B5563) // gray-600
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Back to Connection Guide",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}