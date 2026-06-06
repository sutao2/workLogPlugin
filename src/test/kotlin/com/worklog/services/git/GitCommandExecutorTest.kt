package com.worklog.services.git

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitCommandExecutorTest {
    @Test
    fun `caps stdout while continuing to drain process output`() {
        val executor = GitCommandExecutor()

        try {
            val result = executor.execute(
                command = arrayOf("/bin/sh", "-c", "yes x | head -c 10000"),
                workDir = java.io.File("."),
                timeoutSeconds = 5,
                maxOutputChars = 128
            )

            assertEquals(0, result?.exitCode)
            assertEquals(128, result?.stdout?.length)
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun `returns null when command times out`() {
        val executor = GitCommandExecutor()

        try {
            val result = executor.execute(
                command = arrayOf("/bin/sh", "-c", "sleep 2"),
                workDir = java.io.File("."),
                timeoutSeconds = 1,
                maxOutputChars = 128
            )

            assertNull(result)
        } finally {
            executor.shutdown()
        }
    }
}
