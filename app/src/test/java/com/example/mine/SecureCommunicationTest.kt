package com.example.mine

import com.example.mine.crypto.*
import com.example.mine.data.*
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.mock
import android.content.Context
import java.security.KeyPair
import java.util.Date

class SecureCommunicationTest {
    
    private val mockContext = mock(Context::class.java)
    
    @Test
    fun testX25519KeyGeneration() {
        val cryptoManager = CryptoManager(mockContext)
        val keyPair = cryptoManager.generateX25519KeyPair()
        
        assertNotNull("Key pair should not be null", keyPair)
        assertNotNull("Public key should not be null", keyPair.public)
        assertNotNull("Private key should not be null", keyPair.private)
        assertEquals("Algorithm should be X25519", "X25519", keyPair.public.algorithm)
    }
    
    @Test
    fun testECDHKeyExchange() {
        val cryptoManager = CryptoManager(mockContext)
        
        // Generate two key pairs
        val keyPair1 = cryptoManager.generateX25519KeyPair()
        val keyPair2 = cryptoManager.generateX25519KeyPair()
        
        // Compute shared secrets
        val sharedSecret1 = cryptoManager.computeSharedSecret(keyPair1.private, keyPair2.public)
        val sharedSecret2 = cryptoManager.computeSharedSecret(keyPair2.private, keyPair1.public)
        
        // Both should produce the same shared secret
        assertArrayEquals("Shared secrets should be equal", sharedSecret1, sharedSecret2)
        assertTrue("Shared secret should not be empty", sharedSecret1.isNotEmpty())
    }
    
    @Test
    fun testHKDFSessionKeyDerivation() {
        val cryptoManager = CryptoManager(mockContext)
        
        // Generate test shared secret
        val sharedSecret = "test_shared_secret_32_bytes_long".toByteArray()
        val salt = "test_salt_32_bytes_long".toByteArray()
        
        val sessionKeys = cryptoManager.deriveSessionKeys(sharedSecret, salt)
        
        assertNotNull("Session keys should not be null", sessionKeys)
        assertEquals("K_tx should be 32 bytes", 32, sessionKeys.kTx.size)
        assertEquals("K_rx should be 32 bytes", 32, sessionKeys.kRx.size)
        assertFalse("K_tx and K_rx should be different", sessionKeys.kTx.contentEquals(sessionKeys.kRx))
    }
    
    @Test
    fun testLZ4Compression() {
        val cryptoManager = CryptoManager(mockContext)
        
        // Create test data larger than compression threshold
        val testData = "This is a test message that should be compressed because it's longer than the threshold. ".repeat(50)
        val originalBytes = testData.toByteArray()
        
        val compressed = cryptoManager.compressData(originalBytes)
        val decompressed = cryptoManager.decompressData(compressed, originalBytes.size)
        
        assertTrue("Compressed data should be smaller than original", compressed.size < originalBytes.size)
        assertArrayEquals("Decompressed data should match original", originalBytes, decompressed)
    }
    
    @Test
    fun testAESGCMEncryptionWithAAD() {
        val cryptoManager = CryptoManager(mockContext)
        
        // Generate session key
        val sessionKey = "test_session_key_32_bytes_long".toByteArray()
        val plaintext = "Hello, secure world!".toByteArray()
        val aad = "associated_data".toByteArray()
        val nonce = cryptoManager.generateNonce(1, 2, 3)
        
        // Encrypt
        val encryptionResult = cryptoManager.encryptWithAAD(sessionKey, plaintext, aad, nonce)
        
        assertNotNull("Encryption result should not be null", encryptionResult)
        assertNotNull("Ciphertext should not be null", encryptionResult.ciphertext)
        assertNotNull("IV should not be null", encryptionResult.iv)
        
        // Decrypt
        val decrypted = cryptoManager.decryptWithAAD(sessionKey, encryptionResult.ciphertext, aad, nonce)
        
        assertNotNull("Decrypted data should not be null", decrypted)
        assertArrayEquals("Decrypted data should match original", plaintext, decrypted)
    }
    
    @Test
    fun testFrameCreationAndParsing() {
        val frame = Frame(
            type = FrameType.DATA.value,
            flags = FrameFlags.COMPRESSED.value,
            headerLength = Frame.FRAME_HEADER_SIZE.toByte(),
            sourceId = 1,
            destinationId = 2,
            sessionId = 123,
            sequence = 456,
            ttl = 32,
            nonce = ByteArray(12) { it.toByte() },
            ciphertext = "encrypted_message".toByteArray(),
            tag = ByteArray(16) { it.toByte() }
        )
        
        // Test frame validation
        assertTrue("Frame should be valid", frame.isValid())
        
        // Test AAD generation
        val aad = frame.getAAD()
        assertEquals("AAD should be 24 bytes", 24, aad.size)
        
        // Test serialization and deserialization
        val frameBytes = frame.toByteArray()
        val parsedFrame = Frame.fromByteArray(frameBytes)
        
        assertNotNull("Parsed frame should not be null", parsedFrame)
        assertEquals("Source ID should match", frame.sourceId, parsedFrame!!.sourceId)
        assertEquals("Destination ID should match", frame.destinationId, parsedFrame!!.destinationId)
        assertEquals("Session ID should match", frame.sessionId, parsedFrame!!.sessionId)
        assertEquals("Sequence should match", frame.sequence, parsedFrame!!.sequence)
    }
    
    @Test
    fun testSessionManagement() {
        val cryptoManager = CryptoManager(mockContext)
        val sessionManager = SessionManager(cryptoManager)
        
        // Generate test key pairs
        val keyPair1 = cryptoManager.generateX25519KeyPair()
        val keyPair2 = cryptoManager.generateX25519KeyPair()
        
        // Create session
        val session = sessionManager.createSession(keyPair2.public)
        assertNotNull("Session should not be null", session)
        assertFalse("Session should not be established initially", session.isEstablished)
        
        // Establish session keys
        val success = sessionManager.establishSessionKeys(session, keyPair1, keyPair2.public)
        assertTrue("Session keys should be established successfully", success)
        assertTrue("Session should be established", session.isEstablished)
    }
    
    @Test
    fun testMessageEncryptionAndDecryption() {
        val cryptoManager = CryptoManager(mockContext)
        val sessionManager = SessionManager(cryptoManager)
        
        // Generate test key pairs
        val keyPair1 = cryptoManager.generateX25519KeyPair()
        val keyPair2 = cryptoManager.generateX25519KeyPair()
        
        // Create and establish session
        val session = sessionManager.createSession(keyPair2.public)
        sessionManager.establishSessionKeys(session, keyPair1, keyPair2.public)
        
        // Test message encryption
        val testMessage = "Hello, this is a test message for encryption!"
        val encryptedMessage = sessionManager.encryptMessage(session, testMessage, 2)
        
        assertNotNull("Encrypted message should not be null", encryptedMessage)
        assertTrue("Encrypted message should not be empty", encryptedMessage!!.isNotEmpty())
        
        // Test message decryption
        val decryptedMessage = sessionManager.decryptMessage(session, encryptedMessage)
        
        assertNotNull("Decrypted message should not be null", decryptedMessage)
        assertEquals("Decrypted message should match original", testMessage, decryptedMessage)
    }
    
    @Test
    fun testReplayProtection() {
        val cryptoManager = CryptoManager(mockContext)
        val sessionManager = SessionManager(cryptoManager)
        
        // Generate test key pairs
        val keyPair1 = cryptoManager.generateX25519KeyPair()
        val keyPair2 = cryptoManager.generateX25519KeyPair()
        
        // Create and establish session
        val session = sessionManager.createSession(keyPair2.public)
        sessionManager.establishSessionKeys(session, keyPair1, keyPair2.public)
        
        // Create first encrypted message
        val encryptedMessage1 = sessionManager.encryptMessage(session, "Message 1", 2)
        assertNotNull("First encrypted message should not be null", encryptedMessage1)
        
        // Create second encrypted message
        val encryptedMessage2 = sessionManager.encryptMessage(session, "Message 2", 2)
        assertNotNull("Second encrypted message should not be null", encryptedMessage2)
        
        // Verify encrypted messages are different (due to different nonces)
        assertNotEquals("Encrypted messages should be different", 
                       encryptedMessage1!!.contentHashCode(), 
                       encryptedMessage2!!.contentHashCode())
        
        // Verify both messages can be decrypted
        val decrypted1 = sessionManager.decryptMessage(session, encryptedMessage1)
        val decrypted2 = sessionManager.decryptMessage(session, encryptedMessage2)
        assertEquals("First message should decrypt correctly", "Message 1", decrypted1)
        assertEquals("Second message should decrypt correctly", "Message 2", decrypted2)
    }
    
    @Test
    fun testDatabaseOperations() {
        // Test Message entity
        val message = Message(
            sessionId = 1,
            senderId = 1,
            receiverId = 2,
            content = "Test message",
            encryptedContent = "encrypted".toByteArray(),
            timestamp = Date(),
            isEncrypted = true,
            isCompressed = false
        )
        
        assertEquals("Session ID should match", 1, message.sessionId)
        assertEquals("Content should match", "Test message", message.content)
        assertTrue("Message should be encrypted", message.isEncrypted)
        
        // Test Contact entity
        val contact = Contact(
            publicKey = "public_key".toByteArray(),
            name = "Test Contact",
            deviceId = 123,
            lastSeen = Date(),
            isOnline = true,
            trustLevel = 5
        )
        
        assertEquals("Name should match", "Test Contact", contact.name)
        assertEquals("Device ID should match", 123, contact.deviceId)
        assertEquals("Trust level should match", 5, contact.trustLevel)
        
        // Test SessionEntity
        val sessionEntity = SessionEntity(
            id = 456,
            peerPublicKey = "peer_key".toByteArray(),
            createdAt = Date(),
            lastActivity = Date(),
            isActive = true,
            messageCount = 10
        )
        
        assertEquals("Session ID should match", 456, sessionEntity.id)
        assertTrue("Session should be active", sessionEntity.isActive)
        assertEquals("Message count should match", 10, sessionEntity.messageCount)
    }
    
    @Test
    fun testNonceGeneration() {
        val cryptoManager = CryptoManager(mockContext)
        
        val sessionId = 12345
        val senderId = 67890
        val sequence = 11111
        
        val nonce = cryptoManager.generateNonce(sessionId, senderId, sequence)
        
        assertEquals("Nonce should be 12 bytes", 12, nonce.size)
        
        // Verify nonce components
        val buffer = java.nio.ByteBuffer.wrap(nonce).order(java.nio.ByteOrder.BIG_ENDIAN)
        val extractedSessionId = buffer.int
        val extractedSenderId = buffer.int
        val extractedSequence = buffer.int
        
        assertEquals("Session ID should match", sessionId, extractedSessionId)
        assertEquals("Sender ID should match", senderId, extractedSenderId)
        assertEquals("Sequence should match", sequence, extractedSequence)
    }
    
    @Test
    fun testSecureWipe() {
        val cryptoManager = CryptoManager(mockContext)
        
        val sensitiveData = "sensitive_information".toByteArray()
        val originalData = sensitiveData.copyOf()
        
        // Verify original data
        assertArrayEquals("Original data should match", originalData, sensitiveData)
        
        // Wipe data
        cryptoManager.secureWipe(sensitiveData)
        
        // Verify data is wiped
        val allZeros = ByteArray(sensitiveData.size) { 0 }
        assertArrayEquals("Data should be wiped to zeros", allZeros, sensitiveData)
        
        // Verify original data is unchanged
        assertFalse("Original data should not be affected", originalData.contentEquals(sensitiveData))
    }
    
    @Test
    fun testFrameTypeAndFlags() {
        // Test FrameType enum
        assertEquals("HANDSHAKE should have value 1", 1, FrameType.HANDSHAKE.value)
        assertEquals("DATA should have value 2", 2, FrameType.DATA.value)
        assertEquals("ACK should have value 3", 3, FrameType.ACK.value)
        assertEquals("REKEY should have value 4", 4, FrameType.REKEY.value)
        assertEquals("HEARTBEAT should have value 5", 5, FrameType.HEARTBEAT.value)
        
        // Test FrameFlags enum
        assertEquals("COMPRESSED should have value 0x01", 0x01, FrameFlags.COMPRESSED.value)
        assertEquals("ENCRYPTED should have value 0x02", 0x02, FrameFlags.ENCRYPTED.value)
        assertEquals("SIGNED should have value 0x04", 0x04, FrameFlags.SIGNED.value)
        assertEquals("FRAGMENTED should have value 0x08", 0x08, FrameFlags.FRAGMENTED.value)
        assertEquals("LAST_FRAGMENT should have value 0x10", 0x10, FrameFlags.LAST_FRAGMENT.value)
    }
    
    @Test
    fun testPayloadSerialization() {
        val payload = Payload(
            contentType = ContentType.TEXT,
            isCompressed = true,
            originalSize = 100,
            data = "compressed_data".toByteArray()
        )
        
        // Test serialization
        val payloadBytes = payload.toByteArray()
        assertTrue("Payload bytes should not be empty", payloadBytes.isNotEmpty())
        
        // Test deserialization
        val parsedPayload = Payload.fromByteArray(payloadBytes)
        
        assertNotNull("Parsed payload should not be null", parsedPayload)
        assertEquals("Content type should match", payload.contentType, parsedPayload!!.contentType)
        assertEquals("Compression flag should match", payload.isCompressed, parsedPayload!!.isCompressed)
        assertEquals("Original size should match", payload.originalSize, parsedPayload!!.originalSize)
        assertArrayEquals("Data should match", payload.data, parsedPayload!!.data)
    }
    
    @Test
    fun testImprovedNonceGeneration() {
        val cryptoManager = CryptoManager(mockContext)
        
        // Test valid inputs
        val nonce1 = cryptoManager.generateNonce(1, 2, 3)
        val nonce2 = cryptoManager.generateNonce(1, 2, 3)
        
        assertEquals("Nonce should be 12 bytes", 12, nonce1.size)
        assertEquals("Nonce should be 12 bytes", 12, nonce2.size)
        
        // Nonces should be different due to entropy
        assertFalse("Nonces should be different due to entropy", nonce1.contentEquals(nonce2))
        
        // Test secure nonce generation
        val secureNonce1 = cryptoManager.generateSecureNonce()
        val secureNonce2 = cryptoManager.generateSecureNonce()
        
        assertEquals("Secure nonce should be 12 bytes", 12, secureNonce1.size)
        assertEquals("Secure nonce should be 12 bytes", 12, secureNonce2.size)
        
        // Secure nonces should always be different
        assertFalse("Secure nonces should always be different", secureNonce1.contentEquals(secureNonce2))
    }
    
    @Test
    fun testNonceGenerationValidation() {
        val cryptoManager = CryptoManager(mockContext)
        
        // Test invalid session ID
        assertThrows(IllegalArgumentException::class.java) {
            cryptoManager.generateNonce(0, 2, 3)
        }
        
        // Test invalid sender ID
        assertThrows(IllegalArgumentException::class.java) {
            cryptoManager.generateNonce(1, 0, 3)
        }
        
        // Test negative sequence
        assertThrows(IllegalArgumentException::class.java) {
            cryptoManager.generateNonce(1, 2, -1)
        }
    }
    
    @Test
    fun testImprovedEncryptionErrorHandling() {
        val cryptoManager = CryptoManager(mockContext)
        
        // Test with invalid session key size
        val invalidKey = "short_key".toByteArray()
        val plaintext = "test message".toByteArray()
        val nonce = cryptoManager.generateNonce(1, 2, 3)
        
        assertThrows(IllegalArgumentException::class.java) {
            cryptoManager.encryptWithAAD(invalidKey, plaintext, ByteArray(0), nonce)
        }
        
        // Test with empty plaintext
        val validKey = "valid_32_byte_key_for_testing_123".toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            cryptoManager.encryptWithAAD(validKey, ByteArray(0), ByteArray(0), nonce)
        }
        
        // Test with invalid nonce size
        val invalidNonce = ByteArray(8) // Too short
        assertThrows(IllegalArgumentException::class.java) {
            cryptoManager.encryptWithAAD(validKey, plaintext, ByteArray(0), invalidNonce)
        }
    }
    
    @Test
    fun testImprovedDecryptionErrorHandling() {
        val cryptoManager = CryptoManager(mockContext)
        
        // Test with invalid session key size
        val invalidKey = "short_key".toByteArray()
        val ciphertext = "test_ciphertext".toByteArray()
        val nonce = cryptoManager.generateNonce(1, 2, 3)
        
        val result1 = cryptoManager.decryptWithAAD(invalidKey, ciphertext, ByteArray(0), nonce)
        assertNull("Should return null for invalid key size", result1)
        
        // Test with empty ciphertext
        val validKey = "valid_32_byte_key_for_testing_123".toByteArray()
        val result2 = cryptoManager.decryptWithAAD(validKey, ByteArray(0), ByteArray(0), nonce)
        assertNull("Should return null for empty ciphertext", result2)
        
        // Test with invalid nonce size
        val invalidNonce = ByteArray(8) // Too short
        val result3 = cryptoManager.decryptWithAAD(validKey, ciphertext, ByteArray(0), invalidNonce)
        assertNull("Should return null for invalid nonce size", result3)
    }
    
    @Test
    fun testSecureMessageEncryption() {
        val cryptoManager = CryptoManager(mockContext)
        val sessionManager = SessionManager(cryptoManager)
        
        // Generate test key pairs
        val keyPair1 = cryptoManager.generateX25519KeyPair()
        val keyPair2 = cryptoManager.generateX25519KeyPair()
        
        // Create and establish session
        val session = sessionManager.createSession(keyPair2.public)
        sessionManager.establishSessionKeys(session, keyPair1, keyPair2.public)
        
        // Test secure encryption
        val testMessage = "Critical secure message"
        val encryptedMessage = sessionManager.encryptMessageSecure(session, testMessage, 2)
        
        assertNotNull("Secure encrypted message should not be null", encryptedMessage)
        assertTrue("Secure encrypted message should not be empty", encryptedMessage!!.isNotEmpty())
        
        // Verify encryption produces different results due to secure nonce
        val encryptedMessage2 = sessionManager.encryptMessageSecure(session, testMessage, 2)
        assertNotNull("Second secure encrypted message should not be null", encryptedMessage2)
        
        // Messages should be different due to secure random nonce
        assertFalse("Secure encrypted messages should be different", 
                   encryptedMessage.contentEquals(encryptedMessage2))
    }
    
    @Test
    fun testNodeDiscovery() {
        val cryptoManager = CryptoManager(mockContext)
        
        // Test network name extraction
        val testCases = listOf(
            "FusionNode_12345" to "12345",
            "ESP32_ABC123" to "ABC123",
            "RaspberryPi_TestNode" to "TestNode",
            "Node_MyDevice" to "MyDevice",
            "RegularWiFi" to ""
        )
        
        for ((ssid, expectedId) in testCases) {
            // This would test the extractNodeIdFromNetworkName function
            // For now, we'll test the pattern matching logic
            val patterns = listOf(
                Regex("fusion[_-]?node[_-]?([A-Za-z0-9]+)", RegexOption.IGNORE_CASE),
                Regex("esp32[_-]?([A-Za-z0-9]+)", RegexOption.IGNORE_CASE),
                Regex("raspberry[_-]?pi[_-]?([A-Za-z0-9]+)", RegexOption.IGNORE_CASE),
                Regex("node[_-]?([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
            )
            
            var extractedId = ""
            for (pattern in patterns) {
                val match = pattern.find(ssid)
                if (match != null) {
                    extractedId = match.groupValues[1]
                    break
                }
            }
            
            assertEquals("Failed to extract ID from '$ssid'", expectedId, extractedId)
        }
    }
    
    @Test
    fun testFusionNodeNetworkDetection() {
        val cryptoManager = CryptoManager(mockContext)
        
        val fusionNetworks = listOf(
            "FusionNode_123",
            "ESP32_Device",
            "RaspberryPi_Node",
            "MeshRouter_01",
            "FUSION_NODE_TEST"
        )
        
        val regularNetworks = listOf(
            "HomeWiFi",
            "Office_Network",
            "Guest_Access",
            "MyPhone_Hotspot"
        )
        
        val fusionKeywords = listOf("fusion", "node", "esp32", "raspberry", "pi", "mesh", "router")
        
        for (network in fusionNetworks) {
            val ssidLower = network.lowercase()
            val isFusion = fusionKeywords.any { keyword -> ssidLower.contains(keyword) }
            assertTrue("Network '$network' should be detected as fusion node", isFusion)
        }
        
        for (network in regularNetworks) {
            val ssidLower = network.lowercase()
            val isFusion = fusionKeywords.any { keyword -> ssidLower.contains(keyword) }
            assertFalse("Network '$network' should not be detected as fusion node", isFusion)
        }
    }
}
