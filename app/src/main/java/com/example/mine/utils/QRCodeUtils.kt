package com.example.mine.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import android.util.Log
import androidx.core.graphics.set
import com.example.mine.crypto.QRCodeData
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.security.PublicKey

class QRCodeUtils {
    
    companion object {
        private const val TAG = "QRCodeUtils"
        private const val QR_SIZE = 512
        private const val MARGIN = 0
    }
    
    // Generate QR code for public key
    fun generateQRCode(publicKey: PublicKey): Bitmap? {
        return try {
            // Convert public key to base64 string
            val publicKeyBytes = publicKey.encoded
            val publicKeyString = Base64.encodeToString(publicKeyBytes, Base64.DEFAULT)
            
            // Create QR code writer
            val hints = HashMap<EncodeHintType, Any>().apply {
                put(EncodeHintType.MARGIN, MARGIN)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }
            
            val writer = QRCodeWriter()
            val bitMatrix: BitMatrix = writer.encode(
                publicKeyString,
                BarcodeFormat.QR_CODE,
                QR_SIZE,
                QR_SIZE,
                hints
            )
            
            // Convert bit matrix to bitmap
            val bitmap = Bitmap.createBitmap(QR_SIZE, QR_SIZE, Bitmap.Config.ARGB_8888)
            
            for (x in 0 until QR_SIZE) {
                for (y in 0 until QR_SIZE) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            
            Log.d(TAG, "QR code generated successfully for public key")
            bitmap
            
        } catch (e: WriterException) {
            Log.e(TAG, "Failed to generate QR code", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error generating QR code", e)
            null
        }
    }
    
    // Generate QR code for public key with custom size
    fun generateQRCode(publicKey: PublicKey, size: Int): Bitmap? {
        return try {
            // Convert public key to base64 string
            val publicKeyBytes = publicKey.encoded
            val publicKeyString = Base64.encodeToString(publicKeyBytes, Base64.DEFAULT)
            
            // Create QR code writer
            val hints = HashMap<EncodeHintType, Any>().apply {
                put(EncodeHintType.MARGIN, MARGIN)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }
            
            val writer = QRCodeWriter()
            val bitMatrix: BitMatrix = writer.encode(
                publicKeyString,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )
            
            // Convert bit matrix to bitmap
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap[x, y] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            
            Log.d(TAG, "QR code generated successfully for public key with size $size")
            bitmap
            
        } catch (e: WriterException) {
            Log.e(TAG, "Failed to generate QR code", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error generating QR code", e)
            null
        }
    }
    
    // Generate QR code for custom data
    fun generateQRCode(data: String, size: Int = QR_SIZE): Bitmap? {
        return try {
            // Create QR code writer
            val hints = HashMap<EncodeHintType, Any>().apply {
                put(EncodeHintType.MARGIN, MARGIN)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }
            
            val writer = QRCodeWriter()
            val bitMatrix: BitMatrix = writer.encode(
                data,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )
            
            // Convert bit matrix to bitmap
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            
            Log.d(TAG, "QR code generated successfully for data: $data")
            bitmap
            
        } catch (e: WriterException) {
            Log.e(TAG, "Failed to generate QR code for data: $data", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error generating QR code for data: $data", e)
            null
        }
    }
    
    // Generate QR code with custom colors
    fun generateQRCode(
        data: String,
        size: Int = QR_SIZE,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap? {
        return try {
            // Create QR code writer
            val hints = HashMap<EncodeHintType, Any>().apply {
                put(EncodeHintType.MARGIN, MARGIN)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }
            
            val writer = QRCodeWriter()
            val bitMatrix: BitMatrix = writer.encode(
                data,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )
            
            // Convert bit matrix to bitmap with custom colors
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) foregroundColor else backgroundColor)
                }
            }
            
            Log.d(TAG, "QR code generated successfully with custom colors")
            bitmap
            
        } catch (e: WriterException) {
            Log.e(TAG, "Failed to generate QR code with custom colors", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error generating QR code with custom colors", e)
            null
        }
    }
    
    // Generate QR code for public key with custom colors
    fun generateQRCode(
        publicKey: PublicKey,
        size: Int = QR_SIZE,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap? {
        return try {
            // Convert public key to base64 string
            val publicKeyBytes = publicKey.encoded
            val publicKeyString = Base64.encodeToString(publicKeyBytes, Base64.DEFAULT)
            
            // Generate QR code with custom colors
            generateQRCode(publicKeyString, size, foregroundColor, backgroundColor)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate QR code for public key with custom colors", e)
            null
        }
    }
    
    // Parse QR code data to extract public key
    fun parsePublicKeyFromQR(qrData: String): ByteArray? {
        return try {
            // Decode base64 string to get public key bytes
            val publicKeyBytes = Base64.decode(qrData, Base64.DEFAULT)
            Log.d(TAG, "Successfully parsed public key from QR code: ${publicKeyBytes.size} bytes")
            publicKeyBytes
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid base64 data in QR code", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error parsing public key from QR code", e)
            null
        }
    }
    
    // Validate QR code data format
    fun isValidPublicKeyQR(qrData: String): Boolean {
        return try {
            val decoded = Base64.decode(qrData, Base64.DEFAULT)
            // Basic validation - check if it looks like a public key
            // In real implementation, you'd validate the actual key format
            decoded.size > 0 && decoded.size < 10000 // Reasonable size for public key
        } catch (e: Exception) {
            false
        }
    }
    
    // Generate QR code for enhanced QRCodeData structure
    fun generateQRCode(qrData: QRCodeData, size: Int = QR_SIZE): Bitmap? {
        return try {
            val jsonString = qrData.toJson()
            Log.d(TAG, "Generating QR code for enhanced data: $jsonString")
            generateQRCode(jsonString, size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate QR code for enhanced data", e)
            null
        }
    }
    
    // Generate QR code for enhanced QRCodeData with custom colors
    fun generateQRCode(
        qrData: QRCodeData,
        size: Int = QR_SIZE,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap? {
        return try {
            val jsonString = qrData.toJson()
            Log.d(TAG, "Generating QR code for enhanced data with custom colors: $jsonString")
            generateQRCode(jsonString, size, foregroundColor, backgroundColor)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate QR code for enhanced data with custom colors", e)
            null
        }
    }
    
    // Parse QR code data to extract QRCodeData object
    fun parseQRCodeData(qrData: String): QRCodeData? {
        return try {
            // First try to parse as JSON (new format)
            val parsedData = QRCodeData.fromJson(qrData)
            if (parsedData != null && QRCodeData.isValid(parsedData)) {
                Log.d(TAG, "Successfully parsed QR code data as JSON format")
                return parsedData
            }
            
            // Fallback: try to parse as base64 public key (old format)
            val publicKeyBytes = parsePublicKeyFromQR(qrData)
            if (publicKeyBytes != null) {
                Log.d(TAG, "Parsed QR code data as legacy base64 format")
                // Create a minimal QRCodeData object for backward compatibility
                return QRCodeData(
                    publicKey = qrData,
                    fusionNode = "UNKNOWN"
                )
            }
            
            Log.e(TAG, "Failed to parse QR code data in any supported format")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing QR code data", e)
            null
        }
    }
    
    // Validate enhanced QR code data
    fun isValidQRCodeData(qrData: String): Boolean {
        return try {
            val parsedData = QRCodeData.fromJson(qrData)
            parsedData != null && QRCodeData.isValid(parsedData)
        } catch (e: Exception) {
            // Fallback to old format validation
            isValidPublicKeyQR(qrData)
        }
    }
    
    // Get QR code information for display
    fun getQRCodeInfo(qrData: String): String {
        return try {
            val parsedData = QRCodeData.fromJson(qrData)
            if (parsedData != null && QRCodeData.isValid(parsedData)) {
                return """
                    Fusion Node: ${parsedData.fusionNode}
                    Public Key: ${parsedData.publicKey.take(20)}...
                    Format: Simplified JSON
                """.trimIndent()
            } else {
                return "Legacy QR Code Format"
            }
        } catch (e: Exception) {
            "Invalid QR Code Format"
        }
    }
    
    // Format time remaining in MM:SS format
    private fun formatTimeRemaining(timeRemainingMs: Long): String {
        val seconds = timeRemainingMs / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }
}
