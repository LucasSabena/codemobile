package com.codemobile.core.github

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.codemobile.core.data.repository.ProviderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitCloneManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val providerRepository: ProviderRepository
) {
    val projectsDir: String = File(context.filesDir, "projects").apply { mkdirs() }.absolutePath

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

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

    /**
     * Clone a repository by downloading its ZIP archive from GitHub.
     * Supports both local filesystem paths and SAF (content://) URIs as destination.
     */
    fun clone(
        repoUrl: String,
        destinationDir: String,
        repoName: String
    ): Flow<CloneEvent> = flow {
        emit(CloneEvent.Progress("Descargando repositorio..."))

        try {
            // Extract owner/repo from URL to use the GitHub API endpoint
            // which properly supports Bearer token auth (works for private repos)
            val cleanUrl = repoUrl.removeSuffix(".git").removeSuffix("/")
            val ownerRepo = extractOwnerRepo(cleanUrl)

            val zipStream = if (ownerRepo != null) {
                // Use GitHub API endpoint (supports auth for private repos)
                val (owner, repo) = ownerRepo
                downloadZip("https://api.github.com/repos/$owner/$repo/zipball/main")
                    ?: downloadZip("https://api.github.com/repos/$owner/$repo/zipball/master")
                    ?: downloadZip("https://api.github.com/repos/$owner/$repo/zipball")
            } else {
                // Fallback to web archive URL for non-GitHub URLs
                downloadZip("$cleanUrl/archive/refs/heads/main.zip")
                    ?: downloadZip("$cleanUrl/archive/refs/heads/master.zip")
            }

            if (zipStream == null) {
                emit(CloneEvent.Error("No se pudo descargar el repositorio. Verificá la URL y los permisos."))
                return@flow
            }

            emit(CloneEvent.Progress("Extrayendo archivos..."))

            val resultPath = if (destinationDir.startsWith("content://")) {
                extractZipToSaf(zipStream, destinationDir, repoName)
            } else {
                extractZipToLocal(zipStream, destinationDir, repoName)
            }

            if (resultPath != null) {
                emit(CloneEvent.Success(resultPath))
            } else {
                emit(CloneEvent.Error("Error al extraer los archivos del repositorio."))
            }

        } catch (e: Exception) {
            emit(CloneEvent.Error("Error: ${e.message ?: "Fallo desconocido"}"))
        }
    }.flowOn(Dispatchers.IO)

    private fun downloadZip(url: String): InputStream? {
        val token = providerRepository.getGitHubToken()?.takeIf { it.isNotBlank() }

        val isApiUrl = url.contains("api.github.com")
        val requestBuilder = Request.Builder().url(url)
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        // API endpoint needs octet-stream Accept to get the actual ZIP bytes
        if (isApiUrl) {
            requestBuilder.header("Accept", "application/vnd.github.v3+json")
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            response.close()
            return null
        }
        return response.body?.byteStream()
    }

    /**
     * Extracts owner and repo name from a GitHub URL.
     * e.g. "https://github.com/LucasSabena/binary2026" → Pair("LucasSabena", "binary2026")
     */
    private fun extractOwnerRepo(url: String): Pair<String, String>? {
        val regex = Regex("""github\.com[/:]([^/]+)/([^/]+)""")
        val match = regex.find(url) ?: return null
        val owner = match.groupValues[1]
        val repo = match.groupValues[2].removeSuffix(".git")
        return if (owner.isNotBlank() && repo.isNotBlank()) Pair(owner, repo) else null
    }

    /**
     * Extract a ZIP to local filesystem.
     * GitHub ZIPs contain a top-level folder like "repo-main/", we skip that prefix.
     */
    private fun extractZipToLocal(
        zipStream: InputStream,
        destinationDir: String,
        repoName: String
    ): String? {
        val targetDir = File(destinationDir, repoName)
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()

        val zis = ZipInputStream(zipStream)
        try {
            var topLevelPrefix: String? = null
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name

                if (topLevelPrefix == null && entryName.contains('/')) {
                    topLevelPrefix = entryName.substringBefore('/') + "/"
                }

                val relativePath = if (topLevelPrefix != null && entryName.startsWith(topLevelPrefix)) {
                    entryName.removePrefix(topLevelPrefix)
                } else {
                    entryName
                }

                if (relativePath.isNotBlank()) {
                    val outFile = File(targetDir, relativePath)
                    val isSafe = outFile.canonicalPath.startsWith(targetDir.canonicalPath)
                    if (isSafe) {
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { out -> zis.copyTo(out) }
                        }
                    }
                }
                entry = zis.nextEntry
            }
        } finally {
            zis.close()
        }

        return targetDir.absolutePath
    }

    /**
     * Extract a ZIP to a SAF destination (content:// URI).
     */
    private fun extractZipToSaf(
        zipStream: InputStream,
        destinationUri: String,
        repoName: String
    ): String? {
        val parentDoc = DocumentFile.fromTreeUri(context, Uri.parse(destinationUri))
            ?: return null

        val existing = parentDoc.findFile(repoName)
        val repoDir = if (existing != null && existing.isDirectory) {
            existing
        } else {
            parentDoc.createDirectory(repoName) ?: return null
        }

        val zis = ZipInputStream(zipStream)
        try {
            var topLevelPrefix: String? = null
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name

                if (topLevelPrefix == null && entryName.contains('/')) {
                    topLevelPrefix = entryName.substringBefore('/') + "/"
                }

                val relativePath = if (topLevelPrefix != null && entryName.startsWith(topLevelPrefix)) {
                    entryName.removePrefix(topLevelPrefix)
                } else {
                    entryName
                }

                if (relativePath.isNotBlank()) {
                    if (entry.isDirectory) {
                        ensureSafDirectory(repoDir, relativePath.trimEnd('/'))
                    } else {
                        val parentPath = relativePath.substringBeforeLast('/', "")
                        val fileName = relativePath.substringAfterLast('/')
                        val parentFolder = if (parentPath.isBlank()) repoDir
                        else ensureSafDirectory(repoDir, parentPath)

                        if (parentFolder != null) {
                            val mimeType = guessMimeType(fileName)
                            val existingFile = parentFolder.findFile(fileName)
                            existingFile?.delete()
                            val newFile = parentFolder.createFile(mimeType, fileName)
                            if (newFile != null) {
                                context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                                    zis.copyTo(out)
                                }
                            }
                        }
                    }
                }
                entry = zis.nextEntry
            }
        } finally {
            zis.close()
        }

        return repoDir.uri.toString()
    }

    private fun ensureSafDirectory(root: DocumentFile, relativePath: String): DocumentFile? {
        var current = root
        for (segment in relativePath.split('/').filter { it.isNotBlank() }) {
            val existing = current.findFile(segment)
            current = when {
                existing == null -> current.createDirectory(segment) ?: return null
                existing.isDirectory -> existing
                else -> return null
            }
        }
        return current
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js", "mjs" -> "application/javascript"
            "json" -> "application/json"
            "ts" -> "application/typescript"
            "kt" -> "text/x-kotlin"
            "java" -> "text/x-java"
            "xml" -> "application/xml"
            "md" -> "text/markdown"
            "txt" -> "text/plain"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "svg" -> "image/svg+xml"
            "gif" -> "image/gif"
            else -> "application/octet-stream"
        }
    }
}
