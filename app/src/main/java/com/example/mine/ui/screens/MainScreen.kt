package com.example.mine.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mine.viewmodel.SecureChatViewModel
import com.example.mine.viewmodel.UiState
import com.example.mine.ui.screens.ChatScreen
import kotlinx.coroutines.delay
import android.util.Log

enum class AppStep {
    WELCOME,
    NETWORK_DISCOVERY,
    CONNECTION_ESTABLISHED,
    QR_CODE_DISPLAY,
    QR_CODE_SCANNING,
    CHAT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernMainScreen() {
    val context = LocalContext.current
    val viewModel = viewModel<SecureChatViewModel>(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return SecureChatViewModel(context) as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val publicKey by viewModel.publicKey.collectAsState()
    val qrCodeBitmap by viewModel.qrCodeBitmap.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val discoveredNetworks by viewModel.discoveredNetworks.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val keyGenerationProgress by viewModel.keyGenerationProgress.collectAsState()
    val connectedFusionNode by viewModel.connectedFusionNode.collectAsState()
    val targetFusionNode by viewModel.targetFusionNode.collectAsState()
    val peerPublicKey by viewModel.peerPublicKey.collectAsState()
    
    // New state variables for permissions and discovery status
    val permissionStatus by viewModel.permissionStatus.collectAsState()
    val hasAllPermissions by viewModel.hasAllPermissions.collectAsState()
    val bluetoothDiscoveryStatus by viewModel.bluetoothDiscoveryStatus.collectAsState()
    val wifiDiscoveryStatus by viewModel.wifiDiscoveryStatus.collectAsState()
    
    // Computed property for error message to avoid smart cast issues
    val errorMessage: String? = when (val currentState = uiState) {
        is UiState.Error -> currentState.message
        else -> null
    }
    
    var currentStep by remember { mutableStateOf(AppStep.WELCOME) }
    var showQRScanner by remember { mutableStateOf(false) }
    var showPeerKeyDialog by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }
    var isGeneratingKeys by remember { mutableStateOf(false) }
    var scannedQRData by remember { mutableStateOf<String?>(null) }
    
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
            is UiState.ConnectionEstablished -> {
                currentStep = AppStep.CONNECTION_ESTABLISHED
            }
            is UiState.KeyGenerated -> {
                currentStep = AppStep.QR_CODE_DISPLAY
            }
            is UiState.DiscoveryActive -> {
                currentStep = AppStep.NETWORK_DISCOVERY
            }
            is UiState.Error -> {
                // Handle error state - stay on current step but show error
                Log.e("MainScreen", "UI Error: ${errorMessage}")
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
                            currentStep = AppStep.NETWORK_DISCOVERY
                            viewModel.startDeviceDiscovery()
                        },
                        isGenerating = isGeneratingKeys,
                        progress = keyGenerationProgress,
                        hasAllPermissions = hasAllPermissions,
                        permissionStatus = permissionStatus
                    )
                    AppStep.NETWORK_DISCOVERY -> DiscoveryStep(
                        discoveredDevices = discoveredDevices,
                        discoveredNetworks = discoveredNetworks,
                        bluetoothDiscoveryStatus = bluetoothDiscoveryStatus,
                        wifiDiscoveryStatus = wifiDiscoveryStatus,
                        onStartDiscovery = { 
                            // Check permissions first, then start discovery
                            if (hasAllPermissions) {
                                viewModel.startDeviceDiscovery()
                            } else {
                                // Request permissions through the activity
                                viewModel.checkAndRequestPermissions(context as android.app.Activity)
                            }
                        },
                        onConnectBle = { device -> viewModel.connectToBleDevice(device) },
                        onConnectWifi = { network -> viewModel.connectToWifiNetwork(network) },
                        onNext = { currentStep = AppStep.CONNECTION_ESTABLISHED },
                        hasAllPermissions = hasAllPermissions
                    )
                    AppStep.CONNECTION_ESTABLISHED -> {
                        // This step shows connection established and automatically transitions to key generation
                        // The ViewModel will handle the transition
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
                                        text = "Connection Established!",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Text(
                                        text = "Connected to: ${connectedFusionNode ?: "Unknown Node"}",
                                        fontSize = 16.sp,
                                        color = Color(0xFF10B981), // emerald-500
                                        fontWeight = FontWeight.Medium
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Text(
                                        text = "Generating your encryption keys...",
                                        fontSize = 16.sp,
                                        color = Color.Gray.copy(alpha = 0.8f)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(32.dp))
                                    
                                    // Show progress from ViewModel
                                    if (keyGenerationProgress > 0f) {
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
                                                    .fillMaxWidth(keyGenerationProgress)
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
                                            text = "${(keyGenerationProgress * 100).toInt()}% Complete",
                                            fontSize = 14.sp,
                                            color = Color.Gray.copy(alpha = 0.6f)
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(40.dp),
                                            color = Color(0xFF3B82F6) // blue-500
                                        )
                                    }
                                }
                            }
                        }
                    }
                    AppStep.QR_CODE_DISPLAY -> KeyDisplayStep(
                        publicKey = publicKey,
                        qrCodeBitmap = qrCodeBitmap,
                        connectedFusionNode = connectedFusionNode,
                        onNext = { currentStep = AppStep.QR_CODE_SCANNING }
                    )
                    AppStep.QR_CODE_SCANNING -> KeyExchangeStep(
                        onScanQR = { showQRScanner = true },
                        onEnterKey = { showPeerKeyDialog = true },
                        onNext = { currentStep = AppStep.CHAT },
                        scannedQRData = scannedQRData,
                        targetFusionNode = targetFusionNode,
                        peerPublicKey = peerPublicKey,
                        onProcessQRData = { data ->
                            // The ViewModel will handle this automatically
                            // Just update the local state
                            scannedQRData = data
                        }
                    )
                    else -> WelcomeStep(
                        onGenerateKeys = {
                            isGeneratingKeys = true
                            currentStep = AppStep.NETWORK_DISCOVERY
                            viewModel.startDeviceDiscovery()
                        },
                        isGenerating = isGeneratingKeys,
                        progress = keyGenerationProgress,
                        hasAllPermissions = hasAllPermissions,
                        permissionStatus = permissionStatus
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
                // peerPublicKey = key // This line is removed as per the edit hint
                showPeerKeyDialog = false
                // TODO: Parse and use the peer public key
            }
        )
    }
    
    // QR Code Scanner Dialog
    if (showQRScanner) {
        QRCodeScannerDialog(
            onDismiss = { showQRScanner = false },
            onQRCodeScanned = { qrData ->
                scannedQRData = qrData
                showQRScanner = false
                // Process the scanned QR code
                viewModel.handleQRCodeScanResult(qrData)
            }
        )
    }
}

@Composable
fun WelcomeStep(
    onGenerateKeys: () -> Unit,
    isGenerating: Boolean,
    progress: Float,
    hasAllPermissions: Boolean,
    permissionStatus: String
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
                    text = "End-to-End Encrypted Messaging via Fusion Nodes",
                    fontSize = 16.sp,
                    color = Color.Gray.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Start Network Discovery button
                Button(
                    onClick = onGenerateKeys, // This will now start network discovery
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
                                    text = "Discovering Networks...",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "📡",
                                    fontSize = 20.sp
                                )
                                Text(
                                    text = "Start Network Discovery",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
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
                    Column(
                        modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🛡️",
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                                text = "Connect to fusion nodes to establish secure communication",
                            fontSize = 14.sp,
                            color = Color(0xFF93C5FD) // blue-300
                        )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Permission status
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (hasAllPermissions) 
                                    Color(0xFF10B981).copy(alpha = 0.1f) // Green for granted
                                else 
                                    Color(0xFFF59E0B).copy(alpha = 0.1f) // Yellow for pending
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (hasAllPermissions) "✅" else "⚠️",
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = permissionStatus,
                                    fontSize = 12.sp,
                                    color = if (hasAllPermissions) 
                                        Color(0xFF10B981) // Green
                                    else 
                                        Color(0xFFF59E0B) // Yellow
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeyGenerationStep(
    progress: Float,
    error: String?,
    onRetry: () -> Unit,
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
                // Show error if present
                if (error != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFDC2626).copy(alpha = 0.2f) // red with transparency
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "❌ Key Generation Failed",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFCA5A5) // red-300
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = error,
                                fontSize = 14.sp,
                                color = Color(0xFFFCA5A5) // red-300
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFDC2626) // red-600
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Retry", color = Color.White)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
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
                        text = if (error != null) "⚠️" else "🔄",
                        fontSize = 24.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = if (error != null) "Key Generation Failed" else "Generating Keys",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (error != null) "Please try again or check your device" else "Creating your encryption keypair...",
                    fontSize = 16.sp,
                    color = Color.Gray.copy(alpha = 0.8f)
                )
                
                // Only show progress if no error
                if (error == null) {
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
}

@Composable
fun KeyDisplayStep(
    publicKey: java.security.KeyPair?,
    qrCodeBitmap: android.graphics.Bitmap?,
    connectedFusionNode: String?,
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
                    text = "Your QR Code",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Share this QR code with your contact",
                    fontSize = 16.sp,
                    color = Color.Gray.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Valid for 10 minutes",
                    fontSize = 14.sp,
                    color = Color(0xFFF59E0B), // amber-500
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // QR Code display
                Card(
                    modifier = Modifier
                        .size(250.dp)
                        .shadow(12.dp, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    if (qrCodeBitmap != null) {
                        Image(
                            bitmap = qrCodeBitmap.asImageBitmap(),
                            contentDescription = "Secure Communication QR Code",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "📱",
                                fontSize = 48.sp
                            )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Generating QR Code...",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Info about what's in the QR code
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF059669).copy(alpha = 0.1f) // emerald with transparency
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                            text = "🔐 QR Code Contains:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF10B981) // emerald-500
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• Your public key (hidden)",
                            fontSize = 12.sp,
                            color = Color(0xFF10B981) // emerald-500
                        )
                        Text(
                            text = "• Connected fusion node ID",
                            fontSize = 12.sp,
                            color = Color(0xFF10B981) // emerald-500
                        )
                        Text(
                            text = "• Timestamp (10 min expiry)",
                            fontSize = 12.sp,
                            color = Color(0xFF10B981) // emerald-500
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Current Node: ${connectedFusionNode ?: "Unknown"}",
                            fontSize = 12.sp,
                            color = Color(0xFFF59E0B), // amber-500
                            fontWeight = FontWeight.Medium
                        )
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
                                text = "📷",
                                fontSize = 20.sp
                            )
                            Text(
                                text = "Scan Contact's QR Code",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
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
    bluetoothDiscoveryStatus: String,
    wifiDiscoveryStatus: String,
    onStartDiscovery: () -> Unit,
    onConnectBle: (com.example.mine.network.FusionNode) -> Unit,
    onConnectWifi: (com.example.mine.network.FusionWifiNetwork) -> Unit,
    onNext: () -> Unit,
    hasAllPermissions: Boolean
) {
    var selectedTab by remember { mutableStateOf(0) }
    
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
                    text = "Find and connect to fusion nodes in your area",
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
                                    colors = if (hasAllPermissions) listOf(
                                        Color(0xFF7C2D12), // orange-800
                                        Color(0xFFDC2626)  // red-600
                                    ) else listOf(
                                        Color(0xFF059669), // emerald-600
                                        Color(0xFF10B981)  // emerald-500
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
                                text = if (hasAllPermissions) "📡" else "🔐",
                                fontSize = 20.sp
                            )
                            Text(
                                text = if (hasAllPermissions) "Start Discovery" else "Grant Permissions",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                
                // Discovery status display
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1F2937).copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                    Text(
                            text = "Discovery Status",
                            fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Bluetooth status
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📱",
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = bluetoothDiscoveryStatus,
                                fontSize = 12.sp,
                                color = Color.Gray.copy(alpha = 0.8f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // WiFi status
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📶",
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = wifiDiscoveryStatus,
                                fontSize = 12.sp,
                                color = Color.Gray.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Tab selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // WiFi Tab
                    Button(
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == 0) 
                                Color(0xFF10B981).copy(alpha = 0.3f) 
                            else Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "📶",
                                fontSize = 16.sp
                            )
                    Text(
                        text = "WiFi Networks",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                    
                    // Bluetooth Tab
                    Button(
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == 1) 
                                Color(0xFF3B82F6).copy(alpha = 0.3f) 
                            else Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "📱",
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Bluetooth",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Tab content
                when (selectedTab) {
                    0 -> {
                        // WiFi Networks Tab
                        if (discoveredNetworks.isNotEmpty()) {
                            Text(
                                text = "WiFi Fusion Nodes",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
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
                        } else {
                            Text(
                                text = "No WiFi fusion nodes discovered yet",
                                fontSize = 14.sp,
                                color = Color.Gray.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    1 -> {
                        // Bluetooth Devices Tab
                        if (discoveredDevices.isNotEmpty()) {
                            Text(
                                text = "Bluetooth Fusion Nodes",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
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
                        } else {
                            Text(
                                text = "No Bluetooth fusion nodes discovered yet",
                                fontSize = 14.sp,
                                color = Color.Gray.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Next button (only show if something is discovered)
                if (discoveredDevices.isNotEmpty() || discoveredNetworks.isNotEmpty()) {
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
                                text = "Continue",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        }
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
    onNext: () -> Unit,
    scannedQRData: String? = null,
    targetFusionNode: String? = null,
    peerPublicKey: String? = null,
    onProcessQRData: (String) -> Unit = {}
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
                
                // Show scanned QR code information if available
                if (scannedQRData != null && targetFusionNode != null && peerPublicKey != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF059669).copy(alpha = 0.1f) // emerald with transparency
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "✅ QR Code Scanned Successfully",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981) // emerald-500
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "Target Fusion Node: $targetFusionNode",
                                fontSize = 14.sp,
                                color = Color(0xFF10B981) // emerald-500
                            )
                            
                            Text(
                                text = "Peer Public Key: ${peerPublicKey.take(20)}...",
                                fontSize = 14.sp,
                                color = Color(0xFF10B981) // emerald-500
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = { onProcessQRData(scannedQRData) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981) // emerald-600
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Establish Secure Session", color = Color.White)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
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
                                    fontWeight = FontWeight.SemiBold,
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
                                    fontWeight = FontWeight.SemiBold,
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
                
                // Only show next button if QR code is scanned and processed
                if (scannedQRData != null && targetFusionNode != null && peerPublicKey != null) {
                Spacer(modifier = Modifier.height(24.dp))
                
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
                            fontWeight = FontWeight.SemiBold
                        )
                        }
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
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
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Signal strength and additional info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Signal strength indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Signal:",
                        fontSize = 12.sp,
                        color = Color.Gray.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${rssi} dBm",
                        fontSize = 12.sp,
                        color = when {
                            rssi >= -50 -> Color(0xFF10B981) // Green
                            rssi >= -70 -> Color(0xFFF59E0B) // Yellow
                            else -> Color(0xFFEF4444) // Red
                        },
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Signal strength bar
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    repeat(5) { index ->
                        Box(
                            modifier = Modifier
                                .size(width = 4.dp, height = 12.dp)
                                .background(
                                    color = if (index < getSignalBars(rssi)) {
                                        when {
                                            rssi >= -50 -> Color(0xFF10B981) // Green
                                            rssi >= -70 -> Color(0xFFF59E0B) // Yellow
                                            else -> Color(0xFFEF4444) // Red
                                        }
                                    } else {
                                        Color.Gray.copy(alpha = 0.3f)
                                    },
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

// Helper function to convert RSSI to signal bars (1-5)
private fun getSignalBars(rssi: Int): Int {
    return when {
        rssi >= -50 -> 5
        rssi >= -60 -> 4
        rssi >= -70 -> 3
        rssi >= -80 -> 2
        rssi >= -90 -> 1
        else -> 0
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
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
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Network details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Signal strength indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Signal:",
                        fontSize = 12.sp,
                        color = Color.Gray.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${rssi} dBm",
                        fontSize = 12.sp,
                        color = when {
                            rssi >= -50 -> Color(0xFF10B981) // Green
                            rssi >= -70 -> Color(0xFFF59E0B) // Yellow
                            else -> Color(0xFFEF4444) // Red
                        },
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Signal strength bar
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    repeat(5) { index ->
                        Box(
                            modifier = Modifier
                                .size(width = 4.dp, height = 12.dp)
                                .background(
                                    color = if (index < getSignalBars(rssi)) {
                                        when {
                                            rssi >= -50 -> Color(0xFF10B981) // Green
                                            rssi >= -70 -> Color(0xFFF59E0B) // Yellow
                                            else -> Color(0xFFEF4444) // Red
                                        }
                                    } else {
                                        Color.Gray.copy(alpha = 0.3f)
                                    },
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
            
            // Additional network info (placeholder for now)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Security: WPA2",
                    fontSize = 11.sp,
                    color = Color.Gray.copy(alpha = 0.6f)
                )
                
                Text(
                    text = "2.4 GHz",
                    fontSize = 11.sp,
                    color = Color.Gray.copy(alpha = 0.6f)
                )
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

@Composable
fun QRCodeScannerDialog(
    onDismiss: () -> Unit,
    onQRCodeScanned: (String) -> Unit
) {
    var simulatedQRData by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("QR Code Scanner")
        },
        text = {
            Column {
                Text(
                    text = "For testing purposes, enter a simulated QR code data:",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = simulatedQRData,
                    onValueChange = { simulatedQRData = it },
                    label = { Text("QR Code Data (JSON format)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("{\"pk\":\"base64key\",\"node\":\"FusionNode2\",\"ts\":1234567890}")
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Example: {\"pk\":\"dGVzdGtleQ==\",\"node\":\"RaspberryPi_BLE_002\",\"ts\":${System.currentTimeMillis()}}",
                    fontSize = 12.sp,
                    color = Color.Gray.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    if (simulatedQRData.isNotEmpty()) {
                        onQRCodeScanned(simulatedQRData)
                    }
                }
            ) {
                Text("Scan")
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

