package com.example.groqchat

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    var title: String,
    val createdAt: Long
)

/**
 * A stored chat item. `kind` determines how the other fields are used:
 * - "text": role = "user"/"assistant", content = the message text
 * - "steps": content = step lines joined by "\n" (always shown as completed on reload)
 * - "artifact": title = artifact title, content = artifact body, meta = ArtifactType name, language = optional
 * - "apk": content = absolute file path to the built APK (file lives in app storage, survives restarts)
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val kind: String,
    val role: String? = null,
    val title: String? = null,
    val content: String,
    val meta: String? = null,
    val language: String? = null,
    val timestamp: Long
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY createdAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Insert
    suspend fun insertConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun renameConversation(id: String, title: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Insert
    suspend fun insertMessage(message: MessageEntity)
}

@Database(entities = [ConversationEntity::class, MessageEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "lone_ai.db"
                )
                    // Schema evolved to support persisting step logs/artifacts/apk results.
                    // No user data exists yet in fresh installs, so destructive migration is fine.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}

/** Derives a short chat title from the first user message. */
fun titleFromFirstMessage(text: String): String {
    val cleaned = text.trim().replace(Regex("\\s+"), " ")
    return if (cleaned.length <= 40) cleaned else cleaned.take(40).trimEnd() + "…"
}
