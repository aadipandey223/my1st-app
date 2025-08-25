package com.example.mine.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.*
import java.net.Socket
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

class TcpManager {
    private val TAG = "TcpManager"
    
    // State flows for connection status and received messages
    private val _connectionStatus = MutableStateFlow<TcpConnectionStatus>(TcpConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<TcpConnectionStatus> = _connectionStatus.asStateFlow()
    
    private val _receivedMessages = MutableStateFlow<List<TcpMessage>>(emptyList())
    val receivedMessages: StateFlow<List<TcpMessage>> = _receivedMessages.asStateFlow()
    
    private val _connectedNodeId = MutableStateFlow<String?>(null)
    val connectedNodeId: StateFlow<String?> = _connectedNodeId.asStateFlow()
    
    // Connection management
    private var socket: Socket? = null
    private var inputStream: BufferedReader? = null
    private var outputStream: PrintWriter? = null
    private val isConnected = AtomicBoolean(false)
    private var connectionJob: Job? = null
    
    // Configuration
    companion object {
        const val DEFAULT_PORT = 18080
        const val CONNECTION_TIMEOUT = 5000 // 5 seconds
        const val READ_TIMEOUT = 3000 // 3 seconds
    }
    
    /**
     * Connect to a fusion node via TCP
     * @param ipAddress The IP address of the ESP32 fusion node
     * @param port The port number (default: 18080)
     */
    fun connectToFusionNode(ipAddress: String, port: Int = DEFAULT_PORT) {
        if (isConnected.get()) {
            Log.w(TAG, "⚠️ Already connected to a fusion node")
            return
        }
        
        connectionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🚀 Attempting to connect to fusion node at $ipAddress:$port")
                _connectionStatus.value = TcpConnectionStatus.Connecting
                
                // Create socket with timeout
                socket = Socket()
                Log.d(TAG, "🔌 Socket created, attempting connection...")
                socket?.connect(InetSocketAddress(ipAddress, port), CONNECTION_TIMEOUT)
                socket?.soTimeout = READ_TIMEOUT
                
                if (socket?.isConnected == true) {
                    Log.d(TAG, "✅ Successfully connected to fusion node!")
                    _connectionStatus.value = TcpConnectionStatus.Connected
                    isConnected.set(true)
                    
                    // Setup streams
                    inputStream = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                    outputStream = PrintWriter(socket!!.getOutputStream(), true)
                    Log.d(TAG, "📡 Streams setup complete")
                    
                    // Start listening for messages
                    Log.d(TAG, "👂 Starting message listener...")
                    startMessageListener()
                    
                    // Send initial handshake (optional)
                    Log.d(TAG, "🤝 Sending handshake...")
                    sendHandshake()
                    
                } else {
                    Log.e(TAG, "❌ Failed to connect to fusion node - socket not connected")
                    _connectionStatus.value = TcpConnectionStatus.Failed("Connection failed")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "💥 Error connecting to fusion node: ${e.message}")
                _connectionStatus.value = TcpConnectionStatus.Failed(e.message ?: "Unknown error")
                disconnect()
            }
        }
    }
    
    /**
     * Start listening for messages from the fusion node
     */
    private fun startMessageListener() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Starting message listener")
                
                while (isConnected.get() && socket?.isConnected == true) {
                    val message = inputStream?.readLine()
                    if (message != null && message.isNotBlank()) {
                        Log.d(TAG, "Received message: $message")
                        processReceivedMessage(message)
                    }
                    
                    // Small delay to prevent busy waiting
                    delay(10)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in message listener: ${e.message}")
                if (isConnected.get()) {
                    _connectionStatus.value = TcpConnectionStatus.Failed("Connection lost: ${e.message}")
                    disconnect()
                }
            }
        }
    }
    
    /**
     * Process received messages and extract Node ID if present
     */
    private fun processReceivedMessage(message: String) {
        Log.d(TAG, "🔍 Processing message: '$message'")
        
        try {
            // Try to parse as JSON
            val json = JSONObject(message)
            val messageType = json.optString("type", "")
            
            Log.d(TAG, "📋 Message type: '$messageType'")
            
            when (messageType) {
                "NODE_ID" -> {
                    val nodeId = json.optString("id", "")
                    Log.d(TAG, "🎯 Found NODE_ID message, extracted ID: '$nodeId'")
                    
                    if (nodeId.isNotBlank()) {
                        Log.d(TAG, "✅ Successfully received Node ID: $nodeId")
                        _connectedNodeId.value = nodeId
                        
                        // Add to received messages
                        val tcpMessage = TcpMessage(
                            type = TcpMessageType.NODE_ID,
                            content = nodeId,
                            timestamp = System.currentTimeMillis(),
                            rawMessage = message
                        )
                        
                        _receivedMessages.value = _receivedMessages.value + tcpMessage
                        
                        // Update connection status
                        _connectionStatus.value = TcpConnectionStatus.ConnectedWithNodeId(nodeId)
                        Log.d(TAG, "🔄 Updated connection status to ConnectedWithNodeId")
                    } else {
                        Log.w(TAG, "⚠️ NODE_ID message received but 'id' field is empty")
                    }
                }
                "HANDSHAKE_RESPONSE" -> {
                    val nodeId = json.optString("node_id", json.optString("id", ""))
                    if (nodeId.isNotBlank()) {
                        Log.d(TAG, "✅ Received Node ID from handshake response: $nodeId")
                        _connectedNodeId.value = nodeId
                        _connectionStatus.value = TcpConnectionStatus.ConnectedWithNodeId(nodeId)
                    }
                }
                "INFO" -> {
                    val nodeId = json.optString("node_id", json.optString("id", ""))
                    if (nodeId.isNotBlank()) {
                        Log.d(TAG, "✅ Received Node ID from info message: $nodeId")
                        _connectedNodeId.value = nodeId
                        _connectionStatus.value = TcpConnectionStatus.ConnectedWithNodeId(nodeId)
                    }
                }
                else -> {
                    Log.d(TAG, "📝 Other message type: '$messageType'")
                    // Handle other message types
                    val tcpMessage = TcpMessage(
                        type = TcpMessageType.OTHER,
                        content = message,
                        timestamp = System.currentTimeMillis(),
                        rawMessage = message
                    )
                    _receivedMessages.value = _receivedMessages.value + tcpMessage
                }
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "❌ Failed to parse message as JSON: '$message', Error: ${e.message}")
            // Handle as plain text message - try to extract node ID from plain text
            val extractedNodeId = extractNodeIdFromPlainText(message)
            if (extractedNodeId.isNotBlank()) {
                Log.d(TAG, "✅ Extracted Node ID from plain text: $extractedNodeId")
                _connectedNodeId.value = extractedNodeId
                _connectionStatus.value = TcpConnectionStatus.ConnectedWithNodeId(extractedNodeId)
            }
            
            // Add to received messages
            val tcpMessage = TcpMessage(
                type = TcpMessageType.PLAIN_TEXT,
                content = message,
                timestamp = System.currentTimeMillis(),
                rawMessage = message
            )
            _receivedMessages.value = _receivedMessages.value + tcpMessage
        }
    }
    
    /**
     * Extract node ID from plain text message
     */
    private fun extractNodeIdFromPlainText(message: String): String {
        val patterns = listOf(
            Regex("Node ID:\\s*([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE),
            Regex("NodeID:\\s*([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE),
            Regex("ID:\\s*([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE),
            Regex("node_id:\\s*([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE),
            Regex("nodeid:\\s*([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE),
            Regex("FusionNode[_-]?([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE),
            Regex("ESP32[_-]?([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(message)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        
        return ""
    }
    
    /**
     * Send a handshake message to the fusion node
     */
    private fun sendHandshake() {
        try {
            val handshake = JSONObject().apply {
                put("type", "HANDSHAKE")
                put("client", "android")
                put("version", "1.0")
            }
            
            outputStream?.println(handshake.toString())
            Log.d(TAG, "Sent handshake: ${handshake}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending handshake: ${e.message}")
        }
    }
    
    /**
     * Send a message to the fusion node
     */
    fun sendMessage(message: String): Boolean {
        return try {
            if (isConnected.get() && outputStream != null) {
                outputStream?.println(message)
                Log.d(TAG, "Sent message: $message")
                true
            } else {
                Log.w(TAG, "Cannot send message: not connected")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message: ${e.message}")
            false
        }
    }
    
    /**
     * Disconnect from the fusion node
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting from fusion node")
        
        isConnected.set(false)
        connectionJob?.cancel()
        
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing streams: ${e.message}")
        }
        
        // Reset state
        inputStream = null
        outputStream = null
        socket = null
        _connectionStatus.value = TcpConnectionStatus.Disconnected
        _connectedNodeId.value = null
        
        Log.d(TAG, "Disconnected from fusion node")
    }
    
    /**
     * Check if currently connected
     */
    fun isConnected(): Boolean = isConnected.get()
    
    /**
     * Get the current Node ID if connected
     */
    fun getCurrentNodeId(): String? = _connectedNodeId.value
    
    /**
     * Clear received messages
     */
    fun clearMessages() {
        _receivedMessages.value = emptyList()
    }
}

// Data classes for TCP communication
sealed class TcpConnectionStatus {
    object Disconnected : TcpConnectionStatus()
    object Connecting : TcpConnectionStatus()
    object Connected : TcpConnectionStatus()
    data class ConnectedWithNodeId(val nodeId: String) : TcpConnectionStatus()
    data class Failed(val error: String) : TcpConnectionStatus()
}

enum class TcpMessageType {
    NODE_ID,
    OTHER,
    PLAIN_TEXT
}

data class TcpMessage(
    val type: TcpMessageType,
    val content: String,
    val timestamp: Long,
    val rawMessage: String
)
