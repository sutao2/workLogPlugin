package com.worklog.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.worklog.models.ReviewResult
import com.worklog.services.review.ReviewPromptBuilder
import com.worklog.services.review.ReviewResultParser
import com.worklog.settings.AppSettingsState
import kotlinx.serialization.json.Json

data class StagedReviewContext(
    val snapshotKey: String,
    val commitMessage: String?
)

private data class StagedReviewPayload(
    val context: StagedReviewContext,
    val files: List<String>,
    val diff: String,
    val truncated: Boolean
)

@Service(Service.Level.PROJECT)
class CodeReviewService(private val project: Project) {

    private val gitService: GitService
        get() = project.getService(GitService::class.java)

    private val aiService: AIService
        get() = project.getService(AIService::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
    }
    private val resultParser = ReviewResultParser(json)

    fun isAutoReviewEnabled(): Boolean {
        val settings = AppSettingsState.getInstance()
        return settings.reviewEnabled && settings.reviewAutoRunBeforeCommit && settings.allowCodeAccess
    }

    fun hasStagedChanges(): Boolean {
        return gitService.getStagedFiles().isNotEmpty()
    }

    fun getCurrentStagedFiles(): List<String> {
        return gitService.getStagedFiles()
    }

    fun getStagedReviewContext(commitMessage: String? = null): StagedReviewContext {
        val settings = AppSettingsState.getInstance()
        validateReviewSettings(settings)
        return buildStagedReviewPayload(settings, commitMessage).context
    }

    suspend fun reviewStagedChanges(commitMessage: String? = null): ReviewResult {
        return reviewStagedChangesWithContext(commitMessage).second
    }

    suspend fun reviewStagedChangesWithContext(commitMessage: String? = null): Pair<StagedReviewContext, ReviewResult> {
        val settings = AppSettingsState.getInstance()
        validateReviewSettings(settings)
        val payload = buildStagedReviewPayload(settings, commitMessage)
        val prompt = ReviewPromptBuilder.build(
            settings.reviewUserPromptTemplate,
            payload.files,
            payload.diff,
            payload.context.commitMessage
        )
        val content = aiService.callAI(prompt, settings.reviewSystemPrompt)
        val result = buildReviewResult(
            title = "代码评审结果",
            content = content,
            files = payload.files,
            truncated = payload.truncated,
            sourceCommitHashes = emptyList(),
            reviewedCommitSummaries = emptyList()
        )
        return payload.context to result
    }

    suspend fun reviewCommitByHash(commitHash: String): ReviewResult {
        val settings = AppSettingsState.getInstance()
        validateReviewSettings(settings)

        val reviewInput = gitService.getCommitReviewInput(commitHash, settings.reviewMaxDiffChars)
        if (reviewInput.files.isEmpty() || reviewInput.diff.isBlank()) {
            throw IllegalStateException("所选提交没有可评审的代码变更")
        }

        val commitMessage = gitService.getCommitMessage(commitHash)
        val prompt = ReviewPromptBuilder.build(
            settings.reviewUserPromptTemplate,
            reviewInput.files,
            reviewInput.diff,
            commitMessage
        )
        val content = aiService.callAI(prompt, settings.reviewSystemPrompt)
        val shortHash = gitService.getCommitShortHash(commitHash)
        val commitTitle = commitMessage?.takeIf { it.isNotBlank() }?.let { "$shortHash - $it" } ?: shortHash
        return buildReviewResult(
            title = "提交代码评审结果 ($commitTitle)",
            content = content,
            files = reviewInput.files,
            truncated = reviewInput.truncated,
            sourceCommitHashes = listOf(commitHash),
            reviewedCommitSummaries = listOf(commitTitle)
        )
    }

    suspend fun reviewCommitsByHash(commitHashes: List<String>): ReviewResult {
        val settings = AppSettingsState.getInstance()
        validateReviewSettings(settings)
        if (commitHashes.isEmpty()) {
            throw IllegalStateException("没有可评审的提交")
        }

        val commitInputs = commitHashes.mapNotNull { hash ->
            val input = gitService.getCommitReviewInput(hash, settings.reviewMaxDiffChars)
            if (input.files.isEmpty() || input.diff.isBlank()) {
                null
            } else {
                Triple(hash, gitService.getCommitMessage(hash), input)
            }
        }
        if (commitInputs.isEmpty()) {
            throw IllegalStateException("所选提交没有可评审的代码变更")
        }

        val combinedFiles = commitInputs.flatMap { it.third.files }.distinct()

        // 缓存 shortHash，避免对同一 hash 重复启动 git 进程
        val shortHashCache = commitInputs.associate { (hash, _, _) -> hash to gitService.getCommitShortHash(hash) }

        val commitSummaries = commitInputs.map { (hash, message, _) ->
            val shortHash = shortHashCache[hash]!!
            message?.takeIf { it.isNotBlank() }?.let { "$shortHash - $it" } ?: shortHash
        }

        // 对每个 commit 的 diff 单独截断，防止合并后超限
        val perCommitMaxChars = (settings.reviewMaxDiffChars / commitInputs.size.coerceAtLeast(1)).coerceAtLeast(500)
        val combinedDiff = buildString {
            commitInputs.forEachIndexed { index, (hash, message, input) ->
                if (index > 0) append("\n\n---\n\n")
                val shortHash = shortHashCache[hash]!!
                val title = message?.takeIf { it.isNotBlank() }?.let { "$shortHash - $it" } ?: shortHash
                append("# Commit $title\n\n")
                if (input.diff.length > perCommitMaxChars) {
                    append(input.diff.take(perCommitMaxChars))
                    append("\n\n... (该提交 diff 已截断)")
                } else {
                    append(input.diff)
                }
            }
        }
        val combinedMessages = commitSummaries.joinToString("\n") { "- $it" }

        val prompt = ReviewPromptBuilder.build(
            settings.reviewUserPromptTemplate,
            combinedFiles,
            combinedDiff,
            combinedMessages
        )
        val content = aiService.callAI(prompt, settings.reviewSystemPrompt)
        val title = if (commitInputs.size == 1) {
            val hash = commitInputs.first().first
            val message = commitInputs.first().second
            val shortHash = shortHashCache[hash]!!
            val commitTitle = message?.takeIf { it.isNotBlank() }?.let { "$shortHash - $it" } ?: shortHash
            "提交代码评审结果 ($commitTitle)"
        } else {
            "批量提交代码评审结果 (${commitInputs.size} 条提交)"
        }

        return buildReviewResult(
            title = title,
            content = content,
            files = combinedFiles,
            truncated = commitInputs.any { it.third.truncated },
            sourceCommitHashes = commitInputs.map { it.first },
            reviewedCommitSummaries = commitSummaries
        )
    }

    fun buildReviewPromptPreview(commitHashes: List<String>): String {
        return commitHashes.joinToString(", ") { gitService.getCommitShortHash(it) }
    }

    private fun buildReviewResult(
        title: String,
        content: String,
        files: List<String>,
        truncated: Boolean,
        sourceCommitHashes: List<String>,
        reviewedCommitSummaries: List<String>
    ): ReviewResult {
        val normalizedContent = content.trim()
        val issues = resultParser.parse(normalizedContent, files)
        val hasFindings = issues.isNotEmpty() || !normalizedContent.contains(ReviewPromptBuilder.NO_FINDINGS_MARKER)
        return ReviewResult(
            title = title,
            content = normalizedContent,
            hasFindings = hasFindings,
            reviewedFiles = files,
            truncated = truncated,
            sourceCommitHashes = sourceCommitHashes,
            reviewedCommitSummaries = reviewedCommitSummaries,
            issues = issues
        )
    }

    private fun buildStagedReviewPayload(settings: AppSettingsState, commitMessage: String?): StagedReviewPayload {
        val normalizedCommitMessage = commitMessage?.trim()?.takeIf { it.isNotEmpty() }
        val reviewInput = gitService.getStagedReviewInput(settings.reviewMaxDiffChars)
        if (reviewInput.files.isEmpty() || reviewInput.diff.isBlank()) {
            throw IllegalStateException("当前暂存区没有可评审的代码变更")
        }

        return StagedReviewPayload(
            context = StagedReviewContext(
                snapshotKey = gitService.getRawStagedDiffHash(),
                commitMessage = normalizedCommitMessage
            ),
            files = reviewInput.files,
            diff = reviewInput.diff,
            truncated = reviewInput.truncated
        )
    }

    private fun validateReviewSettings(settings: AppSettingsState) {
        if (!settings.reviewEnabled) {
            throw IllegalStateException("请先在设置中启用代码评审功能")
        }
        if (!settings.allowCodeAccess) {
            throw IllegalStateException("请先在设置中允许读取代码内容，代码评审才可用")
        }
    }
}
