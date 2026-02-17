package com.codemobile.ai.tools

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.codemobile.ai.model.AIToolCall
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Executes AI tool calls against the project filesystem and terminal.
 * Supports both local file paths and SAF tree roots (`content://...`).
 *
 * @param projectRootPath Project root path. Can be a filesystem path or SAF tree URI.
 * @param context Android context required when using SAF roots.
 * @param shellCommand Path to the shell binary (e.g. sh, bash).
 * @param environment Environment variables for command execution.
 */
class ToolExecutor(
    private val projectRootPath: String,
    private val context: Context? = null,
    private val shellCommand: String = "/system/bin/sh",
    private val environment: Map<String, String> = emptyMap()
) {

    companion object {
        private const val TAG = "ToolExecutor"
        private const val MAX_FILE_SIZE = 512 * 1024 // 512 KB
        private const val MAX_OUTPUT_CHARS = 32_000
        private const val MAX_SEARCH_RESULTS = 50
    }

    private val isSafProject = projectRootPath.startsWith("content://")
    private val projectRootFile = if (isSafProject) null else File(projectRootPath)
    private val projectRootUri = if (isSafProject) Uri.parse(projectRootPath) else null

    /**
     * Execute a tool call and return the result as a string.
     * Never throws - errors are returned as descriptive strings.
     */
    fun execute(toolCall: AIToolCall): ToolResult {
        val args = try {
            JsonParser.parseString(toolCall.arguments).asJsonObject
        } catch (e: Exception) {
            return ToolResult(
                output = "Error: Invalid tool arguments JSON: ${toolCall.arguments.take(200)}",
                success = false
            )
        }

        Log.d(TAG, "Executing tool: ${toolCall.name} with args: ${toolCall.arguments.take(500)}")

        return try {
            when (toolCall.name) {
                AgentTools.READ_FILE -> {
                    val path = args.get("path")?.asString
                        ?: return ToolResult("Error: 'path' parameter is required.", false)
                    val startLine = args.get("start_line")?.asInt
                    val endLine = args.get("end_line")?.asInt
                    readFile(path, startLine, endLine)
                }

                AgentTools.WRITE_FILE -> {
                    val path = args.get("path")?.asString
                        ?: return ToolResult("Error: 'path' parameter is required.", false)
                    val content = args.get("content")?.asString
                        ?: return ToolResult("Error: 'content' parameter is required.", false)
                    writeFile(path, content)
                }

                AgentTools.EDIT_FILE -> {
                    val path = args.get("path")?.asString
                        ?: return ToolResult("Error: 'path' parameter is required.", false)
                    val oldString = args.get("old_string")?.asString
                        ?: return ToolResult("Error: 'old_string' parameter is required.", false)
                    val newString = args.get("new_string")?.asString
                        ?: return ToolResult("Error: 'new_string' parameter is required.", false)
                    editFile(path, oldString, newString)
                }

                AgentTools.DELETE_FILE -> {
                    val path = args.get("path")?.asString
                        ?: return ToolResult("Error: 'path' parameter is required.", false)
                    deleteFile(path)
                }

                AgentTools.LIST_DIR -> {
                    val path = args.get("path")?.asString ?: "."
                    val recursive = args.get("recursive")?.asBoolean ?: false
                    listDirectory(path, recursive)
                }

                AgentTools.RUN_COMMAND -> {
                    val command = args.get("command")?.asString
                        ?: return ToolResult("Error: 'command' parameter is required.", false)
                    val cwd = args.get("cwd")?.asString
                    runCommand(command, cwd)
                }

                AgentTools.SEARCH_FILES -> {
                    val pattern = args.get("pattern")?.asString
                        ?: return ToolResult("Error: 'pattern' parameter is required.", false)
                    val path = args.get("path")?.asString
                    val filePattern = args.get("file_pattern")?.asString
                    searchFiles(pattern, path, filePattern)
                }

                else -> ToolResult("Error: Unknown tool '${toolCall.name}'.", false)
            }
        } catch (e: SecurityException) {
            ToolResult("Error: Permission denied: ${e.message}", false)
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution error: ${toolCall.name}", e)
            ToolResult("Error executing ${toolCall.name}: ${e.message}", false)
        }
    }

    private fun readFile(path: String, startLine: Int?, endLine: Int?): ToolResult {
        return if (isSafProject) readFileSaf(path, startLine, endLine) else readFileLocal(path, startLine, endLine)
    }

    private fun writeFile(path: String, content: String): ToolResult {
        return if (isSafProject) writeFileSaf(path, content) else writeFileLocal(path, content)
    }

    private fun editFile(path: String, oldString: String, newString: String): ToolResult {
        return if (isSafProject) editFileSaf(path, oldString, newString) else editFileLocal(path, oldString, newString)
    }

    private fun deleteFile(path: String): ToolResult {
        return if (isSafProject) deleteFileSaf(path) else deleteFileLocal(path)
    }

    private fun listDirectory(path: String, recursive: Boolean): ToolResult {
        return if (isSafProject) listDirectorySaf(path, recursive) else listDirectoryLocal(path, recursive)
    }

    private fun runCommand(command: String, cwd: String?): ToolResult {
        if (isSafProject) {
            return ToolResult(
                "Error: run_command is not supported for SAF content:// projects. Use a filesystem-based project path for shell commands.",
                false
            )
        }
        return runCommandLocal(command, cwd)
    }

    private fun searchFiles(pattern: String, basePath: String?, filePattern: String?): ToolResult {
        return if (isSafProject) searchFilesSaf(pattern, basePath, filePattern)
        else searchFilesLocal(pattern, basePath, filePattern)
    }

    // Local filesystem implementation

    private fun readFileLocal(path: String, startLine: Int?, endLine: Int?): ToolResult {
        val file = resolveLocalPath(path)
        if (!file.exists()) return ToolResult("Error: File not found: $path", false)
        if (!file.isFile) return ToolResult("Error: '$path' is a directory, use list_directory instead.", false)
        if (file.length() > MAX_FILE_SIZE) {
            return ToolResult(
                "Error: File too large (${file.length()} bytes). Use start_line/end_line to read a portion.",
                false
            )
        }

        val content = file.readText()
        return formatReadResult(path, content, startLine, endLine)
    }

    private fun writeFileLocal(path: String, content: String): ToolResult {
        val file = resolveLocalPath(path)

        file.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }

        val existed = file.exists()
        file.writeText(content)

        val lineCount = lineCount(content)
        return ToolResult(
            if (existed) "Updated file: $path ($lineCount lines written)"
            else "Created file: $path ($lineCount lines written)",
            true
        )
    }

    private fun editFileLocal(path: String, oldString: String, newString: String): ToolResult {
        val file = resolveLocalPath(path)
        if (!file.exists()) return ToolResult("Error: File not found: $path", false)
        if (!file.isFile) return ToolResult("Error: '$path' is not a file.", false)

        val content = file.readText()
        val occurrences = countOccurrences(content, oldString)

        return when (occurrences) {
            0 -> ToolResult(
                "Error: Could not find the specified text in $path. Make sure old_string matches exactly including whitespace and indentation.",
                false
            )

            1 -> {
                val newContent = content.replace(oldString, newString)
                file.writeText(newContent)
                ToolResult("Edited file: $path (replaced 1 occurrence)", true)
            }

            else -> ToolResult(
                "Error: Found $occurrences occurrences of old_string in $path. Include more context to make the match unique.",
                false
            )
        }
    }

    private fun deleteFileLocal(path: String): ToolResult {
        val root = projectRootFile ?: return ToolResult("Error: Invalid local project root.", false)
        val file = resolveLocalPath(path)
        if (!file.exists()) return ToolResult("Error: File not found: $path", false)

        val canonical = file.canonicalFile
        val rootCanonical = root.canonicalFile
        if (canonical == rootCanonical || !canonical.path.startsWith(rootCanonical.path)) {
            return ToolResult("Error: Cannot delete files outside the project root.", false)
        }

        return if (file.isDirectory) {
            if (file.listFiles()?.isNotEmpty() == true) {
                ToolResult("Error: Directory is not empty. Delete contents first.", false)
            } else {
                val deleted = file.delete()
                if (deleted) ToolResult("Deleted directory: $path", true)
                else ToolResult("Error: Failed to delete directory: $path", false)
            }
        } else {
            val deleted = file.delete()
            if (deleted) ToolResult("Deleted file: $path", true)
            else ToolResult("Error: Failed to delete file: $path", false)
        }
    }

    private fun listDirectoryLocal(path: String, recursive: Boolean): ToolResult {
        val dir = resolveLocalPath(path)
        if (!dir.exists()) return ToolResult("Error: Directory not found: $path", false)
        if (!dir.isDirectory) return ToolResult("Error: '$path' is a file, not a directory.", false)

        val output = StringBuilder()
        if (recursive) {
            buildTreeStringLocal(dir, output, "", maxEntries = 200)
        } else {
            val children = dir.listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?: emptyList()

            for (child in children) {
                val suffix = if (child.isDirectory) "/" else ""
                output.appendLine("${child.name}$suffix")
            }
        }

        if (output.isEmpty()) return ToolResult("Directory is empty: $path", true)
        return ToolResult(output.toString().trimEnd(), true)
    }

    private fun runCommandLocal(command: String, cwd: String?): ToolResult {
        val root = projectRootFile ?: return ToolResult("Error: Invalid local project root.", false)
        val workingDir = if (cwd != null) resolveLocalPath(cwd) else root
        if (!workingDir.exists() || !workingDir.isDirectory) {
            return ToolResult("Error: Working directory not found: ${workingDir.path}", false)
        }

        return try {
            val pb = ProcessBuilder(shellCommand, "-c", command)
                .directory(workingDir)
                .redirectErrorStream(true)

            pb.environment().clear()
            environment.forEach { (k, v) -> pb.environment()[k] = v }

            val process = pb.start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            val truncatedOutput = output.take(MAX_OUTPUT_CHARS)
            val truncated = if (output.length > MAX_OUTPUT_CHARS) "\n... (output truncated)" else ""

            ToolResult(
                buildString {
                    appendLine("Exit code: $exitCode")
                    if (truncatedOutput.isNotEmpty()) {
                        append(truncatedOutput)
                        append(truncated)
                    } else {
                        append("(no output)")
                    }
                }.trimEnd(),
                exitCode == 0
            )
        } catch (e: Exception) {
            ToolResult("Error executing command: ${e.message}", false)
        }
    }

    private fun searchFilesLocal(pattern: String, basePath: String?, filePattern: String?): ToolResult {
        val root = projectRootFile ?: return ToolResult("Error: Invalid local project root.", false)
        val dir = resolveLocalPath(basePath ?: ".")
        if (!dir.exists() || !dir.isDirectory) {
            return ToolResult("Error: Directory not found: ${basePath ?: "."}", false)
        }

        val results = mutableListOf<String>()
        val regex = try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (_: Exception) {
            Regex(Regex.escape(pattern), RegexOption.IGNORE_CASE)
        }

        searchRecursiveLocal(dir, root, regex, filePattern, results)

        return if (results.isEmpty()) {
            ToolResult("No matches found for '$pattern'.", true)
        } else {
            val header = "Found ${results.size} match${if (results.size > 1) "es" else ""}:\n"
            ToolResult(header + results.joinToString("\n"), true)
        }
    }

    // SAF implementation

    private fun readFileSaf(path: String, startLine: Int?, endLine: Int?): ToolResult = withSafRoot { ctx, root ->
        val relativePath = resolveSafRelativePath(path)
            ?: return@withSafRoot ToolResult("Error: Invalid path for SAF project: $path", false)
        val target = findSafDocument(root, relativePath)
            ?: return@withSafRoot ToolResult("Error: File not found: $path", false)
        if (!target.isFile) return@withSafRoot ToolResult("Error: '$path' is a directory, use list_directory instead.", false)

        val size = target.length()
        if (size > MAX_FILE_SIZE) {
            return@withSafRoot ToolResult(
                "Error: File too large ($size bytes). Use start_line/end_line to read a portion.",
                false
            )
        }

        val read = readTextSaf(ctx, target.uri, MAX_FILE_SIZE)
            ?: return@withSafRoot ToolResult("Error: Unable to read file: $path", false)
        if (read.exceededLimit) {
            return@withSafRoot ToolResult(
                "Error: File too large (over $MAX_FILE_SIZE bytes). Use start_line/end_line to read a portion.",
                false
            )
        }

        formatReadResult(path, read.text, startLine, endLine)
    }

    private fun writeFileSaf(path: String, content: String): ToolResult = withSafRoot { ctx, root ->
        val relativePath = resolveSafRelativePath(path)
            ?: return@withSafRoot ToolResult("Error: Invalid path for SAF project: $path", false)
        if (relativePath.isBlank()) return@withSafRoot ToolResult("Error: Path must point to a file, not project root.", false)

        val parentPath = relativePath.substringBeforeLast("/", "")
        val fileName = relativePath.substringAfterLast("/")
        if (fileName.isBlank()) return@withSafRoot ToolResult("Error: Invalid file name in path: $path", false)

        val parentDir = ensureSafDirectory(root, parentPath)
            ?: return@withSafRoot ToolResult("Error: Could not create parent directories for: $path", false)

        val existing = parentDir.findFile(fileName)
        if (existing != null && existing.isDirectory) {
            return@withSafRoot ToolResult("Error: '$path' is a directory.", false)
        }

        val target = existing ?: parentDir.createFile(mimeTypeForName(fileName), fileName)
        if (target == null) return@withSafRoot ToolResult("Error: Could not create file: $path", false)

        val stream = ctx.contentResolver.openOutputStream(target.uri, "wt")
            ?: return@withSafRoot ToolResult("Error: Could not open file for writing: $path", false)

        stream.use {
            it.write(content.toByteArray(Charsets.UTF_8))
        }

        val lineCount = lineCount(content)
        ToolResult(
            if (existing != null) "Updated file: $path ($lineCount lines written)"
            else "Created file: $path ($lineCount lines written)",
            true
        )
    }

    private fun editFileSaf(path: String, oldString: String, newString: String): ToolResult = withSafRoot { ctx, root ->
        val relativePath = resolveSafRelativePath(path)
            ?: return@withSafRoot ToolResult("Error: Invalid path for SAF project: $path", false)
        val target = findSafDocument(root, relativePath)
            ?: return@withSafRoot ToolResult("Error: File not found: $path", false)
        if (!target.isFile) return@withSafRoot ToolResult("Error: '$path' is not a file.", false)

        val read = readTextSaf(ctx, target.uri, MAX_FILE_SIZE)
            ?: return@withSafRoot ToolResult("Error: Unable to read file: $path", false)
        if (read.exceededLimit) {
            return@withSafRoot ToolResult("Error: File too large (over $MAX_FILE_SIZE bytes).", false)
        }

        val content = read.text
        val occurrences = countOccurrences(content, oldString)

        when (occurrences) {
            0 -> ToolResult(
                "Error: Could not find the specified text in $path. Make sure old_string matches exactly including whitespace and indentation.",
                false
            )

            1 -> {
                val newContent = content.replace(oldString, newString)
                val stream = ctx.contentResolver.openOutputStream(target.uri, "wt")
                    ?: return@withSafRoot ToolResult("Error: Could not open file for writing: $path", false)
                stream.use { it.write(newContent.toByteArray(Charsets.UTF_8)) }
                ToolResult("Edited file: $path (replaced 1 occurrence)", true)
            }

            else -> ToolResult(
                "Error: Found $occurrences occurrences of old_string in $path. Include more context to make the match unique.",
                false
            )
        }
    }

    private fun deleteFileSaf(path: String): ToolResult = withSafRoot { _, root ->
        val relativePath = resolveSafRelativePath(path)
            ?: return@withSafRoot ToolResult("Error: Invalid path for SAF project: $path", false)
        if (relativePath.isBlank()) return@withSafRoot ToolResult("Error: Cannot delete project root.", false)

        val target = findSafDocument(root, relativePath)
            ?: return@withSafRoot ToolResult("Error: File not found: $path", false)

        if (target.isDirectory && target.listFiles().isNotEmpty()) {
            return@withSafRoot ToolResult("Error: Directory is not empty. Delete contents first.", false)
        }

        val wasDirectory = target.isDirectory
        val deleted = target.delete()
        if (!deleted) return@withSafRoot ToolResult("Error: Failed to delete: $path", false)

        if (wasDirectory) ToolResult("Deleted directory: $path", true)
        else ToolResult("Deleted file: $path", true)
    }

    private fun listDirectorySaf(path: String, recursive: Boolean): ToolResult = withSafRoot { _, root ->
        val relativePath = resolveSafRelativePath(path)
            ?: return@withSafRoot ToolResult("Error: Invalid path for SAF project: $path", false)
        val dir = findSafDocument(root, relativePath)
            ?: return@withSafRoot ToolResult("Error: Directory not found: $path", false)
        if (!dir.isDirectory) return@withSafRoot ToolResult("Error: '$path' is a file, not a directory.", false)

        val output = StringBuilder()
        if (recursive) {
            buildTreeStringSaf(dir, output, "", maxEntries = 200)
        } else {
            val children = dir.listFiles()
                .sortedWith(compareBy({ !it.isDirectory }, { (it.name ?: "").lowercase() }))
            for (child in children) {
                val suffix = if (child.isDirectory) "/" else ""
                output.appendLine("${child.name ?: "(unnamed)"}$suffix")
            }
        }

        if (output.isEmpty()) return@withSafRoot ToolResult("Directory is empty: $path", true)
        ToolResult(output.toString().trimEnd(), true)
    }

    private fun searchFilesSaf(pattern: String, basePath: String?, filePattern: String?): ToolResult = withSafRoot { ctx, root ->
        val relativePath = resolveSafRelativePath(basePath ?: ".")
            ?: return@withSafRoot ToolResult("Error: Invalid path for SAF project: ${basePath ?: "."}", false)
        val dir = findSafDocument(root, relativePath)
            ?: return@withSafRoot ToolResult("Error: Directory not found: ${basePath ?: "."}", false)
        if (!dir.isDirectory) return@withSafRoot ToolResult("Error: '${basePath ?: "."}' is a file, not a directory.", false)

        val results = mutableListOf<String>()
        val regex = try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (_: Exception) {
            Regex(Regex.escape(pattern), RegexOption.IGNORE_CASE)
        }

        val startPrefix = relativePath.trim('/').takeIf { it.isNotBlank() } ?: ""
        searchRecursiveSaf(
            context = ctx,
            dir = dir,
            currentRelative = startPrefix,
            pattern = regex,
            fileGlob = filePattern,
            results = results
        )

        if (results.isEmpty()) {
            ToolResult("No matches found for '$pattern'.", true)
        } else {
            val header = "Found ${results.size} match${if (results.size > 1) "es" else ""}:\n"
            ToolResult(header + results.joinToString("\n"), true)
        }
    }

    // Shared helpers

    private fun formatReadResult(path: String, content: String, startLine: Int?, endLine: Int?): ToolResult {
        val lines = if (content.isEmpty()) emptyList() else content.split("\n")
        val totalLines = lines.size

        if (totalLines == 0) {
            return ToolResult("File: $path (0 lines)\n", true)
        }

        val start = (startLine ?: 1).coerceIn(1, totalLines)
        val end = (endLine ?: totalLines).coerceIn(start, totalLines)

        val selectedLines = lines.subList(start - 1, end)
        val selectedContent = selectedLines.joinToString("\n")

        val header = if (startLine != null || endLine != null) {
            "File: $path (lines $start-$end of $totalLines)\n"
        } else {
            "File: $path ($totalLines lines)\n"
        }

        return ToolResult(header + selectedContent, true)
    }

    private fun lineCount(content: String): Int {
        return content.count { it == '\n' } + if (content.isNotEmpty()) 1 else 0
    }

    private fun resolveLocalPath(path: String): File {
        val root = projectRootFile ?: File(projectRootPath)
        val file = File(path)
        return if (file.isAbsolute) file else File(root, path)
    }

    private fun countOccurrences(text: String, search: String): Int {
        var count = 0
        var startIndex = 0
        while (true) {
            val index = text.indexOf(search, startIndex)
            if (index < 0) break
            count++
            startIndex = index + 1
        }
        return count
    }

    private fun matchGlob(name: String, glob: String): Boolean {
        val regex = glob
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
        return Regex(regex, RegexOption.IGNORE_CASE).matches(name)
    }

    private inline fun withSafRoot(block: (Context, DocumentFile) -> ToolResult): ToolResult {
        if (!isSafProject) return ToolResult("Error: Project is not configured as SAF.", false)
        val ctx = context ?: return ToolResult(
            "Error: Missing Android context for SAF access.",
            false
        )
        val rootUri = projectRootUri ?: return ToolResult("Error: Invalid SAF root URI.", false)
        val root = DocumentFile.fromTreeUri(ctx, rootUri) ?: return ToolResult(
            "Error: Could not open SAF project root. Re-select the folder and grant access.",
            false
        )
        return block(ctx, root)
    }

    private fun resolveSafRelativePath(path: String): String? {
        val trimmed = path.trim()
        if (trimmed.isBlank() || trimmed == ".") return ""

        if (!trimmed.startsWith("content://") && File(trimmed).isAbsolute) {
            return null
        }

        var candidate = trimmed.replace('\\', '/')

        if (candidate.startsWith("content://")) {
            candidate = when {
                candidate == projectRootPath -> ""
                candidate.startsWith(projectRootPath) -> candidate.removePrefix(projectRootPath).trimStart('/')
                else -> {
                    val fromDocId = extractRelativeFromDocumentUri(candidate)
                    fromDocId ?: return null
                }
            }
        }

        if (candidate.startsWith("/")) return null
        if (candidate.startsWith("./")) candidate = candidate.removePrefix("./")

        val segments = candidate.split('/').filter { it.isNotBlank() && it != "." }
        if (segments.any { it == ".." }) return null

        return segments.joinToString("/")
    }

    private fun extractRelativeFromDocumentUri(uriString: String): String? {
        val rootUri = projectRootUri ?: return null
        return runCatching {
            val rootDocId = DocumentsContract.getTreeDocumentId(rootUri)
            val targetDocId = DocumentsContract.getDocumentId(Uri.parse(uriString))
            when {
                targetDocId == rootDocId -> ""
                targetDocId.startsWith("$rootDocId/") -> targetDocId.removePrefix("$rootDocId/")
                else -> null
            }
        }.getOrNull()
    }

    private fun findSafDocument(root: DocumentFile, relativePath: String): DocumentFile? {
        if (relativePath.isBlank()) return root
        var current: DocumentFile = root
        for (segment in relativePath.split('/')) {
            val next = current.findFile(segment) ?: return null
            current = next
        }
        return current
    }

    private fun ensureSafDirectory(root: DocumentFile, relativeDirPath: String): DocumentFile? {
        if (relativeDirPath.isBlank()) return root
        var current: DocumentFile = root
        for (segment in relativeDirPath.split('/').filter { it.isNotBlank() }) {
            val existing = current.findFile(segment)
            current = when {
                existing == null -> current.createDirectory(segment) ?: return null
                existing.isDirectory -> existing
                else -> return null
            }
        }
        return current
    }

    private fun mimeTypeForName(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension.isBlank()) return "text/plain"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "text/plain"
    }

    private fun readTextSaf(ctx: Context, uri: Uri, maxBytes: Int): ReadTextResult? {
        val input = ctx.contentResolver.openInputStream(uri) ?: return null
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var exceeded = false
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                if (out.size() + read > maxBytes) {
                    val remaining = maxBytes - out.size()
                    if (remaining > 0) out.write(buffer, 0, remaining)
                    exceeded = true
                    break
                }
                out.write(buffer, 0, read)
            }
            return ReadTextResult(
                text = out.toString(Charsets.UTF_8.name()),
                exceededLimit = exceeded
            )
        }
    }

    private fun buildTreeStringLocal(
        dir: File,
        out: StringBuilder,
        prefix: String,
        maxEntries: Int,
        count: IntArray = intArrayOf(0)
    ) {
        val children = dir.listFiles()
            ?.filter { !it.name.startsWith(".") }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: return

        for ((idx, child) in children.withIndex()) {
            if (count[0] >= maxEntries) {
                out.appendLine("${prefix}... (truncated)")
                return
            }
            count[0]++

            val isLast = idx == children.lastIndex
            val connector = if (isLast) "\\-- " else "|-- "
            val suffix = if (child.isDirectory) "/" else ""
            out.appendLine("$prefix$connector${child.name}$suffix")

            if (child.isDirectory) {
                val extension = if (isLast) "    " else "|   "
                buildTreeStringLocal(child, out, "$prefix$extension", maxEntries, count)
            }
        }
    }

    private fun buildTreeStringSaf(
        dir: DocumentFile,
        out: StringBuilder,
        prefix: String,
        maxEntries: Int,
        count: IntArray = intArrayOf(0)
    ) {
        val children = dir.listFiles()
            .filter { !(it.name ?: "").startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { (it.name ?: "").lowercase() }))

        for ((idx, child) in children.withIndex()) {
            if (count[0] >= maxEntries) {
                out.appendLine("${prefix}... (truncated)")
                return
            }
            count[0]++

            val isLast = idx == children.lastIndex
            val connector = if (isLast) "\\-- " else "|-- "
            val name = child.name ?: "(unnamed)"
            val suffix = if (child.isDirectory) "/" else ""
            out.appendLine("$prefix$connector$name$suffix")

            if (child.isDirectory) {
                val extension = if (isLast) "    " else "|   "
                buildTreeStringSaf(child, out, "$prefix$extension", maxEntries, count)
            }
        }
    }

    private fun searchRecursiveLocal(
        dir: File,
        root: File,
        pattern: Regex,
        fileGlob: String?,
        results: MutableList<String>
    ) {
        if (results.size >= MAX_SEARCH_RESULTS) return
        val children = dir.listFiles() ?: return

        for (file in children) {
            if (results.size >= MAX_SEARCH_RESULTS) {
                results.add("... (results truncated, showing first $MAX_SEARCH_RESULTS)")
                return
            }

            if (file.name.startsWith(".") || file.name == "node_modules" ||
                file.name == "build" || file.name == "__pycache__"
            ) continue

            if (file.isDirectory) {
                searchRecursiveLocal(file, root, pattern, fileGlob, results)
            } else {
                if (fileGlob != null && !matchGlob(file.name, fileGlob)) continue
                if (file.length() > MAX_FILE_SIZE) continue

                try {
                    val lines = file.readLines()
                    for ((lineNum, line) in lines.withIndex()) {
                        if (pattern.containsMatchIn(line)) {
                            val relativePath = runCatching {
                                file.relativeTo(root).invariantSeparatorsPath
                            }.getOrElse { file.path }
                            results.add("$relativePath:${lineNum + 1}: ${line.trim().take(120)}")
                            if (results.size >= MAX_SEARCH_RESULTS) return
                        }
                    }
                } catch (_: Exception) {
                    // Skip unreadable files
                }
            }
        }
    }

    private fun searchRecursiveSaf(
        context: Context,
        dir: DocumentFile,
        currentRelative: String,
        pattern: Regex,
        fileGlob: String?,
        results: MutableList<String>
    ) {
        if (results.size >= MAX_SEARCH_RESULTS) return
        val children = dir.listFiles()

        for (file in children) {
            if (results.size >= MAX_SEARCH_RESULTS) {
                results.add("... (results truncated, showing first $MAX_SEARCH_RESULTS)")
                return
            }

            val name = file.name ?: continue
            if (name.startsWith(".") || name == "node_modules" || name == "build" || name == "__pycache__") continue

            val relativePath = if (currentRelative.isBlank()) name else "$currentRelative/$name"

            if (file.isDirectory) {
                searchRecursiveSaf(context, file, relativePath, pattern, fileGlob, results)
            } else {
                if (fileGlob != null && !matchGlob(name, fileGlob)) continue
                val length = file.length()
                if (length > MAX_FILE_SIZE) continue

                val read = readTextSaf(context, file.uri, MAX_FILE_SIZE) ?: continue
                if (read.exceededLimit) continue

                val lines = read.text.split('\n')
                for ((lineNum, line) in lines.withIndex()) {
                    if (pattern.containsMatchIn(line)) {
                        results.add("$relativePath:${lineNum + 1}: ${line.trim().take(120)}")
                        if (results.size >= MAX_SEARCH_RESULTS) return
                    }
                }
            }
        }
    }

    private data class ReadTextResult(
        val text: String,
        val exceededLimit: Boolean
    )
}

/**
 * Result of a tool execution.
 */
data class ToolResult(
    val output: String,
    val success: Boolean
)
