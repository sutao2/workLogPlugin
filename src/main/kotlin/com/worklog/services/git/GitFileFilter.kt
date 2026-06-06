package com.worklog.services.git

import com.intellij.openapi.vfs.VirtualFile
import com.worklog.settings.AppSettingsState

internal data class GitFileFilterRules(
    val excludedExtensions: Set<String>,
    val excludedDirectories: List<String>,
    val maxSizeBytes: Long
) {
    companion object {
        fun from(settings: AppSettingsState): GitFileFilterRules {
            return GitFileFilterRules(
                excludedExtensions = settings.excludedFileExtensions
                    .split(",")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .toSet(),
                excludedDirectories = settings.excludedDirectories
                    .split(",")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() },
                maxSizeBytes = settings.maxFileSizeKb * 1024L
            )
        }
    }
}

internal object GitFileFilter {
    fun shouldIncludePath(
        filePath: String,
        rules: GitFileFilterRules
    ): Boolean {
        val path = filePath.lowercase()
        val fileName = path.substringAfterLast('/')

        val extension = fileName.substringAfterLast('.', "")
        if (extension in rules.excludedExtensions) {
            return false
        }

        val normalizedPath = "/${path.trimStart('/')}"
        return !rules.excludedDirectories.any { normalizedPath.contains(it) || path.contains(it) }
    }

    fun shouldIncludeVirtualFile(
        file: VirtualFile,
        rules: GitFileFilterRules
    ): Boolean {
        if (!shouldIncludePath(file.path, rules)) {
            return false
        }

        return try {
            file.length <= rules.maxSizeBytes
        } catch (_: Exception) {
            true
        }
    }
}
