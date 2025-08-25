package com.example.mine.crypto

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.security.PublicKey
import android.util.Base64

/**
 * Simplified data class representing the content of a QR code for secure communication
 * 
 * NEW APPROACH: Contains only essential information for message routing and decryption:
 * - publicKey: For establishing secure session (ECDH key exchange)
 * - fusionNode: For routing messages to the correct Fusion Node
 * 
 * REMOVED: deviceId, deviceName, connectionType, timestamp, version (not needed for new payload)
 * 
 * Payload Structure: [fusion_id_len | fusion_id | encrypted_message]
 */
data class QRCodeData(
    @SerializedName("pk")
    val publicKey: String, // Base64 encoded public key (ESSENTIAL for ECDH)
    
    @SerializedName("fusion_node")
    val fusionNode: String // Connected Fusion Node name/ID (ESSENTIAL for routing)
) {
    companion object {
        private val gson = Gson()
        
        /**
         * Create QR code data from public key and fusion node
         */
        fun create(
            publicKey: PublicKey,
            fusionNode: String
        ): QRCodeData {
            val publicKeyBase64 = Base64.encodeToString(publicKey.encoded, Base64.DEFAULT)
            
            return QRCodeData(
                publicKey = publicKeyBase64,
                fusionNode = fusionNode
            )
        }
        
        /**
         * Parse QR code data from JSON string
         */
        fun fromJson(json: String): QRCodeData? {
            return try {
                gson.fromJson(json, QRCodeData::class.java)
            } catch (e: Exception) {
                null
            }
        }
        
        /**
         * Validate QR code data
         */
        fun isValid(qrData: QRCodeData): Boolean {
            return qrData.publicKey.isNotEmpty() &&
                   qrData.fusionNode.isNotEmpty()
        }
        
        // No expiration check needed for simplified QR code
    }
    
    /**
     * Convert to JSON string
     */
    fun toJson(): String {
        return gson.toJson(this)
    }
    
    /**
     * Get public key bytes from base64 string
     */
    fun getPublicKeyBytes(): ByteArray? {
        return try {
            Base64.decode(publicKey, Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get formatted timestamp (not available in simplified QR code)
     */
    fun getFormattedTimestamp(): String {
        return "N/A"
    }
    
    /**
     * Get time remaining until expiration (not available in simplified QR code)
     */
    fun getTimeRemaining(maxAgeMs: Long = 5 * 60 * 1000): Long {
        return 0L
    }
}
