package com.example.mine.network

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiDiscoveryManager(private val context: Context) {
    
    private val wifiManager: WifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    
    private val _discoveredNetworks = MutableStateFlow<List<FusionWifiNetwork>>(emptyList())
    val discoveredNetworks: StateFlow<List<FusionWifiNetwork>> = _discoveredNetworks.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private var scanReceiver: BroadcastReceiver? = null
    private val discoveredNetworksList = mutableListOf<FusionWifiNetwork>()
    
    companion object {
        private const val TAG = "WifiDiscoveryManager"
        private const val SCAN_TIMEOUT = 15000L // 15 seconds
    }
    
    // Check if WiFi is supported
    fun isWifiSupported(): Boolean {
        return wifiManager != null
    }
    
    // Check if WiFi is enabled
    fun isWifiEnabled(): Boolean {
        return wifiManager.isWifiEnabled
    }
    
    // Check if we have required permissions
    fun hasRequiredPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED &&
               ActivityCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
    }
    
    // Start WiFi network scanning
    fun startScan() {
        if (!isWifiSupported()) {
            _errorMessage.value = "WiFi is not supported on this device"
            return
        }
        
        if (!isWifiEnabled()) {
            _errorMessage.value = "WiFi is disabled. Please enable WiFi."
            return
        }
        
        if (!hasRequiredPermissions()) {
            _errorMessage.value = "WiFi permissions required for scanning"
            return
        }
        
        try {
            // Clear previous results
            discoveredNetworksList.clear()
            _discoveredNetworks.value = emptyList()
            _errorMessage.value = null
            
            // Register receiver for scan results
            registerScanReceiver()
            
            // Start scanning
            val scanStarted = wifiManager.startScan()
            if (scanStarted) {
                _isScanning.value = true
                Log.d(TAG, "WiFi scan started")
                
                // Stop scanning after timeout
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    stopScan()
                }, SCAN_TIMEOUT)
            } else {
                _errorMessage.value = "Failed to start WiFi scan"
                Log.e(TAG, "Failed to start WiFi scan")
            }
        } catch (e: Exception) {
            _errorMessage.value = "Error starting WiFi scan: ${e.message}"
            Log.e(TAG, "Error starting WiFi scan", e)
        }
    }
    
    // Stop WiFi scanning
    fun stopScan() {
        try {
            _isScanning.value = false
            Log.d(TAG, "WiFi scan stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping WiFi scan", e)
        }
    }
    
    // Register broadcast receiver for scan results
    private fun registerScanReceiver() {
        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                        handleScanResults()
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        }
        
        context.registerReceiver(scanReceiver, filter)
    }
    
    // Handle scan results
    private fun handleScanResults() {
        try {
            val scanResults = wifiManager.scanResults
            Log.d(TAG, "WiFi scan completed. Found ${scanResults.size} networks")
            
            discoveredNetworksList.clear()
            
            scanResults.forEach { scanResult ->
                val fusionNetwork = createFusionWifiNetwork(scanResult)
                discoveredNetworksList.add(fusionNetwork)
            }
            
            // Sort by signal strength (strongest first)
            discoveredNetworksList.sortByDescending { it.rssi }
            
            _discoveredNetworks.value = discoveredNetworksList.toList()
            _isScanning.value = false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling scan results", e)
            _errorMessage.value = "Error processing scan results: ${e.message}"
            _isScanning.value = false
        }
    }
    
    // Create FusionWifiNetwork from ScanResult
    private fun createFusionWifiNetwork(scanResult: ScanResult): FusionWifiNetwork {
        return FusionWifiNetwork(
            ssid = scanResult.SSID,
            bssid = scanResult.BSSID,
            rssi = scanResult.level,
            frequency = scanResult.frequency,
            capabilities = scanResult.capabilities,
            securityType = getSecurityType(scanResult.capabilities),
            channel = getChannelFromFrequency(scanResult.frequency),
            isFusionNode = isFusionNode(scanResult.SSID, scanResult.capabilities)
        )
    }
    
    // Determine security type from capabilities
    private fun getSecurityType(capabilities: String): String {
        return when {
            capabilities.contains("WPA3") -> "WPA3"
            capabilities.contains("WPA2") -> "WPA2"
            capabilities.contains("WPA") -> "WPA"
            capabilities.contains("WEP") -> "WEP"
            capabilities.contains("ESS") -> "Open"
            else -> "Unknown"
        }
    }
    
    // Get channel from frequency
    private fun getChannelFromFrequency(frequency: Int): Int {
        return when (frequency) {
            in 2412..2484 -> (frequency - 2412) / 5 + 1
            in 5170..5825 -> (frequency - 5170) / 5 + 34
            else -> 0
        }
    }
    
    // Check if this might be a fusion node (custom logic)
    private fun isFusionNode(ssid: String, capabilities: String): Boolean {
        // This is a heuristic - you can customize this logic
        val fusionKeywords = listOf("fusion", "node", "mesh", "router", "gateway")
        val ssidLower = ssid.lowercase()
        
        return fusionKeywords.any { keyword ->
            ssidLower.contains(keyword)
        } || capabilities.contains("WPA2") // Most fusion nodes use WPA2
    }
    
    // Connect to a specific network
    fun connectToNetwork(network: FusionWifiNetwork, password: String? = null): Boolean {
        // This would implement the actual connection logic
        // For now, just return true to simulate successful connection
        Log.d(TAG, "Connecting to network: ${network.ssid} (${network.bssid})")
        return true
    }
    
    // Get current scan status
    fun getScanStatus(): String {
        return when {
            !isWifiSupported() -> "WiFi not supported"
            !isWifiEnabled() -> "WiFi disabled"
            !hasRequiredPermissions() -> "Permissions required"
            isScanning.value -> "Scanning networks..."
            discoveredNetworksList.isEmpty() -> "No networks found"
            else -> "Found ${discoveredNetworksList.size} networks"
        }
    }
    
    // Get network details for display
    fun getNetworkDetails(network: FusionWifiNetwork): String {
        return buildString {
            append("SSID: ${network.ssid}\n")
            append("BSSID: ${network.bssid}\n")
            append("Signal: ${network.rssi} dBm\n")
            append("Security: ${network.securityType}\n")
            append("Channel: ${network.channel}\n")
            append("Frequency: ${network.frequency} MHz")
        }
    }
    
    // Clean up resources
    fun cleanup() {
        try {
            stopScan()
            scanReceiver?.let { receiver ->
                context.unregisterReceiver(receiver)
                scanReceiver = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}
