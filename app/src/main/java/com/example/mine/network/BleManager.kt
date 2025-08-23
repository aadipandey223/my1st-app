package com.example.mine.network

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
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
import java.util.UUID

class BleManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BleManager"
        
        // Fusion node service UUID
        private val FUSION_SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
        
        // Characteristic UUIDs for communication
        private val MESSAGE_CHARACTERISTIC_UUID = UUID.fromString("87654321-4321-4321-4321-cba987654321")
        private val STATUS_CHARACTERISTIC_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")
        
        // BLE scan settings
        private const val SCAN_PERIOD_MS = 10000L
    }
    
    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    private var bluetoothGatt: BluetoothGatt? = null
    private var isConnected = false
    private var isScanning = false
    
    // State flows
    private val _discoveredDevices = MutableStateFlow<List<FusionNode>>(emptyList())
    val discoveredDevices: StateFlow<List<FusionNode>> = _discoveredDevices.asStateFlow()
    
    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    
    private val _receivedMessages = MutableStateFlow<List<ByteArray>>(emptyList())
    val receivedMessages: StateFlow<List<ByteArray>> = _receivedMessages.asStateFlow()
    
    // Callback for GATT operations
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to GATT server")
                    isConnected = true
                    _connectionStatus.value = ConnectionStatus.Connected
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from GATT server")
                    isConnected = false
                    _connectionStatus.value = ConnectionStatus.Disconnected
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "GATT services discovered")
                setupCharacteristicNotifications(gatt)
            } else {
                Log.e(TAG, "GATT service discovery failed: $status")
            }
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            when (characteristic.uuid) {
                MESSAGE_CHARACTERISTIC_UUID -> {
                    Log.d(TAG, "Received message: ${value.size} bytes")
                    handleReceivedMessage(value)
                }
                STATUS_CHARACTERISTIC_UUID -> {
                    Log.d(TAG, "Status update: ${value.contentToString()}")
                }
            }
        }
        
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Message sent successfully")
            } else {
                Log.e(TAG, "Failed to send message: $status")
            }
        }
    }
    
    // Modern BLE scan callback for Android 5.0+ (API 21+)
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            
            val device = result.device
            val rssi = result.rssi
            val scanRecord = result.scanRecord
            
            // Check if this is a fusion node by looking for our service UUID in scan record
            if (isFusionNode(scanRecord?.bytes)) {
                val fusionNode = FusionNode(
                    device = device,
                    rssi = rssi,
                    name = device.name ?: "Unknown Device",
                    address = device.address
                )
                
                addDiscoveredDevice(fusionNode)
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
            isScanning = false
        }
    }
    
    // Check if BLE is supported and enabled
    fun isBleSupported(): Boolean {
        return bluetoothLeScanner != null
    }
    
    fun isBleEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    // Start scanning for fusion nodes
    fun startScan() {
        if (!isBleSupported() || !isBleEnabled()) {
            Log.e(TAG, "BLE not supported or not enabled")
            return
        }
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_SCAN permission not granted")
            return
        }
        
        if (isScanning) {
            Log.d(TAG, "Scan already in progress")
            return
        }
        
        // Clear previous discoveries
        _discoveredDevices.value = emptyList()
        
        // Configure scan settings for better performance
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        // Start scanning
        bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
        isScanning = true
        
        Log.d(TAG, "Started BLE scan for fusion nodes")
        
        // Stop scan after period
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            stopScan()
        }, SCAN_PERIOD_MS)
    }
    
    // Stop scanning
    fun stopScan() {
        if (isScanning && bluetoothLeScanner != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothLeScanner.stopScan(scanCallback)
                isScanning = false
                Log.d(TAG, "Stopped BLE scan")
            }
        }
    }
    
    // Check if device is a fusion node
    private fun isFusionNode(scanRecord: ByteArray?): Boolean {
        if (scanRecord == null) return false
        
        // Look for our service UUID in the scan record
        // This is a simplified check - in real implementation, you'd parse the scan record properly
        val serviceUuidString = FUSION_SERVICE_UUID.toString()
        return scanRecord.any { it.toChar() == serviceUuidString.first() }
    }
    
    // Add discovered device to list
    private fun addDiscoveredDevice(fusionNode: FusionNode) {
        val currentList = _discoveredDevices.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.address == fusionNode.address }
        
        if (existingIndex >= 0) {
            // Update existing device with new RSSI
            currentList[existingIndex] = fusionNode
        } else {
            // Add new device
            currentList.add(fusionNode)
        }
        
        _discoveredDevices.value = currentList
        Log.d(TAG, "Discovered fusion node: ${fusionNode.name} (${fusionNode.address})")
    }
    
    // Connect to a fusion node
    fun connectToDevice(fusionNode: FusionNode) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted")
            return
        }
        
        _connectionStatus.value = ConnectionStatus.Connecting
        
        // Use autoConnect = false for faster connection
        bluetoothGatt = fusionNode.device.connectGatt(context, false, gattCallback)
        Log.d(TAG, "Attempting to connect to ${fusionNode.name}")
    }
    
    // Disconnect from current device
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isConnected = false
        _connectionStatus.value = ConnectionStatus.Disconnected
        Log.d(TAG, "Disconnected from device")
    }
    
    // Send message to connected fusion node
    fun sendMessage(message: ByteArray): Boolean {
        if (!isConnected || bluetoothGatt == null) {
            Log.e(TAG, "Not connected to any device")
            return false
        }
        
        val service = bluetoothGatt?.getService(FUSION_SERVICE_UUID)
        val characteristic = service?.getCharacteristic(MESSAGE_CHARACTERISTIC_UUID)
        
        if (characteristic == null) {
            Log.e(TAG, "Message characteristic not found")
            return false
        }
        
        characteristic.value = message
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            return bluetoothGatt?.writeCharacteristic(characteristic) == true
        }
        
        return false
    }
    
    // Setup characteristic notifications
    private fun setupCharacteristicNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(FUSION_SERVICE_UUID)
        val messageCharacteristic = service?.getCharacteristic(MESSAGE_CHARACTERISTIC_UUID)
        val statusCharacteristic = service?.getCharacteristic(STATUS_CHARACTERISTIC_UUID)
        
        // Enable notifications for message characteristic
        messageCharacteristic?.let { characteristic ->
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            )
            descriptor.value = byteArrayOf(0x01, 0x00)
            gatt.writeDescriptor(descriptor)
        }
        
        // Enable notifications for status characteristic
        statusCharacteristic?.let { characteristic ->
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            )
            descriptor.value = byteArrayOf(0x01, 0x00)
            gatt.writeDescriptor(descriptor)
        }
    }
    
    // Handle received message
    private fun handleReceivedMessage(message: ByteArray) {
        val currentMessages = _receivedMessages.value.toMutableList()
        currentMessages.add(message)
        _receivedMessages.value = currentMessages
    }
    
    // Get current connection status
    fun getConnectionStatus(): ConnectionStatus = _connectionStatus.value
    
    // Check if connected
    fun isConnected(): Boolean = isConnected
    
    // Check if scanning
    fun isScanning(): Boolean = isScanning
}

data class FusionNode(
    val device: BluetoothDevice,
    val rssi: Int,
    val name: String,
    val address: String
)

sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    object Connecting : ConnectionStatus()
    object Connected : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}
