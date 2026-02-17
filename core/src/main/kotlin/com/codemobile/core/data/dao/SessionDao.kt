package com.codemobile.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.codemobile.core.model.Session
import com.codemobile.core.model.SessionMode
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun getByProject(projectId: String): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): Session?

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLastOpened(): Session?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: Session)

    @Update
    suspend fun update(session: Session)

    @Delete
    suspend fun delete(session: Session)

    @Query("""
        UPDATE sessions 
        SET updatedAt = :time, 
            totalInputTokens = totalInputTokens + :inputTokens, 
            totalOutputTokens = totalOutputTokens + :outputTokens 
        WHERE id = :sessionId
    """)
    suspend fun updateTokens(
        sessionId: String,
        inputTokens: Int,
        outputTokens: Int,
        time: Long = System.currentTimeMillis()
    )

    @Query("UPDATE sessions SET title = :title WHERE id = :sessionId")
    suspend fun updateTitle(sessionId: String, title: String)

    @Query("UPDATE sessions SET providerId = :providerId, modelId = :modelId WHERE id = :sessionId")
    suspend fun updateProvider(sessionId: String, providerId: String, modelId: String)

    @Query("UPDATE sessions SET mode = :mode WHERE id = :sessionId")
    suspend fun updateMode(sessionId: String, mode: SessionMode)
}
