package com.example.mine.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class WifiManager(private val context: Context) {
    
    companion object {
        private const val TAG = "WifiManager"
        private const val FUSION_SSID_PREFIX = "FUSION_"
        private const val FUSION_PORT = 18080 // Updated to match ESP32 port
        private const val CONNECTION_TIMEOUT = 5000
    }
    
    private val wifiManager: WifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val executor = Executors.newCachedThreadPool()
    private val tcpManager = TcpManager() // Add TCP manager for ESP32 communication
    
    // State flows
    private val _discoveredNetworks = MutableStateFlow<List<FusionWifiNetwork>>(emptyList())
    val discoveredNetworks: StateFlow<List<FusionWifiNetwork>> = _discoveredNetworks.asStateFlow()
    
    private val _connectionStatus = MutableStateFlow<WifiConnectionStatus>(WifiConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<WifiConnectionStatus> = _connectionStatus.asStateFlow()
    
    private var currentSocket: Socket? = null
    private var isConnected = false
    
    // Check if WiFi is enabled
    fun isWifiEnabled(): Boolean {
        return wifiManager.isWifiEnabled
    }
    
    // Get TCP connection status
    fun getTcpConnectionStatus(): StateFlow<TcpConnectionStatus> {
        return tcpManager.connectionStatus
    }
    
    // Get connected Node ID
    fun getConnectedNodeId(): StateFlow<String?> {
        return tcpManager.connectedNodeId
    }
    
    // Get received TCP messages
    fun getReceivedTcpMessages(): StateFlow<List<TcpMessage>> {
        return tcpManager.receivedMessages
    }
    
    // Start scanning for fusion networks
    fun startScan() {
        if (!isWifiEnabled()) {
            Log.e(TAG, "WiFi is not enabled")
            return
        }

        // Explicit permission checks per API level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "NEARBY_WIFI_DEVICES permission required for WiFi scanning on Android 13+")
                _discoveredNetworks.value = emptyList()
                return
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "ACCESS_FINE_LOCATION permission required for WiFi scanning")
                _discoveredNetworks.value = emptyList()
                return
            }
        }

        try {
            val success = wifiManager.startScan()
            if (success) {
                Log.d(TAG, "Started WiFi scan for fusion networks")
                processScanResults()
            } else {
                Log.e(TAG, "Failed to start WiFi scan")
            }
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException starting WiFi scan", se)
            _discoveredNetworks.value = emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting WiFi scan", e)
            _discoveredNetworks.value = emptyList()
        }
    }
    
    // Process scan results to find fusion networks
    private fun processScanResults() {
        try {
            // Check for location permission (required for WiFi scanning on Android 6+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "Location permission required for WiFi scanning")
                    _discoveredNetworks.value = emptyList()
                    return
                }
            }
            
            val scanResults = wifiManager.scanResults
            val fusionNetworks = mutableListOf<FusionWifiNetwork>()
            
            for (result in scanResults) {
                // Show all networks, not just fusion networks
                val fusionNetwork = FusionWifiNetwork(
                    ssid = result.SSID,
                    bssid = result.BSSID,
                    rssi = result.level,
                    capabilities = result.capabilities,
                    frequency = result.frequency,
                    securityType = getSecurityType(result.capabilities),
                    channel = getChannelFromFrequency(result.frequency)
                )
                fusionNetworks.add(fusionNetwork)
            }
            
            _discoveredNetworks.value = fusionNetworks
            Log.d(TAG, "Found ${fusionNetworks.size} fusion networks")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while processing scan results", e)
            _discoveredNetworks.value = emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing scan results", e)
            _discoveredNetworks.value = emptyList()
        }
    }
    
    // Check if network is a fusion network
    private fun isFusionNetwork(scanResult: ScanResult): Boolean {
        return scanResult.SSID.startsWith(FUSION_SSID_PREFIX, ignoreCase = true)
    }
    
    // Connect to a fusion network
    fun connectToNetwork(fusionNetwork: FusionWifiNetwork) {
        _connectionStatus.value = WifiConnectionStatus.Connecting
        
        executor.execute {
            try {
                // For Android 10+ use NetworkRequest
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    connectUsingNetworkRequest(fusionNetwork)
                } else {
                    connectUsingLegacyMethod(fusionNetwork)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to network", e)
                _connectionStatus.value = WifiConnectionStatus.Error("Connection failed: ${e.message}")
            }
        }
    }
    
    // Connect using NetworkRequest (Android 10+)
    private fun connectUsingNetworkRequest(fusionNetwork: FusionWifiNetwork) {
        // This would require implementing NetworkCallback and NetworkRequest
        // For now, we'll use a simplified approach
        Log.d(TAG, "Using NetworkRequest to connect to ${fusionNetwork.ssid}")
        
        // Simulate connection process
        Thread.sleep(2000)
        
        // Try to establish socket connection
        establishSocketConnection(fusionNetwork)
    }
    
    // Connect using legacy method (Android 9 and below)
    private fun connectUsingLegacyMethod(fusionNetwork: FusionWifiNetwork) {
        try {
            Log.d(TAG, "Using legacy method to connect to ${fusionNetwork.ssid}")
            
            // Check for required permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "Location permission required for WiFi connection")
                    _connectionStatus.value = WifiConnectionStatus.Error("Location permission required")
                    return
                }
            }
            
            // Create network configuration
            val config = WifiConfiguration().apply {
                SSID = "\"${fusionNetwork.ssid}\""
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE) // Open network
            }
            
            // Add network
            val networkId = wifiManager.addNetwork(config)
            if (networkId != -1) {
                val success = wifiManager.enableNetwork(networkId, true)
                if (success) {
                    Log.d(TAG, "Successfully connected to ${fusionNetwork.ssid}")
                    establishSocketConnection(fusionNetwork)
                } else {
                    Log.e(TAG, "Failed to enable network")
                    _connectionStatus.value = WifiConnectionStatus.Error("Failed to enable network")
                }
            } else {
                Log.e(TAG, "Failed to add network")
                _connectionStatus.value = WifiConnectionStatus.Error("Failed to add network")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while connecting to network", e)
            _connectionStatus.value = WifiConnectionStatus.Error("Permission denied: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to network", e)
            _connectionStatus.value = WifiConnectionStatus.Error("Connection failed: ${e.message}")
        }
    }
    
    // Establish socket connection to fusion node
    private fun establishSocketConnection(fusionNetwork: FusionWifiNetwork) {
        try {
            // First establish WiFi connection
            currentSocket = null
            isConnected = true
            _connectionStatus.value = WifiConnectionStatus.Connected(fusionNetwork)
            
            Log.d(TAG, "WiFi connected to ${fusionNetwork.ssid}")
            
            // Now establish TCP connection to ESP32
            // ESP32 typically uses SoftAP with IP 192.168.4.1
            val esp32IpAddress = "192.168.4.1" // Default ESP32 SoftAP IP
            Log.d(TAG, "Attempting TCP connection to ESP32 at $esp32IpAddress:$FUSION_PORT")
            
            tcpManager.connectToFusionNode(esp32IpAddress, FUSION_PORT)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish connection", e)
            _connectionStatus.value = WifiConnectionStatus.Error("Connection failed: ${e.message}")
        }
    }
    
    // Start listening for incoming messages
    private fun startMessageListener(socket: Socket) {
        executor.execute {
            try {
                val inputStream = socket.getInputStream()
                val buffer = ByteArray(4096)
                
                while (isConnected && !socket.isClosed) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        val message = buffer.copyOf(bytesRead)
                        handleReceivedMessage(message)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in message listener", e)
                disconnect()
            }
        }
    }
    
    // Handle received message
    private fun handleReceivedMessage(message: ByteArray) {
        Log.d(TAG, "WiFi received message: ${message.size} bytes")
        
        // Forward to ViewModel for processing
        messageCallback?.onMessageReceived(message)
    }
    
    // Callback interface for message handling
    interface MessageCallback {
        fun onMessageReceived(message: ByteArray)
    }
    
    private var messageCallback: MessageCallback? = null
    
    fun setMessageCallback(callback: MessageCallback?) {
        messageCallback = callback
    }
    
    // Send message via WiFi
    fun sendMessage(message: ByteArray): Boolean {
        if (!isConnected || currentSocket == null || currentSocket?.isClosed == true) {
            Log.e(TAG, "Not connected to any network")
            return false
        }
        
        return try {
            val outputStream = currentSocket?.getOutputStream()
            outputStream?.write(message)
            outputStream?.flush()
            Log.d(TAG, "Message sent successfully via WiFi")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message via WiFi", e)
            false
        }
    }
    
    // Disconnect from current network
    fun disconnect() {
        try {
            currentSocket?.close()
            currentSocket = null
            isConnected = false
            _connectionStatus.value = WifiConnectionStatus.Disconnected
            Log.d(TAG, "Disconnected from WiFi network")
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect", e)
        }
    }
    
    // Check if connected
    fun isConnected(): Boolean = isConnected
    
    // Get current connection status
    fun getConnectionStatus(): WifiConnectionStatus = _connectionStatus.value
    
    // Get discovered networks
    fun getDiscoveredNetworks(): List<FusionWifiNetwork> = _discoveredNetworks.value
    
    // Helper function to determine security type from capabilities
    private fun getSecurityType(capabilities: String): String {
        return when {
            capabilities.contains("WPA3") -> "WPA3"
            capabilities.contains("WPA2") -> "WPA2"
            capabilities.contains("WPA") -> "WPA"
            capabilities.contains("WEP") -> "WEP"
            else -> "Open"
        }
    }
    
    // Helper function to get channel from frequency
    private fun getChannelFromFrequency(frequency: Int): Int {
        return when {
            frequency >= 2412 && frequency <= 2484 -> {
                // 2.4 GHz channels
                (frequency - 2412) / 5 + 1
            }
            frequency >= 5170 && frequency <= 5825 -> {
                // 5 GHz channels
                (frequency - 5170) / 5 + 34
            }
            else -> 0
        }
    }
}

sealed class WifiConnectionStatus {
    object Disconnected : WifiConnectionStatus()
    object Connecting : WifiConnectionStatus()
    data class Connected(val network: FusionWifiNetwork) : WifiConnectionStatus()
    data class Error(val message: String) : WifiConnectionStatus()
}
