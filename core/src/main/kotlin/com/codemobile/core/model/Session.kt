package com.codemobile.core.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class SessionMode { BUILD, PLAN }

@Entity(
    tableName = "sessions",
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class Session(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val title: String = "Nueva sesión",
    val providerId: String = "",
    val modelId: String = "",
    val mode: SessionMode = SessionMode.BUILD,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val totalInputTokens: Int = 0,
    val totalOutputTokens: Int = 0
)
