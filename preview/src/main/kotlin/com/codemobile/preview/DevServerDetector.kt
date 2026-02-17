package com.codemobile.preview

/**
 * Detects dev server URLs from terminal output.
 * Monitors for common patterns like "localhost:PORT" that indicate a dev server is running.
 */
object DevServerDetector {

    private val URL_PATTERNS = listOf(
        Regex(
            """(?:Local|Server|App|Network):\s*(https?://(?:localhost|127\.0\.0\.1|0\.0\.0\.0):\d+)""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """listening\s+(?:on|at)\s*(https?://(?:localhost|127\.0\.0\.1):\d+)""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """ready\s+(?:on|in|at)\s*(?:.*?)(https?://(?:localhost|127\.0\.0\.1):\d+)""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """started\s+(?:server\s+)?(?:on|at)\s*(https?://(?:localhost|127\.0\.0\.1):\d+)""",
            RegexOption.IGNORE_CASE
        ),
        Regex("""(https?://localhost:\d+)""")
    )

    /**
     * Attempt to detect a dev server URL from terminal output text.
     * @return The detected URL normalized to localhost, or null if none found.
     */
    fun detectUrl(terminalOutput: String): String? {
        for (pattern in URL_PATTERNS) {
            pattern.find(terminalOutput)?.let { match ->
                return match.groupValues[1]
                    .replace("0.0.0.0", "localhost")
                    .replace("127.0.0.1", "localhost")
            }
        }
        return null
    }
}
