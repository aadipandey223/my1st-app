package com.example.mine.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRCodeScanner(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var preview: Preview? = null
    
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _scannedData = MutableStateFlow<String?>(null)
    val scannedData: StateFlow<String?> = _scannedData.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val barcodeScanner: BarcodeScanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }
    
    companion object {
        private const val TAG = "QRCodeScanner"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }
    
    // Check if camera permission is granted
    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    // Start QR code scanning
    fun startScanning() {
        if (!hasCameraPermission()) {
            _errorMessage.value = "Camera permission is required"
            return
        }
        
        try {
            _isScanning.value = true
            _errorMessage.value = null
            _scannedData.value = null
            
            startCamera()
            Log.d(TAG, "QR code scanning started")
            
        } catch (e: Exception) {
            _errorMessage.value = "Error starting camera: ${e.message}"
            Log.e(TAG, "Error starting camera", e)
            _isScanning.value = false
        }
    }
    
    // Stop QR code scanning
    fun stopScanning() {
        try {
            _isScanning.value = false
            cameraProvider?.unbindAll()
            Log.d(TAG, "QR code scanning stopped")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
    }
    
    // Start camera with preview and analysis
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                
                // Set up preview use case
                preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                
                // Set up image analysis use case
                imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, QRCodeAnalyzer())
                    }
                
                // Select back camera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                // Bind use cases to camera
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up camera", e)
                _errorMessage.value = "Error setting up camera: ${e.message}"
                _isScanning.value = false
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    // Image analyzer for QR code detection
    private inner class QRCodeAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(image: ImageProxy) {
            try {
                val mediaImage = image.image
                if (mediaImage != null) {
                    val inputImage = InputImage.fromMediaImage(
                        mediaImage, 
                        image.imageInfo.rotationDegrees
                    )
                    
                    barcodeScanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) {
                                val qrData = barcode.rawValue
                                if (!qrData.isNullOrEmpty()) {
                                    Log.d(TAG, "QR Code detected: $qrData")
                                    _scannedData.value = qrData
                                    _isScanning.value = false
                                    
                                    // Stop scanning after successful detection
                                    stopScanning()
                                    break
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error analyzing image", e)
                        }
                        .addOnCompleteListener {
                            image.close()
                        }
                } else {
                    image.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in image analyzer", e)
                image.close()
            }
        }
    }
    
    // Get camera status
    fun getCameraStatus(): String {
        return when {
            !hasCameraPermission() -> "Camera permission required"
            !_isScanning.value -> "Camera not scanning"
            _scannedData.value != null -> "QR Code detected"
            else -> "Scanning for QR codes..."
        }
    }
    
    // Reset scanner state
    fun reset() {
        _scannedData.value = null
        _errorMessage.value = null
        _isScanning.value = false
    }
    
    // Clean up resources
    fun cleanup() {
        try {
            stopScanning()
            cameraExecutor.shutdown()
            barcodeScanner.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}
