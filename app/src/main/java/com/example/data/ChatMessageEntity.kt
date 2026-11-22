package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["scanId"])]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val scanId: String,
    val isUser: Boolean,
    val text: String,
    val createdAtMillis: Long
)
