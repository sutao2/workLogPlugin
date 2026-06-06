package com.worklog.ui

import com.worklog.models.ReviewIssue
import com.worklog.models.ReviewResult

internal data class ReviewFileGroup(
    val path: String,
    val issues: List<ReviewIssue>,
    val isUnlocated: Boolean
) {
    val displayPath: String = if (isUnlocated) "未定位问题" else path
    val canOpenFile: Boolean = !isUnlocated && path.isNotBlank()
    val highestSeverity: String? = issues.minByOrNull { CodeReviewIssueGrouper.severityRank(it.severity) }?.severity
    val severitySortRank: Int = highestSeverity?.let { CodeReviewIssueGrouper.severityRank(it) } ?: CodeReviewIssueGrouper.NO_FINDINGS_RANK
}

internal object CodeReviewIssueGrouper {
    const val UNLOCATED_GROUP_PATH = "__WORKLOG_UNLOCATED_ISSUES__"
    const val NO_FINDINGS_RANK = 4

    fun buildReviewIssues(result: ReviewResult, detailSearchText: String): List<ReviewIssue> {
        if (!result.hasFindings) {
            return emptyList()
        }
        if (result.issues.isNotEmpty()) {
            return result.issues.sortedWith(issueComparator())
        }

        val seen = mutableSetOf<String>()
        return detailSearchText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { line ->
                severityOf(line) != null || result.reviewedFiles.any { file ->
                    line.contains(file) || line.contains(file.substringAfterLast('/'))
                }
            }
            .mapNotNull { line ->
                val title = cleanupFindingTitle(line)
                if (title.length < 4 || !seen.add(title)) {
                    null
                } else {
                    ReviewIssue(
                        filePath = findMentionedFile(line, result.reviewedFiles).orEmpty(),
                        line = lineReferenceOf(line),
                        severity = severityOf(line) ?: "MEDIUM",
                        title = title,
                        message = line
                    )
                }
            }
            .take(20)
            .sortedWith(issueComparator())
            .toList()
    }

    fun buildFileGroups(result: ReviewResult, detailSearchText: String): List<ReviewFileGroup> {
        val issues = buildReviewIssues(result, detailSearchText)
        val reviewedPaths = result.reviewedFiles
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()
        issues.map { it.filePath.trim() }
            .filter { it.isNotBlank() }
            .forEach { reviewedPaths.add(it) }

        val groups = reviewedPaths.map { path ->
            ReviewFileGroup(
                path = path,
                issues = issues.filter { it.filePath == path }.sortedWith(issueComparator()),
                isUnlocated = false
            )
        }.toMutableList()

        val unlocatedIssues = issues
            .filter { it.filePath.isBlank() }
            .sortedWith(issueComparator())
        if (unlocatedIssues.isNotEmpty()) {
            groups.add(
                ReviewFileGroup(
                    path = UNLOCATED_GROUP_PATH,
                    issues = unlocatedIssues,
                    isUnlocated = true
                )
            )
        }

        return groups.sortedWith(
            compareBy<ReviewFileGroup> { it.severitySortRank }
                .thenByDescending { it.issues.size }
                .thenBy { it.displayPath.lowercase() }
        )
    }

    fun issueComparator(): Comparator<ReviewIssue> {
        return compareBy<ReviewIssue> { severityRank(it.severity) }
            .thenBy { it.line ?: Int.MAX_VALUE }
            .thenBy { it.title.lowercase() }
    }

    fun severityRank(severity: String): Int {
        return when (severity.uppercase()) {
            "HIGH", "高" -> 0
            "MEDIUM", "中" -> 1
            "LOW", "低" -> 2
            else -> 3
        }
    }

    private fun severityOf(line: String): String? {
        return when {
            line.contains("严重") || line.contains("高") || line.contains("P0") || line.contains("P1") -> "高"
            line.contains("中") || line.contains("P2") -> "中"
            line.contains("低") || line.contains("P3") -> "低"
            else -> null
        }
    }

    private fun cleanupFindingTitle(line: String): String {
        return line
            .removePrefix("###")
            .removePrefix("##")
            .removePrefix("#")
            .removePrefix("-")
            .removePrefix("*")
            .trim()
            .trim('|')
            .trim()
            .let { if (it.length > 120) it.take(117) + "..." else it }
    }

    private fun findMentionedFile(line: String, reviewedFiles: List<String>): String? {
        return reviewedFiles.firstOrNull { file ->
            line.contains(file) || line.contains(file.substringAfterLast('/'))
        }
    }

    private fun lineReferenceOf(line: String): Int? {
        return Regex(""":(\d+)\b""").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}
