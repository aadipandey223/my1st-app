package com.example.mine.data

import android.content.Context
import androidx.room.*
import java.util.Date

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Int,
    val senderId: Int,
    val receiverId: Int,
    val content: String,
    val encryptedContent: ByteArray,
    val timestamp: Date,
    val isEncrypted: Boolean = true,
    val isCompressed: Boolean = false,
    val messageType: String = "TEXT",
    val status: String = "SENT" // SENT, DELIVERED, READ, FAILED
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as Message
        return id == other.id &&
                sessionId == other.sessionId &&
                senderId == other.senderId &&
                receiverId == other.receiverId &&
                content == other.content &&
                encryptedContent.contentEquals(other.encryptedContent) &&
                timestamp == other.timestamp &&
                isEncrypted == other.isEncrypted &&
                isCompressed == other.isCompressed &&
                messageType == other.messageType &&
                status == other.status
    }
    
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + sessionId
        result = 31 * result + senderId
        result = 31 * result + receiverId
        result = 31 * result + content.hashCode()
        result = 31 * result + encryptedContent.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + isEncrypted.hashCode()
        result = 31 * result + isCompressed.hashCode()
        result = 31 * result + messageType.hashCode()
        result = 31 * result + status.hashCode()
        return result
    }
}

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val publicKey: ByteArray,
    val name: String,
    val deviceId: Int,
    val lastSeen: Date,
    val isOnline: Boolean = false,
    val trustLevel: Int = 1 // 1-5 scale
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as Contact
        return id == other.id &&
                publicKey.contentEquals(other.publicKey) &&
                name == other.name &&
                deviceId == other.deviceId &&
                lastSeen == other.lastSeen &&
                isOnline == other.isOnline &&
                trustLevel == other.trustLevel
    }
    
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + deviceId
        result = 31 * result + lastSeen.hashCode()
        result = 31 * result + isOnline.hashCode()
        result = 31 * result + trustLevel
        return result
    }
}

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: Int,
    val peerPublicKey: ByteArray,
    val createdAt: Date,
    val lastActivity: Date,
    val isActive: Boolean = true,
    val messageCount: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as SessionEntity
        return id == other.id &&
                peerPublicKey.contentEquals(other.peerPublicKey) &&
                createdAt == other.createdAt &&
                lastActivity == other.lastActivity &&
                isActive == other.isActive &&
                messageCount == other.messageCount
    }
    
    override fun hashCode(): Int {
        var result = id
        result = 31 * result + peerPublicKey.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + lastActivity.hashCode()
        result = 31 * result + isActive.hashCode()
        result = 31 * result + messageCount
        return result
    }
}

@Entity(tableName = "device_keys")
data class DeviceKey(
    @PrimaryKey val id: Int = 1,
    val publicKey: ByteArray,
    val privateKeyAlias: String, // Reference to Android Keystore
    val createdAt: Date,
    val lastUsed: Date,
    val keyType: String = "X25519"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as DeviceKey
        return id == other.id &&
                publicKey.contentEquals(other.publicKey) &&
                privateKeyAlias == other.privateKeyAlias &&
                createdAt == other.createdAt &&
                lastUsed == other.lastUsed &&
                keyType == other.keyType
    }
    
    override fun hashCode(): Int {
        var result = id
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + privateKeyAlias.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + lastUsed.hashCode()
        result = 31 * result + keyType.hashCode()
        return result
    }
}

// Type converters for Room
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }
    
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
    
    @TypeConverter
    fun fromByteArray(value: ByteArray?): String? {
        return value?.let { android.util.Base64.encodeToString(it, android.util.Base64.DEFAULT) }
    }
    
    @TypeConverter
    fun toByteArray(value: String?): ByteArray? {
        return value?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }
    }
}

// DAOs (Data Access Objects)
@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    suspend fun getAllMessages(): List<Message>
    
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun getMessagesBySession(sessionId: Int): List<Message>
    
    @Query("SELECT * FROM messages WHERE (senderId = :userId OR receiverId = :userId) ORDER BY timestamp DESC")
    suspend fun getAllMessagesForUser(userId: Int): List<Message>
    
    @Insert
    suspend fun insertMessage(message: Message): Long
    
    @Update
    suspend fun updateMessage(message: Message)
    
    @Delete
    suspend fun deleteMessage(message: Message)
    
    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySession(sessionId: Int)
    
    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId")
    suspend fun getMessageCountForSession(sessionId: Int): Int
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    suspend fun getAllContacts(): List<Contact>
    
    @Query("SELECT * FROM contacts WHERE deviceId = :deviceId")
    suspend fun getContactByDeviceId(deviceId: Int): Contact?
    
    @Insert
    suspend fun insertContact(contact: Contact): Long
    
    @Update
    suspend fun updateContact(contact: Contact)
    
    @Delete
    suspend fun deleteContact(contact: Contact)
    
    @Query("UPDATE contacts SET isOnline = :isOnline, lastSeen = :lastSeen WHERE deviceId = :deviceId")
    suspend fun updateContactStatus(deviceId: Int, isOnline: Boolean, lastSeen: Date)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE isActive = 1 ORDER BY lastActivity DESC")
    suspend fun getActiveSessions(): List<SessionEntity>
    
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Int): SessionEntity?
    
    @Insert
    suspend fun insertSession(session: SessionEntity): Long
    
    @Update
    suspend fun updateSession(session: SessionEntity)
    
    @Query("UPDATE sessions SET isActive = 0 WHERE id = :sessionId")
    suspend fun deactivateSession(sessionId: Int)
    
    @Query("UPDATE sessions SET lastActivity = :lastActivity, messageCount = messageCount + 1 WHERE id = :sessionId")
    suspend fun updateSessionActivity(sessionId: Int, lastActivity: Date)
}

@Dao
interface DeviceKeyDao {
    @Query("SELECT * FROM device_keys WHERE id = 1")
    suspend fun getDeviceKey(): DeviceKey?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeviceKey(deviceKey: DeviceKey): Long
    
    @Update
    suspend fun updateDeviceKey(deviceKey: DeviceKey)
    
    @Query("UPDATE device_keys SET lastUsed = :lastUsed WHERE id = 1")
    suspend fun updateLastUsed(lastUsed: Date)
}

// Main database class
@Database(
    entities = [Message::class, Contact::class, SessionEntity::class, DeviceKey::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MessageDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun contactDao(): ContactDao
    abstract fun sessionDao(): SessionDao
    abstract fun deviceKeyDao(): DeviceKeyDao
    
    companion object {
        @Volatile
        private var INSTANCE: MessageDatabase? = null
        
        fun getDatabase(context: Context): MessageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MessageDatabase::class.java,
                    "secure_message_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
