package com.codemobile.ai.tools

import android.util.Log
import com.codemobile.ai.model.AIToolCall
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File

/**
 * Executes AI tool calls against the local filesystem and terminal.
 * Each tool returns a human-readable string result that is sent back
 * to the AI as a tool response message.
 *
 * @param projectRoot The root directory of the current project.
 * @param shellCommand Path to the shell binary (e.g. dash, bash, sh).
 * @param environment Environment variables for command execution.
 */
class ToolExecutor(
    private val projectRoot: File,
    private val shellCommand: String = "/system/bin/sh",
    private val environment: Map<String, String> = emptyMap()
) {
    private val gson = Gson()

    companion object {
        private const val TAG = "ToolExecutor"
        private const val MAX_FILE_SIZE = 512 * 1024 // 512 KB
        private const val MAX_OUTPUT_CHARS = 32_000
        private const val MAX_SEARCH_RESULTS = 50
    }

    /**
     * Execute a tool call and return the result as a string.
     * Never throws — errors are returned as descriptive strings.
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

    // ── Tool implementations ───────────────────

    private fun readFile(path: String, startLine: Int?, endLine: Int?): ToolResult {
        val file = resolvePath(path)
        if (!file.exists()) return ToolResult("Error: File not found: $path", false)
        if (!file.isFile) return ToolResult("Error: '$path' is a directory, use list_directory instead.", false)
        if (file.length() > MAX_FILE_SIZE) {
            return ToolResult("Error: File too large (${file.length()} bytes). Use start_line/end_line to read a portion.", false)
        }

        val lines = file.readLines()
        val totalLines = lines.size

        val start = (startLine ?: 1).coerceIn(1, totalLines)
        val end = (endLine ?: totalLines).coerceIn(start, totalLines)

        val selectedLines = lines.subList(start - 1, end)
        val content = selectedLines.joinToString("\n")

        val header = if (startLine != null || endLine != null) {
            "File: $path (lines $start-$end of $totalLines)\n"
        } else {
            "File: $path ($totalLines lines)\n"
        }

        return ToolResult(header + content, true)
    }

    private fun writeFile(path: String, content: String): ToolResult {
        val file = resolvePath(path)

        // Create parent directories if needed
        file.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }

        val existed = file.exists()
        file.writeText(content)

        val lineCount = content.count { it == '\n' } + if (content.isNotEmpty()) 1 else 0
        return ToolResult(
            if (existed) "Updated file: $path ($lineCount lines written)"
            else "Created file: $path ($lineCount lines written)",
            true
        )
    }

    private fun editFile(path: String, oldString: String, newString: String): ToolResult {
        val file = resolvePath(path)
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

    private fun deleteFile(path: String): ToolResult {
        val file = resolvePath(path)
        if (!file.exists()) return ToolResult("Error: File not found: $path", false)

        // Safety: don't delete project root or parent directories
        val canonical = file.canonicalFile
        val rootCanonical = projectRoot.canonicalFile
        if (canonical == rootCanonical || !canonical.path.startsWith(rootCanonical.path)) {
            return ToolResult("Error: Cannot delete files outside the project root.", false)
        }

        return if (file.isDirectory) {
            if (file.listFiles()?.isNotEmpty() == true) {
                ToolResult("Error: Directory is not empty. Delete contents first or use run_command with 'rm -rf'.", false)
            } else {
                file.delete()
                ToolResult("Deleted directory: $path", true)
            }
        } else {
            file.delete()
            ToolResult("Deleted file: $path", true)
        }
    }

    private fun listDirectory(path: String, recursive: Boolean): ToolResult {
        val dir = resolvePath(path)
        if (!dir.exists()) return ToolResult("Error: Directory not found: $path", false)
        if (!dir.isDirectory) return ToolResult("Error: '$path' is a file, not a directory.", false)

        val output = StringBuilder()
        if (recursive) {
            buildTreeString(dir, output, "", maxEntries = 200)
        } else {
            val children = dir.listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?: emptyArray<File>().toList()

            for (child in children) {
                val suffix = if (child.isDirectory) "/" else ""
                output.appendLine("${child.name}$suffix")
            }
        }

        if (output.isEmpty()) return ToolResult("Directory is empty: $path", true)
        return ToolResult(output.toString().trimEnd(), true)
    }

    private fun runCommand(command: String, cwd: String?): ToolResult {
        val workingDir = if (cwd != null) resolvePath(cwd) else projectRoot
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

    private fun searchFiles(pattern: String, basePath: String?, filePattern: String?): ToolResult {
        val dir = resolvePath(basePath ?: ".")
        if (!dir.exists() || !dir.isDirectory) {
            return ToolResult("Error: Directory not found: ${basePath ?: "."}", false)
        }

        val results = mutableListOf<String>()
        val regex = try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (e: Exception) {
            // Fall back to literal search if not valid regex
            Regex(Regex.escape(pattern), RegexOption.IGNORE_CASE)
        }

        searchRecursive(dir, regex, filePattern, results)

        return if (results.isEmpty()) {
            ToolResult("No matches found for '$pattern'.", true)
        } else {
            val header = "Found ${results.size} match${if (results.size > 1) "es" else ""}:\n"
            ToolResult(header + results.joinToString("\n"), true)
        }
    }

    // ── Helpers ─────────────────────────────────

    /**
     * Resolve a path that can be absolute or relative to projectRoot.
     */
    private fun resolvePath(path: String): File {
        val file = File(path)
        return if (file.isAbsolute) file else File(projectRoot, path)
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

    private fun buildTreeString(
        dir: File, out: StringBuilder, prefix: String, maxEntries: Int, count: IntArray = intArrayOf(0)
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
            val connector = if (isLast) "└── " else "├── "
            val suffix = if (child.isDirectory) "/" else ""
            out.appendLine("$prefix$connector${child.name}$suffix")

            if (child.isDirectory) {
                val extension = if (isLast) "    " else "│   "
                buildTreeString(child, out, "$prefix$extension", maxEntries, count)
            }
        }
    }

    private fun searchRecursive(
        dir: File,
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

            // Skip hidden dirs and common ignore patterns
            if (file.name.startsWith(".") || file.name == "node_modules" ||
                file.name == "build" || file.name == "__pycache__"
            ) continue

            if (file.isDirectory) {
                searchRecursive(file, pattern, fileGlob, results)
            } else {
                // Check file pattern
                if (fileGlob != null && !matchGlob(file.name, fileGlob)) continue

                // Check file size (skip large/binary files)
                if (file.length() > MAX_FILE_SIZE) continue

                try {
                    val lines = file.readLines()
                    for ((lineNum, line) in lines.withIndex()) {
                        if (pattern.containsMatchIn(line)) {
                            val relativePath = file.relativeTo(projectRoot).path
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

    private fun matchGlob(name: String, glob: String): Boolean {
        val regex = glob
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
        return Regex(regex, RegexOption.IGNORE_CASE).matches(name)
    }
}

/**
 * Result of a tool execution.
 */
data class ToolResult(
    val output: String,
    val success: Boolean
)
