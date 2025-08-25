package com.example.mine.viewmodel

import android.content.Context
import android.util.Log
import android.net.wifi.WifiManager as AndroidWifiManager
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
import com.example.mine.network.BleManager
import com.example.mine.network.WifiManager
import com.example.mine.network.TcpManager
import com.example.mine.network.TcpConnectionStatus
import com.example.mine.network.TcpMessage
import com.example.mine.network.TcpMessageType
import com.example.mine.network.FusionNode
import com.example.mine.network.FusionWifiNetwork
import com.example.mine.network.WifiConnectionHistory
import com.example.mine.network.WifiUsageStatistics
import com.example.mine.network.CommunicationVerifier
import com.example.mine.network.CommunicationProof
import com.example.mine.utils.PermissionManager

import com.example.mine.crypto.QRCodeData
import com.example.mine.crypto.PeerDeviceInfo
import com.example.mine.crypto.RoutingInfo
import com.example.mine.callback.ConnectionCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import java.io.ByteArrayOutputStream

// UI State enum for managing different states of the app
sealed class UiState {
    object Initial : UiState()
    data class Loading(val message: String = "Loading...") : UiState()
    data class Error(val message: String) : UiState()
    object KeyGenerated : UiState()
    object Connected : UiState()
    object Disconnected : UiState()
}

class SecureChatViewModel(private val context: Context) : ViewModel() {
    
    private var connectionCallback: ConnectionCallback? = null
    
    fun setConnectionCallback(callback: ConnectionCallback) {
        connectionCallback = callback
    }
    
    companion object {
        private const val TAG = "SecureChatViewModel"
        private const val DEVICE_ID = 1
    }
    
    // Core components
    private lateinit var cryptoManager: CryptoManager
    private lateinit var sessionManager: SessionManager
    lateinit var bluetoothDiscoveryManager: BluetoothDiscoveryManager // Changed to public
    private lateinit var wifiDiscoveryManager: WifiDiscoveryManager
    lateinit var bleManager: BleManager // Changed to public
    lateinit var wifiManager: WifiManager // Changed to public
    private lateinit var tcpManager: TcpManager // TCP manager for ESP32 communication
    private lateinit var communicationVerifier: CommunicationVerifier
    private lateinit var permissionManager: PermissionManager

    
    // Note: Public properties (val) automatically generate getter methods
    // No need for explicit getter functions
    private val database = MessageDatabase.getDatabase(context)
    
    // State management
    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    private val _publicKey = MutableStateFlow<KeyPair?>(null)
    val publicKey: StateFlow<KeyPair?> = _publicKey.asStateFlow()
    

    
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
    
    // Track connected node
    private val _connectedFusionNode = MutableStateFlow<String?>(null)
    val connectedFusionNode: StateFlow<String?> = _connectedFusionNode.asStateFlow()
    
    // Track if we're listening for fusion node ID
    private val _isListeningForNodeId = MutableStateFlow(false)
    val isListeningForNodeId: StateFlow<Boolean> = _isListeningForNodeId.asStateFlow()
    
    // Track target node for routing (from scanned QR codes)
    private val _targetFusionNode = MutableStateFlow<String?>(null)
    val targetFusionNode: StateFlow<String?> = _targetFusionNode.asStateFlow()
    
    private val _peerPublicKey = MutableStateFlow<String?>(null)
    val peerPublicKey: StateFlow<String?> = _peerPublicKey.asStateFlow()
    
    // Peer device information for routing
    private val _peerDeviceInfo = MutableStateFlow<PeerDeviceInfo?>(null)
    val peerDeviceInfo: StateFlow<PeerDeviceInfo?> = _peerDeviceInfo.asStateFlow()
    
    // Track if we're in discovery mode (user has clicked "Start Discovery")
    private val _isInDiscoveryMode = MutableStateFlow(false)
    val isInDiscoveryMode: StateFlow<Boolean> = _isInDiscoveryMode.asStateFlow()
    
    // Communication verification state
    private val _communicationProofs = MutableStateFlow<List<CommunicationProof>>(emptyList())
    val communicationProofs: StateFlow<List<CommunicationProof>> = _communicationProofs.asStateFlow()
    
    private val _isVerifyingCommunication = MutableStateFlow(false)
    val isVerifyingCommunication: StateFlow<Boolean> = _isVerifyingCommunication.asStateFlow()
    
    private val _verificationProgress = MutableStateFlow(0f)
    val verificationProgress: StateFlow<Float> = _verificationProgress.asStateFlow()
    
    // Configuration for fusion node detection
    private val _fusionNodeDetectionMode = MutableStateFlow("strict") // "strict" or "flexible"
    val fusionNodeDetectionMode: StateFlow<String> = _fusionNodeDetectionMode.asStateFlow()
    
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
    
    // WiFi history state
    private val _wifiHistory = MutableStateFlow<List<WifiConnectionHistory>>(emptyList())
    val wifiHistory: StateFlow<List<WifiConnectionHistory>> = _wifiHistory.asStateFlow()
    
    private val _wifiUsageStatistics = MutableStateFlow<WifiUsageStatistics?>(null)
    val wifiUsageStatistics: StateFlow<WifiUsageStatistics?> = _wifiUsageStatistics.asStateFlow()
    
    // Connection status properties
    private val _isBluetoothConnected = MutableStateFlow(false)
    val isBluetoothConnected: StateFlow<Boolean> = _isBluetoothConnected.asStateFlow()
    
    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()
    
    private val _bluetoothStatus = MutableStateFlow<String>("Disconnected")
    val bluetoothStatus: StateFlow<String> = _bluetoothStatus.asStateFlow()
    
    private val _wifiStatus = MutableStateFlow<String>("Disconnected")
    val wifiStatus: StateFlow<String> = _wifiStatus.asStateFlow()
    
    // TCP connection status for ESP32 communication
    private val _tcpConnectionStatus = MutableStateFlow<TcpConnectionStatus>(TcpConnectionStatus.Disconnected)
    val tcpConnectionStatus: StateFlow<TcpConnectionStatus> = _tcpConnectionStatus.asStateFlow()
    
    // Device ID counter
    private val deviceIdCounter = AtomicInteger(DEVICE_ID)
    
    // Message callback implementation
    private val messageCallback = object : BleManager.MessageCallback, WifiManager.MessageCallback {
        override fun onMessageReceived(message: ByteArray) {
            handleReceivedMessage(message)
        }
    }
    
    // Device information
    private val deviceId = "PHONE_${android.os.Build.MODEL.replace(" ", "_")}_${DEVICE_ID}"
    private val deviceName = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
    
    init {
        Log.d(TAG, "SecureChatViewModel initialized")
        
        // Initialize managers
        permissionManager = PermissionManager(context)
        bluetoothDiscoveryManager = BluetoothDiscoveryManager(context)
        wifiDiscoveryManager = WifiDiscoveryManager(context)
        bleManager = BleManager(context)
        wifiManager = WifiManager(context)
        tcpManager = TcpManager() // Initialize TCP manager
        cryptoManager = CryptoManager(context)
        sessionManager = SessionManager(cryptoManager)

        communicationVerifier = CommunicationVerifier(context)
        
        Log.d(TAG, "Managers initialized successfully")
        
        // Start observing connection status
        observeConnectionStatus()
        
        Log.d(TAG, "Connection status observation started")
        
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
    
    // Start listening for fusion node ID
    startListeningForFusionNodeId()
    }
    
    // Observe connection status
    private fun observeConnectionStatus() {
        viewModelScope.launch {
            // Observe TCP connection status from ESP32
            tcpManager.connectionStatus.collect { status ->
                when (status) {
                    is TcpConnectionStatus.Connected -> {
                        Log.d(TAG, "TCP connected to ESP32")
                        _wifiStatus.value = "Connected to ESP32"
                    }
                    is TcpConnectionStatus.ConnectedWithNodeId -> {
                        Log.d(TAG, "TCP connected to ESP32 with Node ID: ${status.nodeId}")
                        _connectedFusionNode.value = status.nodeId
                        _wifiStatus.value = "Connected to ESP32 (Node: ${status.nodeId})"
                    }
                    is TcpConnectionStatus.Failed -> {
                        Log.e(TAG, "TCP connection failed: ${status.error}")
                        _wifiStatus.value = "Connection failed: ${status.error}"
                    }
                    is TcpConnectionStatus.Disconnected -> {
                        Log.d(TAG, "TCP disconnected from ESP32")
                        _wifiStatus.value = "Disconnected"
                        _connectedFusionNode.value = null
                    }
                    is TcpConnectionStatus.Connecting -> {
                        Log.d(TAG, "TCP connecting to ESP32...")
                        _wifiStatus.value = "Connecting..."
                    }
                }
            }
        }
        
        viewModelScope.launch {
            // Observe received TCP messages
            tcpManager.receivedMessages.collect { messages ->
                messages.forEach { message ->
                    when (message.type) {
                        TcpMessageType.NODE_ID -> {
                            Log.d(TAG, "Received Node ID via TCP: ${message.content}")
                            _connectedFusionNode.value = message.content
                        }
                        else -> {
                            Log.d(TAG, "Received TCP message: ${message.content}")
                        }
                    }
                }
            }
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
    
    // Start listening for fusion node ID from ESP32
    private fun startListeningForFusionNodeId() {
        viewModelScope.launch {
            _isListeningForNodeId.value = true
            Log.d(TAG, "Started listening for fusion node ID")
            
            try {
                // Listen for messages from both Bluetooth and WiFi managers
                launch {
                    bleManager.receivedMessages.collect { messages ->
                        messages.forEach { message ->
                            handleFusionNodeIdMessage(message)
                        }
                    }
                }
                
                // Listen for TCP messages from ESP32
                launch {
                    tcpManager.connectedNodeId.collect { nodeId ->
                        if (nodeId != null) {
                            Log.d(TAG, "Received Node ID via TCP: $nodeId")
                            _connectedFusionNode.value = nodeId
                        }
                    }
                }
                
                launch {
                    wifiManager.connectionStatus.collect { status ->
                        // Handle WiFi connection status changes
                        Log.d(TAG, "WiFi connection status: $status")
                    }
                }
                
                // Also listen for discovered devices that might contain node ID
                launch {
                    bluetoothDiscoveryManager.discoveredDevices.collect { devices ->
                        devices.forEach { device ->
                            if (device.name.contains("ESP32") || device.name.contains("FUSION")) {
                                Log.d(TAG, "Found potential fusion node: ${device.name}")
                                // Try to extract node ID from device name or connect to get more info
                                attemptConnectionToFusionNode(device)
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error while listening for fusion node ID", e)
                _isListeningForNodeId.value = false
            }
        }
    }
    
    // Handle incoming messages to check for fusion node ID
    private fun handleFusionNodeIdMessage(message: ByteArray) {
        try {
            val messageStr = String(message, Charsets.UTF_8)
            Log.d(TAG, "Received message: $messageStr")
            
            // Check if message contains node ID (format: "Node ID: <id_str>")
            if (messageStr.contains("Node ID:") || messageStr.contains("id_str")) {
                val nodeId = extractNodeIdFromMessage(messageStr)
                if (nodeId.isNotEmpty()) {
                    Log.d(TAG, "Extracted fusion node ID: $nodeId")
                    _connectedFusionNode.value = nodeId
                    _isListeningForNodeId.value = false
                    
                    // Notify UI that we have received the node ID
                    _uiState.value = UiState.Connected
                    
                    // Show success message
                    Log.d(TAG, "Successfully received fusion node ID: $nodeId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling fusion node ID message", e)
        }
    }
    
    // Extract node ID from message string
    private fun extractNodeIdFromMessage(message: String): String {
        return try {
            // Look for patterns like "Node ID: <id>" or "id_str: <id>"
            val patterns = listOf(
                Regex("Node ID:\\s*([^\\s]+)"),
                Regex("id_str:\\s*([^\\s]+)"),
                Regex("NodeID:\\s*([^\\s]+)"),
                Regex("ID:\\s*([^\\s]+)")
            )
            
            for (pattern in patterns) {
                val match = pattern.find(message)
                if (match != null) {
                    return match.groupValues[1].trim()
                }
            }
            
            // If no pattern matches, try to extract any alphanumeric string that looks like an ID
            val words = message.split("\\s+".toRegex())
            words.find { word ->
                word.matches(Regex("[A-Za-z0-9_]+")) && word.length >= 3
            } ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting node ID", e)
            ""
        }
    }
    
    // Attempt connection to fusion node to get more information
    private fun attemptConnectionToFusionNode(device: FusionNode) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Attempting to connect to fusion node: ${device.name}")
                
                // Try to connect and request node ID
                if (device.type == "bluetooth") {
                    bleManager.connectToDevice(device)
                    // Send a request for node ID
                    val requestMessage = "REQUEST_NODE_ID".toByteArray()
                    bleManager.sendMessage(requestMessage)
                } else if (device.type == "wifi") {
                    // For WiFi, we need to create a FusionWifiNetwork object
                    val fusionNetwork = FusionWifiNetwork(
                        ssid = device.name, // Use device name as SSID
                        bssid = device.address,
                        rssi = -50, // Default RSSI
                        capabilities = "",
                        frequency = 2412, // Default frequency
                        securityType = "WPA2",
                        channel = 1
                    )
                    wifiManager.connectToNetwork(fusionNetwork)
                    
                    // After WiFi connection, TCP connection will be established automatically
                    // The TcpManager will handle the ESP32 communication and Node ID reception
                    Log.d(TAG, "WiFi connection initiated, TCP connection will follow")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to fusion node", e)
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
    

    
    // Check camera permissions specifically
    fun checkCameraPermissions(activity: android.app.Activity) {
        if (!permissionManager.hasCameraPermission()) {
            permissionManager.requestCameraPermission(activity)
        }
        updatePermissionStatus()
    }
    
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
            
            // Load WiFi history
            loadWifiHistory()
            
            Log.d(TAG, "Initial data loaded: ${sessions.size} sessions, ${allMessages.size} messages, ${allContacts.size} contacts")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load initial data", e)
        }
    }
    
    // Load WiFi connection history
    fun loadWifiHistory() {
        viewModelScope.launch {
            try {
                val history = wifiDiscoveryManager.getWifiConnectionHistory()
                _wifiHistory.value = history
                
                val statistics = wifiDiscoveryManager.getWifiUsageStatistics()
                _wifiUsageStatistics.value = statistics
                
                Log.d(TAG, "WiFi history loaded: ${history.size} networks")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load WiFi history", e)
            }
        }
    }
    
    // Get recently connected networks
    fun getRecentlyConnectedNetworks(): List<WifiConnectionHistory> {
        return wifiDiscoveryManager.getRecentlyConnectedNetworks()
    }
    
    // Get most frequent networks
    fun getMostFrequentNetworks(): List<WifiConnectionHistory> {
        return wifiDiscoveryManager.getMostFrequentNetworks()
    }
    
    // Get potential fusion nodes from history
    fun getPotentialFusionNodesFromHistory(): List<WifiConnectionHistory> {
        return wifiDiscoveryManager.getPotentialFusionNodesFromHistory()
    }
    
    // Get networks by location
    fun getNetworksByLocation(): Map<String, List<WifiConnectionHistory>> {
        return wifiDiscoveryManager.getNetworksByLocation()
    }
    
    // Get networks by time of day
    fun getNetworksByTimeOfDay(): Map<String, List<WifiConnectionHistory>> {
        return wifiDiscoveryManager.getNetworksByTimeOfDay()
    }
    
    // Get networks by signal strength
    fun getNetworksBySignalStrength(): Map<String, List<WifiConnectionHistory>> {
        return wifiDiscoveryManager.getNetworksBySignalStrength()
    }
    
    // Get networks with poor signal
    fun getNetworksWithPoorSignal(): List<WifiConnectionHistory> {
        return wifiDiscoveryManager.getNetworksWithPoorSignal()
    }
    
    // Get networks with excellent signal
    fun getNetworksWithExcellentSignal(): List<WifiConnectionHistory> {
        return wifiDiscoveryManager.getNetworksWithExcellentSignal()
    }
    
    // Check if a network is in history
    fun isNetworkInHistory(ssid: String): Boolean {
        return wifiDiscoveryManager.isNetworkInHistory(ssid)
    }
    
    // Get network from history
    fun getNetworkFromHistory(ssid: String): WifiConnectionHistory? {
        return wifiDiscoveryManager.getNetworkFromHistory(ssid)
    }
    
    // Get WiFi usage statistics
    fun getWifiUsageStatistics(): WifiUsageStatistics? {
        return _wifiUsageStatistics.value
    }
    
    // Manual connection check - called when user returns from settings
    fun checkConnectionStatus() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== Manual connection check called ===")
                
                // Only check for connections if we're in discovery mode
                if (!_isInDiscoveryMode.value) {
                    Log.d(TAG, "Not in discovery mode, skipping connection check")
                    return@launch
                }
                
                Log.d(TAG, "In discovery mode, checking for connections...")
                
                // Check Bluetooth connections
                if (bluetoothDiscoveryManager.isBluetoothSupported() && bluetoothDiscoveryManager.isBluetoothEnabled()) {
                    val connectedBleDevices = bluetoothDiscoveryManager.getConnectedDevices()
                    Log.d(TAG, "Found ${connectedBleDevices.size} connected Bluetooth devices")
                    
                    if (connectedBleDevices.isNotEmpty()) {
                        val connectedNode = connectedBleDevices.firstOrNull { device ->
                            device.name != null && device.name.isNotEmpty() && 
                            if (_fusionNodeDetectionMode.value == "strict") {
                                // Strict mode: only fusion-related devices
                                (device.name.contains("Fusion", ignoreCase = true) || 
                                 device.name.contains("Node", ignoreCase = true) ||
                                 device.name.contains("Raspberry", ignoreCase = true) ||
                                 device.name.contains("ESP", ignoreCase = true))
                            } else {
                                // Flexible mode: any named device (for testing)
                                true
                            }
                        }
                        if (connectedNode != null) {
                            Log.d(TAG, "Found connected fusion node via Bluetooth: ${connectedNode.name}")
                            _connectedFusionNode.value = connectedNode.name
                            _uiState.value = UiState.Connected
                            
                            // Call the callback to bring app to foreground
                            connectionCallback?.onConnectionDetected(connectedNode.name)
                            
                            return@launch
                        }
                    }
                }
                
                // Check WiFi connections
                if (wifiDiscoveryManager.isWifiSupported() && wifiDiscoveryManager.isWifiEnabled()) {
                    val connectedWifi = wifiDiscoveryManager.getConnectedNetwork()
                    if (connectedWifi != null && connectedWifi.ssid.isNotEmpty()) {
                        // Check if it's a fusion node based on detection mode
                        val isFusionNode = if (_fusionNodeDetectionMode.value == "strict") {
                            // Strict mode: only fusion-related networks
                            (connectedWifi.ssid.contains("Fusion", ignoreCase = true) || 
                             connectedWifi.ssid.contains("Node", ignoreCase = true) ||
                             connectedWifi.ssid.contains("Raspberry", ignoreCase = true) ||
                             connectedWifi.ssid.contains("ESP", ignoreCase = true))
                        } else {
                            // Flexible mode: any connected network (for testing)
                            true
                        }
                        
                        if (isFusionNode) {
                            
                            Log.d(TAG, "Found connected fusion node via WiFi: ${connectedWifi.ssid}")
                            _connectedFusionNode.value = connectedWifi.ssid
                            _uiState.value = UiState.Connected
                            
                            // Call the callback to bring app to foreground
                            connectionCallback?.onConnectionDetected(connectedWifi.ssid)
                            
                            return@launch
                        } else {
                            Log.d(TAG, "Connected to WiFi network but not a fusion node: ${connectedWifi.ssid}")
                        }
                    }
                }
                
                Log.d(TAG, "No connections detected")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during connection check", e)
            }
        }
    }
    
    // Handle connection establishment
    fun onConnectionEstablished() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== onConnectionEstablished() called ===")
                Log.d(TAG, "Connection established with node: ${_connectedFusionNode.value}")
                
                // Reset discovery mode since we're now connected
                _isInDiscoveryMode.value = false
                Log.d(TAG, "Discovery mode disabled - connection established")
                
                // Show connection established briefly, then automatically proceed
                delay(2000) // Show connection established for 2 seconds
                
                Log.d(TAG, "Automatically proceeding to key generation...")
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
        _isInDiscoveryMode.value = false
        _connectedFusionNode.value = null
        Log.d(TAG, "Key generation reset - returning to initial state")
    }
    
    // Reset entire app state
    fun resetAppState() {
        _uiState.value = UiState.Initial
        _isInDiscoveryMode.value = false
        _connectedFusionNode.value = null
        _keyGenerationProgress.value = 0f
        _publicKey.value = null
        // Set to flexible mode for testing - can be changed to "strict" for production
        _fusionNodeDetectionMode.value = "flexible"
        Log.d(TAG, "App state reset to initial")
    }
    
    // Function to change fusion node detection mode
    fun setFusionNodeDetectionMode(mode: String) {
        _fusionNodeDetectionMode.value = mode
        Log.d(TAG, "Fusion node detection mode set to: $mode")
    }
    
    // Reset QR code scanning state
    fun resetQRCodeScanning() {
        _targetFusionNode.value = null
        _peerPublicKey.value = null
        Log.d(TAG, "QR code scanning state reset")
    }
    
    // Set peer public key manually
    fun setPeerPublicKey(publicKey: String) {
        _peerPublicKey.value = publicKey
        Log.d(TAG, "Peer public key set manually: ${publicKey.take(20)}...")
    }
    
    // Set peer node ID manually
    fun setPeerNodeId(nodeId: String) {
        _targetFusionNode.value = nodeId
        Log.d(TAG, "Peer node ID set manually: $nodeId")
    }
    
    // Generate new X25519 key pair with progress tracking
    fun generateKeyPair() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting key generation process...")
                _uiState.value = UiState.Loading("Generating key pair...")
                _keyGenerationProgress.value = 0f
                
                // Check if we already have a key pair
                if (_publicKey.value != null) {
                    Log.d(TAG, "Public key already exists, skipping generation...")
                    _keyGenerationProgress.value = 1.0f
                    _uiState.value = UiState.KeyGenerated
                    return@launch
                }
                
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
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate key pair", e)
                _uiState.value = UiState.Error("Failed to generate key pair: ${e.message}")
                _keyGenerationProgress.value = 0f
                
                // Reset state on error
                _publicKey.value = null
            }
        }
    }
    
    // Ensure a public key exists, create one if needed
    fun ensurePublicKeyExists() {
        try {
            if (_publicKey.value == null) {
                Log.d(TAG, "No public key exists, creating one...")
                generateKeyPair()
            } else {
                Log.d(TAG, "Public key already exists")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring public key exists", e)
            // Create a fallback public key to prevent crashes
            _publicKey.value = createFallbackKeyPair()
        }
    }
    
    // Create a fallback key pair if crypto operations fail
    private fun createFallbackKeyPair(): KeyPair? {
        return try {
            Log.d(TAG, "Creating fallback RSA key pair...")
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(2048)
            keyGen.generateKeyPair()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create fallback key pair", e)
            null
        }
    }
    
    // Get public key information in readable format
    fun getPublicKeyInfo(): String? {
        return _publicKey.value?.let { keyPair ->
            val publicKeyBytes = keyPair.public.encoded
            val publicKeyHex = publicKeyBytes.take(16).joinToString("") { "%02x".format(it) }
            "pk_$publicKeyHex"
        }
    }
    
    // Get current timestamp in readable format
    fun getCurrentTimestamp(): String {
        val timestamp = System.currentTimeMillis()
        val date = java.util.Date(timestamp)
        val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(date)
    }
    
    // Get fusion node information in readable format
    fun getFusionNodeInfo(): String {
        return _connectedFusionNode.value ?: "TestFusionNode_${System.currentTimeMillis() % 10000}"
    }
    
    // Start listening for Node ID from any fusion node (ESP32, Pi, etc.)
    fun startListeningForNodeId() {
        _isListeningForNodeId.value = true
        Log.d(TAG, "Started listening for Node ID from fusion node")
        
        // Try to connect to fusion node via WiFi connection
        viewModelScope.launch {
            try {
                val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as AndroidWifiManager
                val connectionInfo = wifiManager.connectionInfo
                
                if (connectionInfo != null && connectionInfo.networkId != -1) {
                    val ssid = connectionInfo.ssid?.removeSurrounding("\"") ?: ""
                    Log.d(TAG, "Connected to WiFi network: $ssid")
                    
                    // Check if we're connected to a fusion node network
                    if (isFusionNodeNetwork(ssid)) {
                        Log.d(TAG, "Connected to fusion node network: $ssid")
                        // Try to get node ID from network name or connect to the node
                        val nodeId = extractNodeIdFromNetworkName(ssid)
                        if (nodeId.isNotEmpty()) {
                            Log.d(TAG, "Extracted node ID from network name: $nodeId")
                            _connectedFusionNode.value = nodeId
                            _tcpConnectionStatus.value = TcpConnectionStatus.ConnectedWithNodeId(nodeId)
                            return@launch
                        }
                    }
                    
                    // Try multiple common fusion node IP addresses
                    val possibleNodeIps = listOf(
                        "192.168.4.1",    // ESP32 default hotspot IP
                        "192.168.1.100",  // Common Pi/ESP32 IP in home networks
                        "192.168.1.101",  // Alternative Pi/ESP32 IP
                        "10.0.0.100",     // Alternative network range
                        "172.20.10.1",    // iPhone hotspot range
                        "192.168.1.1",    // Router IP (might be fusion node)
                        "192.168.0.1"     // Alternative router IP
                    )
                    
                    Log.d(TAG, "Attempting to connect to fusion node at multiple IPs: $possibleNodeIps")
                    
                    // Try each IP address until one works
                    for (ip in possibleNodeIps) {
                        try {
                            Log.d(TAG, "Trying to connect to fusion node at IP: $ip")
                            tcpManager.connectToFusionNode(ip, 18080)
                            
                            // Wait a bit to see if connection succeeds
                            delay(3000)
                            
                            // Check if we got a Node ID
                            if (_connectedFusionNode.value != null) {
                                Log.d(TAG, "Successfully connected to fusion node at $ip")
                                break
                            }
                            
                            // Also check TCP manager's node ID
                            val tcpNodeId = tcpManager.getCurrentNodeId()
                            if (tcpNodeId != null) {
                                Log.d(TAG, "Received node ID from TCP manager: $tcpNodeId")
                                _connectedFusionNode.value = tcpNodeId
                                _tcpConnectionStatus.value = TcpConnectionStatus.ConnectedWithNodeId(tcpNodeId)
                                break
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to connect to $ip: ${e.message}")
                            continue
                        }
                    }
                    
                    // If no automatic connection worked, try scanning the network
                    if (_connectedFusionNode.value == null) {
                        Log.w(TAG, "Could not automatically connect to fusion node, trying network scan")
                        scanForFusionNodes()
                    }
                    
                    // If still no connection, set status for manual input
                    if (_connectedFusionNode.value == null) {
                        Log.w(TAG, "Could not automatically connect to fusion node")
                        _tcpConnectionStatus.value = TcpConnectionStatus.Failed("Please connect manually or check fusion node status")
                    }
                } else {
                    Log.w(TAG, "No WiFi connection found")
                    _tcpConnectionStatus.value = TcpConnectionStatus.Failed("No WiFi connection - please connect to a network first")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to fusion node: ${e.message}")
                _tcpConnectionStatus.value = TcpConnectionStatus.Failed("Connection error: ${e.message}")
            }
        }
    }
    
    // Check if the current WiFi network might be a fusion node
    private fun isFusionNodeNetwork(ssid: String): Boolean {
        val fusionKeywords = listOf("fusion", "node", "esp32", "raspberry", "pi", "mesh", "router")
        val ssidLower = ssid.lowercase()
        return fusionKeywords.any { keyword -> ssidLower.contains(keyword) }
    }
    
    // Extract node ID from network name
    private fun extractNodeIdFromNetworkName(ssid: String): String {
        // Look for patterns like "FusionNode_12345" or "ESP32_ABC123"
        val patterns = listOf(
            Regex("fusion[_-]?node[_-]?([A-Za-z0-9]+)", RegexOption.IGNORE_CASE),
            Regex("esp32[_-]?([A-Za-z0-9]+)", RegexOption.IGNORE_CASE),
            Regex("raspberry[_-]?pi[_-]?([A-Za-z0-9]+)", RegexOption.IGNORE_CASE),
            Regex("node[_-]?([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(ssid)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        
        return ""
    }
    
    // Helper function to convert IP address from int to string
    private fun intToIp(ip: Int): String {
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }
    
    // Manual method to connect to any fusion node with specific IP
    fun connectToFusionNodeWithIp(ipAddress: String, port: Int = 18080) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Manually connecting to fusion node at IP: $ipAddress:$port")
                tcpManager.connectToFusionNode(ipAddress, port)
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to fusion node at $ipAddress:$port: ${e.message}")
                _tcpConnectionStatus.value = TcpConnectionStatus.Failed("Connection error: ${e.message}")
            }
        }
    }
    
    // Scan for fusion nodes on the network
    fun scanForFusionNodes() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Scanning for fusion nodes on network")
                _tcpConnectionStatus.value = TcpConnectionStatus.Connecting
                
                // Common fusion node IP ranges to scan
                val ipRanges = listOf(
                    "192.168.1",  // Common home network range
                    "192.168.4",  // ESP32 hotspot range
                    "10.0.0",     // Alternative network range
                    "172.20.10"   // iPhone hotspot range
                )
                
                val foundNodes = mutableListOf<String>()
                
                for (baseIp in ipRanges) {
                    for (lastOctet in 1..254) {
                        val ip = "$baseIp.$lastOctet"
                        
                        try {
                            Log.d(TAG, "Scanning IP: $ip")
                            tcpManager.connectToFusionNode(ip, 18080)
                            
                            // Wait briefly to see if connection succeeds
                            delay(500)
                            
                            // Check if we got a Node ID
                            if (_connectedFusionNode.value != null) {
                                foundNodes.add(ip)
                                Log.d(TAG, "Found fusion node at: $ip")
                                break
                            }
                        } catch (e: Exception) {
                            // Continue scanning
                            continue
                        }
                    }
                }
                
                if (foundNodes.isNotEmpty()) {
                    Log.d(TAG, "Found fusion nodes: $foundNodes")
                    _tcpConnectionStatus.value = TcpConnectionStatus.Connected
                } else {
                    Log.w(TAG, "No fusion nodes found on network")
                    _tcpConnectionStatus.value = TcpConnectionStatus.Failed("No fusion nodes found")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning for fusion nodes: ${e.message}")
                _tcpConnectionStatus.value = TcpConnectionStatus.Failed("Scan error: ${e.message}")
            }
        }
    }
    
    // Stop listening for Node ID
    fun stopListeningForNodeId() {
        _isListeningForNodeId.value = false
        Log.d(TAG, "Stopped listening for Node ID")
    }
    
    // Stop device discovery
    fun stopDeviceDiscovery() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== Stopping device discovery ===")
                _uiState.value = UiState.Loading("Stopping discovery...")
                
                // Set discovery mode flag
                _isInDiscoveryMode.value = false
                Log.d(TAG, "Discovery mode disabled")
                
                // Stop Bluetooth discovery
                if (bluetoothDiscoveryManager.isBluetoothSupported() && 
                    bluetoothDiscoveryManager.isBluetoothEnabled()) {
                    bluetoothDiscoveryManager.stopDiscovery()
                    Log.d(TAG, "Bluetooth discovery stopped")
                }
                
                // Stop WiFi discovery
                if (wifiDiscoveryManager.isWifiSupported() && 
                    wifiDiscoveryManager.isWifiEnabled()) {
                    wifiDiscoveryManager.stopScan()
                    Log.d(TAG, "WiFi discovery stopped")
                }
                
                _uiState.value = UiState.Initial
                Log.d(TAG, "Device discovery stopped successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop device discovery", e)
                _uiState.value = UiState.Error("Failed to stop discovery: ${e.message}")
            }
        }
    }
    
    // Start device discovery (Bluetooth + WiFi)
    fun startDeviceDiscovery() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== Starting device discovery ===")
                _uiState.value = UiState.Loading("Starting device discovery...")
                
                // Set discovery mode flag
                _isInDiscoveryMode.value = true
                Log.d(TAG, "Discovery mode enabled")
                
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
                
                _uiState.value = UiState.Connected // Changed from DiscoveryActive to Connected
                
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
                    _isBluetoothConnected.value = true
                    _isWifiConnected.value = false
                    _bluetoothStatus.value = "Connected to ${fusionNode.name}"
                    _wifiStatus.value = "Disconnected"
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
    fun connectToWifiNetwork(fusionNetwork: FusionWifiNetwork, password: String? = null) {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading("Connecting to ${fusionNetwork.ssid}...")
                
                // Check if WiFi is ready
                if (!isWifiReady()) {
                    _uiState.value = UiState.Error("WiFi not ready. Please check permissions and enable WiFi.")
                    return@launch
                }
                
                // Attempt connection
                val success = wifiDiscoveryManager.connectToNetwork(fusionNetwork, password)
                if (success) {
                    // Wait for actual connection confirmation
                    var attempts = 0
                    val maxAttempts = 30 // Wait up to 30 seconds
                    
                    while (attempts < maxAttempts) {
                        delay(1000) // Wait 1 second between checks
                        attempts++
                        
                        val currentConnection = wifiDiscoveryManager.getCurrentConnection()
                        if (currentConnection.contains(fusionNetwork.ssid)) {
                            _connectedFusionNode.value = fusionNetwork.ssid
                            _isWifiConnected.value = true
                            _isBluetoothConnected.value = false
                            _wifiStatus.value = "Connected to ${fusionNetwork.ssid}"
                            _bluetoothStatus.value = "Disconnected"
                            _uiState.value = UiState.Connected
                            onConnectionEstablished()
                            Log.d(TAG, "Successfully connected to WiFi network: ${fusionNetwork.ssid}")
                            return@launch
                        }
                        
                        // Update loading message
                        _uiState.value = UiState.Loading("Connecting to ${fusionNetwork.ssid}... (${attempts}s)")
                    }
                    
                    // If we get here, connection failed
                    _uiState.value = UiState.Error("Connection timeout. Failed to connect to ${fusionNetwork.ssid}")
                    Log.e(TAG, "Connection timeout for ${fusionNetwork.ssid}")
                    
                } else {
                    _uiState.value = UiState.Error("Failed to initiate connection to ${fusionNetwork.ssid}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to WiFi network", e)
                _uiState.value = UiState.Error("Connection failed: ${e.message}")
            }
        }
    }
    
    // Disconnect from current device
    fun disconnectFromDevice() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Disconnecting from current device...")
                
                // Reset connection status
                _isBluetoothConnected.value = false
                _isWifiConnected.value = false
                _bluetoothStatus.value = "Disconnected"
                _wifiStatus.value = "Disconnected"
                _connectedFusionNode.value = null
                
                // Disconnect from managers
                bluetoothDiscoveryManager.disconnectFromDevice()
                wifiDiscoveryManager.disconnectFromNetwork()
                
                _uiState.value = UiState.Disconnected
                Log.d(TAG, "Successfully disconnected from device")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting from device", e)
            }
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
    
    // Create QR code data containing public key and fusion node
    private fun createQRCodeData(
        publicKey: java.security.PublicKey, 
        fusionNode: String?
    ): QRCodeData {
        val nodeName = fusionNode ?: "Unknown"
        
        return QRCodeData.create(
            publicKey = publicKey,
            fusionNode = nodeName
        )
    }
    
    // Get current connection type
    private fun getCurrentConnectionType(): String {
        return when {
            isBluetoothConnected() -> "bluetooth"
            isWifiConnected() -> "wifi"
            else -> "unknown"
        }
    }
    

    

    
    // Extract routing information from received message payload
    fun extractRoutingInfo(payload: ByteArray): RoutingInfo? {
        return try {
            val payloadString = String(payload)
            val parts = payloadString.split(0x00.toChar())
            
            if (parts.size >= 2) {
                val currentNode = parts[0]
                val targetNode = parts[1]
                
                // Extract peer device information if available
                val peerDeviceInfo = if (parts.size >= 5) {
                    PeerDeviceInfo(
                        deviceId = parts[2],
                        fusionNode = parts[3],
                        connectionType = parts[4],
                        deviceName = parts.getOrNull(5) ?: "Unknown",
                        publicKeyBytes = ByteArray(0) // Not needed for routing
                    )
                } else null
                
                RoutingInfo(
                    sourceNode = currentNode,
                    targetNode = targetNode,
                    peerDeviceInfo = peerDeviceInfo
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract routing information", e)
            null
        }
    }
    
    // Get current routing information for display
    fun getCurrentRoutingInfo(): String {
        val currentNode = _connectedFusionNode.value ?: "Not Connected"
        val targetNode = _targetFusionNode.value ?: "No Target"
        val peerInfo = _peerDeviceInfo.value
        
        return buildString {
            appendLine("Current Routing Information:")
            appendLine("  - Connected to: $currentNode")
            appendLine("  - Target Node: $targetNode")
            peerInfo?.let { peer ->
                appendLine("  - Peer Fusion Node: ${peer.fusionNode}")
                appendLine("  - Peer Device ID: ${peer.deviceId}")
                appendLine("  - Connection Type: ${peer.connectionType}")
            }
        }
    }
    
    // Analyze routing payload structure
    fun analyzeRoutingPayload(): String {
        val currentNode = _connectedFusionNode.value ?: "Not Connected"
        val targetNode = _targetFusionNode.value ?: "No Target"
        val peerInfo = _peerDeviceInfo.value
        
        return buildString {
            appendLine("Routing Payload Structure:")
            appendLine("  - Source Node: $currentNode")
            appendLine("  - Target Node: $targetNode")
            peerInfo?.let { peer ->
                appendLine("  - Peer Device ID: ${peer.deviceId}")
                appendLine("  - Peer Fusion Node ID: ${peer.fusionNode} (for routing)")
                appendLine("  - Peer Connection Type: ${peer.connectionType}")
                appendLine("  - Encrypted Message: [Binary Data]")
            }
        }
    }
    
    // Handle received message from Fusion Node
    fun handleReceivedMessage(messageBytes: ByteArray) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Received message from Fusion Node: ${messageBytes.size} bytes")
                
                // Parse simple Phone to Fusion payload: [fusion_id_len | fusion_id | encrypted_message]
                val parsedPayload = parsePhoneToFusionPayload(messageBytes)
                if (parsedPayload == null) {
                    Log.e(TAG, "Failed to parse Phone to Fusion payload")
                    return@launch
                }
                
                Log.d(TAG, "Parsed payload - Destination: ${parsedPayload.destination}")
                Log.d(TAG, "Parsed payload - Fusion ID: ${parsedPayload.fusionId}")
                Log.d(TAG, "Parsed payload - Inner message size: ${parsedPayload.innerMessage.size} bytes")
                
                // Check if this message is for us
                val ourDeviceId = _peerDeviceInfo.value?.deviceId ?: "Phone1"
                if (parsedPayload.destination != ourDeviceId) {
                    Log.d(TAG, "Message not for us, ignoring")
                    return@launch
                }
                
                // Check if fusion ID matches our connected fusion
                val ourFusionId = _connectedFusionNode.value
                if (ourFusionId != null && parsedPayload.fusionId != ourFusionId) {
                    Log.d(TAG, "Message not for our fusion node, ignoring")
                    return@launch
                }
                
                // Extract encrypted message (inner part)
                val encryptedMessage = parsedPayload.innerMessage
                
                // Decrypt message
                val session = _currentSession.value
                if (session == null) {
                    Log.e(TAG, "No active session for decryption")
                    return@launch
                }
                
                val decryptedMessage = sessionManager.decryptMessage(session, encryptedMessage)
                if (decryptedMessage == null) {
                    Log.e(TAG, "Failed to decrypt received message")
                    return@launch
                }
                
                Log.d(TAG, "Successfully decrypted message: $decryptedMessage")
                
                // Store received message in database
                val messageEntity = Message(
                    sessionId = session.id,
                    senderId = parsedPayload.destination.hashCode().mod(Int.MAX_VALUE),
                    receiverId = DEVICE_ID,
                    content = decryptedMessage,
                    encryptedContent = encryptedMessage,
                    timestamp = Date(),
                    isEncrypted = true,
                    isCompressed = false // Will be determined from frame flags
                )
                
                val messageId = database.messageDao().insertMessage(messageEntity)
                Log.d(TAG, "Received message stored with ID: $messageId")
                
                // Update UI with new message
                loadMessagesForSession(session.id)
                
                // Update UI state
                _uiState.value = UiState.Connected // Changed from ChatActive to Connected
                
            } catch (e: Exception) {
                Log.e(TAG, "Error handling received message", e)
                _uiState.value = UiState.Error("Failed to process received message: ${e.message}")
            }
        }
    }
    
    // Parse simple Phone to Fusion payload: [fusion_id_len | fusion_id | encrypted_message]
    private fun parsePhoneToFusionPayload(payload: ByteArray): PhoneToFusionPayload? {
        return try {
            var offset = 0
            
            // Extract fusion_id_len (fusion ID length)
            if (offset >= payload.size) return null
            val fusionIdLen = payload[offset++].toInt()
            
            // Extract fusion_id (fusion ID string)
            if (offset + fusionIdLen > payload.size) return null
            val fusionId = String(payload, offset, fusionIdLen)
            offset += fusionIdLen
            
            // Extract encrypted_message (E2EE encrypted message)
            if (offset >= payload.size) return null
            val encryptedMessage = payload.copyOfRange(offset, payload.size)
            
            PhoneToFusionPayload("Phone2", fusionId, encryptedMessage) // Default destination
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing simple Phone to Fusion payload", e)
            null
        }
    }
    
    // Data class for parsed Phone to Fusion payload
    data class PhoneToFusionPayload(
        val destination: String, // Always "Phone2" for now
        val fusionId: String,
        val innerMessage: ByteArray
    )
    
    // Extract encrypted message from payload (remove routing header) - legacy
    private fun extractEncryptedMessage(payload: ByteArray): ByteArray? {
        return try {
            val payloadString = String(payload)
            val separator = 0x00.toChar()
            val parts = payloadString.split(separator)
            
            // Find where the encrypted message starts
            // Skip routing header parts
            val messageStartIndex = parts.takeWhile { it.isNotEmpty() }.size
            val messageStart = payloadString.indexOf(separator, payloadString.indexOf(separator, payloadString.indexOf(separator, payloadString.indexOf(separator, payloadString.indexOf(separator) + 1) + 1) + 1) + 1) + 1
            
            if (messageStart > 0 && messageStart < payload.size) {
                payload.copyOfRange(messageStart, payload.size)
            } else {
                // Fallback: assume last part is the message
                payload
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting encrypted message", e)
            null
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
                        _uiState.value = UiState.Connected // Changed from SessionEstablished to Connected
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
    
    // Send encrypted message to Fusion Node with correct payload structure
    fun sendMessage(message: String, destinationId: Int) {
        viewModelScope.launch {
            try {
                val session = _currentSession.value
                if (session == null) {
                    _uiState.value = UiState.Error("No active session")
                    return@launch
                }
                
                // Get destination phone ID and fusion ID from peer device info
                val destinationPhone = _peerDeviceInfo.value?.deviceId ?: "Phone2"
                val targetFusionId = _peerDeviceInfo.value?.fusionNode ?: "FUSION_DEVICE_002"
                
                Log.d(TAG, "Sending message to Fusion Node:")
                Log.d(TAG, "  - Destination: $destinationPhone")
                Log.d(TAG, "  - Target Fusion ID: $targetFusionId")
                Log.d(TAG, "  - Message: $message")
                
                // Encrypt message with E2EE (End-to-End Encryption)
                val encryptedMessage = sessionManager.encryptMessage(session, message, destinationId)
                if (encryptedMessage == null) {
                    _uiState.value = UiState.Error("Failed to encrypt message")
                    return@launch
                }
                
                // Create simple payload structure: [fusion_id_len | fusion_id | encrypted_message]
                val phoneToFusionPayload = createPhoneToFusionPayload(targetFusionId, encryptedMessage)
                
                // Send to Fusion Node via current connection (BLE or WiFi)
                val sent = if (isBluetoothConnected()) {
                    Log.d(TAG, "Sending payload via BLE to Fusion Node")
                    bleManager.sendMessage(phoneToFusionPayload)
                } else if (isWifiConnected()) {
                    Log.d(TAG, "Sending payload via WiFi to Fusion Node")
                    wifiManager.sendMessage(phoneToFusionPayload)
                } else {
                    Log.e(TAG, "No active connection to Fusion Node")
                    false
                }
                
                if (sent) {
                    // Store message in database
                    val messageEntity = Message(
                        sessionId = session.id,
                        senderId = DEVICE_ID,
                        receiverId = destinationId,
                        content = message,
                        encryptedContent = encryptedMessage,
                        timestamp = Date(),
                        isEncrypted = true,
                        isCompressed = false // Simplified approach - no frame flags
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
    
    // Get real WiFi connection details
    fun getRealWifiConnectionInfo(): String {
        return try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            
            // Check for required permissions
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return "Location permission required for WiFi info"
                }
            }
            
            if (wifiManager.isWifiEnabled) {
                val connectionInfo = wifiManager.connectionInfo
                if (connectionInfo != null && connectionInfo.networkId != -1) {
                    val ssid = connectionInfo.ssid.replace("\"", "") // Remove quotes
                    val bssid = connectionInfo.bssid ?: "Unknown"
                    val rssi = connectionInfo.rssi
                    val linkSpeed = connectionInfo.linkSpeed
                    val frequency = connectionInfo.frequency
                    
                    "Connected to: $ssid\nBSSID: $bssid\nSignal: ${rssi}dBm\nSpeed: ${linkSpeed}Mbps\nFreq: ${frequency}MHz"
                } else {
                    "WiFi enabled but not connected"
                }
            } else {
                "WiFi is disabled"
            }
        } catch (e: SecurityException) {
            "Permission denied: ${e.message}"
        } catch (e: Exception) {
            "Error getting WiFi info: ${e.message}"
        }
    }
    
    // Get real Bluetooth connection details
    fun getRealBluetoothConnectionInfo(): String {
        return try {
            if (bluetoothDiscoveryManager.isBluetoothEnabled()) {
                val connectedDevices = bluetoothDiscoveryManager.getConnectedDevices()
                if (connectedDevices.isNotEmpty()) {
                    val device = connectedDevices.first()
                    "Connected to: ${device.name}\nAddress: ${device.address}\nType: ${device.type}"
                } else {
                    "Bluetooth enabled but no devices connected"
                }
            } else {
                "Bluetooth is disabled"
            }
        } catch (e: Exception) {
            "Error getting Bluetooth info: ${e.message}"
        }
    }
    
    // Get comprehensive connection status with real details
    fun getConnectionStatusWithDetails(): String {
        return when {
            isWifiConnected() -> {
                val realWifiInfo = getRealWifiConnectionInfo()
                "WiFi Connected\n$realWifiInfo"
            }
            isBluetoothConnected() -> {
                val realBtInfo = getRealBluetoothConnectionInfo()
                "Bluetooth Connected\n$realBtInfo"
            }
            else -> {
                val wifiStatus = if (wifiDiscoveryManager.isWifiEnabled()) "WiFi: Enabled" else "WiFi: Disabled"
                val btStatus = if (bluetoothDiscoveryManager.isBluetoothEnabled()) "Bluetooth: Enabled" else "Bluetooth: Disabled"
                "Not Connected\n$wifiStatus\n$btStatus"
            }
        }
    }
    
    // Get current connection summary for UI display
    fun getCurrentConnectionSummary(): String {
        val wifiInfo = getRealWifiConnectionInfo()
        val btInfo = getRealBluetoothConnectionInfo()
        
        return buildString {
            appendLine("=== CONNECTION STATUS ===")
            appendLine(wifiInfo)
            appendLine()
            appendLine(btInfo)
            appendLine()
            appendLine("=== FUSION NODE STATUS ===")
            appendLine("Connected Fusion Node: ${_connectedFusionNode.value ?: "None"}")
            appendLine("Peer Device Info: ${_peerDeviceInfo.value?.let { "${it.deviceId} on ${it.fusionNode}" } ?: "None"}")
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
    
    // Create simple Phone to Fusion Node payload: [fusion_id_len | fusion_id | encrypted_message]
    private fun createPhoneToFusionPayload(fusionId: String, encryptedMessage: ByteArray): ByteArray {
        val payload = ByteArrayOutputStream()
        
        // fusion_id_len → fusion ID string की length (0-255)
        val fusionIdBytes = fusionId.toByteArray()
        val fusionIdLen = fusionIdBytes.size.toByte()  // uint8_t fusion_id_len
        
        // fusion_id → target fusion node ID (like "FUSION_DEVICE_002")
        val fusionIdData = fusionId.toByteArray()
        
        // encrypted_message → E2EE encrypted user message
        val encryptedData = encryptedMessage
        
        // Simple payload structure: [fusion_id_len | fusion_id | encrypted_message]
        payload.write(byteArrayOf(fusionIdLen))
        payload.write(fusionIdData)
        payload.write(encryptedData)
        
        Log.d(TAG, "Created simple Phone to Fusion payload:")
        Log.d(TAG, "  - fusion_id_len: $fusionIdLen")
        Log.d(TAG, "  - fusion_id: $fusionId")
        Log.d(TAG, "  - encrypted_message size: ${encryptedData.size} bytes")
        
        return payload.toByteArray()
    }
    
    // Create a payload that includes the encrypted message and routing information (legacy)
    private fun createRoutingPayload(
        frame: Frame, 
        currentNode: String, 
        targetNode: String,
        peerDeviceInfo: PeerDeviceInfo?
    ): ByteArray {
        val messageBytes = frame.toByteArray()
        val currentNodeBytes = currentNode.toByteArray()
        val targetNodeBytes = targetNode.toByteArray()
        
        // Create enhanced routing header with peer device information
        val routingHeader = createRoutingHeader(currentNode, targetNode, peerDeviceInfo)
        
        val payload = ByteArrayOutputStream()
        payload.write(routingHeader)
        payload.write(messageBytes)
        
        return payload.toByteArray()
    }
    
    // Create routing header with peer device information
    private fun createRoutingHeader(
        currentNode: String, 
        targetNode: String, 
        peerDeviceInfo: PeerDeviceInfo?
    ): ByteArray {
        val header = ByteArrayOutputStream()
        
        // Add routing information
        header.write(currentNode.toByteArray())
        header.write(byteArrayOf(0x00)) // Separator
        header.write(targetNode.toByteArray())
        header.write(byteArrayOf(0x00)) // Separator
        
        // Add peer device information if available (including Fusion Node ID)
        peerDeviceInfo?.let { peer ->
            header.write(peer.deviceId.toByteArray())
            header.write(byteArrayOf(0x00)) // Separator
            header.write(peer.fusionNode.toByteArray()) // Fusion Node ID for routing
            header.write(byteArrayOf(0x00)) // Separator
            header.write(peer.connectionType.toByteArray())
            header.write(byteArrayOf(0x00)) // Separator
        }
        
        return header.toByteArray()
    }
    
    // Communication verification methods
    fun verifyCommunicationWithDevice(
        deviceId: String,
        deviceName: String? = null,
        connectionType: String,
        deviceAddress: String? = null
    ) {
        viewModelScope.launch {
            try {
                _isVerifyingCommunication.value = true
                
                // Get real device address from current connection
                val realDeviceAddress = when {
                    connectionType.lowercase() == "bluetooth" -> {
                        val connectedBleDevices = bluetoothDiscoveryManager.getConnectedDevices()
                        connectedBleDevices.firstOrNull()?.address
                    }
                    connectionType.lowercase() == "wifi" -> {
                        val wifiStatus = wifiManager.getConnectionStatus()
                        if (wifiStatus is com.example.mine.network.WifiConnectionStatus.Connected) {
                            wifiStatus.network.bssid
                        } else null
                    }
                    else -> deviceAddress
                }
                
                Log.d(TAG, "Real device address for verification: $realDeviceAddress")
                
                // Perform verification with real device address
                val proofs = communicationVerifier.verifyCommunication(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    connectionType = connectionType,
                    deviceAddress = realDeviceAddress
                )
                
                _communicationProofs.value = proofs
                Log.d(TAG, "Communication verification completed with ${proofs.size} proofs")
                
            } catch (e: Exception) {
                Log.e(TAG, "Communication verification failed", e)
                _communicationProofs.value = listOf(
                    CommunicationProof(
                        deviceId = deviceId,
                        connectionType = connectionType,
                        timestamp = System.currentTimeMillis(),
                        proofType = com.example.mine.network.ProofType.MESSAGE_DELIVERY,
                        proofData = "Verification failed",
                        success = false,
                        errorMessage = e.message
                    )
                )
            } finally {
                _isVerifyingCommunication.value = false
                _verificationProgress.value = 0f
            }
        }
    }
    
    fun clearCommunicationProofs() {
        _communicationProofs.value = emptyList()
        communicationVerifier.clearProofs()
    }
    
    fun getCommunicationSummary(): String {
        return communicationVerifier.getCommunicationSummary()
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
    
    // Manual method to set node ID for testing
    fun setNodeIdManually(nodeId: String) {
        Log.d(TAG, "Manually setting node ID: $nodeId")
        _connectedFusionNode.value = nodeId
        _tcpConnectionStatus.value = TcpConnectionStatus.ConnectedWithNodeId(nodeId)
        _isListeningForNodeId.value = false
    }
}
