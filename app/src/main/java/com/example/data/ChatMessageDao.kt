package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE scanId = :scanId ORDER BY createdAtMillis ASC")
    suspend fun getMessagesForScan(scanId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(entity: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE scanId = :scanId")
    suspend fun deleteMessagesForScan(scanId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}
