package com.codemobile.terminal

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sets up the terminal environment on first launch.
 * Creates directory structure, extracts npm scripts, and prepares env vars.
 */
@Singleton
class TerminalBootstrap @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var isSetup = false

    val homeDir: File get() = context.filesDir
    val binDir: String get() = context.applicationInfo.nativeLibraryDir
    val shimsDir: File get() = File(homeDir, "bin")
    val projectsDir: File get() = File(homeDir, "projects")
    val tmpDir: File get() = File(homeDir, "tmp")

    /**
     * Environment variables for terminal sessions.
     */
    val environment: Map<String, String> by lazy {
        mapOf(
            "HOME" to homeDir.absolutePath,
            "PATH" to "${shimsDir.absolutePath}:$binDir:${homeDir.absolutePath}/.npm-global/bin:/system/bin",
            "PREFIX" to homeDir.absolutePath,
            "TMPDIR" to tmpDir.absolutePath,
            "NODE_PATH" to "${homeDir.absolutePath}/.npm-global/lib/node_modules",
            "TERM" to "xterm-256color",
            "LANG" to "en_US.UTF-8",
            "SHELL" to (getNativeBinaryPath("dash") ?: "/system/bin/sh"),
            "npm_config_prefix" to "${homeDir.absolutePath}/.npm-global",
            "npm_config_cache" to "${homeDir.absolutePath}/.npm/_cache"
        )
    }

    /**
     * Convert environment map to the String array format needed for ProcessBuilder.
     */
    val environmentArray: Array<String>
        get() = environment.entries.map { "${it.key}=${it.value}" }.toTypedArray()

    /**
     * Run initial setup. Safe to call multiple times — only executes once.
     */
    fun setup() {
        if (isSetup) return

        // Create directory structure
        projectsDir.mkdirs()
        tmpDir.mkdirs()
        File(homeDir, ".npm-global/bin").mkdirs()
        File(homeDir, ".npm-global/lib/node_modules").mkdirs()
        File(homeDir, ".npm/_cache").mkdirs()
        File(homeDir, ".config").mkdirs()
        shimsDir.mkdirs()

        // Ensure bundled native binaries are executable when present.
        listOf("node", "git", "dash", "bash", "ssl", "crypto", "z", "readline").forEach { name ->
            val nativeFile = File(binDir, "lib$name.so")
            if (nativeFile.exists()) {
                nativeFile.setExecutable(true, false)
            }
        }

        createRuntimeShims()

        // Create a basic .bashrc / .profile
        val profile = File(homeDir, ".profile")
        if (!profile.exists()) {
            profile.writeText(
                """
                export HOME=${homeDir.absolutePath}
                export PATH=${shimsDir.absolutePath}:$binDir:${'$'}HOME/.npm-global/bin:${'$'}PATH
                export PREFIX=${'$'}HOME
                export TMPDIR=${'$'}HOME/tmp
                export TERM=xterm-256color
                
                alias ls='ls --color=auto'
                alias ll='ls -la'
                alias cls='clear'
                
                cd ${'$'}HOME/projects
                """.trimIndent()
            )
        }

        isSetup = true
    }

    /**
     * Get the path to a native binary (.so file) by its short name.
     * e.g., "node" → "/data/app/.../lib/arm64/libnode.so"
     */
    fun getNativeBinaryPath(name: String): String? {
        val soName = "lib$name.so"
        val file = File(binDir, soName)
        if (!file.exists()) return null
        if (!file.canExecute()) {
            file.setExecutable(true, false)
        }
        return if (file.canExecute()) file.absolutePath else null
    }

    /**
     * Check which native binaries are available.
     */
    fun availableBinaries(): Map<String, Boolean> {
        val names = listOf("node", "git", "dash", "ssl", "crypto", "z", "readline")
        return names.associateWith { name ->
            getNativeBinaryPath(name) != null
        }
    }

    /**
     * Get the shell command to use for sessions.
     */
    fun getShellCommand(): String {
        return File(shimsDir, "dash").takeIf { it.exists() }?.absolutePath
            ?: getNativeBinaryPath("dash")
            ?: getNativeBinaryPath("bash")
            ?: "/system/bin/sh"
    }

    private fun createRuntimeShims() {
        val nodeNative = getNativeBinaryPath("node")
        val dashNative = getNativeBinaryPath("dash") ?: "/system/bin/sh"

        val dashShim = File(shimsDir, "dash")
        if (!dashShim.exists()) {
            dashShim.writeText(
                """
                #!$dashNative
                exec "$dashNative" "${'$'}@"
                """.trimIndent()
            )
            dashShim.setExecutable(true, false)
        }

        if (nodeNative != null) {
            val nodeShim = File(shimsDir, "node")
            nodeShim.writeText(
                """
                #!$dashNative
                exec "$nodeNative" "${'$'}@"
                """.trimIndent()
            )
            nodeShim.setExecutable(true, false)
        }

        val npmCli = File(homeDir, ".npm-global/lib/node_modules/npm/bin/npm-cli.js")
        if (npmCli.exists() && nodeNative != null) {
            val npmShim = File(shimsDir, "npm")
            npmShim.writeText(
                """
                #!$dashNative
                exec "${File(shimsDir, "node").absolutePath}" "$npmCli" "${'$'}@"
                """.trimIndent()
            )
            npmShim.setExecutable(true, false)
        }
    }
}
