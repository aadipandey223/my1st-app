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
        assertEquals("Source ID should match", frame.sourceId, parsedFrame.sourceId)
        assertEquals("Destination ID should match", frame.destinationId, parsedFrame.destinationId)
        assertEquals("Session ID should match", frame.sessionId, parsedFrame.sessionId)
        assertEquals("Sequence should match", frame.sequence, parsedFrame.sequence)
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
        val frame = sessionManager.encryptMessage(session, testMessage, 2)
        
        assertNotNull("Encrypted frame should not be null", frame)
        assertEquals("Frame type should be DATA", FrameType.DATA.value, frame.type)
        
        // Test message decryption
        val decryptedMessage = sessionManager.decryptMessage(session, frame)
        
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
        
        // Create first frame
        val frame1 = sessionManager.encryptMessage(session, "Message 1", 2)
        assertNotNull("First frame should not be null", frame1)
        
        // Create second frame
        val frame2 = sessionManager.encryptMessage(session, "Message 2", 2)
        assertNotNull("Second frame should not be null", frame2)
        
        // Verify sequence numbers are different
        assertNotEquals("Sequence numbers should be different", frame1.sequence, frame2.sequence)
        
        // Verify second frame has higher sequence
        assertTrue("Second frame should have higher sequence", frame2.sequence > frame1.sequence)
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
        assertEquals("Content type should match", payload.contentType, parsedPayload.contentType)
        assertEquals("Compression flag should match", payload.isCompressed, parsedPayload.isCompressed)
        assertEquals("Original size should match", payload.originalSize, parsedPayload.originalSize)
        assertArrayEquals("Data should match", payload.data, parsedPayload.data)
    }
}
