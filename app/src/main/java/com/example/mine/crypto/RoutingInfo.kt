package com.example.mine.crypto

/**
 * LEGACY: Data class representing routing information extracted from message payloads
 * 
 * NOTE: This is kept for backward compatibility. The new simplified payload structure
 * [fusion_id_len | fusion_id | encrypted_message] doesn't use complex routing info.
 * 
 * New implementations should focus on:
 * - fusionNode ID for routing
 * - encrypted message for content
 */
data class RoutingInfo(
    val sourceNode: String,         // Source Fusion Node ID (legacy)
    val targetNode: String,         // Target Fusion Node ID (legacy)
    val peerDeviceInfo: PeerDeviceInfo? = null  // Peer device information if available (legacy)
) {
    /**
     * Check if this is a cross-node message (different source and target nodes)
     */
    fun isCrossNodeMessage(): Boolean {
        return sourceNode != targetNode
    }
    
    /**
     * Get routing description for logging/display
     */
    fun getRoutingDescription(): String {
        return if (isCrossNodeMessage()) {
            "Cross-node: $sourceNode → $targetNode"
        } else {
            "Same node: $sourceNode"
        }
    }
    
    /**
     * Get peer device description if available
     */
    fun getPeerDescription(): String? {
        return peerDeviceInfo?.let { peer ->
            "${peer.deviceName} (${peer.deviceId}) on ${peer.fusionNode}"
        }
    }
}
