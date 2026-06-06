package com.worklog.services.git

import com.worklog.models.GitCommit
import java.time.Instant

internal object GitLogParser {
    const val COMMIT_FIELD_SEPARATOR = "\u001F"

    fun parseCommits(
        output: String,
        shouldIncludeFilePath: (String) -> Boolean
    ): List<GitCommit> {
        val commits = mutableListOf<GitCommit>()
        val lines = output.lines().filter { it.isNotBlank() }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!line.contains(COMMIT_FIELD_SEPARATOR)) {
                i++
                continue
            }

            val parts = line.split(COMMIT_FIELD_SEPARATOR, limit = 6)
            if (parts.size != 6) {
                i++
                continue
            }

            val timestamp = parts[5].toLongOrNull()?.let { Instant.ofEpochSecond(it) }
            if (timestamp == null) {
                i++
                continue
            }

            val files = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].contains(COMMIT_FIELD_SEPARATOR)) {
                val filePath = lines[i]
                if (filePath.isNotBlank() && shouldIncludeFilePath(filePath)) {
                    files.add(filePath)
                }
                i++
            }

            commits.add(
                GitCommit(
                    hash = parts[0],
                    shortHash = parts[1],
                    message = parts[2],
                    author = parts[3],
                    authorEmail = parts[4],
                    timestamp = timestamp,
                    files = files
                )
            )
        }

        return commits
    }
}
