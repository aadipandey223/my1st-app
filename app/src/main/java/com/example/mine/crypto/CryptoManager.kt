package com.example.mine.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Security
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class CryptoManager(private val context: Context) {
    
    companion object {
        private const val KEY_SIZE = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        private const val COMPRESSION_THRESHOLD = 1024 // 1KB
        private const val SESSION_KEY_LENGTH = 32
        private const val MAX_SEQUENCE = 0xFFFFFFFFL
    }
    
    init {
        Security.addProvider(BouncyCastleProvider())
    }
    
    // X25519 Key Generation
    fun generateX25519KeyPair(): KeyPair {
        return try {
            val keyPairGenerator = KeyPairGenerator.getInstance("X25519", "BC")
            keyPairGenerator.initialize(KEY_SIZE)
            val keyPair = keyPairGenerator.generateKeyPair()
            android.util.Log.d("CryptoManager", "X25519 key pair generated successfully")
            keyPair
        } catch (e: Exception) {
            android.util.Log.e("CryptoManager", "Failed to generate X25519 key pair", e)
            // Fallback to RSA if X25519 fails
            try {
                val rsaKeyGen = KeyPairGenerator.getInstance("RSA")
                rsaKeyGen.initialize(2048)
                val rsaKeyPair = rsaKeyGen.generateKeyPair()
                android.util.Log.d("CryptoManager", "RSA key pair generated as fallback")
                rsaKeyPair
            } catch (fallbackException: Exception) {
                android.util.Log.e("CryptoManager", "Both X25519 and RSA key generation failed", fallbackException)
                throw fallbackException
            }
        }
    }
    
    // ECDH Key Exchange
    fun computeSharedSecret(privateKey: java.security.PrivateKey, publicKey: java.security.PublicKey): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(org.bouncycastle.crypto.params.X25519PrivateKeyParameters(privateKey.encoded))
        val sharedSecret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(org.bouncycastle.crypto.params.X25519PublicKeyParameters(publicKey.encoded), sharedSecret, 0)
        return sharedSecret
    }
    
    // HKDF-SHA256 Session Key Derivation
    fun deriveSessionKeys(
        sharedSecret: ByteArray,
        salt: ByteArray,
        info: String = "v1-session-keys"
    ): SessionKeys {
        val hkdf = HKDFBytesGenerator(org.bouncycastle.crypto.digests.SHA256Digest())
        val params = HKDFParameters(sharedSecret, salt, info.toByteArray())
        hkdf.init(params)
        
        val sessionKeyMaterial = ByteArray(SESSION_KEY_LENGTH * 2)
        hkdf.generateBytes(sessionKeyMaterial, 0, sessionKeyMaterial.size)
        
        return SessionKeys(
            kTx = sessionKeyMaterial.copyOfRange(0, SESSION_KEY_LENGTH),
            kRx = sessionKeyMaterial.copyOfRange(SESSION_KEY_LENGTH, sessionKeyMaterial.size)
        )
    }
    
    // Java Deflate Compression
    fun compressData(data: ByteArray): ByteArray {
        if (data.size < COMPRESSION_THRESHOLD) {
            return data
        }
        
        return try {
            val deflater = Deflater()
            deflater.setInput(data)
            deflater.finish()
            
            val compressedData = ByteArray(data.size)
            val compressedSize = deflater.deflate(compressedData)
            deflater.end()
            
            if (compressedSize < data.size) {
                compressedData.copyOfRange(0, compressedSize)
            } else {
                data // Return original if compression doesn't help
            }
        } catch (e: Exception) {
            data // Return original if compression fails
        }
    }
    
    // Java Inflate Decompression
    fun decompressData(compressedData: ByteArray, originalSize: Int): ByteArray {
        return try {
            val inflater = Inflater()
            inflater.setInput(compressedData)
            
            val decompressedData = ByteArray(originalSize)
            val decompressedSize = inflater.inflate(decompressedData)
            inflater.end()
            
            decompressedData.copyOfRange(0, decompressedSize)
        } catch (e: Exception) {
            compressedData // Return original if decompression fails
        }
    }
    
    // AES-GCM Encryption with AAD - improved error handling
    fun encryptWithAAD(
        sessionKey: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
        nonce: ByteArray
    ): EncryptionResult {
        return try {
            // Validate inputs
            require(sessionKey.size == 32) { "Session key must be 32 bytes (256 bits)" }
            require(plaintext.isNotEmpty()) { "Plaintext cannot be empty" }
            require(nonce.size == 12) { "Nonce must be 12 bytes for AES-GCM" }
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(sessionKey, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce)
            
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            cipher.updateAAD(aad)
            
            val ciphertext = cipher.doFinal(plaintext)
            
            android.util.Log.d("CryptoManager", "Encrypted ${plaintext.size} bytes to ${ciphertext.size} bytes")
            EncryptionResult(ciphertext, cipher.iv)
        } catch (e: Exception) {
            android.util.Log.e("CryptoManager", "Encryption failed: ${e.message}", e)
            throw e
        }
    }
    
    // AES-GCM Decryption with AAD - improved error handling
    fun decryptWithAAD(
        sessionKey: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray,
        nonce: ByteArray
    ): ByteArray? {
        return try {
            // Validate inputs
            require(sessionKey.size == 32) { "Session key must be 32 bytes (256 bits)" }
            require(ciphertext.isNotEmpty()) { "Ciphertext cannot be empty" }
            require(nonce.size == 12) { "Nonce must be 12 bytes for AES-GCM" }
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(sessionKey, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce)
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            cipher.updateAAD(aad)
            
            val plaintext = cipher.doFinal(ciphertext)
            
            android.util.Log.d("CryptoManager", "Decrypted ${ciphertext.size} bytes to ${plaintext.size} bytes")
            plaintext
        } catch (e: javax.crypto.AEADBadTagException) {
            android.util.Log.e("CryptoManager", "Decryption failed - authentication tag mismatch", e)
            null
        } catch (e: javax.crypto.BadPaddingException) {
            android.util.Log.e("CryptoManager", "Decryption failed - bad padding", e)
            null
        } catch (e: javax.crypto.IllegalBlockSizeException) {
            android.util.Log.e("CryptoManager", "Decryption failed - illegal block size", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("CryptoManager", "Decryption failed: ${e.message}", e)
            null
        }
    }
    
    // Generate unique nonce with improved security
    fun generateNonce(sessionId: Int, senderId: Int, sequence: Int): ByteArray {
        return try {
            // Validate inputs
            require(sessionId > 0) { "Session ID must be positive" }
            require(senderId > 0) { "Sender ID must be positive" }
            require(sequence >= 0) { "Sequence must be non-negative" }
            
            val nonce = ByteArray(12)
            
            // Use first 8 bytes for deterministic part (session + sender + sequence)
            nonce[0] = (sessionId shr 24).toByte()
            nonce[1] = (sessionId shr 16).toByte()
            nonce[2] = (sessionId shr 8).toByte()
            nonce[3] = sessionId.toByte()
            nonce[4] = (senderId shr 24).toByte()
            nonce[5] = (senderId shr 16).toByte()
            nonce[6] = (senderId shr 8).toByte()
            nonce[7] = senderId.toByte()
            nonce[8] = (sequence shr 24).toByte()
            nonce[9] = (sequence shr 16).toByte()
            nonce[10] = (sequence shr 8).toByte()
            nonce[11] = sequence.toByte()
            
            // Add some entropy to prevent predictability
            // In production, consider using SecureRandom for the last 4 bytes
            val entropy = (System.nanoTime() % 0xFFFFFFFFL).toInt()
            nonce[8] = (nonce[8].toInt() xor (entropy shr 24)).toByte()
            nonce[9] = (nonce[9].toInt() xor (entropy shr 16)).toByte()
            nonce[10] = (nonce[10].toInt() xor (entropy shr 8)).toByte()
            nonce[11] = (nonce[11].toInt() xor entropy).toByte()
            
            android.util.Log.d("CryptoManager", "Generated nonce for session=$sessionId, sender=$senderId, seq=$sequence")
            nonce
        } catch (e: Exception) {
            android.util.Log.e("CryptoManager", "Failed to generate nonce", e)
            throw e
        }
    }
    
    // Generate cryptographically secure random nonce
    fun generateSecureNonce(): ByteArray {
        return try {
            val nonce = ByteArray(12)
            val secureRandom = java.security.SecureRandom()
            secureRandom.nextBytes(nonce)
            
            android.util.Log.d("CryptoManager", "Generated secure random nonce")
            nonce
        } catch (e: Exception) {
            android.util.Log.e("CryptoManager", "Failed to generate secure nonce", e)
            throw e
        }
    }
    
    // Secure key storage in Android Keystore
    fun storeKeyInKeystore(alias: String, key: SecretKey) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        if (!keyStore.containsAlias(alias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }
    
    // Clean up sensitive data
    fun secureWipe(data: ByteArray) {
        for (i in data.indices) {
            data[i] = 0
        }
    }
}

data class SessionKeys(
    val kTx: ByteArray, // Transmit key
    val kRx: ByteArray  // Receive key
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as SessionKeys
        return kTx.contentEquals(other.kTx) && kRx.contentEquals(other.kRx)
    }
    
    override fun hashCode(): Int {
        var result = kTx.contentHashCode()
        result = 31 * result + kRx.contentHashCode()
        return result
    }
}

data class EncryptionResult(
    val ciphertext: ByteArray,
    val iv: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as EncryptionResult
        return ciphertext.contentEquals(other.ciphertext) && iv.contentEquals(other.iv)
    }
    
    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}
