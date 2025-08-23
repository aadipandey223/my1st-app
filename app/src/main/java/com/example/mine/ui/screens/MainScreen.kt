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
import androidx.compose.ui.draw.blur
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mine.ui.theme.*
import com.example.mine.viewmodel.SecureChatViewModel
import com.example.mine.viewmodel.UiState
import kotlinx.coroutines.delay

enum class AppStep {
    WELCOME,
    KEY_GENERATION,
    KEY_DISPLAY,
    DISCOVERY,
    KEY_EXCHANGE,
    CHAT
}

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
    
    var currentStep by remember { mutableStateOf(AppStep.WELCOME) }
    var showQRScanner by remember { mutableStateOf(false) }
    var showPeerKeyDialog by remember { mutableStateOf(false) }
    var peerPublicKey by remember { mutableStateOf("") }
    var showChat by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }
    var keyGenerationProgress by remember { mutableStateOf(0f) }
    var isGeneratingKeys by remember { mutableStateOf(false) }
    
    // Background gradient
    val backgroundGradient = Brush.radialGradient(
        colors = listOf(
            Color(0xFF1E293B), // slate-900
            Color(0xFF581C87), // purple-900
            Color(0xFF1E293B)  // slate-900
        )
    )
    
    // Floating orbs animation
    val infiniteTransition = rememberInfiniteTransition(label = "orbs")
    val orb1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb1"
    )
    
    val orb2Offset by infiniteTransition.animateFloat(
        initialValue = 100f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb2"
    )
    
    LaunchedEffect(uiState) {
        when (uiState) {
            is UiState.SessionEstablished -> {
                showChat = true
                currentStep = AppStep.CHAT
            }
            is UiState.KeyGenerated -> {
                currentStep = AppStep.KEY_DISPLAY
            }
            is UiState.DiscoveryActive -> {
                currentStep = AppStep.DISCOVERY
            }
            else -> {}
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        // Floating orbs background
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Orb 1
            Box(
                modifier = Modifier
                    .offset(x = (orb1Offset - 50).dp, y = 100.dp)
                    .size(120.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x336366F1), // violet-500 with transparency
                                Color(0x008B5CF6)  // purple-600 with transparency
                            )
                        ),
                        shape = RoundedCornerShape(50)
                    )
                    .blur(20.dp)
            )
            
            // Orb 2
            Box(
                modifier = Modifier
                    .offset(x = (orb2Offset - 30).dp, y = 300.dp)
                    .size(80.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x338B5CF6), // purple-600 with transparency
                                Color(0x006366F1)  // violet-500 with transparency
                            )
                        ),
                        shape = RoundedCornerShape(40)
                    )
                    .blur(15.dp)
            )
        }
        
        // Main content
        when {
            showChat -> {
                ChatScreen(
                    messages = messages,
                    onSendMessage = { message ->
                        viewModel.sendMessage(message, 2)
                    },
                    onBack = { 
                        showChat = false
                        currentStep = AppStep.WELCOME
                    }
                )
            }
            else -> {
                when (currentStep) {
                    AppStep.WELCOME -> WelcomeStep(
                        onGenerateKeys = {
                            isGeneratingKeys = true
                            currentStep = AppStep.KEY_GENERATION
                            viewModel.generateKeyPair()
                        },
                        isGenerating = isGeneratingKeys,
                        progress = keyGenerationProgress
                    )
                    AppStep.KEY_GENERATION -> KeyGenerationStep(
                        progress = keyGenerationProgress,
                        onComplete = {
                            currentStep = AppStep.KEY_DISPLAY
                            isGeneratingKeys = false
                        }
                    )
                    AppStep.KEY_DISPLAY -> KeyDisplayStep(
                        publicKey = publicKey,
                        qrCodeBitmap = qrCodeBitmap,
                        onNext = { currentStep = AppStep.DISCOVERY }
                    )
                    AppStep.DISCOVERY -> DiscoveryStep(
                        discoveredDevices = discoveredDevices,
                        discoveredNetworks = discoveredNetworks,
                        onStartDiscovery = { viewModel.startDeviceDiscovery() },
                        onConnectBle = { device -> viewModel.connectToBleDevice(device) },
                        onConnectWifi = { network -> viewModel.connectToWifiNetwork(network) },
                        onNext = { currentStep = AppStep.KEY_EXCHANGE }
                    )
                    AppStep.KEY_EXCHANGE -> KeyExchangeStep(
                        onScanQR = { showQRScanner = true },
                        onEnterKey = { showPeerKeyDialog = true },
                        onNext = { currentStep = AppStep.CHAT }
                    )
                    else -> WelcomeStep(
                        onGenerateKeys = {
                            isGeneratingKeys = true
                            currentStep = AppStep.KEY_GENERATION
                            viewModel.generateKeyPair()
                        },
                        isGenerating = isGeneratingKeys,
                        progress = keyGenerationProgress
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
fun WelcomeStep(
    onGenerateKeys: () -> Unit,
    isGenerating: Boolean,
    progress: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "welcome")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glassmorphism card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(20.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated logo
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(logoScale)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF8B5CF6), // violet-500
                                    Color(0xFF7C3AED)  // violet-600
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔐",
                        fontSize = 32.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Title with gradient
                Text(
                    text = "SecureChat",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "End-to-End Encrypted Messaging",
                    fontSize = 16.sp,
                    color = Color.Gray.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Generate keys button
                Button(
                    onClick = onGenerateKeys,
                    enabled = !isGenerating,
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
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF7C3AED), // violet-600
                                        Color(0xFF6D28D9)  // violet-700
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isGenerating) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Generating Keys... ${(progress * 100).toInt()}%",
                                    color = Color.White,
                                    fontWeight = FontWeight.Semibold
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "🔑",
                                    fontSize = 20.sp
                                )
                                Text(
                                    text = "Generate Encryption Keys",
                                    color = Color.White,
                                    fontWeight = FontWeight.Semibold
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Security info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF3B82F6).copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🛡️",
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Keys are generated locally and stored securely",
                            fontSize = 14.sp,
                            color = Color(0xFF93C5FD) // blue-300
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KeyGenerationStep(
    progress: Float,
    onComplete: () -> Unit
) {
    LaunchedEffect(progress) {
        if (progress >= 1f) {
            delay(500)
            onComplete()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(20.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated icon
                val infiniteTransition = rememberInfiniteTransition(label = "keyGen")
                val iconRotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing)
                    ),
                    label = "rotation"
                )
                
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .rotate(iconRotation)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF3B82F6), // blue-500
                                    Color(0xFF06B6D4)  // cyan-500
                                )
                            ),
                            shape = RoundedCornerShape(32.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔄",
                        fontSize = 24.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Generating Keys",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Creating your encryption keypair...",
                    fontSize = 16.sp,
                    color = Color.Gray.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(
                            color = Color.Gray.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(4.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF3B82F6), // blue-500
                                        Color(0xFF06B6D4)  // cyan-500
                                    )
                                ),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "${(progress * 100).toInt()}% Complete",
                    fontSize = 14.sp,
                    color = Color.Gray.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun KeyDisplayStep(
    publicKey: java.security.KeyPair?,
    qrCodeBitmap: android.graphics.Bitmap?,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(20.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Your Public Key",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Share this with your contact",
                    fontSize = 16.sp,
                    color = Color.Gray.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // QR Code display
                Card(
                    modifier = Modifier
                        .size(200.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (qrCodeBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = qrCodeBitmap.asImageBitmap(),
                            contentDescription = "Public Key QR Code",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📱",
                                fontSize = 48.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Public Key (X25519)",
                    fontSize = 12.sp,
                    color = Color.Gray.copy(alpha = 0.6f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Key display
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Gray.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = publicKey?.public?.encoded?.let { 
                            android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)
                        } ?: "Generating...",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = Color.Gray.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Next button
                Button(
                    onClick = onNext,
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
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF059669), // emerald-600
                                        Color(0xFF0D9488)  // teal-600
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "📥",
                                fontSize = 20.sp
                            )
                            Text(
                                text = "Enter Receiver's Key",
                                color = Color.White,
                                fontWeight = FontWeight.Semibold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveryStep(
    discoveredDevices: List<com.example.mine.network.FusionNode>,
    discoveredNetworks: List<com.example.mine.network.FusionWifiNetwork>,
    onStartDiscovery: () -> Unit,
    onConnectBle: (com.example.mine.network.FusionNode) -> Unit,
    onConnectWifi: (com.example.mine.network.FusionWifiNetwork) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(20.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = "Discover Fusion Nodes",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Find nearby devices to connect with",
                    fontSize = 16.sp,
                    color = Color.Gray.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Start discovery button
                Button(
                    onClick = onStartDiscovery,
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
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF7C2D12), // orange-800
                                        Color(0xFFDC2626)  // red-600
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🔍",
                                fontSize = 20.sp
                            )
                            Text(
                                text = "Start Discovery",
                                color = Color.White,
                                fontWeight = FontWeight.Semibold
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Discovered devices
                if (discoveredDevices.isNotEmpty()) {
                    Text(
                        text = "Bluetooth LE Devices",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Semibold,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    discoveredDevices.forEach { device ->
                        DeviceItem(
                            name = device.name,
                            address = device.address,
                            rssi = device.rssi,
                            onConnect = { onConnectBle(device) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                // Discovered networks
                if (discoveredNetworks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "WiFi Networks",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Semibold,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    discoveredNetworks.forEach { network ->
                        NetworkItem(
                            ssid = network.ssid,
                            rssi = network.rssi,
                            onConnect = { onConnectWifi(network) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Next button
                Button(
                    onClick = onNext,
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
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF7C3AED), // violet-600
                                        Color(0xFF6D28D9)  // violet-700
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Next: Key Exchange",
                            color = Color.White,
                            fontWeight = FontWeight.Semibold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KeyExchangeStep(
    onScanQR: () -> Unit,
    onEnterKey: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(20.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Key Exchange",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Establish secure connection with peer",
                    fontSize = 16.sp,
                    color = Color.Gray.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Two buttons side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Scan QR button
                    Button(
                        onClick = onScanQR,
                        modifier = Modifier
                            .weight(1f)
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
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF8B5CF6), // violet-500
                                            Color(0xFF7C3AED)  // violet-600
                                        )
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "📷",
                                    fontSize = 20.sp
                                )
                                Text(
                                    text = "Scan QR",
                                    color = Color.White,
                                    fontWeight = FontWeight.Semibold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    
                    // Enter Key button
                    Button(
                        onClick = onEnterKey,
                        modifier = Modifier
                            .weight(1f)
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
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF8B5CF6), // violet-500
                                            Color(0xFF7C3AED)  // violet-600
                                        )
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "⌨️",
                                    fontSize = 20.sp
                                )
                                Text(
                                    text = "Enter Key",
                                    color = Color.White,
                                    fontWeight = FontWeight.Semibold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Scan a QR code or manually enter the peer's public key to establish a secure session",
                    fontSize = 14.sp,
                    color = Color.Gray.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Next button
                Button(
                    onClick = onNext,
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
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF059669), // emerald-600
                                        Color(0xFF0D9488)  // teal-600
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Start Secure Chat",
                            color = Color.White,
                            fontWeight = FontWeight.Semibold
                        )
                    }
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        shape = RoundedCornerShape(12.dp)
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
                tint = Color(0xFF3B82F6), // blue-500
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray.copy(alpha = 0.8f)
                )
                Text(
                    text = "Signal: ${rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray.copy(alpha = 0.8f)
                )
            }
            
            Button(
                onClick = onConnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3B82F6) // blue-500
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Connect", color = Color.White)
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        shape = RoundedCornerShape(12.dp)
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
                tint = Color(0xFF10B981), // emerald-500
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ssid,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = "Signal: ${rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray.copy(alpha = 0.8f)
                )
            }
            
            Button(
                onClick = onConnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981) // emerald-500
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Connect", color = Color.White)
            }
        }
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
