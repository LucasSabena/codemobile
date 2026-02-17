package com.codemobile.terminal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Represents a single terminal session backed by a Process.
 * In the full implementation, this will use Termux's PTY emulation.
 * For now, it wraps ProcessBuilder for basic command execution.
 */
class TerminalSession(
    val id: String = UUID.randomUUID().toString(),
    private val shellCommand: String,
    private val workingDir: File,
    private val environment: Array<String>
) {
    private var process: Process? = null
    private var outputStream: OutputStream? = null

    private val _output = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val output: Flow<String> = _output.asSharedFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _exitCode = MutableStateFlow<Int?>(null)
    val exitCode: StateFlow<Int?> = _exitCode.asStateFlow()

    private var readerThread: Thread? = null

    /**
     * Start the terminal session.
     */
    fun start() {
        if (process != null) return

        try {
            val pb = ProcessBuilder(shellCommand)
                .directory(workingDir)
                .redirectErrorStream(true)

            pb.environment().clear()
            environment.forEach { env ->
                val parts = env.split("=", limit = 2)
                if (parts.size == 2) {
                    pb.environment()[parts[0]] = parts[1]
                }
            }

            process = pb.start()
            outputStream = process!!.outputStream
            _isRunning.value = true

            // Read output in background thread
            readerThread = Thread {
                readOutput(process!!.inputStream)
            }.apply {
                isDaemon = true
                name = "terminal-reader-$id"
                start()
            }

        } catch (e: Exception) {
            _output.tryEmit("\r\n[Error starting terminal: ${e.message}]\r\n")
            _isRunning.value = false
        }
    }

    /**
     * Write text to the terminal's stdin.
     */
    fun write(text: String) {
        try {
            outputStream?.let { os ->
                os.write(text.toByteArray())
                os.flush()
            }
        } catch (e: Exception) {
            _output.tryEmit("\r\n[Write error: ${e.message}]\r\n")
        }
    }

    /**
     * Send a command (appends newline).
     */
    fun sendCommand(command: String) {
        write(command + "\n")
    }

    /**
     * Destroy the terminal process.
     */
    fun destroy() {
        try {
            process?.destroyForcibly()
        } catch (_: Exception) {}
        process = null
        outputStream = null
        _isRunning.value = false
        readerThread?.interrupt()
        readerThread = null
    }

    private fun readOutput(inputStream: InputStream) {
        try {
            val buffer = ByteArray(4096)
            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                val text = String(buffer, 0, bytesRead)
                _output.tryEmit(text)
            }
        } catch (_: Exception) {
            // Stream closed
        } finally {
            _isRunning.value = false
            _exitCode.value = try {
                process?.waitFor()
            } catch (_: Exception) {
                -1
            }
            _output.tryEmit("\r\n[Process exited with code ${_exitCode.value}]\r\n")
        }
    }
}
