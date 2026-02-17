package com.codemobile.core.data.repository

import com.codemobile.core.data.dao.ProjectDao
import com.codemobile.core.model.Project
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao
) {
    fun getAll(): Flow<List<Project>> = projectDao.getAll()

    suspend fun getById(id: String): Project? = projectDao.getById(id)

    suspend fun create(name: String, path: String): Project {
        val project = Project(name = name, path = path)
        projectDao.insert(project)
        return project
    }

    suspend fun update(project: Project) = projectDao.update(project)

    suspend fun delete(project: Project) = projectDao.delete(project)

    suspend fun markOpened(id: String) = projectDao.updateLastOpened(id)
}
