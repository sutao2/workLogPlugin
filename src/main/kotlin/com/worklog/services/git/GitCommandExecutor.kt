package com.worklog.services.git

import java.io.Reader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal data class GitCommandResult(
    val exitCode: Int,
    val stdout: String
)

internal class GitCommandExecutor {
    private val streamThreadCounter = AtomicInteger()
    private val streamReaderExecutor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "WorkLog Git Stream Reader-${streamThreadCounter.incrementAndGet()}").apply {
            isDaemon = true
        }
    }

    fun execute(
        command: Array<String>,
        workDir: java.io.File,
        timeoutSeconds: Long = 15,
        maxOutputChars: Int = MAX_GIT_OUTPUT_CHARS
    ): GitCommandResult? {
        val process = ProcessBuilder(*command)
            .directory(workDir)
            .redirectErrorStream(false)
            .start()

        return try {
            val stdoutFuture = streamReaderExecutor.submit<String> {
                process.inputStream.bufferedReader().use { readLimited(it, maxOutputChars) }
            }
            val stderrFuture = streamReaderExecutor.submit<String> {
                process.errorStream.bufferedReader().use { readLimited(it, maxOutputChars) }
            }

            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                closeProcessStreams(process)
                process.waitFor(2, TimeUnit.SECONDS)
                null
            } else {
                val stdout = getFutureValue(stdoutFuture)
                getFutureValue(stderrFuture)
                GitCommandResult(process.exitValue(), stdout)
            }
        } catch (_: Exception) {
            process.destroyForcibly()
            closeProcessStreams(process)
            null
        }
    }

    fun shutdown() {
        streamReaderExecutor.shutdownNow()
    }

    private fun readLimited(reader: Reader, maxChars: Int): String {
        val output = StringBuilder(maxChars.coerceAtMost(64 * 1024))
        val buffer = CharArray(8192)
        var storedChars = 0

        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break

            val remaining = maxChars - storedChars
            if (remaining > 0) {
                val toAppend = minOf(count, remaining)
                output.append(buffer, 0, toAppend)
                storedChars += toAppend
            }
        }

        return output.toString()
    }

    private fun getFutureValue(future: Future<String>): String {
        return try {
            future.get(2, TimeUnit.SECONDS)
        } catch (_: Exception) {
            ""
        }
    }

    private fun closeProcessStreams(process: Process) {
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        runCatching { process.outputStream.close() }
    }

    private companion object {
        private const val MAX_GIT_OUTPUT_CHARS = 2_000_000
    }
}
