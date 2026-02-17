package com.codemobile.ai.tools

import com.codemobile.ai.model.AITool

/**
 * Defines the coding agent tools available to the AI.
 * Each tool has a JSON Schema describing its parameters,
 * which is sent to the LLM so it can invoke them.
 */
object AgentTools {

    /** Maximum number of agentic round-trips before forcing a text response. */
    const val MAX_TOOL_ROUNDS = 25

    // ── Tool names (constants for dispatch) ────────────

    const val READ_FILE = "read_file"
    const val WRITE_FILE = "write_file"
    const val EDIT_FILE = "edit_file"
    const val DELETE_FILE = "delete_file"
    const val LIST_DIR = "list_directory"
    const val RUN_COMMAND = "run_command"
    const val SEARCH_FILES = "search_files"

    // ── Tool definitions ───────────────────────────────

    private val readFile = AITool(
        name = READ_FILE,
        description = "Read the contents of a file at the given path. Use this to examine existing code, configuration files, or any text file in the project.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "path" to mapOf(
                    "type" to "string",
                    "description" to "Absolute path or path relative to the project root."
                ),
                "start_line" to mapOf(
                    "type" to "integer",
                    "description" to "Optional 1-based start line to read from. Omit to read from the beginning."
                ),
                "end_line" to mapOf(
                    "type" to "integer",
                    "description" to "Optional 1-based end line (inclusive). Omit to read to the end."
                )
            ),
            "required" to listOf("path")
        )
    )

    private val writeFile = AITool(
        name = WRITE_FILE,
        description = "Create a new file or completely overwrite an existing file with the provided content. Use this when you need to create a new file or replace all contents of an existing file.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "path" to mapOf(
                    "type" to "string",
                    "description" to "Absolute path or path relative to the project root."
                ),
                "content" to mapOf(
                    "type" to "string",
                    "description" to "The full content to write to the file."
                )
            ),
            "required" to listOf("path", "content")
        )
    )

    private val editFile = AITool(
        name = EDIT_FILE,
        description = "Edit an existing file by replacing a specific string with new content. The old_string must match exactly (including whitespace and indentation). Include enough context lines to make the match unique.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "path" to mapOf(
                    "type" to "string",
                    "description" to "Absolute path or path relative to the project root."
                ),
                "old_string" to mapOf(
                    "type" to "string",
                    "description" to "The exact text to find and replace. Must match exactly including whitespace."
                ),
                "new_string" to mapOf(
                    "type" to "string",
                    "description" to "The replacement text. Use empty string to delete the old_string."
                )
            ),
            "required" to listOf("path", "old_string", "new_string")
        )
    )

    private val deleteFile = AITool(
        name = DELETE_FILE,
        description = "Delete a file or empty directory at the given path.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "path" to mapOf(
                    "type" to "string",
                    "description" to "Absolute path or path relative to the project root."
                )
            ),
            "required" to listOf("path")
        )
    )

    private val listDir = AITool(
        name = LIST_DIR,
        description = "List all files and directories in the given directory. Returns names with '/' suffix for directories.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "path" to mapOf(
                    "type" to "string",
                    "description" to "Absolute path or path relative to the project root. Use '.' or '' for project root."
                ),
                "recursive" to mapOf(
                    "type" to "boolean",
                    "description" to "If true, list recursively as a tree. Default false."
                )
            ),
            "required" to listOf("path")
        )
    )

    private val runCommand = AITool(
        name = RUN_COMMAND,
        description = "Execute a shell command in the project directory. Use for running build tools, git commands, package managers, or any CLI operation. The command runs in a shell (bash/sh).",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "command" to mapOf(
                    "type" to "string",
                    "description" to "The shell command to execute."
                ),
                "cwd" to mapOf(
                    "type" to "string",
                    "description" to "Optional working directory. Defaults to project root."
                )
            ),
            "required" to listOf("command")
        )
    )

    private val searchFiles = AITool(
        name = SEARCH_FILES,
        description = "Search for files matching a pattern or containing a text string. Use to find files by name or content.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "pattern" to mapOf(
                    "type" to "string",
                    "description" to "Text to search for in file contents (grep-style)."
                ),
                "path" to mapOf(
                    "type" to "string",
                    "description" to "Directory to search in. Defaults to project root."
                ),
                "file_pattern" to mapOf(
                    "type" to "string",
                    "description" to "Optional glob pattern to filter files (e.g. '*.kt', '*.xml')."
                )
            ),
            "required" to listOf("pattern")
        )
    )

    /**
     * Returns the full list of tools to send to the AI provider.
     */
    fun allTools(): List<AITool> = listOf(
        readFile,
        writeFile,
        editFile,
        deleteFile,
        listDir,
        runCommand,
        searchFiles
    )
}
