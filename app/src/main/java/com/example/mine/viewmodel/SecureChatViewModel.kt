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
import com.example.mine.network.BluetoothDiscoveryManager
import com.example.mine.network.WifiDiscoveryManager
import com.example.mine.network.FusionNode
import com.example.mine.network.FusionWifiNetwork
import com.example.mine.utils.PermissionManager
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
import java.io.ByteArrayOutputStream

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
    private val bluetoothDiscoveryManager = BluetoothDiscoveryManager(context)
    private val wifiDiscoveryManager = WifiDiscoveryManager(context)
    private val permissionManager = PermissionManager(context)
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
    
    // Track connected fusion node
    private val _connectedFusionNode = MutableStateFlow<String?>(null)
    val connectedFusionNode: StateFlow<String?> = _connectedFusionNode.asStateFlow()
    
    // Track target fusion node for routing (from scanned QR codes)
    private val _targetFusionNode = MutableStateFlow<String?>(null)
    val targetFusionNode: StateFlow<String?> = _targetFusionNode.asStateFlow()
    
    // Track peer public key from scanned QR codes
    private val _peerPublicKey = MutableStateFlow<String?>(null)
    val peerPublicKey: StateFlow<String?> = _peerPublicKey.asStateFlow()
    
    // Permission status
    private val _permissionStatus = MutableStateFlow<String>("Checking permissions...")
    val permissionStatus: StateFlow<String> = _permissionStatus.asStateFlow()
    
    // Permission granted status
    private val _hasAllPermissions = MutableStateFlow(false)
    val hasAllPermissions: StateFlow<Boolean> = _hasAllPermissions.asStateFlow()
    
    // Discovery status
    private val _bluetoothDiscoveryStatus = MutableStateFlow<String>("Ready")
    val bluetoothDiscoveryStatus: StateFlow<String> = _bluetoothDiscoveryStatus.asStateFlow()
    
    private val _wifiDiscoveryStatus = MutableStateFlow<String>("Ready")
    val wifiDiscoveryStatus: StateFlow<String> = _wifiDiscoveryStatus.asStateFlow()
    
    // Device ID counter
    private val deviceIdCounter = AtomicInteger(DEVICE_ID)
    
    // Initialize ViewModel
    init {
        // Test key generation on initialization
        testKeyGeneration(context)
        
        // Check initial permission status
        updatePermissionStatus()
        
        // Observe discovery status
        observeDiscoveryStatus()
        
        // Observe discovered devices and networks
        observeDiscoveredDevices()
        
        // Load initial data
        viewModelScope.launch {
            loadInitialData()
        }
    }
    
    // Update permission status
    private fun updatePermissionStatus() {
        _permissionStatus.value = permissionManager.getPermissionStatusMessage()
        _hasAllPermissions.value = permissionManager.hasAllRequiredPermissions()
    }
    
    // Observe discovery status from managers
    private fun observeDiscoveryStatus() {
        viewModelScope.launch {
            bluetoothDiscoveryManager.isDiscovering.collect { isDiscovering ->
                _bluetoothDiscoveryStatus.value = bluetoothDiscoveryManager.getDiscoveryStatus()
            }
        }
        
        viewModelScope.launch {
            wifiDiscoveryManager.isScanning.collect { isScanning ->
                _wifiDiscoveryStatus.value = wifiDiscoveryManager.getScanStatus()
            }
        }
    }
    
    // Observe discovered devices and networks
    private fun observeDiscoveredDevices() {
        viewModelScope.launch {
            bluetoothDiscoveryManager.discoveredDevices.collect { devices ->
                _discoveredDevices.value = devices
                Log.d(TAG, "Bluetooth devices updated: ${devices.size} devices")
            }
        }
        
        viewModelScope.launch {
            wifiDiscoveryManager.discoveredNetworks.collect { networks ->
                _discoveredNetworks.value = networks
                Log.d(TAG, "WiFi networks updated: ${networks.size} networks")
            }
        }
    }
    
    // Check and request permissions
    fun checkAndRequestPermissions(activity: android.app.Activity) {
        if (!permissionManager.hasAllRequiredPermissions()) {
            permissionManager.requestRequiredPermissions(activity)
        }
        updatePermissionStatus()
    }
    
    // Check Bluetooth permissions specifically
    fun checkBluetoothPermissions(activity: android.app.Activity) {
        if (!permissionManager.hasBluetoothPermissions()) {
            permissionManager.requestBluetoothPermissions(activity)
        }
        updatePermissionStatus()
    }
    
    // Location permissions are no longer required
    
    // Check camera permissions specifically
    fun checkCameraPermissions(activity: android.app.Activity) {
        if (!permissionManager.hasCameraPermission()) {
            permissionManager.requestCameraPermission(activity)
        }
        updatePermissionStatus()
    }
    
    // Location permission no longer required
    
    // Get permission status for UI
    fun getPermissionStatus(): String {
        return _permissionStatus.value
    }
    
    // Check if all permissions are granted
    fun hasAllPermissions(): Boolean {
        return permissionManager.hasAllRequiredPermissions()
    }
    
    // Check if Bluetooth discovery is ready
    fun isBluetoothReady(): Boolean {
        return bluetoothDiscoveryManager.isBluetoothSupported() && 
               bluetoothDiscoveryManager.isBluetoothEnabled() && 
               bluetoothDiscoveryManager.isBleSupported() &&
               bluetoothDiscoveryManager.hasRequiredPermissions()
    }
    
    // Check if WiFi discovery is ready
    fun isWifiReady(): Boolean {
        return wifiDiscoveryManager.isWifiSupported() && 
               wifiDiscoveryManager.isWifiEnabled() && 
               wifiDiscoveryManager.hasRequiredPermissions()
    }
    
    // Load initial data
    private suspend fun loadInitialData() {
        try {
            // Load existing sessions and messages
            val sessions = database.sessionDao().getActiveSessions()
            val allMessages = database.messageDao().getAllMessages()
            val allContacts = database.contactDao().getAllContacts()
            
            _messages.value = allMessages
            _contacts.value = allContacts
            
            Log.d(TAG, "Initial data loaded: ${sessions.size} sessions, ${allMessages.size} messages, ${allContacts.size} contacts")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load initial data", e)
        }
    }
    
    // Handle QR code scan result
    fun handleQRCodeScanResult(qrData: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Processing QR code scan result: $qrData")
                
                // Parse QR code data
                val parsedData = parseQRCodeData(qrData)
                if (parsedData != null) {
                    val (publicKey, nodeId) = parsedData
                    
                    // Store the parsed data
                    _peerPublicKey.value = publicKey
                    _targetFusionNode.value = nodeId
                    
                    Log.d(TAG, "QR code parsed successfully:")
                    Log.d(TAG, "  - Public Key: ${publicKey.take(20)}...")
                    Log.d(TAG, "  - Target Node: $nodeId")
                    
                    // Attempt to establish session
                    val publicKeyBytes = android.util.Base64.decode(publicKey, android.util.Base64.NO_WRAP)
                    establishSession(publicKeyBytes)
                    
                } else {
                    _uiState.value = UiState.Error("Invalid or expired QR code")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle QR code scan result", e)
                _uiState.value = UiState.Error("Failed to process QR code: ${e.message}")
            }
        }
    }
    
    // Parse QR code data to extract public key and fusion node ID
    fun parseQRCodeData(qrData: String): Pair<String, String>? {
        return try {
            // Remove JSON braces and parse the data
            val cleanData = qrData.trim().removePrefix("{").removeSuffix("}")
            val keyValuePairs = cleanData.split(",").associate { pair ->
                val (key, value) = pair.split(":", limit = 2)
                key.trim().removeSurrounding("\"") to value.trim().removeSurrounding("\"")
            }
            
            val publicKey = keyValuePairs["pk"] ?: return null
            val nodeId = keyValuePairs["node"] ?: return null
            val timestamp = keyValuePairs["ts"]?.toLongOrNull() ?: 0L
            
            // Check if QR code is expired (10 minutes)
            val currentTime = System.currentTimeMillis()
            if (currentTime - timestamp > 10 * 60 * 1000) {
                Log.w(TAG, "QR code expired: ${currentTime - timestamp}ms old")
                return null
            }
            
            Log.d(TAG, "QR code parsed successfully: node=$nodeId, timestamp=$timestamp")
            Pair(publicKey, nodeId)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse QR code data: $qrData", e)
            null
        }
    }
    
    // Create QR code data containing public key and fusion node ID
    private fun createQRCodeData(publicKey: java.security.PublicKey, fusionNodeId: String?): String {
        val publicKeyBase64 = android.util.Base64.encodeToString(publicKey.encoded, android.util.Base64.NO_WRAP)
        val nodeId = fusionNodeId ?: "Unknown"
        
        // Create JSON-like structure for QR code data
        return "{\"pk\":\"$publicKeyBase64\",\"node\":\"$nodeId\",\"ts\":${System.currentTimeMillis()}}"
    }
    
    // Handle connection establishment
    fun onConnectionEstablished() {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.ConnectionEstablished
                Log.d(TAG, "Connection established, proceeding to key generation")
                
                // Automatically start key generation after connection
                delay(1000) // Small delay for UX
                generateKeyPair()
            
        } catch (e: Exception) {
                Log.e(TAG, "Error after connection establishment", e)
                _uiState.value = UiState.Error("Failed to proceed after connection: ${e.message}")
            }
        }
    }
    
    // Reset key generation progress
    fun resetKeyGeneration() {
        _keyGenerationProgress.value = 0f
        _uiState.value = UiState.Initial
    }
    
    // Reset QR code scanning state
    fun resetQRCodeScanning() {
        _targetFusionNode.value = null
        _peerPublicKey.value = null
        Log.d(TAG, "QR code scanning state reset")
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
                    Log.d(TAG, "Generating QR code for public key and fusion node...")
                    
                    // Create QR code data containing both public key and fusion node ID
                    val qrData = createQRCodeData(keyPair.public, _connectedFusionNode.value)
                    qrCodeUtils.generateQRCode(qrData)
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
    
    // Start device discovery (Bluetooth + WiFi)
    fun startDeviceDiscovery() {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading("Starting device discovery...")
                
                // Check permissions first
                if (!permissionManager.hasAllRequiredPermissions()) {
                    _uiState.value = UiState.Error("Required permissions not granted")
                    return@launch
                }
                
                // Start Bluetooth discovery
                if (bluetoothDiscoveryManager.isBluetoothSupported() && 
                    bluetoothDiscoveryManager.isBluetoothEnabled() && 
                    bluetoothDiscoveryManager.isBleSupported() &&
                    bluetoothDiscoveryManager.hasRequiredPermissions()) {
                    bluetoothDiscoveryManager.startDiscovery()
                    Log.d(TAG, "Bluetooth discovery started")
                } else {
                    Log.w(TAG, "Bluetooth not ready: supported=${bluetoothDiscoveryManager.isBluetoothSupported()}, enabled=${bluetoothDiscoveryManager.isBluetoothEnabled()}, bleSupported=${bluetoothDiscoveryManager.isBleSupported()}, permissions=${bluetoothDiscoveryManager.hasRequiredPermissions()}")
                }
                
                // Start WiFi discovery
                if (wifiDiscoveryManager.isWifiSupported() && 
                    wifiDiscoveryManager.isWifiEnabled() && 
                    wifiDiscoveryManager.hasRequiredPermissions()) {
                    wifiDiscoveryManager.startScan()
                    Log.d(TAG, "WiFi discovery started")
                } else {
                    Log.w(TAG, "WiFi not ready: supported=${wifiDiscoveryManager.isWifiSupported()}, enabled=${wifiDiscoveryManager.isWifiEnabled()}, permissions=${wifiDiscoveryManager.hasRequiredPermissions()}")
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
                
                // Check if Bluetooth is ready
                if (!isBluetoothReady()) {
                    _uiState.value = UiState.Error("Bluetooth not ready. Please check permissions and enable Bluetooth.")
                    return@launch
                }
                
                // Attempt connection
                val success = bluetoothDiscoveryManager.connectToDevice(fusionNode)
                if (success) {
                    _connectedFusionNode.value = fusionNode.name
                            _uiState.value = UiState.Connected
                    onConnectionEstablished()
                            Log.d(TAG, "Connected to BLE device: ${fusionNode.name}")
                } else {
                    _uiState.value = UiState.Error("Failed to connect to ${fusionNode.name}")
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
                
                // Check if WiFi is ready
                if (!isWifiReady()) {
                    _uiState.value = UiState.Error("WiFi not ready. Please check permissions and enable WiFi.")
                    return@launch
                }
                
                // Attempt connection
                val success = wifiDiscoveryManager.connectToNetwork(fusionNetwork)
                if (success) {
                    _connectedFusionNode.value = fusionNetwork.ssid
                            _uiState.value = UiState.Connected
                    onConnectionEstablished()
                            Log.d(TAG, "Connected to WiFi network: ${fusionNetwork.ssid}")
                } else {
                    _uiState.value = UiState.Error("Failed to connect to ${fusionNetwork.ssid}")
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
    
    // Send encrypted message with fusion node routing
    fun sendMessage(message: String, destinationId: Int) {
        viewModelScope.launch {
            try {
                val session = _currentSession.value
                if (session == null) {
                    _uiState.value = UiState.Error("No active session")
                    return@launch
                }
                
                // Get current and target fusion node IDs for routing
                val currentFusionNode = _connectedFusionNode.value
                val targetFusionNode = _targetFusionNode.value
                
                if (currentFusionNode == null) {
                    _uiState.value = UiState.Error("Not connected to any fusion node")
                    return@launch
                }
                
                if (targetFusionNode == null) {
                    _uiState.value = UiState.Error("No target fusion node specified")
                    return@launch
                }
                
                Log.d(TAG, "Sending message with routing:")
                Log.d(TAG, "  - From fusion node: $currentFusionNode")
                Log.d(TAG, "  - To fusion node: $targetFusionNode")
                Log.d(TAG, "  - Message: $message")
                
                // Encrypt message and create frame with routing information
                val frame = sessionManager.encryptMessage(session, message, destinationId)
                if (frame == null) {
                    _uiState.value = UiState.Error("Failed to encrypt message")
                    return@launch
                }
                
                // Create routing payload that includes fusion node information
                val routingPayload = createRoutingPayload(frame, currentFusionNode, targetFusionNode)
                
                // Send via current connection (BLE or WiFi)
                val sent = if (isBluetoothConnected()) {
                    // For now, simulate BLE message sending
                    // In real implementation, you'd use bluetoothDiscoveryManager.sendMessage()
                    Log.d(TAG, "Simulating BLE message send to $targetFusionNode")
                    true
                } else if (isWifiConnected()) {
                    // For now, simulate WiFi message sending
                    // In real implementation, you'd use wifiDiscoveryManager.sendMessage()
                    Log.d(TAG, "Simulating WiFi message send to $targetFusionNode")
                    true
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
                    _uiState.value = UiState.Error("Failed to send message - no active connection")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                _uiState.value = UiState.Error("Failed to send message: ${e.message}")
            }
        }
    }
    
    // Check if Bluetooth is connected
    private fun isBluetoothConnected(): Boolean {
        return _connectedFusionNode.value != null && 
               bluetoothDiscoveryManager.isBluetoothSupported() && 
               bluetoothDiscoveryManager.isBluetoothEnabled()
    }
    
    // Check if WiFi is connected
    private fun isWifiConnected(): Boolean {
        return _connectedFusionNode.value != null && 
               wifiDiscoveryManager.isWifiSupported() && 
               wifiDiscoveryManager.isWifiEnabled()
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
    
    // Create a payload that includes the encrypted message and routing information
    private fun createRoutingPayload(frame: Frame, currentFusionNode: String, targetFusionNode: String): ByteArray {
        val messageBytes = frame.toByteArray()
        val currentFusionNodeBytes = currentFusionNode.toByteArray()
        val targetFusionNodeBytes = targetFusionNode.toByteArray()
        
        val payload = ByteArrayOutputStream()
        payload.write(currentFusionNodeBytes)
        payload.write(targetFusionNodeBytes)
        payload.write(messageBytes)
        
        return payload.toByteArray()
    }
    
    // Clean up resources
    override fun onCleared() {
        super.onCleared()
        
        try {
            // Clean up Bluetooth discovery manager
            bluetoothDiscoveryManager.cleanup()
            
            // Clean up WiFi discovery manager
            wifiDiscoveryManager.cleanup()
            
            Log.d(TAG, "ViewModel resources cleaned up")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}

// UI States
sealed class UiState {
    object Initial : UiState()
    data class Loading(val message: String = "Loading...") : UiState()
    object DiscoveryActive : UiState()
    object ConnectionEstablished : UiState()
    object KeyGenerated : UiState()
    object Connected : UiState()
    object SessionEstablished : UiState()
    object ChatActive : UiState()
    data class Error(val message: String) : UiState()
}
