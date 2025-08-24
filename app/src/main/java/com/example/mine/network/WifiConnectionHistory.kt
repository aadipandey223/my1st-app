package com.example.mine.network

/**
 * Data class representing a WiFi connection history entry
 * Contains information about previously connected WiFi networks
 */
data class WifiConnectionHistory(
    val ssid: String,                    // Network SSID
    val bssid: String,                   // Network BSSID (MAC address)
    val securityType: String,            // Security type (WPA, WPA2, Open, etc.)
    val lastConnected: Long,             // Timestamp of last connection
    val connectionCount: Int,            // Number of times connected
    val signalStrength: Int,             // Last known signal strength in dBm
    val isCurrentlyConnected: Boolean    // Whether currently connected to this network
) {
    /**
     * Get formatted last connected time
     */
    fun getFormattedLastConnected(): String {
        val now = System.currentTimeMillis()
        val diff = now - lastConnected
        
        return when {
            diff < 60 * 1000 -> "Just now"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} minutes ago"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} hours ago"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)} days ago"
            else -> "${diff / (7 * 24 * 60 * 60 * 1000)} weeks ago"
        }
    }
    
    /**
     * Get signal strength as bars (1-5)
     */
    fun getSignalBars(): Int {
        return when {
            signalStrength >= -50 -> 5
            signalStrength >= -60 -> 4
            signalStrength >= -70 -> 3
            signalStrength >= -80 -> 2
            signalStrength >= -90 -> 1
            else -> 0
        }
    }
    
    /**
     * Get signal strength color
     */
    fun getSignalColor(): String {
        return when {
            signalStrength >= -50 -> "Excellent"
            signalStrength >= -70 -> "Good"
            signalStrength >= -80 -> "Fair"
            else -> "Poor"
        }
    }
}
