package com.example.mine.callback

/**
 * Callback interface for automatic app redirection when a connection is detected
 */
interface ConnectionCallback {
    fun onConnectionDetected(nodeName: String)
}
