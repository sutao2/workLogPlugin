package com.worklog.services.ai

import com.worklog.models.GitCommit
import com.worklog.utils.MarkdownUtil

internal object WorkLogPromptBuilder {
    private val CONDITIONAL_BLOCK_REGEX = Regex("\\{\\{#if hasCodeAccess}}([\\s\\S]*?)\\{\\{/if}}")
    private val CONDITIONAL_BLOCK_STRIP_REGEX = Regex("\\{\\{#if hasCodeAccess}}[\\s\\S]*?\\{\\{/if}}")

    fun build(
        commits: List<GitCommit>,
        includeCode: Boolean,
        userPromptTemplate: String
    ): String {
        val commitsInfo = MarkdownUtil.extractCommitsForAI(commits, includeCode)
        var prompt = userPromptTemplate.replace("{{commits}}", commitsInfo)

        prompt = if (includeCode && commits.any { it.diff != null }) {
            val codeDiff = commits.mapNotNull { it.diff }.joinToString("\n\n")
            val truncatedDiff = if (codeDiff.length > MAX_CODE_DIFF_CHARS) {
                codeDiff.take(MAX_CODE_DIFF_CHARS) + "\n\n... (代码变更过长，已截断)"
            } else {
                codeDiff
            }

            prompt.replace(CONDITIONAL_BLOCK_REGEX) { matchResult ->
                matchResult.groupValues[1].replace("{{code_diff}}", truncatedDiff)
            }
        } else {
            prompt.replace(CONDITIONAL_BLOCK_STRIP_REGEX, "")
        }

        return prompt
    }

    private const val MAX_CODE_DIFF_CHARS = 30000
}
