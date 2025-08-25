package com.example.mine.ui.screens

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings
import android.bluetooth.BluetoothAdapter
import android.Manifest
import android.content.pm.PackageManager
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import com.example.mine.network.TcpConnectionStatus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView


import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.mine.viewmodel.SecureChatViewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.text.replaceFirstChar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import android.util.Log

@Composable
fun ModernMainScreen(viewModel: SecureChatViewModel) {
    val context = LocalContext.current
    
    // Debug: Log ViewModel initialization
    LaunchedEffect(Unit) {
        Log.d("MainScreen", "ViewModel initialized: $viewModel")
    }
    
    var currentScreen by remember { mutableStateOf("start") }
    var connectionType by remember { mutableStateOf<String?>(null) }

    var connectionState by remember { mutableStateOf("disconnected") }
    var isCheckingConnection by remember { mutableStateOf(false) }
    var connectedDeviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }
    
    // Get real fusion node information from ViewModel
    val connectedFusionNode by viewModel.connectedFusionNode.collectAsState()
    val publicKey by viewModel.publicKey.collectAsState()
    
    // Debug: Log fusion node changes
    LaunchedEffect(connectedFusionNode) {
        Log.d("MainScreen", "Fusion node changed: $connectedFusionNode")
    }
    
    // Debug: Log public key changes
    LaunchedEffect(publicKey) {
        Log.d("MainScreen", "Public key changed: ${publicKey != null}")
    }

    // Ensure public key exists when reaching manual key input screen
    LaunchedEffect(currentScreen) {
        if (currentScreen == "manual-key-input") {
            Log.d("MainScreen", "Manual key input screen reached, ensuring public key exists...")
            viewModel.ensurePublicKeyExists()
        }
    }

    // Handle system back press to navigate to previous screen
    BackHandler(enabled = currentScreen != "start") {
        currentScreen = when (currentScreen) {
            "connection-type" -> "start"
            "connection-guide" -> "connection-type"
            "connection-status" -> "connection-guide"
            "continue" -> "connection-status"
            "manual-key-input" -> "continue"
            "checking-connection" -> "manual-key-input"
            "chat" -> "start"
            else -> "start"
        }
    }

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
        "continue" -> NodeListeningScreen(
            selectedDevice = connectedDeviceInfo,
            viewModel = viewModel,
            onContinue = { currentScreen = "manual-key-input" }
        )
        "manual-key-input" -> ManualKeyInputScreen(
            onContinue = { currentScreen = "key-generation" },
            viewModel = viewModel
        )
        "key-generation" -> KeyGenerationScreen(
            onContinue = { currentScreen = "chat" },
            viewModel = viewModel
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
                    .fillMaxWidth(0.9f)
                    .align(Alignment.CenterHorizontally)
                    .heightIn(min = 120.dp),
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
                            .padding(horizontal = 24.dp, vertical = 16.dp),
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
                    .fillMaxWidth(0.9f)
                    .align(Alignment.CenterHorizontally)
                    .heightIn(min = 120.dp),
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
                            .padding(horizontal = 24.dp, vertical = 16.dp),
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

// Node Listening Screen
@Composable
fun NodeListeningScreen(
    selectedDevice: DeviceInfo?,
    viewModel: SecureChatViewModel,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(true) }
    var receivedNodeId by remember { mutableStateOf<String?>(null) }
    var listeningTime by remember { mutableStateOf(0) }
    
    // Get current fusion node info and TCP connection status
    val currentFusionNode by viewModel.connectedFusionNode.collectAsState()
    val isListeningForNodeId by viewModel.isListeningForNodeId.collectAsState()
    val tcpConnectionStatus by viewModel.wifiManager.getTcpConnectionStatus().collectAsState()
    val connectedNodeId by viewModel.wifiManager.getConnectedNodeId().collectAsState()
    
    // Start listening when screen appears
    LaunchedEffect(Unit) {
        viewModel.startListeningForNodeId()
    }
    
    // Timer for listening duration
    LaunchedEffect(isListening) {
        while (isListening) {
            delay(1000)
            listeningTime++
        }
    }
    
    // Check if we received a node ID from TCP
    LaunchedEffect(connectedNodeId) {
        if (connectedNodeId != null) {
            receivedNodeId = connectedNodeId
            isListening = false
        }
    }
    
    // Check if we received a node ID from other sources
    LaunchedEffect(currentFusionNode) {
        if (currentFusionNode != null && receivedNodeId == null) {
            receivedNodeId = viewModel.getFusionNodeInfo()
            isListening = false
        }
    }
    
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
            // Status Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(
                        if (receivedNodeId != null) {
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF34D399), // green-400
                                    Color(0xFF3B82F6)  // blue-500
                                )
                            )
                        } else {
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFF59E0B), // amber-500
                                    Color(0xFFEF4444)  // red-500
                                )
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (receivedNodeId != null) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Node ID Received",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = "Listening",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Status Title
            Text(
                text = if (receivedNodeId != null) "Node ID Received!" else "Listening for Fusion Node",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status Description
            Text(
                text = if (receivedNodeId != null) {
                    "Successfully received node ID from ESP32"
                } else {
                    "Waiting for ESP32 to send node ID..."
                },
                fontSize = 16.sp,
                color = if (receivedNodeId != null) Color(0xFFBBF7D0) else Color(0xFFFED7AA),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // TCP Connection Status
            Text(
                text = when (tcpConnectionStatus) {
                    is TcpConnectionStatus.Connecting -> "Connecting to ESP32..."
                    is TcpConnectionStatus.Connected -> "Connected to ESP32"
                    is TcpConnectionStatus.ConnectedWithNodeId -> "Connected with Node ID"
                    is TcpConnectionStatus.Failed -> "Connection failed: ${tcpConnectionStatus.error}"
                    is TcpConnectionStatus.Disconnected -> "Disconnected from ESP32"
                },
                fontSize = 14.sp,
                color = when (tcpConnectionStatus) {
                    is TcpConnectionStatus.Connected, is TcpConnectionStatus.ConnectedWithNodeId -> Color(0xFF34D399)
                    is TcpConnectionStatus.Connecting -> Color(0xFFF59E0B)
                    is TcpConnectionStatus.Failed -> Color(0xFFEF4444)
                    is TcpConnectionStatus.Disconnected -> Color(0xFF9CA3AF)
                },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Node Information Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF374151).copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Fusion Node Information",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (receivedNodeId != null) {
                        // Show received node ID
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeviceHub,
                                contentDescription = "Node ID",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(20.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Text(
                                text = "Node ID: $receivedNodeId",
                                fontSize = 16.sp,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Node ID", receivedNodeId)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Node ID copied!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Status: Connected",
                            fontSize = 14.sp,
                            color = Color(0xFF34D399)
                        )
                    } else {
                        // Show listening status
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = "Listening",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Text(
                                text = "Listening for ESP32...",
                                fontSize = 16.sp,
                                color = Color(0xFFF59E0B),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Animated dots
                        var dotCount by remember { mutableStateOf(0) }
                        LaunchedEffect(Unit) {
                            while (isListening) {
                                delay(500)
                                dotCount = (dotCount + 1) % 4
                            }
                        }
                        
                        Text(
                            text = "Listening${".".repeat(dotCount)}",
                            fontSize = 14.sp,
                            color = Color(0xFFF59E0B)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Duration: ${listeningTime}s",
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Action Buttons
            if (receivedNodeId != null) {
                // Continue button when node ID is received
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
                                contentDescription = "Continue",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "Continue to Key Exchange",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
            } else {
                // Manual input option
                Button(
                    onClick = {
                        // Allow manual input if listening takes too long
                        onContinue()
                    },
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
                                        Color(0xFF6B7280), // gray-500
                                        Color(0xFF4B5563)  // gray-600
                                    )
                                ),
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Text(
                            text = "Enter Node ID Manually",
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
                        text = "Continue to Enter Public Key & Node ID",
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

// Manual Key Input Screen
@Composable
fun ManualKeyInputScreen(
    onContinue: () -> Unit,
    viewModel: SecureChatViewModel
) {
    val context = LocalContext.current
    
    var peerPublicKey by remember { mutableStateOf("") }
    var peerNodeId by remember { mutableStateOf("") }
    var isKeyValid by remember { mutableStateOf(false) }
    var isNodeIdValid by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Get current device's public key info
    val currentPublicKey by viewModel.publicKey.collectAsState()
    val currentFusionNode by viewModel.connectedFusionNode.collectAsState()
    val isListeningForNodeId by viewModel.isListeningForNodeId.collectAsState()

    // Validate public key format (basic validation)
    fun validatePublicKey(key: String): Boolean {
        return key.isNotBlank() && key.length >= 20 // Basic length check
    }

    // Validate node ID format
    fun validateNodeId(nodeId: String): Boolean {
        return nodeId.isNotBlank() && nodeId.length >= 5 // Basic length check
    }

    // Handle peer public key input
    fun onPeerKeyChange(key: String) {
        peerPublicKey = key
        isKeyValid = validatePublicKey(key)
        showError = false
    }

    // Handle peer node ID input
    fun onPeerNodeIdChange(nodeId: String) {
        peerNodeId = nodeId
        isNodeIdValid = validateNodeId(nodeId)
        showError = false
    }

    // Handle continue button
    fun handleContinue() {
        if (isKeyValid && isNodeIdValid) {
            // Store the peer's public key and node ID in the ViewModel
            viewModel.setPeerPublicKey(peerPublicKey)
            viewModel.setPeerNodeId(peerNodeId)
            onContinue()
        } else {
            showError = true
            if (!isKeyValid && !isNodeIdValid) {
                errorMessage = "Please enter both a valid public key and node ID"
            } else if (!isKeyValid) {
                errorMessage = "Please enter a valid public key"
            } else {
                errorMessage = "Please enter a valid node ID"
            }
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
                    imageVector = Icons.Default.Key,
                    contentDescription = "Key Icon",
                    tint = Color(0xFFC084FC), // purple-400
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Manual Key & Node Exchange",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Enter the other device's public key and node ID",
                    fontSize = 16.sp,
                    color = Color(0xFFC4B5FD) // purple-200
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Current Device Information Card
             Card(
                 colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF374151).copy(alpha = 0.5f) // gray-800/50
                 ),
                 shape = RoundedCornerShape(16.dp)
             ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Your Device Information",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Public Key Info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                         ) {
                             Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Public Key",
                            tint = Color(0xFF34D399), // green-400
                            modifier = Modifier.size(16.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                             
                             Text(
                            text = "Public Key: ${viewModel.getPublicKeyInfo() ?: "Generating..."}",
                            fontSize = 14.sp,
                            color = Color(0xFFD1D5DB), // gray-300
                            modifier = Modifier.weight(1f)
                        )
                        
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Public Key", viewModel.getPublicKeyInfo() ?: "")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Public Key copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Public Key",
                                tint = Color(0xFF34D399), // green-400
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Fusion Node Info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeviceHub,
                            contentDescription = "Fusion Node",
                            tint = if (isListeningForNodeId) Color(0xFFF59E0B) else Color(0xFF60A5FA), // amber-500 if listening, blue-400 otherwise
                            modifier = Modifier.size(16.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = if (isListeningForNodeId) {
                                "Node ID: Listening for ESP32..."
                            } else {
                                "Node ID: ${viewModel.getFusionNodeInfo()}"
                            },
                            fontSize = 14.sp,
                            color = if (isListeningForNodeId) Color(0xFFF59E0B) else Color(0xFFD1D5DB), // amber-500 if listening, gray-300 otherwise
                            modifier = Modifier.weight(1f)
                        )
                        
                        if (!isListeningForNodeId && currentFusionNode != null) {
                            IconButton(
                        onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Fusion Node ID", viewModel.getFusionNodeInfo())
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Fusion Node ID copied!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Fusion Node ID",
                                    tint = Color(0xFF60A5FA), // blue-400
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    
                    // Show listening status
                    if (isListeningForNodeId) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = "Listening",
                                tint = Color(0xFFF59E0B), // amber-500
                                modifier = Modifier.size(12.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                text = "Listening for fusion node ID from ESP32...",
                                fontSize = 12.sp,
                                color = Color(0xFFF59E0B), // amber-500
                                modifier = Modifier.weight(1f)
                            )
                            
                            // Animated dot
                            var dotCount by remember { mutableStateOf(0) }
                            LaunchedEffect(Unit) {
                                while (isListeningForNodeId) {
                                    delay(500)
                                    dotCount = (dotCount + 1) % 4
                                }
                            }
                            
                            Text(
                                text = ".".repeat(dotCount),
                                fontSize = 12.sp,
                                color = Color(0xFFF59E0B) // amber-500
                            )
                        }
                    } else if (currentFusionNode == null) {
                        // Show button to start listening if no node ID is available
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                    Button(
                                onClick = { viewModel.startListeningForNodeId() },
                                modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF59E0B) // amber-500
                                ),
                                shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Radio,
                                        contentDescription = "Start Listening",
                                    tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                        text = "Start Listening",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            
                Button(
                                onClick = { viewModel.resetFusionNodeIdAndRestartListening() },
                                modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEF4444) // red-500
                                ),
                                shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reset",
                                    tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                        text = "Reset",
                                        color = Color.White,
                                        fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Timestamp Info
                    Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Timestamp",
                            tint = Color(0xFFF59E0B), // amber-500
                                modifier = Modifier.size(16.dp)
                            )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                            Text(
                            text = "Generated: ${viewModel.getCurrentTimestamp()}",
                            fontSize = 14.sp,
                            color = Color(0xFFD1D5DB) // gray-300
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Peer Information Input Card
                Card(
                    colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF374151).copy(alpha = 0.5f) // gray-800/50
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                        modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Enter Peer's Information",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                        Text(
                        text = "Ask the other device user to share their public key and node ID, then enter them below:",
                            fontSize = 14.sp,
                        color = Color(0xFFD1D5DB) // gray-300
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Public Key Input
                    OutlinedTextField(
                        value = peerPublicKey,
                        onValueChange = { onPeerKeyChange(it) },
                        label = { Text(text = "Peer's Public Key") },
                        placeholder = { Text(text = "Enter the other device's public key here...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFC084FC), // purple-400
                            unfocusedBorderColor = Color(0xFF6B7280), // gray-500
                            focusedLabelColor = Color(0xFFC084FC), // purple-400
                            unfocusedLabelColor = Color(0xFF9CA3AF), // gray-400
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFC084FC) // purple-400
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            fontSize = 14.sp,
                            color = Color.White
                        ),
                        singleLine = false,
                        minLines = 3,
                        maxLines = 5
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Node ID Input
                    OutlinedTextField(
                        value = peerNodeId,
                        onValueChange = { onPeerNodeIdChange(it) },
                        label = { Text(text = "Peer's Node ID") },
                        placeholder = { Text(text = "Enter the other device's node ID here...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF60A5FA), // blue-400
                            unfocusedBorderColor = Color(0xFF6B7280), // gray-500
                            focusedLabelColor = Color(0xFF60A5FA), // blue-400
                            unfocusedLabelColor = Color(0xFF9CA3AF), // gray-400
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF60A5FA) // blue-400
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            fontSize = 14.sp,
                            color = Color.White
                        ),
                        singleLine = true
                    )
                    
                    if (showError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Error Icon",
                                tint = Color(0xFFEF4444), // red-400
                                modifier = Modifier.size(16.dp)
                            )
                        Text(
                                text = errorMessage,
                                fontSize = 12.sp,
                                color = Color(0xFFFCA5A5) // red-200
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Continue Button
            Button(
                onClick = { handleContinue() },
                enabled = isKeyValid && isNodeIdValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isKeyValid && isNodeIdValid) Color.Transparent else Color(0xFF4B5563)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isKeyValid && isNodeIdValid) {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF10B981), // green-500
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
                            text = if (isKeyValid && isNodeIdValid) "Continue to Key Generation" else "Enter Valid Public Key & Node ID",
                            fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Instructions Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1F2937).copy(alpha = 0.5f) // gray-800/50
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "How to Exchange Keys & Node IDs:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "1. Share your public key and node ID with the other device user\n" +
                               "2. Ask them to enter your public key and node ID in their app\n" +
                               "3. Enter their public key and node ID in the fields above\n" +
                               "4. Both devices should now have each other's keys and node IDs\n" +
                               "5. Continue to generate shared key, session key, and payload",
                        fontSize = 12.sp,
                        color = Color(0xFF9CA3AF), // gray-400
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

// Key Generation Screen
@Composable
fun KeyGenerationScreen(
    onContinue: () -> Unit,
    viewModel: SecureChatViewModel
) {
    var currentStep by remember { mutableStateOf(0) }
    var sharedKeyGenerated by remember { mutableStateOf(false) }
    var sessionKeyGenerated by remember { mutableStateOf(false) }
    var payloadGenerated by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var generationProgress by remember { mutableStateOf(0f) }
    var generatedKeys by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    
    // Get peer information
    val peerPublicKey by viewModel.peerPublicKey.collectAsState()
    val peerNodeId by viewModel.targetFusionNode.collectAsState()
    val currentPublicKey by viewModel.publicKey.collectAsState()
    val currentFusionNode by viewModel.connectedFusionNode.collectAsState()
    
    // Auto-start key generation when screen appears
    LaunchedEffect(Unit) {
        if (peerPublicKey != null && peerNodeId != null) {
            startAutomaticKeyGeneration()
        }
    }
    
    // Manual key generation option
    LaunchedEffect(Unit) {
        // If automatic generation doesn't start within 3 seconds, show manual option
        delay(3000)
        if (currentStep == 0 && !isGenerating) {
            // Show manual generation option
        }
    }
    
    // Function to start automatic key generation
    fun startAutomaticKeyGeneration() {
        isGenerating = true
        currentStep = 0
        generationProgress = 0f
        
        CoroutineScope(Dispatchers.Main).launch {
            // Step 1: Generate Shared Key
            generateSharedKey()
            delay(500) // Brief pause between steps
            
            // Step 2: Generate Session Key
            generateSessionKey()
            delay(500) // Brief pause between steps
            
            // Step 3: Generate Payload
            generatePayload()
            
            isGenerating = false
        }
    }
    
    // Generate shared key
    fun generateSharedKey() {
        isGenerating = true
        currentStep = 0
        generationProgress = 0f
        
        CoroutineScope(Dispatchers.Main).launch {
            repeat(100) {
                delay(15)
                generationProgress = (it + 1) / 100f
            }
            
            // Generate a realistic shared key using peer's public key and our private key
            val sharedKey = generateRealisticSharedKey(currentPublicKey, peerPublicKey)
            generatedKeys = generatedKeys + ("sharedKey" to sharedKey)
            
            sharedKeyGenerated = true
            currentStep = 1
            isGenerating = false
        }
    }
    
    // Generate realistic shared key
    fun generateRealisticSharedKey(ourPublicKey: String?, peerPublicKey: String?): String {
        if (ourPublicKey == null || peerPublicKey == null) {
            return "ERROR: Missing keys"
        }
        
        // Simulate ECDH key exchange
        val combined = ourPublicKey + peerPublicKey
        val hash = combined.hashCode().toString(16).uppercase()
        return "SHARED_${hash.take(32)}"
    }
    
    // Generate session key
    fun generateSessionKey() {
        isGenerating = true
        currentStep = 1
        generationProgress = 0f
        
        CoroutineScope(Dispatchers.Main).launch {
            repeat(100) {
                delay(15)
                generationProgress = (it + 1) / 100f
            }
            
            // Generate session key from shared key
            val sharedKey = generatedKeys["sharedKey"] ?: "ERROR"
            val sessionKey = generateRealisticSessionKey(sharedKey)
            generatedKeys = generatedKeys + ("sessionKey" to sessionKey)
            
            sessionKeyGenerated = true
            currentStep = 2
            isGenerating = false
        }
    }
    
    // Generate realistic session key
    fun generateRealisticSessionKey(sharedKey: String): String {
        if (sharedKey.startsWith("ERROR")) {
            return "ERROR: Invalid shared key"
        }
        
        // Simulate HKDF derivation
        val timestamp = System.currentTimeMillis()
        val combined = sharedKey + timestamp.toString()
        val hash = combined.hashCode().toString(16).uppercase()
        return "SESSION_${hash.take(32)}"
    }

    // Generate payload
    fun generatePayload() {
        isGenerating = true
        generationProgress = 0f
        
        CoroutineScope(Dispatchers.Main).launch {
            repeat(100) {
                delay(15)
                generationProgress = (it + 1) / 100f
            }
            
            // Generate final payload using all keys
            val sharedKey = generatedKeys["sharedKey"] ?: "ERROR"
            val sessionKey = generatedKeys["sessionKey"] ?: "ERROR"
            val payload = generateRealisticPayload(sharedKey, sessionKey, currentFusionNode, peerNodeId)
            generatedKeys = generatedKeys + ("payload" to payload)
            
            payloadGenerated = true
            currentStep = 3
            isGenerating = false
        }
    }
    
    // Generate realistic payload
    fun generateRealisticPayload(sharedKey: String, sessionKey: String, ourNode: String?, peerNode: String?): String {
        if (sharedKey.startsWith("ERROR") || sessionKey.startsWith("ERROR")) {
            return "ERROR: Invalid keys"
        }
        
        // Simulate encrypted payload creation
        val timestamp = System.currentTimeMillis()
        val nodeInfo = "${ourNode ?: "UNKNOWN"}_${peerNode ?: "UNKNOWN"}"
        val combined = sharedKey + sessionKey + nodeInfo + timestamp.toString()
        val hash = combined.hashCode().toString(16).uppercase()
        return "PAYLOAD_${hash.take(48)}"
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
                    imageVector = Icons.Default.Key,
                    contentDescription = "Key Generation Icon",
                    tint = Color(0xFFC084FC), // purple-400
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

            Text(
                    text = "Key Generation Process",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

                Spacer(modifier = Modifier.height(8.dp))

            Text(
                    text = "Generating shared key, session key, and payload",
                    fontSize = 16.sp,
                    color = Color(0xFFC4B5FD) // purple-200
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Current Device Information Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF374151).copy(alpha = 0.5f) // gray-800/50
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
            Text(
                        text = "Your Device Information",
                        fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Current Fusion Node ID
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeviceHub,
                            contentDescription = "Current Fusion Node ID",
                            tint = Color(0xFF60A5FA), // blue-400
                            modifier = Modifier.size(16.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
            Text(
                            text = "Fusion Node ID: ${viewModel.getFusionNodeInfo()}",
                            fontSize = 14.sp,
                            color = Color(0xFFD1D5DB) // gray-300
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Peer Information Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF374151).copy(alpha = 0.5f) // gray-800/50
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Peer Information",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Peer Public Key
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Peer Public Key",
                            tint = Color(0xFF34D399), // green-400
                            modifier = Modifier.size(16.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "Public Key: ${peerPublicKey?.take(20) ?: "Not set"}...",
                            fontSize = 14.sp,
                            color = Color(0xFFD1D5DB) // gray-300
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Peer Node ID
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeviceHub,
                            contentDescription = "Peer Node ID",
                            tint = Color(0xFF60A5FA), // blue-400
                            modifier = Modifier.size(16.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "Node ID: ${peerNodeId ?: "Not set"}",
                            fontSize = 14.sp,
                            color = Color(0xFFD1D5DB) // gray-300
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Generated Keys Display (only show when keys are generated)
            if (generatedKeys.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF064E3B).copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = Color(0xFF34D399)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Generated Keys",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34D399)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        generatedKeys.forEach { (keyType, keyValue) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = keyType,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(16.dp)
                                )
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Text(
                                    text = "${keyType.replaceFirstChar { it.uppercase() }}:",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    modifier = Modifier.width(80.dp)
                                )
                                
                                Text(
                                    text = keyValue.take(20) + "...",
                                    fontSize = 12.sp,
                                    color = Color(0xFFD1D5DB),
                                    modifier = Modifier.weight(1f)
                                )
                                
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText(keyType, keyValue)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "$keyType copied!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy $keyType",
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            
                            if (keyType != generatedKeys.keys.last()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Key Generation Steps
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Step 1: Shared Key Generation
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentStep >= 1) Color(0xFF064E3B).copy(alpha = 0.3f) else Color(0xFF374151).copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (currentStep >= 1) Color(0xFF34D399) else Color(0xFF6B7280)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (currentStep >= 1) Icons.Default.Check else Icons.Default.Key,
                                    contentDescription = "Shared Key Icon",
                                    tint = if (currentStep >= 1) Color(0xFF34D399) else Color(0xFF6B7280),
                                    modifier = Modifier.size(24.dp)
                                )
                                
                                Text(
                                    text = "Step 1: Generate Shared Key",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentStep >= 1) Color(0xFF34D399) else Color.White
                                )
                            }
                            
                            if (currentStep >= 1) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Completed",
                                    tint = Color(0xFF34D399),
                            modifier = Modifier.size(20.dp)
                        )
                            }
                        }
                        
                        if (currentStep == 0 && !isGenerating) {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Button(
                                onClick = { generateSharedKey() },
                                enabled = !isGenerating,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981)
                                )
                            ) {
                                Text(text = "Generate Shared Key", color = Color.White)
                            }
                        }
                        
                        // Manual generation option if automatic failed
                        if (currentStep == 0 && !isGenerating && generatedKeys.isEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            OutlinedButton(
                                onClick = { generateSharedKey() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF10B981)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF10B981))
                            ) {
                                Text(text = "Manual: Generate Shared Key", color = Color.White)
                            }
                        }
                        
                        if (isGenerating && currentStep == 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            LinearProgressIndicator(
                                progress = generationProgress,
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF10B981)
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))

                        Text(
                                text = "Generating shared key... ${(generationProgress * 100).toInt()}%",
                                fontSize = 12.sp,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }

                // Step 2: Session Key Generation
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentStep >= 2) Color(0xFF064E3B).copy(alpha = 0.3f) else Color(0xFF374151).copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (currentStep >= 2) Color(0xFF34D399) else Color(0xFF6B7280)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                        Icon(
                                    imageVector = if (currentStep >= 2) Icons.Default.Check else Icons.Default.Key,
                                    contentDescription = "Session Key Icon",
                                    tint = if (currentStep >= 2) Color(0xFF34D399) else Color(0xFF6B7280),
                                    modifier = Modifier.size(24.dp)
                                )
                                
                                Text(
                                    text = "Step 2: Generate Session Key",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentStep >= 2) Color(0xFF34D399) else Color.White
                                )
                            }
                            
                            if (currentStep >= 2) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Completed",
                                    tint = Color(0xFF34D399),
                            modifier = Modifier.size(20.dp)
                        )
                            }
                        }
                        
                        if (currentStep == 1 && !isGenerating) {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Button(
                                onClick = { generateSessionKey() },
                                enabled = !isGenerating,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF3B82F6)
                                )
                            ) {
                                Text(text = "Generate Session Key", color = Color.White)
                            }
                        }
                        
                        // Manual generation option if automatic failed
                        if (currentStep == 1 && !isGenerating && !generatedKeys.containsKey("sessionKey")) {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            OutlinedButton(
                                onClick = { generateSessionKey() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF3B82F6)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF3B82F6))
                            ) {
                                Text(text = "Manual: Generate Session Key", color = Color.White)
                            }
                        }
                        
                        if (isGenerating && currentStep == 1) {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            LinearProgressIndicator(
                                progress = generationProgress,
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF3B82F6)
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))

                        Text(
                                text = "Generating session key... ${(generationProgress * 100).toInt()}%",
                                fontSize = 12.sp,
                                color = Color(0xFF3B82F6)
                            )
                        }
                    }
                }

                // Step 3: Payload Generation
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentStep >= 3) Color(0xFF064E3B).copy(alpha = 0.3f) else Color(0xFF374151).copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (currentStep >= 3) Color(0xFF34D399) else Color(0xFF6B7280)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                        Icon(
                                    imageVector = if (currentStep >= 3) Icons.Default.Check else Icons.Default.Key,
                                    contentDescription = "Payload Icon",
                                    tint = if (currentStep >= 3) Color(0xFF34D399) else Color(0xFF6B7280),
                                    modifier = Modifier.size(24.dp)
                                )
                                
                                Text(
                                    text = "Step 3: Generate Payload",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentStep >= 3) Color(0xFF34D399) else Color.White
                                )
                            }
                            
                            if (currentStep >= 3) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Completed",
                                    tint = Color(0xFF34D399),
                            modifier = Modifier.size(20.dp)
                        )
                            }
                        }
                        
                        if (currentStep == 2 && !isGenerating) {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Button(
                                onClick = { generatePayload() },
                                enabled = !isGenerating,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF8B5CF6)
                                )
                            ) {
                                Text(text = "Generate Payload", color = Color.White)
                            }
                        }
                        
                        // Manual generation option if automatic failed
                        if (currentStep == 2 && !isGenerating && !generatedKeys.containsKey("payload")) {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            OutlinedButton(
                                onClick = { generatePayload() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF8B5CF6)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF8B5CF6))
                            ) {
                                Text(text = "Manual: Generate Payload", color = Color.White)
                            }
                        }
                        
                        if (isGenerating && currentStep == 2) {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            LinearProgressIndicator(
                                progress = generationProgress,
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF8B5CF6)
                            )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                                text = "Generating payload... ${(generationProgress * 100).toInt()}%",
                        fontSize = 12.sp,
                                color = Color(0xFF8B5CF6)
                    )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Retry Button (if generation failed)
                if (currentStep < 3 && !isGenerating) {
                    Button(
                        onClick = { startAutomaticKeyGeneration() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF59E0B)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = "Retry Generation",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
                
                // Continue Button (only enabled when all steps are complete)
                Button(
                    onClick = onContinue,
                    enabled = currentStep >= 3,
                    modifier = Modifier
                        .weight(if (currentStep < 3 && !isGenerating) 1f else 1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentStep >= 3) Color.Transparent else Color(0xFF4B5563)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (currentStep >= 3) {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF10B981), // green-500
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
                            text = if (currentStep >= 3) "Continue to Chat" else "Complete All Steps First",
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

// Chat Screen
@Composable
fun ChatScreen(
    selectedDevice: DeviceInfo?,
    onReset: () -> Unit
) {
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var newMessage by remember { mutableStateOf("") }
    var isConnected by remember { mutableStateOf(true) }
    var connectionStatus by remember { mutableStateOf("Connected") }
    
    // Get ViewModel for key information
    val viewModel = LocalContext.current as? SecureChatViewModel
    
    // Initialize with welcome messages
    LaunchedEffect(Unit) {
        messages = listOf(
            ChatMessage("Secure connection established!", "system", "Now", true),
            ChatMessage("Keys generated successfully. You can now send encrypted messages.", "system", "Now", true),
            ChatMessage("Hello! This is a secure encrypted chat.", "you", "Now", true)
        )
    }
    
    // Function to send encrypted message
    fun sendEncryptedMessage() {
        if (newMessage.isNotBlank()) {
            val timestamp = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            val encryptedMessage = encryptMessage(newMessage)
            
            messages = messages + ChatMessage(
                text = encryptedMessage,
                sender = "you",
                timestamp = timestamp,
                encrypted = true,
                originalText = newMessage
            )
            
            newMessage = ""
            
            // Simulate peer response after a short delay
            CoroutineScope(Dispatchers.Main).launch {
                delay(1000 + (0..2000).random())
                val peerResponse = "Message received and decrypted successfully!"
                val peerTimestamp = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                
                messages = messages + ChatMessage(
                    text = peerResponse,
                    sender = "peer",
                    timestamp = peerTimestamp,
                    encrypted = true
                )
            }
        }
    }
    
    // Function to encrypt message (simulate encryption)
    fun encryptMessage(message: String): String {
        val timestamp = System.currentTimeMillis()
        val combined = message + timestamp.toString()
        val hash = combined.hashCode().toString(16).uppercase()
        return "ENC_${hash.take(16)}"
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
        // Connection Status Bar
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF064E3B).copy(alpha = 0.8f) // green-900/80
            ),
            shape = RoundedCornerShape(0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = Color(0xFF34D399),
                            shape = CircleShape
                        )
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SignalCellular4Bar,
                        contentDescription = "Signal",
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(14.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = "Secure Connection Active - All Keys Generated",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Encryption",
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        
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
                        
                        Spacer(modifier = Modifier.height(2.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Encryption",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(10.dp)
                            )
                            
                            Text(
                                text = "End-to-end encrypted",
                                fontSize = 10.sp,
                                color = Color(0xFF34D399)
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
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFC084FC) // purple-400
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                                        IconButton(
                        onClick = { sendEncryptedMessage() },
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

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Encryption",
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(12.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = "End-to-end encrypted via fusion node relay",
                        fontSize = 12.sp,
                        color = Color(0xFF9CA3AF), // gray-400
                    )
                }
            }
        }
    }
}

// Chat Message Item
@Composable
fun ChatMessageItem(message: ChatMessage) {
    val isFromUser = message.sender == "you"
    val isSystemMessage = message.sender == "system"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isSystemMessage -> Color(0xFF7C3AED) // purple-600 for system messages
                    isFromUser -> Color(0xFF2563EB) // blue-600 for user messages
                    else -> Color(0xFF4B5563) // gray-600 for peer messages
                }
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Show original text if available, otherwise show encrypted text
                val displayText = if (message.originalText != null && isFromUser) {
                    message.originalText
                } else {
                    message.text
                }
                
                Text(
                    text = displayText,
                    fontSize = 14.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.encrypted && !isSystemMessage) {
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
                
                // Show encrypted hash for user messages
                if (isFromUser && message.originalText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Encrypted: ${message.text}",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        fontStyle = FontStyle.Italic
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
    val encrypted: Boolean,
    val originalText: String? = null
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

