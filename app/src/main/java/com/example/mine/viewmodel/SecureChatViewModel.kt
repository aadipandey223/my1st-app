package com.example.mine.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mine.crypto.CryptoManager
import com.example.mine.crypto.SessionManager
import com.example.mine.crypto.Session
import com.example.mine.crypto.Frame
import com.example.mine.data.MessageDatabase
import com.example.mine.data.Message
import com.example.mine.data.Contact
import com.example.mine.data.SessionEntity
import com.example.mine.data.DeviceKey
import com.example.mine.network.BleManager
import com.example.mine.network.WifiManager
import com.example.mine.network.FusionNode
import com.example.mine.network.FusionWifiNetwork
import com.example.mine.network.ConnectionStatus
import com.example.mine.network.WifiConnectionStatus
import com.example.mine.utils.QRCodeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.security.KeyPair
import java.security.PublicKey
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

class SecureChatViewModel(private val context: Context) : ViewModel() {
    
    companion object {
        private const val TAG = "SecureChatViewModel"
        private const val DEVICE_ID = 1
        
        // Test function for debugging key generation
        fun testKeyGeneration(context: Context) {
            val cryptoManager = CryptoManager(context)
            try {
                Log.d(TAG, "Testing key generation...")
                val keyPair = cryptoManager.generateX25519KeyPair()
                Log.d(TAG, "Test key generation successful: ${keyPair.javaClass.simpleName}")
                Log.d(TAG, "Public key length: ${keyPair.public.encoded.size} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Test key generation failed", e)
            }
        }
    }
    
    // Core components
    private val cryptoManager = CryptoManager(context)
    private val sessionManager = SessionManager(cryptoManager)
    private val bleManager = BleManager(context)
    private val wifiManager = WifiManager(context)
    private val qrCodeUtils = QRCodeUtils()
    private val database = MessageDatabase.getDatabase(context)
    
    // State management
    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    private val _publicKey = MutableStateFlow<KeyPair?>(null)
    val publicKey: StateFlow<KeyPair?> = _publicKey.asStateFlow()
    
    private val _qrCodeBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)
    val qrCodeBitmap: StateFlow<android.graphics.Bitmap?> = _qrCodeBitmap.asStateFlow()
    
    private val _discoveredDevices = MutableStateFlow<List<FusionNode>>(emptyList())
    val discoveredDevices: StateFlow<List<FusionNode>> = _discoveredDevices.asStateFlow()
    
    private val _discoveredNetworks = MutableStateFlow<List<FusionWifiNetwork>>(emptyList())
    val discoveredNetworks: StateFlow<List<FusionWifiNetwork>> = _discoveredNetworks.asStateFlow()
    
    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()
    
    // Progress tracking for key generation
    private val _keyGenerationProgress = MutableStateFlow(0f)
    val keyGenerationProgress: StateFlow<Float> = _keyGenerationProgress.asStateFlow()
    
    // Device ID counter
    private val deviceIdCounter = AtomicInteger(DEVICE_ID)
    
    init {
        viewModelScope.launch {
            // Test key generation on startup for debugging
            testKeyGeneration(context)
            
            loadInitialData()
        }
    }
    
    // Load initial data from database
    private suspend fun loadInitialData() {
        try {
            // Load device key if exists
            val deviceKey = database.deviceKeyDao().getDeviceKey()
            if (deviceKey != null) {
                // TODO: Load existing key pair from Android Keystore
                Log.d(TAG, "Device key found in database")
            }
            
            // Load contacts
            val contactsList = database.contactDao().getAllContacts()
            _contacts.value = contactsList
            
            // Load active sessions
            val activeSessions = database.sessionDao().getActiveSessions()
            Log.d(TAG, "Loaded ${activeSessions.size} active sessions")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading initial data", e)
            _uiState.value = UiState.Error("Failed to load data: ${e.message}")
        }
    }
    
    // Reset key generation progress
    fun resetKeyGeneration() {
        _keyGenerationProgress.value = 0f
        _uiState.value = UiState.Initial
    }
    
    // Generate new X25519 key pair with progress tracking
    fun generateKeyPair() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting key generation process...")
                _uiState.value = UiState.Loading("Generating key pair...")
                _keyGenerationProgress.value = 0f
                
                // Simulate progress steps for better UX
                Log.d(TAG, "Progress: 10% - Initializing...")
                _keyGenerationProgress.value = 0.1f
                delay(100) // Small delay to show progress
                
                Log.d(TAG, "Progress: 30% - Preparing key generation...")
                _keyGenerationProgress.value = 0.3f
                delay(100)
                
                // Generate X25519 key pair on background thread with timeout
                Log.d(TAG, "Progress: 50% - Generating key pair on background thread...")
                val keyPair = withTimeout(10000) { // 10 second timeout
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        _keyGenerationProgress.value = 0.5f
                        delay(200) // Simulate key generation work
                        
                        Log.d(TAG, "Calling cryptoManager.generateX25519KeyPair()...")
                        val result = cryptoManager.generateX25519KeyPair()
                        Log.d(TAG, "Key pair generated successfully: ${result.javaClass.simpleName}")
                        _keyGenerationProgress.value = 0.7f
                        delay(100)
                        
                        result
                    }
                }
                
                Log.d(TAG, "Progress: 80% - Storing key pair...")
                _publicKey.value = keyPair
                _keyGenerationProgress.value = 0.8f
                delay(100)
                
                // Store in database
                val deviceKey = DeviceKey(
                    publicKey = keyPair.public.encoded,
                    privateKeyAlias = "device_private_key",
                    createdAt = Date(),
                    lastUsed = Date()
                )
                database.deviceKeyDao().insertDeviceKey(deviceKey)
                Log.d(TAG, "Key pair stored in database")
                
                Log.d(TAG, "Progress: 90% - Generating QR code...")
                _keyGenerationProgress.value = 0.9f
                delay(100)
                
                // Generate QR code for public key
                val qrBitmap = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    Log.d(TAG, "Generating QR code for public key...")
                    qrCodeUtils.generateQRCode(keyPair.public)
                }
                _qrCodeBitmap.value = qrBitmap
                Log.d(TAG, "QR code generated successfully")
                
                Log.d(TAG, "Progress: 100% - Key generation complete!")
                _keyGenerationProgress.value = 1.0f
                delay(200) // Show 100% briefly
                
                _uiState.value = UiState.KeyGenerated
                Log.d(TAG, "Key pair generated successfully - UI state updated to KeyGenerated")
                
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e(TAG, "Key generation timed out after 10 seconds", e)
                _uiState.value = UiState.Error("Key generation timed out. Please try again.")
                _keyGenerationProgress.value = 0f
                _publicKey.value = null
                _qrCodeBitmap.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate key pair", e)
                _uiState.value = UiState.Error("Failed to generate key pair: ${e.message}")
                _keyGenerationProgress.value = 0f
                
                // Reset state on error
                _publicKey.value = null
                _qrCodeBitmap.value = null
            }
        }
    }
    
    // Start device discovery
    fun startDeviceDiscovery() {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading("Discovering fusion nodes...")
                
                // Start BLE scan
                bleManager.startScan()
                
                // Start WiFi scan
                wifiManager.startScan()
                
                // Observe discovered devices
                bleManager.discoveredDevices.collect { devices ->
                    _discoveredDevices.value = devices
                }
                
                // Observe discovered networks
                wifiManager.discoveredNetworks.collect { networks ->
                    _discoveredNetworks.value = networks
                }
                
                _uiState.value = UiState.DiscoveryActive
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start device discovery", e)
                _uiState.value = UiState.Error("Discovery failed: ${e.message}")
            }
        }
    }
    
    // Connect to a fusion node via BLE
    fun connectToBleDevice(fusionNode: FusionNode) {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading("Connecting to ${fusionNode.name}...")
                
                bleManager.connectToDevice(fusionNode)
                
                // Observe connection status
                bleManager.connectionStatus.collect { status ->
                    when (status) {
                        is ConnectionStatus.Connected -> {
                            _uiState.value = UiState.Connected
                            Log.d(TAG, "Connected to BLE device: ${fusionNode.name}")
                        }
                        is ConnectionStatus.Error -> {
                            _uiState.value = UiState.Error("Connection failed: ${status.message}")
                        }
                        else -> {
                            // Handle other states
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to BLE device", e)
                _uiState.value = UiState.Error("Connection failed: ${e.message}")
            }
        }
    }
    
    // Connect to a fusion network via WiFi
    fun connectToWifiNetwork(fusionNetwork: FusionWifiNetwork) {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading("Connecting to ${fusionNetwork.ssid}...")
                
                wifiManager.connectToNetwork(fusionNetwork)
                
                // Observe connection status
                wifiManager.connectionStatus.collect { status ->
                    when (status) {
                        is WifiConnectionStatus.Connected -> {
                            _uiState.value = UiState.Connected
                            Log.d(TAG, "Connected to WiFi network: ${fusionNetwork.ssid}")
                        }
                        is WifiConnectionStatus.Error -> {
                            _uiState.value = UiState.Error("Connection failed: ${status.message}")
                        }
                        else -> {
                            // Handle other states
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to WiFi network", e)
                _uiState.value = UiState.Error("Connection failed: ${e.message}")
            }
        }
    }
    
    // Establish session with a peer
    fun establishSession(peerPublicKeyBytes: ByteArray) {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading("Establishing secure session...")
                
                // Convert bytes to PublicKey (simplified - in real implementation you'd use proper key factory)
                val peerPublicKey = createPublicKeyFromBytes(peerPublicKeyBytes)
                
                // Create or get session
                val session = sessionManager.getOrCreateSession(peerPublicKey)
                
                // Establish session keys
                val currentKeyPair = _publicKey.value
                if (currentKeyPair != null) {
                    val success = sessionManager.establishSessionKeys(session, currentKeyPair, peerPublicKey)
                    
                    if (success) {
                        _currentSession.value = session
                        _uiState.value = UiState.SessionEstablished
                        Log.d(TAG, "Session established successfully")
                        
                        // Store session in database
                        val sessionEntity = SessionEntity(
                            id = session.id,
                            peerPublicKey = peerPublicKeyBytes,
                            createdAt = Date(session.createdAt),
                            lastActivity = Date()
                        )
                        database.sessionDao().insertSession(sessionEntity)
                        
                    } else {
                        _uiState.value = UiState.Error("Failed to establish session keys")
                    }
                } else {
                    _uiState.value = UiState.Error("No device key pair available")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to establish session", e)
                _uiState.value = UiState.Error("Session establishment failed: ${e.message}")
            }
        }
    }
    
    // Send encrypted message
    fun sendMessage(message: String, destinationId: Int) {
        viewModelScope.launch {
            try {
                val session = _currentSession.value
                if (session == null) {
                    _uiState.value = UiState.Error("No active session")
                    return@launch
                }
                
                // Encrypt message and create frame
                val frame = sessionManager.encryptMessage(session, message, destinationId)
                if (frame == null) {
                    _uiState.value = UiState.Error("Failed to encrypt message")
                    return@launch
                }
                
                // Send via current connection
                val sent = if (bleManager.isConnected()) {
                    bleManager.sendMessage(frame.toByteArray())
                } else if (wifiManager.isConnected()) {
                    wifiManager.sendMessage(frame.toByteArray())
                } else {
                    false
                }
                
                if (sent) {
                    // Store message in database
                    val messageEntity = Message(
                        sessionId = session.id,
                        senderId = DEVICE_ID,
                        receiverId = destinationId,
                        content = message,
                        encryptedContent = frame.ciphertext,
                        timestamp = Date(),
                        isEncrypted = true,
                        isCompressed = (frame.flags.toInt() and 0x01) != 0
                    )
                    
                    val messageId = database.messageDao().insertMessage(messageEntity)
                    Log.d(TAG, "Message sent and stored with ID: $messageId")
                    
                    // Update UI
                    loadMessagesForSession(session.id)
                    
                } else {
                    _uiState.value = UiState.Error("Failed to send message")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                _uiState.value = UiState.Error("Failed to send message: ${e.message}")
            }
        }
    }
    
    // Load messages for a session
    private suspend fun loadMessagesForSession(sessionId: Int) {
        try {
            val messagesList = database.messageDao().getMessagesBySession(sessionId)
            _messages.value = messagesList
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load messages", e)
        }
    }
    
    // Create PublicKey from bytes (simplified implementation)
    private fun createPublicKeyFromBytes(bytes: ByteArray): PublicKey {
        // This is a simplified implementation
        // In real implementation, you'd use KeyFactory to reconstruct the proper PublicKey object
        return object : PublicKey {
            override fun getAlgorithm(): String = "X25519"
            override fun getFormat(): String = "X.509"
            override fun getEncoded(): ByteArray = bytes
        }
    }
    
    // Clean up resources
    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
        wifiManager.disconnect()
    }
}

// UI States
sealed class UiState {
    object Initial : UiState()
    data class Loading(val message: String = "Loading...") : UiState()
    object KeyGenerated : UiState()
    object DiscoveryActive : UiState()
    object Connected : UiState()
    object SessionEstablished : UiState()
    object ChatActive : UiState()
    data class Error(val message: String) : UiState()
}
