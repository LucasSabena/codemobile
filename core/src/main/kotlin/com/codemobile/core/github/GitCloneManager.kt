package com.codemobile.core.github

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitCloneManager @Inject constructor(
    @ApplicationContext context: Context
) {
    val projectsDir: String = File(context.filesDir, "projects").apply { mkdirs() }.absolutePath

    sealed class CloneEvent {
        data class Progress(val message: String) : CloneEvent()
        data class Success(val path: String) : CloneEvent()
        data class Error(val message: String) : CloneEvent()
    }

    fun normalizeGitUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("https://") || trimmed.startsWith("http://") -> trimmed
            trimmed.startsWith("git@") -> {
                val path = trimmed.removePrefix("git@").replace(':', '/')
                "https://$path".removeSuffix(".git")
            }
            trimmed.isNotEmpty() -> "https://github.com/$trimmed"
            else -> trimmed
        }
    }

    fun extractRepoName(url: String): String? {
        val clean = url.trim().removeSuffix("/").removeSuffix(".git")
        val lastSegment = clean.substringAfterLast('/', missingDelimiterValue = "")
        return lastSegment.takeIf { it.isNotBlank() }
    }

    fun clone(
        repoUrl: String,
        destinationDir: String,
        repoName: String
    ): Flow<CloneEvent> = flow {
        emit(CloneEvent.Progress("Preparando clonaci\u00f3n..."))

        val targetDir = File(destinationDir, repoName)
        if (targetDir.exists()) {
            emit(CloneEvent.Error("Ya existe una carpeta para '$repoName'."))
            return@flow
        }

        // Stub implementation: keeps the flow functional until native git clone is wired.
        targetDir.mkdirs()
        File(targetDir, "README.txt").writeText(
            "Repositorio pendiente de clonaci\u00f3n real.\nOrigen: $repoUrl\n"
        )

        emit(CloneEvent.Success(targetDir.absolutePath))
    }.flowOn(Dispatchers.IO)
}
