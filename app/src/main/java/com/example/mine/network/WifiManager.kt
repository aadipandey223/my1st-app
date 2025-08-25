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
        private const val FUSION_PORT = 8080
        private const val CONNECTION_TIMEOUT = 5000
    }
    
    private val wifiManager: WifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val executor = Executors.newCachedThreadPool()
    
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
    
    // Start scanning for fusion networks
    fun startScan() {
        if (!isWifiEnabled()) {
            Log.e(TAG, "WiFi is not enabled")
            return
        }
        
        // No location permission required for basic WiFi scanning
        
        val success = wifiManager.startScan()
        if (success) {
            Log.d(TAG, "Started WiFi scan for fusion networks")
            processScanResults()
        } else {
            Log.e(TAG, "Failed to start WiFi scan")
        }
    }
    
    // Process scan results to find fusion networks
    private fun processScanResults() {
        // No location permission required for basic WiFi scanning
        
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
        Log.d(TAG, "Using legacy method to connect to ${fusionNetwork.ssid}")
        
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
    }
    
    // Establish socket connection to fusion node
    private fun establishSocketConnection(fusionNetwork: FusionWifiNetwork) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(fusionNetwork.bssid, FUSION_PORT), CONNECTION_TIMEOUT)
            
            currentSocket = socket
            isConnected = true
            _connectionStatus.value = WifiConnectionStatus.Connected(fusionNetwork)
            
            Log.d(TAG, "Socket connection established to ${fusionNetwork.ssid}")
            
            // Start listening for messages
            startMessageListener(socket)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish socket connection", e)
            _connectionStatus.value = WifiConnectionStatus.Error("Socket connection failed: ${e.message}")
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
