package com.example.mine.network

/**
 * Data class representing WiFi usage statistics
 * Contains aggregated information about WiFi usage patterns
 */
data class WifiUsageStatistics(
    val totalNetworks: Int,                                    // Total number of networks in history
    val currentlyConnected: String,                           // Currently connected network SSID
    val mostFrequentNetwork: String,                          // Most frequently connected network
    val totalConnectionTime: Long,                            // Total connection time in milliseconds
    val averageSignalStrength: Double,                        // Average signal strength across all networks
    val networksBySecurityType: Map<String, List<WifiConnectionHistory>>, // Networks grouped by security type
    val lastConnectedNetwork: String                          // Last connected network SSID
) {
    /**
     * Get formatted total connection time
     */
    fun getFormattedTotalConnectionTime(): String {
        val hours = totalConnectionTime / (60 * 60 * 1000)
        val minutes = (totalConnectionTime % (60 * 60 * 1000)) / (60 * 1000)
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "Less than 1m"
        }
    }
    
    /**
     * Get average signal strength as bars (1-5)
     */
    fun getAverageSignalBars(): Int {
        return when {
            averageSignalStrength >= -50 -> 5
            averageSignalStrength >= -60 -> 4
            averageSignalStrength >= -70 -> 3
            averageSignalStrength >= -80 -> 2
            averageSignalStrength >= -90 -> 1
            else -> 0
        }
    }
    
    /**
     * Get most common security type
     */
    fun getMostCommonSecurityType(): String {
        return networksBySecurityType.maxByOrNull { it.value.size }?.key ?: "Unknown"
    }
    
    /**
     * Get number of networks by security type
     */
    fun getNetworkCountBySecurityType(securityType: String): Int {
        return networksBySecurityType[securityType]?.size ?: 0
    }
    
    /**
     * Get percentage of networks with good signal strength
     */
    fun getGoodSignalPercentage(): Double {
        val totalNetworks = networksBySecurityType.values.flatten().size
        if (totalNetworks == 0) return 0.0
        
        val goodSignalNetworks = networksBySecurityType.values.flatten()
            .count { it.signalStrength >= -70 }
        
        return (goodSignalNetworks.toDouble() / totalNetworks) * 100
    }
    
    /**
     * Get connection frequency score (0-100)
     */
    fun getConnectionFrequencyScore(): Int {
        // This is a simplified scoring system
        // In a real implementation, you'd use more sophisticated metrics
        return when {
            totalNetworks >= 20 -> 100
            totalNetworks >= 15 -> 80
            totalNetworks >= 10 -> 60
            totalNetworks >= 5 -> 40
            totalNetworks >= 2 -> 20
            else -> 0
        }
    }
}
