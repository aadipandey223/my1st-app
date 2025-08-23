package com.example.mine.network

data class FusionWifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val capabilities: String,
    val securityType: String,
    val channel: Int,
    val isFusionNode: Boolean = false
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
    
    // Get security icon
    fun getSecurityIcon(): String {
        return when (securityType) {
            "WPA3" -> "🔒"
            "WPA2" -> "🔒"
            "WPA" -> "🔒"
            "WEP" -> "🔓"
            "Open" -> "🌐"
            else -> "❓"
        }
    }
    
    // Get security description
    fun getSecurityDescription(): String {
        return when (securityType) {
            "WPA3" -> "Very Secure (WPA3)"
            "WPA2" -> "Secure (WPA2)"
            "WPA" -> "Secure (WPA)"
            "WEP" -> "Weak (WEP)"
            "Open" -> "No Security"
            else -> "Unknown Security"
        }
    }
    
    // Get band information
    fun getBand(): String {
        return when {
            frequency in 2412..2484 -> "2.4 GHz"
            frequency in 5170..5825 -> "5 GHz"
            else -> "Unknown"
        }
    }
    
    // Get display name
    fun getDisplayName(): String {
        return if (ssid.isNotEmpty()) {
            ssid
        } else {
            "Hidden Network"
        }
    }
    
    // Get network details for display
    fun getNetworkDetails(): String {
        return buildString {
            append("SSID: ${getDisplayName()}\n")
            append("BSSID: $bssid\n")
            append("Signal: $rssi dBm (${getSignalStrength()})\n")
            append("Security: ${getSecurityDescription()}\n")
            append("Band: ${getBand()}\n")
            append("Channel: $channel\n")
            append("Frequency: $frequency MHz")
            if (isFusionNode) {
                append("\n🌟 Potential Fusion Node")
            }
        }
    }
    
    // Check if network is secure
    fun isSecure(): Boolean {
        return securityType !in listOf("Open", "Unknown")
    }
    
    // Check if network is recommended (strong signal + secure)
    fun isRecommended(): Boolean {
        return rssi >= -70 && isSecure()
    }
}
