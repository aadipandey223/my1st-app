package com.example.mine.network

/**
 * Enhanced WiFi information using additional Android system APIs
 */
data class EnhancedWifiInformation(
    val deviceInfo: DeviceWifiInfo,
    val currentConnection: CurrentConnectionDetails?,
    val networkCapabilities: NetworkCapabilities?,
    val wifiState: String,
    val scanResults: List<ScanResultInfo>,
    val connectionHistory: List<WifiConnectionHistory>
)

/**
 * Device WiFi information
 */
data class DeviceWifiInfo(
    val isWifiEnabled: Boolean,
    val isWifiSupported: Boolean,
    val hasRequiredPermissions: Boolean,
    val wifiState: String,
    val deviceName: String,
    val androidVersion: String,
    val wifiHardware: WifiHardwareInfo
)

/**
 * Current connection details
 */
data class CurrentConnectionDetails(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val linkSpeed: Int,
    val frequency: Int,
    val ipAddress: Int,
    val networkId: Int,
    val hiddenSSID: Boolean
) {
    /**
     * Get formatted IP address
     */
    fun getFormattedIpAddress(): String {
        return "${(ipAddress and 0xFF)}.${(ipAddress shr 8 and 0xFF)}.${(ipAddress shr 16 and 0xFF)}.${(ipAddress shr 24 and 0xFF)}"
    }
    
    /**
     * Get frequency band
     */
    fun getFrequencyBand(): String {
        return when {
            frequency >= 2400 && frequency <= 2500 -> "2.4 GHz"
            frequency >= 5000 && frequency <= 6000 -> "5 GHz"
            frequency >= 6000 && frequency <= 7000 -> "6 GHz"
            else -> "Unknown"
        }
    }
}

/**
 * Network capabilities
 */
data class NetworkCapabilities(
    val hasInternet: Boolean,
    val hasValidated: Boolean,
    val bandwidth: Int,
    val transportTypes: List<String>,
    val signalStrength: Int
)

/**
 * Scan result information
 */
data class ScanResultInfo(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val capabilities: String,
    val channel: Int,
    val timestamp: Long
) {
    /**
     * Get security type from capabilities
     */
    fun getSecurityType(): String {
        return when {
            capabilities.contains("WPA3") -> "WPA3"
            capabilities.contains("WPA2") -> "WPA2"
            capabilities.contains("WPA") -> "WPA"
            capabilities.contains("WEP") -> "WEP"
            capabilities.contains("ESS") -> "Open"
            else -> "Unknown"
        }
    }
    
    /**
     * Get frequency band
     */
    fun getFrequencyBand(): String {
        return when {
            frequency >= 2400 && frequency <= 2500 -> "2.4 GHz"
            frequency >= 5000 && frequency <= 6000 -> "5 GHz"
            frequency >= 6000 && frequency <= 7000 -> "6 GHz"
            else -> "Unknown"
        }
    }
}

/**
 * WiFi hardware information
 */
data class WifiHardwareInfo(
    val wifiStandard: String,
    val maxSupportedChannels: Int,
    val supports5GHz: Boolean,
    val supports6GHz: Boolean
) {
    /**
     * Get supported frequency bands
     */
    fun getSupportedBands(): List<String> {
        val bands = mutableListOf<String>()
        bands.add("2.4 GHz") // All devices support 2.4 GHz
        if (supports5GHz) bands.add("5 GHz")
        if (supports6GHz) bands.add("6 GHz")
        return bands
    }
    
    /**
     * Get WiFi standard version
     */
    fun getWifiStandardVersion(): String {
        return when (wifiStandard) {
            "802.11ax" -> "Wi-Fi 6"
            "802.11ac" -> "Wi-Fi 5"
            "802.11n" -> "Wi-Fi 4"
            "802.11g" -> "Wi-Fi 3"
            "802.11b" -> "Wi-Fi 2"
            "802.11a" -> "Wi-Fi 1"
            else -> wifiStandard
        }
    }
}
