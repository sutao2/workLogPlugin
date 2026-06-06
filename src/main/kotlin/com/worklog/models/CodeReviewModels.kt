package com.worklog.models

enum class ReviewScope {
    STAGED_ONLY
}

data class ReviewIssue(
    val filePath: String,
    val line: Int?,
    val severity: String,
    val title: String,
    val message: String
)

data class ReviewResult(
    val title: String,
    val content: String,
    val hasFindings: Boolean,
    val reviewedFiles: List<String> = emptyList(),
    val truncated: Boolean = false,
    val sourceCommitHashes: List<String> = emptyList(),
    val reviewedCommitSummaries: List<String> = emptyList(),
    val issues: List<ReviewIssue> = emptyList()
)
