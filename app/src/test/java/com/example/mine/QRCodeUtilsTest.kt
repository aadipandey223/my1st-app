package com.example.mine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.mine.utils.QRCodeUtils
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import java.io.ByteArrayOutputStream

class QRCodeUtilsTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockUri: Uri

    @Test
    fun `test isValidFusionQRCode with valid fusion QR code`() {
        val validQRCode = "fusion:abc123:device456:1703123456789"
        assertTrue(QRCodeUtils.isValidFusionQRCode(validQRCode))
    }

    @Test
    fun `test isValidFusionQRCode with invalid QR code`() {
        val invalidQRCode = "invalid:qr:code"
        assertFalse(QRCodeUtils.isValidFusionQRCode(invalidQRCode))
    }

    @Test
    fun `test isValidFusionQRCode with empty string`() {
        assertFalse(QRCodeUtils.isValidFusionQRCode(""))
    }

    @Test
    fun `test isValidFusionQRCode with null string`() {
        // Note: isValidFusionQRCode doesn't handle null, so this test expects an exception
        assertThrows(IllegalArgumentException::class.java) {
            QRCodeUtils.isValidFusionQRCode(null!!)
        }
    }

    @Test
    fun `test isValidFusionQRCode with incomplete fusion format`() {
        val incompleteQRCode = "fusion:abc123"
        assertFalse(QRCodeUtils.isValidFusionQRCode(incompleteQRCode))
    }

    @Test
    fun `test isValidFusionQRCode with extra parts`() {
        val extraPartsQRCode = "fusion:abc123:device456:1703123456789:extra:parts"
        assertTrue(QRCodeUtils.isValidFusionQRCode(extraPartsQRCode))
    }

    @Test
    fun `test QR code format validation edge cases`() {
        // Test with special characters
        val specialCharsQRCode = "fusion:abc@123:device#456:1703123456789"
        assertTrue(QRCodeUtils.isValidFusionQRCode(specialCharsQRCode))

        // Test with numbers only
        val numbersOnlyQRCode = "fusion:123456:789012:1703123456789"
        assertTrue(QRCodeUtils.isValidFusionQRCode(numbersOnlyQRCode))

        // Test with mixed case
        val mixedCaseQRCode = "FUSION:ABC123:DEVICE456:1703123456789"
        assertFalse(QRCodeUtils.isValidFusionQRCode(mixedCaseQRCode))
    }

    @Test
    fun `test QR code timestamp validation`() {
        // Test with valid timestamp
        val validTimestampQRCode = "fusion:abc123:device456:1703123456789"
        assertTrue(QRCodeUtils.isValidFusionQRCode(validTimestampQRCode))

        // Test with invalid timestamp (non-numeric)
        val invalidTimestampQRCode = "fusion:abc123:device456:invalid_timestamp"
        assertTrue(QRCodeUtils.isValidFusionQRCode(invalidTimestampQRCode)) // Still valid format

        // Test with very long timestamp
        val longTimestampQRCode = "fusion:abc123:device456:1703123456789123456789"
        assertTrue(QRCodeUtils.isValidFusionQRCode(longTimestampQRCode))
    }

    @Test
    fun `test QR code parts validation`() {
        // Test minimum required parts
        val minPartsQRCode = "fusion:key:node:timestamp"
        assertTrue(QRCodeUtils.isValidFusionQRCode(minPartsQRCode))

        // Test with more parts
        val morePartsQRCode = "fusion:key:node:timestamp:extra1:extra2"
        assertTrue(QRCodeUtils.isValidFusionQRCode(morePartsQRCode))

        // Test with empty parts
        val emptyPartsQRCode = "fusion::node:timestamp"
        assertTrue(QRCodeUtils.isValidFusionQRCode(emptyPartsQRCode)) // Still valid format
    }
}
