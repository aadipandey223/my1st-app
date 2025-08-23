package com.example.mine.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionManager(private val context: Context) {
    
    companion object {
        const val PERMISSION_REQUEST_CODE = 100
        const val BLUETOOTH_PERMISSION_REQUEST_CODE = 101
        const val LOCATION_PERMISSION_REQUEST_CODE = 102
        const val CAMERA_PERMISSION_REQUEST_CODE = 103
    }
    
    // Required permissions for different features
    private val requiredPermissions = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    
    private val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    
    // Check if all required permissions are granted
    fun hasAllRequiredPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    // Check if Bluetooth permissions are granted
    fun hasBluetoothPermissions(): Boolean {
        return bluetoothPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    // Check if Camera permission is granted
    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    // Location permission no longer required
    
    // Request all required permissions
    fun requestRequiredPermissions(activity: Activity) {
        val permissionsToRequest = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }
    
    // Request Bluetooth permissions
    fun requestBluetoothPermissions(activity: Activity) {
        val permissionsToRequest = bluetoothPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                permissionsToRequest.toTypedArray(),
                BLUETOOTH_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    // Request Camera permission
    fun requestCameraPermission(activity: Activity) {
        if (!hasCameraPermission()) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    // Location permission no longer required
    
    // Check if permission should show rationale
    fun shouldShowPermissionRationale(activity: Activity, permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
    
    // Get permission status message
    fun getPermissionStatusMessage(): String {
        val missingPermissions = mutableListOf<String>()
        
        if (!hasCameraPermission()) missingPermissions.add("Camera")
        if (!hasBluetoothPermissions()) missingPermissions.add("Bluetooth")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add("Location")
        }
        
        return when {
            missingPermissions.isEmpty() -> "All permissions granted"
            missingPermissions.size == 1 -> "${missingPermissions[0]} permission required"
            else -> "${missingPermissions.joinToString(", ")} permissions required"
        }
    }
}
