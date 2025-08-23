package com.example.mine.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mine.ui.theme.*
import com.example.mine.viewmodel.SecureChatViewModel
import com.example.mine.viewmodel.UiState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val viewModel: SecureChatViewModel = viewModel { SecureChatViewModel(context) }
    val uiState by viewModel.uiState.collectAsState()
    val publicKey by viewModel.publicKey.collectAsState()
    val qrCodeBitmap by viewModel.qrCodeBitmap.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val discoveredNetworks by viewModel.discoveredNetworks.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val messages by viewModel.messages.collectAsState()
    
    var showQRScanner by remember { mutableStateOf(false) }
    var showPeerKeyDialog by remember { mutableStateOf(false) }
    var peerPublicKey by remember { mutableStateOf("") }
    var showChat by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }
    
    val gradientBackground = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1A237E), // Deep Blue
            Color(0xFF0D47A1), // Blue
            Color(0xFF01579B)  // Light Blue
        )
    )
    
    LaunchedEffect(uiState) {
        when (uiState) {
            is UiState.SessionEstablished -> {
                showChat = true
            }
            else -> {}
        }
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBackground)
                .padding(padding)
        ) {
            when {
                showChat -> {
                    ChatScreen(
                        messages = messages,
                        onSendMessage = { message ->
                            viewModel.sendMessage(message, 2) // Destination ID
                        },
                        onBack = { showChat = false }
                    )
                }
                else -> {
                    MainContent(
                        uiState = uiState,
                        publicKey = publicKey,
                        qrCodeBitmap = qrCodeBitmap,
                        discoveredDevices = discoveredDevices,
                        discoveredNetworks = discoveredNetworks,
                        onGenerateKey = { viewModel.generateKeyPair() },
                        onStartDiscovery = { viewModel.startDeviceDiscovery() },
                        onConnectBle = { device -> viewModel.connectToBleDevice(device) },
                        onConnectWifi = { network -> viewModel.connectToWifiNetwork(network) },
                        onScanQR = { showQRScanner = true },
                        onEnterPeerKey = { showPeerKeyDialog = true }
                    )
                }
            }
        }
    }
    
    // Peer Key Input Dialog
    if (showPeerKeyDialog) {
        PeerKeyDialog(
            onDismiss = { showPeerKeyDialog = false },
            onConfirm = { key ->
                peerPublicKey = key
                showPeerKeyDialog = false
                // TODO: Parse and use the peer public key
            }
        )
    }
}

@Composable
fun MainContent(
    uiState: UiState,
    publicKey: java.security.KeyPair?,
    qrCodeBitmap: android.graphics.Bitmap?,
    discoveredDevices: List<com.example.mine.network.FusionNode>,
    discoveredNetworks: List<com.example.mine.network.FusionWifiNetwork>,
    onGenerateKey: () -> Unit,
    onStartDiscovery: () -> Unit,
    onConnectBle: (com.example.mine.network.FusionNode) -> Unit,
    onConnectWifi: (com.example.mine.network.FusionWifiNetwork) -> Unit,
    onScanQR: () -> Unit,
    onEnterPeerKey: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        HeaderSection()
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Key Generation Section
        KeyGenerationSection(
            publicKey = publicKey,
            qrCodeBitmap = qrCodeBitmap,
            onGenerateKey = onGenerateKey
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Device Discovery Section
        DeviceDiscoverySection(
            discoveredDevices = discoveredDevices,
            discoveredNetworks = discoveredNetworks,
            onStartDiscovery = onStartDiscovery,
            onConnectBle = onConnectBle,
            onConnectWifi = onConnectWifi
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Key Exchange Section
        KeyExchangeSection(
            onScanQR = onScanQR,
            onEnterPeerKey = onEnterPeerKey
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Status Section
        StatusSection(uiState = uiState)
    }
}

@Composable
fun HeaderSection() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Security",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Secure Communication",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "End-to-end encrypted messaging via fusion nodes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun KeyGenerationSection(
    publicKey: java.security.KeyPair?,
    qrCodeBitmap: android.graphics.Bitmap?,
    onGenerateKey: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Step 1: Generate Keys",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (publicKey == null) {
                Button(
                    onClick = onGenerateKey,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = "Generate Key",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate X25519 Key Pair")
                }
            } else {
                // Show QR Code
                qrCodeBitmap?.let { bitmap ->
                    Card(
                        modifier = Modifier
                            .size(200.dp)
                            .shadow(4.dp, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        )
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Public Key QR Code",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Public Key Generated!",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Share this QR code with others to establish secure communication",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun DeviceDiscoverySection(
    discoveredDevices: List<com.example.mine.network.FusionNode>,
    discoveredNetworks: List<com.example.mine.network.FusionWifiNetwork>,
    onStartDiscovery: () -> Unit,
    onConnectBle: (com.example.mine.network.FusionNode) -> Unit,
    onConnectWifi: (com.example.mine.network.FusionWifiNetwork) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Step 2: Discover Fusion Nodes",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onStartDiscovery,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Discovery")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // BLE Devices
            if (discoveredDevices.isNotEmpty()) {
                Text(
                    text = "Bluetooth LE Devices",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                discoveredDevices.forEach { device ->
                    DeviceItem(
                        name = device.name,
                        address = device.address,
                        rssi = device.rssi,
                        onConnect = { onConnectBle(device) }
                    )
                }
            }
            
            // WiFi Networks
            if (discoveredNetworks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "WiFi Networks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                discoveredNetworks.forEach { network ->
                    NetworkItem(
                        ssid = network.ssid,
                        rssi = network.rssi,
                        onConnect = { onConnectWifi(network) }
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceItem(
    name: String,
    address: String,
    rssi: Int,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.BluetoothSearching,
                contentDescription = "BLE Device",
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Signal: ${rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Button(
                onClick = onConnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Connect")
            }
        }
    }
}

@Composable
fun NetworkItem(
    ssid: String,
    rssi: Int,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.WifiTethering,
                contentDescription = "WiFi Network",
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ssid,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Signal: ${rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Button(
                onClick = onConnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Connect")
            }
        }
    }
}

@Composable
fun KeyExchangeSection(
    onScanQR: () -> Unit,
    onEnterPeerKey: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Step 3: Key Exchange",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onScanQR,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan QR",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan QR")
                }
                
                Button(
                    onClick = onEnterPeerKey,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Enter Key",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enter Key")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Scan a QR code or manually enter the peer's public key to establish a secure session",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun StatusSection(uiState: UiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            when (uiState) {
                is UiState.Initial -> {
                    StatusItem(
                        icon = Icons.Default.Info,
                        text = "Ready to start",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is UiState.Loading -> {
                    StatusItem(
                        icon = Icons.Default.Refresh,
                        text = uiState.message,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is UiState.KeyGenerated -> {
                    StatusItem(
                        icon = Icons.Default.CheckCircle,
                        text = "Key pair generated successfully",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is UiState.DiscoveryActive -> {
                    StatusItem(
                        icon = Icons.Default.Search,
                        text = "Discovering fusion nodes...",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                is UiState.Connected -> {
                    StatusItem(
                        icon = Icons.Default.WifiTethering,
                        text = "Connected to fusion node",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is UiState.SessionEstablished -> {
                    StatusItem(
                        icon = Icons.Default.Shield,
                        text = "Secure session established",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is UiState.Error -> {
                    StatusItem(
                        icon = Icons.Default.Error,
                        text = uiState.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    StatusItem(
                        icon = Icons.Default.Info,
                        text = "Unknown state",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun StatusItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

@Composable
fun PeerKeyDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var keyText by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Enter Peer Public Key")
        },
        text = {
            OutlinedTextField(
                value = keyText,
                onValueChange = { keyText = it },
                label = { Text("Public Key (Base64)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(keyText) }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}
