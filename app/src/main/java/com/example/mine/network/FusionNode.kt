package com.example.mine.network

data class FusionNode(
    val name: String,
    val address: String,
    val rssi: Int,
    val deviceType: String = "Unknown",
    val isPaired: Boolean = false,
    val isConnected: Boolean = false
) {
    // Get signal strength description
    fun getSignalStrength(): String {
        return when {
            rssi >= -50 -> "Excellent"
            rssi >= -60 -> "Very Good"
            rssi >= -70 -> "Good"
            rssi >= -80 -> "Fair"
            rssi >= -90 -> "Poor"
            else -> "Very Poor"
        }
    }
    
    // Get signal strength color
    fun getSignalColor(): String {
        return when {
            rssi >= -50 -> "#10B981" // Green
            rssi >= -60 -> "#10B981" // Green
            rssi >= -70 -> "#F59E0B" // Yellow
            rssi >= -80 -> "#F59E0B" // Yellow
            rssi >= -90 -> "#EF4444" // Red
            else -> "#EF4444" // Red
        }
    }
    
    // Get display name
    fun getDisplayName(): String {
        return if (name.isNotEmpty() && name != "Unknown Device") {
            name
        } else {
            "Device ${address.takeLast(6)}"
        }
    }
    
    // Get connection status text
    fun getConnectionStatus(): String {
        return when {
            isConnected -> "Connected"
            isPaired -> "Paired"
            else -> "Available"
        }
    }
}
