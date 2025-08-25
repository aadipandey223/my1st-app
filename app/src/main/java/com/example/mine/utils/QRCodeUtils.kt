package com.example.mine.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.example.mine.crypto.QRCodeData
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.security.PublicKey

class QRCodeUtils {
    
    private val TAG = "QRCodeUtils"
    
    /**
     * Generate QR code bitmap from QR code data
     */
    fun generateQRCode(qrData: QRCodeData): Bitmap? {
        return try {
            val jsonString = qrData.toJson()
            generateQRCodeFromString(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating QR code from QRCodeData", e)
            null
        }
    }
    
    /**
     * Generate QR code bitmap from string
     */
    fun generateQRCodeFromString(data: String): Bitmap? {
        return try {
            val hints = hashMapOf<EncodeHintType, Any>().apply {
                put(EncodeHintType.MARGIN, 1)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }
            
            val qrCodeWriter = QRCodeWriter()
            val bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, 512, 512, hints)
            
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error generating QR code from string", e)
            null
        }
    }
    
    /**
     * Parse QR code data from string
     */
    fun parseQRCodeData(qrDataString: String): QRCodeData? {
        return try {
            // First try to parse as JSON (new format)
            QRCodeData.fromJson(qrDataString)?.let { return it }
            
            // Fallback: try to parse as fusion format
            if (isValidFusionQRCode(qrDataString)) {
                val parts = qrDataString.split(":")
                if (parts.size >= 4) {
                    // Create a basic QRCodeData from fusion format
                    // This is a fallback for backward compatibility
                    return QRCodeData(
                        publicKey = parts[1], // Use the second part as public key
                        fusionNode = parts[2] // Use the third part as fusion node
                    )
                }
            }
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing QR code data", e)
            null
        }
    }
    
    /**
     * Get QR code information for display
     */
    fun getQRCodeInfo(qrDataString: String): String {
        return try {
            val qrData = parseQRCodeData(qrDataString)
            if (qrData != null) {
                "Public Key: ${qrData.publicKey.take(20)}...\nFusion Node: ${qrData.fusionNode}"
            } else {
                "Invalid QR code format"
            }
        } catch (e: Exception) {
            "Error parsing QR code: ${e.message}"
        }
    }
    
    /**
     * Create QR code data from public key and fusion node
     */
    fun createQRCodeData(publicKey: PublicKey, fusionNode: String?): QRCodeData {
        return QRCodeData.create(publicKey, fusionNode ?: "unknown")
    }
    
    /**
     * Scan QR code from a gallery image URI
     * @param context The application context
     * @param imageUri The URI of the image from gallery
     * @return The scanned QR code data or null if not found
     */
    suspend fun scanQRFromGallery(context: Context, imageUri: Uri): String? {
        return try {
            // Load bitmap from URI
            val bitmap = loadBitmapFromUri(context, imageUri)
            if (bitmap == null) {
                Log.e(TAG, "Failed to load bitmap from URI: $imageUri")
                return null
            }
            
            // Create InputImage from bitmap
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            // Scan for QR codes
            val qrData = scanQRFromImage(inputImage)
            
            // Clean up bitmap
            bitmap.recycle()
            
            qrData
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning QR from gallery", e)
            null
        }
    }
    
    /**
     * Load bitmap from URI with proper error handling
     */
    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from URI", e)
            null
        }
    }
    
    /**
     * Scan QR code from InputImage using ML Kit
     */
    private suspend fun scanQRFromImage(inputImage: InputImage): String? {
        return suspendCancellableCoroutine { continuation ->
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            
            val scanner = BarcodeScanning.getClient(options)
            
            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    try {
                        for (barcode in barcodes) {
                            val qrData = barcode.rawValue
                            if (!qrData.isNullOrEmpty()) {
                                Log.d(TAG, "QR Code detected from gallery: $qrData")
                                continuation.resume(qrData)
                                return@addOnSuccessListener
                            }
                        }
                        // No QR code found
                        continuation.resume(null)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error scanning QR from image", e)
                    continuation.resumeWithException(e)
                }
                .addOnCompleteListener {
                    scanner.close()
                }
        }
    }
    
    /**
     * Validate if the scanned data is a valid fusion QR code
     */
    fun isValidFusionQRCode(qrData: String): Boolean {
        return qrData.startsWith("fusion:") && qrData.split(":").size >= 4
    }
}
