package com.example.mine.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.security.PublicKey
import java.util.Base64

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
            val publicKeyString = Base64.getEncoder().encodeToString(publicKeyBytes)
            
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
            val publicKeyString = Base64.getEncoder().encodeToString(publicKeyBytes)
            
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
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
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
            val publicKeyString = Base64.getEncoder().encodeToString(publicKeyBytes)
            
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
            val publicKeyBytes = Base64.getDecoder().decode(qrData)
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
            val decoded = Base64.getDecoder().decode(qrData)
            // Basic validation - check if it looks like a public key
            // In real implementation, you'd validate the actual key format
            decoded.size > 0 && decoded.size < 10000 // Reasonable size for public key
        } catch (e: Exception) {
            false
        }
    }
}
