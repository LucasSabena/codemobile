package com.codemobile.core.data.repository

import com.codemobile.core.data.dao.MessageDao
import com.codemobile.core.data.dao.SessionDao
import com.codemobile.core.model.Message
import com.codemobile.core.model.Session
import com.codemobile.core.model.SessionMode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao
) {
    fun getByProject(projectId: String): Flow<List<Session>> =
        sessionDao.getByProject(projectId)

    suspend fun getById(id: String): Session? = sessionDao.getById(id)

    suspend fun getLastOpened(): Session? = sessionDao.getLastOpened()

    suspend fun create(
        projectId: String,
        title: String = "Nueva sesión",
        providerId: String = "",
        modelId: String = ""
    ): Session {
        val session = Session(
            projectId = projectId,
            title = title,
            providerId = providerId,
            modelId = modelId
        )
        sessionDao.insert(session)
        return session
    }

    suspend fun delete(session: Session) = sessionDao.delete(session)

    suspend fun updateTitle(sessionId: String, title: String) =
        sessionDao.updateTitle(sessionId, title)

    suspend fun updateProvider(sessionId: String, providerId: String, modelId: String) =
        sessionDao.updateProvider(sessionId, providerId, modelId)

    suspend fun updateMode(sessionId: String, mode: SessionMode) =
        sessionDao.updateMode(sessionId, mode)

    suspend fun updateTokens(sessionId: String, inputTokens: Int, outputTokens: Int) =
        sessionDao.updateTokens(sessionId, inputTokens, outputTokens)

    // Messages
    fun getMessages(sessionId: String): Flow<List<Message>> =
        messageDao.getBySession(sessionId)

    suspend fun getMessagesList(sessionId: String): List<Message> =
        messageDao.getBySessionList(sessionId)

    suspend fun addMessage(message: Message) =
        messageDao.insert(message)

    suspend fun clearMessages(sessionId: String) =
        messageDao.deleteBySession(sessionId)
}
