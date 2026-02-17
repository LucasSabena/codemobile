package com.codemobile.ai.prompt

import com.codemobile.ai.prompt.SystemPrompts.BUILD_MODE
import com.codemobile.ai.prompt.SystemPrompts.CODE_FORMATTING
import com.codemobile.ai.prompt.SystemPrompts.CONTEXT_AWARENESS
import com.codemobile.ai.prompt.SystemPrompts.IDENTITY
import com.codemobile.ai.prompt.SystemPrompts.PLAN_MODE
import com.codemobile.ai.prompt.SystemPrompts.PROACTIVENESS
import com.codemobile.ai.prompt.SystemPrompts.PROFESSIONAL_STANDARDS
import com.codemobile.ai.prompt.SystemPrompts.PROJECT_CONTEXT_HEADER
import com.codemobile.ai.prompt.SystemPrompts.SAFETY
import com.codemobile.ai.prompt.SystemPrompts.TONE_AND_STYLE
import com.codemobile.ai.prompt.SystemPrompts.TOOL_USAGE

/**
 * Builds a complete system prompt by composing modular sections from [SystemPrompts]
 * and injecting dynamic project context.
 *
 * Usage:
 * ```kotlin
 * val prompt = SystemPromptBuilder()
 *     .mode(SessionMode.BUILD)
 *     .project("MyApp", "/storage/projects/MyApp")
 *     .openFile("src/main/kotlin/Main.kt", "fun main() { ... }")
 *     .modifiedFiles(listOf("src/App.kt", "build.gradle.kts"))
 *     .fileTree("app/\n  src/\n    main/\n      kotlin/")
 *     .build()
 * ```
 */
class SystemPromptBuilder {

    /** BUILD or PLAN — determines which mode-specific section is included. */
    enum class Mode { BUILD, PLAN }

    private var mode: Mode = Mode.BUILD
    private var projectName: String? = null
    private var projectPath: String? = null
    private var openFilePath: String? = null
    private var openFileContent: String? = null
    private var modifiedFiles: List<String> = emptyList()
    private var fileTree: String? = null
    private var customInstructions: String? = null

    // ── Setters (builder pattern) ──────────────

    fun mode(mode: Mode) = apply { this.mode = mode }

    fun project(name: String?, path: String?) = apply {
        this.projectName = name
        this.projectPath = path
    }

    fun openFile(relativePath: String?, contentPreview: String?) = apply {
        this.openFilePath = relativePath
        this.openFileContent = contentPreview
    }

    fun modifiedFiles(files: List<String>) = apply {
        this.modifiedFiles = files
    }

    fun fileTree(tree: String?) = apply {
        this.fileTree = tree
    }

    /**
     * Optional user-defined custom instructions that are appended at the end.
     * This allows users to personalize AI behavior (e.g., "Always respond in Spanish").
     */
    fun customInstructions(instructions: String?) = apply {
        this.customInstructions = instructions
    }

    // ── Build ──────────────────────────────────

    fun build(): String = buildString {
        // 1. Identity
        appendSection(IDENTITY)

        // 2. Tone & Style
        appendSection(TONE_AND_STYLE)

        // 3. Mode-specific behavior
        appendSection(
            when (mode) {
                Mode.BUILD -> BUILD_MODE
                Mode.PLAN -> PLAN_MODE
            }
        )

        // 3b. Tool usage instructions (BUILD mode only)
        if (mode == Mode.BUILD) {
            appendSection(TOOL_USAGE)
        }

        // 4. Code formatting
        appendSection(CODE_FORMATTING)

        // 5. Professional standards
        appendSection(PROFESSIONAL_STANDARDS)

        // 6. Proactiveness
        appendSection(PROACTIVENESS)

        // 7. Context awareness
        appendSection(CONTEXT_AWARENESS)

        // 8. Safety
        appendSection(SAFETY)

        // 9. Dynamic project context
        val projectContext = buildProjectContext()
        if (projectContext.isNotEmpty()) {
            appendSection(projectContext)
        }

        // 10. Custom user instructions (if any)
        customInstructions?.takeIf { it.isNotBlank() }?.let { instructions ->
            appendSection("## Custom Instructions\n$instructions")
        }
    }.trimEnd()

    // ── Helpers ────────────────────────────────

    private fun StringBuilder.appendSection(content: String) {
        append(content.trimIndent().trim())
        append("\n\n")
    }

    private fun buildProjectContext(): String {
        val parts = mutableListOf<String>()

        // Header
        if (projectName != null || projectPath != null || modifiedFiles.isNotEmpty() ||
            openFilePath != null || fileTree != null
        ) {
            parts.add(PROJECT_CONTEXT_HEADER.trimIndent().trim())
        } else {
            return ""
        }

        // Project info
        projectName?.let { parts.add("- Project: $it") }
        projectPath?.let { parts.add("- Root: $it") }

        // Modified files
        if (modifiedFiles.isNotEmpty()) {
            val filesList = modifiedFiles.take(MAX_MODIFIED_FILES).joinToString(", ")
            val suffix = if (modifiedFiles.size > MAX_MODIFIED_FILES) {
                " (+${modifiedFiles.size - MAX_MODIFIED_FILES} more)"
            } else ""
            parts.add("- Recently modified: $filesList$suffix")
        }

        // Open file
        openFilePath?.let { path ->
            parts.add("- Currently viewing: $path")
            openFileContent?.let { content ->
                val truncated = if (content.length > MAX_FILE_PREVIEW_CHARS) {
                    content.take(MAX_FILE_PREVIEW_CHARS) + "\n// ... truncated ..."
                } else content
                parts.add("```\n$truncated\n```")
            }
        }

        // File tree (compact)
        fileTree?.let { tree ->
            val truncated = if (tree.length > MAX_FILE_TREE_CHARS) {
                tree.take(MAX_FILE_TREE_CHARS) + "\n... (truncated)"
            } else tree
            parts.add("- File structure:\n```\n$truncated\n```")
        }

        return parts.joinToString("\n")
    }

    companion object {
        /** Max modified files to include in prompt (to avoid bloating context). */
        private const val MAX_MODIFIED_FILES = 15

        /** Max characters for file preview content. */
        private const val MAX_FILE_PREVIEW_CHARS = 2000

        /** Max characters for file tree display. */
        private const val MAX_FILE_TREE_CHARS = 1500
    }
}
