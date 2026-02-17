package com.codemobile.terminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

/**
 * Foreground service that keeps terminal sessions alive when app is in background.
 * Manages creation and lifecycle of TerminalSession instances.
 */
@AndroidEntryPoint
class TerminalService : Service() {

    @Inject
    lateinit var bootstrap: TerminalBootstrap

    private val sessions = mutableMapOf<String, TerminalSession>()
    private val binder = TerminalBinder()

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "terminal_service"

        fun start(context: Context) {
            val intent = Intent(context, TerminalService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TerminalService::class.java))
        }
    }

    inner class TerminalBinder : Binder() {
        fun getService(): TerminalService = this@TerminalService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        bootstrap.setup()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        sessions.values.forEach { it.destroy() }
        sessions.clear()
        super.onDestroy()
    }

    /**
     * Create a new terminal session.
     * @param cwd Working directory (defaults to projects dir)
     * @param extraEnv Additional environment variables
     */
    fun createSession(
        cwd: String? = null,
        extraEnv: Map<String, String> = emptyMap()
    ): TerminalSession {
        val workingDir = File(cwd ?: bootstrap.projectsDir.absolutePath)
        if (!workingDir.exists()) workingDir.mkdirs()

        val env = bootstrap.environment.toMutableMap()
        env.putAll(extraEnv)
        val envArray = env.map { "${it.key}=${it.value}" }.toTypedArray()

        val session = TerminalSession(
            shellCommand = bootstrap.getShellCommand(),
            workingDir = workingDir,
            environment = envArray
        )
        session.start()
        sessions[session.id] = session

        updateNotification()
        return session
    }

    /**
     * Get an existing session by ID.
     */
    fun getSession(sessionId: String): TerminalSession? = sessions[sessionId]

    /**
     * Get all active sessions.
     */
    fun getAllSessions(): List<TerminalSession> = sessions.values.toList()

    /**
     * Destroy a specific session.
     */
    fun destroySession(sessionId: String) {
        sessions.remove(sessionId)?.destroy()
        updateNotification()

        if (sessions.isEmpty()) {
            stopSelf()
        }
    }

    /**
     * Execute a one-shot command and return its output.
     * Useful for AI tool calls (readFile, runCommand, etc.)
     */
    fun executeCommand(
        command: String,
        cwd: String? = null,
        env: Map<String, String> = emptyMap()
    ): CommandResult {
        val workingDir = File(cwd ?: bootstrap.projectsDir.absolutePath)
        val envMap = bootstrap.environment.toMutableMap()
        envMap.putAll(env)

        return try {
            val shell = bootstrap.getShellCommand()
            val pb = ProcessBuilder(shell, "-c", command)
                .directory(workingDir)
                .redirectErrorStream(true)

            pb.environment().clear()
            envMap.forEach { (k, v) -> pb.environment()[k] = v }

            val process = pb.start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            CommandResult(
                exitCode = exitCode,
                output = output,
                success = exitCode == 0
            )
        } catch (e: Exception) {
            CommandResult(
                exitCode = -1,
                output = "Error: ${e.message}",
                success = false
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Terminal Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps terminal sessions alive"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val count = sessions.size
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Code Mobile")
            .setContentText(
                if (count > 0) "$count terminal session${if (count > 1) "s" else ""} active"
                else "Terminal ready"
            )
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, createNotification())
    }
}

data class CommandResult(
    val exitCode: Int,
    val output: String,
    val success: Boolean
)
