package com.example.mine.network

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BluetoothDiscoveryManager(private val context: Context) {
    
    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    private val _discoveredDevices = MutableStateFlow<List<FusionNode>>(emptyList())
    val discoveredDevices: StateFlow<List<FusionNode>> = _discoveredDevices.asStateFlow()
    
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val discoveredDevicesList = mutableListOf<FusionNode>()
    private var isScanning = false
    
    companion object {
        private const val TAG = "BluetoothDiscoveryManager"
        private const val DISCOVERY_TIMEOUT = 12000L // 12 seconds
    }
    
    // Check if Bluetooth is supported
    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }
    
    // Check if BLE is supported
    fun isBleSupported(): Boolean {
        return bluetoothLeScanner != null
    }
    
    // Check if Bluetooth is enabled
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    // Check if we have required permissions
    fun hasRequiredPermissions(): Boolean {
        val hasScanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No scan permission required for older versions
        }
        
        val hasConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No connect permission required for older versions
        }
        
        val hasLocationPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        Log.d(TAG, "Permission check - Scan: $hasScanPermission, Connect: $hasConnectPermission, Location: $hasLocationPermission")
        
        return hasScanPermission && hasConnectPermission && hasLocationPermission
    }
    
    // Modern BLE scan callback
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            
            val device = result.device
            val rssi = result.rssi
            
            Log.d(TAG, "Scan result: ${device.address} (${device.name ?: "Unknown"}) RSSI: $rssi")
            
            handleDeviceFound(device, rssi)
        }
        
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
            _errorMessage.value = "BLE scan failed: $errorCode"
            _isDiscovering.value = false
            isScanning = false
        }
    }
    
    // Start Bluetooth device discovery using BLE
    fun startDiscovery() {
        Log.d(TAG, "Starting BLE discovery...")
        
        if (!isBluetoothSupported()) {
            _errorMessage.value = "Bluetooth is not supported on this device"
            Log.e(TAG, "Bluetooth not supported")
            return
        }
        
        if (!isBluetoothEnabled()) {
            _errorMessage.value = "Bluetooth is disabled. Please enable Bluetooth."
            Log.e(TAG, "Bluetooth disabled")
            return
        }
        
        if (!isBleSupported()) {
            _errorMessage.value = "BLE not supported on this device"
            Log.e(TAG, "BLE not supported")
            return
        }
        
        if (!hasRequiredPermissions()) {
            _errorMessage.value = "Bluetooth permissions required for scanning"
            Log.e(TAG, "Missing required permissions")
            return
        }
        
        if (bluetoothLeScanner == null) {
            _errorMessage.value = "BLE scanner not available"
            Log.e(TAG, "BLE scanner is null")
            return
        }
        
        if (isScanning) {
            Log.d(TAG, "Scan already in progress")
            return
        }
        
        try {
            // Clear previous results
            discoveredDevicesList.clear()
            _discoveredDevices.value = emptyList()
            _errorMessage.value = null
            
            Log.d(TAG, "Starting BLE scan with low latency mode...")
            
            // Configure scan settings for better discovery
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0) // Report results immediately
                .build()
            
            // Start BLE scan
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothLeScanner.startScan(null, scanSettings, scanCallback)
                isScanning = true
                _isDiscovering.value = true
                Log.d(TAG, "BLE discovery started successfully")
                
                // Stop discovery after timeout
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "Discovery timeout reached, stopping scan")
                    stopDiscovery()
                }, DISCOVERY_TIMEOUT)
            } else {
                _errorMessage.value = "BLUETOOTH_SCAN permission not granted"
                Log.e(TAG, "BLUETOOTH_SCAN permission not granted")
            }
        } catch (e: Exception) {
            _errorMessage.value = "Error starting BLE discovery: ${e.message}"
            Log.e(TAG, "Error starting BLE discovery", e)
        }
    }
    
    // Stop Bluetooth device discovery
    fun stopDiscovery() {
        try {
            if (isScanning && bluetoothLeScanner != null) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothLeScanner.stopScan(scanCallback)
                }
            }
            isScanning = false
            _isDiscovering.value = false
            Log.d(TAG, "BLE discovery stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE discovery", e)
        }
    }
    
    // Handle discovered device
    private fun handleDeviceFound(device: BluetoothDevice, rssi: Int) {
        try {
            val deviceName = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    "Unknown Device"
                } else {
                    device.name ?: "Unknown Device"
                }
            } catch (e: SecurityException) {
                "Unknown Device"
            }
            
            val deviceAddress = device.address
            
            val isPaired = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    false
                } else {
                    device.bondState == BluetoothDevice.BOND_BONDED
                }
            } catch (e: SecurityException) {
                false
            }
            
            // Check if device is already in the list
            val existingDevice = discoveredDevicesList.find { it.address == deviceAddress }
            if (existingDevice == null) {
                val fusionNode = FusionNode(
                    name = deviceName,
                    address = deviceAddress,
                    rssi = rssi,
                    deviceType = getDeviceType(device),
                    isPaired = isPaired,
                    isConnected = false
                )
                
                discoveredDevicesList.add(fusionNode)
                _discoveredDevices.value = discoveredDevicesList.toList()
                
                Log.d(TAG, "Found device: $deviceName ($deviceAddress) RSSI: $rssi")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling discovered device", e)
        }
    }
    
    // Get device type based on device class
    private fun getDeviceType(device: BluetoothDevice): String {
        return try {
            val deviceClass = device.bluetoothClass
            when {
                deviceClass.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.COMPUTER -> "Computer"
                deviceClass.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.PHONE -> "Phone"
                deviceClass.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio/Video"
                deviceClass.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.WEARABLE -> "Wearable"
                deviceClass.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.TOY -> "Toy"
                deviceClass.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.HEALTH -> "Health"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    // Connect to a specific device
    fun connectToDevice(device: FusionNode): Boolean {
        // This would implement the actual connection logic
        // For now, just return true to simulate successful connection
        Log.d(TAG, "Connecting to device: ${device.name} (${device.address})")
        return true
    }
    
    // Clean up resources
    fun cleanup() {
        try {
            stopDiscovery()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    // Get current discovery status
    fun getDiscoveryStatus(): String {
        return when {
            !isBluetoothSupported() -> "Bluetooth not supported"
            !isBluetoothEnabled() -> "Bluetooth disabled"
            !isBleSupported() -> "BLE not supported"
            !hasRequiredPermissions() -> "Permissions required"
            bluetoothLeScanner == null -> "BLE scanner not available"
            isDiscovering.value -> "Discovering devices..."
            discoveredDevicesList.isEmpty() -> "No devices found"
            else -> "Found ${discoveredDevicesList.size} devices"
        }
    }
    
    // Get detailed status for debugging
    fun getDetailedStatus(): String {
        return buildString {
            append("Bluetooth Supported: ${isBluetoothSupported()}\n")
            append("Bluetooth Enabled: ${isBluetoothEnabled()}\n")
            append("BLE Supported: ${isBleSupported()}\n")
            append("BLE Scanner Available: ${bluetoothLeScanner != null}\n")
            append("Has Required Permissions: ${hasRequiredPermissions()}\n")
            append("Is Discovering: ${isDiscovering.value}\n")
            append("Is Scanning: $isScanning\n")
            append("Discovered Devices: ${discoveredDevicesList.size}")
        }
    }
}
