package com.codemobile.core.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class MessageRole { USER, ASSISTANT, SYSTEM, TOOL }

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = Session::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class Message(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val toolCalls: String? = null,
    val toolCallId: String? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)
