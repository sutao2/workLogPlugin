package com.worklog.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.worklog.models.ReviewIssue
import com.worklog.models.ReviewResult
import com.worklog.settings.AppSettingsState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

    companion object {
        private const val PLACEHOLDER_FILES = "{{files}}"
        private const val PLACEHOLDER_DIFF = "{{diff}}"
        private const val PLACEHOLDER_COMMIT_MESSAGE = "{{commit_message}}"
        private const val DEFAULT_COMMIT_MESSAGE = "<未提供提交说明>"
        private const val NO_FINDINGS_MARKER = "未发现明显问题"
        private const val STRUCTURED_RESULT_START = "WORKLOG_REVIEW_JSON_START"
        private const val STRUCTURED_RESULT_END = "WORKLOG_REVIEW_JSON_END"
    }

    private val json = Json {
        ignoreUnknownKeys = true
    }

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
        val prompt = buildPrompt(settings, payload.files, payload.diff, payload.context.commitMessage)
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
        val prompt = buildPrompt(settings, reviewInput.files, reviewInput.diff, commitMessage)
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

        val prompt = buildPrompt(settings, combinedFiles, combinedDiff, combinedMessages)
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

    private fun buildPrompt(
        settings: AppSettingsState,
        files: List<String>,
        diff: String,
        commitMessage: String?
    ): String {
        var prompt = settings.reviewUserPromptTemplate
            .replace(PLACEHOLDER_FILES, files.joinToString("\n") { "- $it" })
            .replace(PLACEHOLDER_DIFF, diff)

        prompt = if (commitMessage.isNullOrBlank()) {
            prompt.replace(PLACEHOLDER_COMMIT_MESSAGE, DEFAULT_COMMIT_MESSAGE)
        } else {
            prompt.replace(PLACEHOLDER_COMMIT_MESSAGE, commitMessage)
        }

        return "$prompt\n\n${buildStructuredOutputInstruction(files)}"
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
        val issues = parseReviewIssues(normalizedContent, files)
        val hasFindings = issues.isNotEmpty() || !normalizedContent.contains(NO_FINDINGS_MARKER)
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

    private fun buildStructuredOutputInstruction(files: List<String>): String {
        return """

            输出要求：
            1. 先用 Markdown 给出简短评审说明。
            2. 如果没有明显问题，正文必须包含“$NO_FINDINGS_MARKER”，并在 JSON 中返回空 issues。
            3. 如果发现问题，必须在最后追加以下机器可解析区块，字段必须准确：

            $STRUCTURED_RESULT_START
            {
              "issues": [
                {
                  "file": "必须是下列变更文件之一",
                  "line": 42,
                  "severity": "HIGH|MEDIUM|LOW",
                  "title": "一句话问题标题",
                  "message": "说明为什么这是问题，以及建议如何修复"
                }
              ]
            }
            $STRUCTURED_RESULT_END

            可用文件路径只能从这里选择：
            ${files.joinToString("\n") { "- $it" }}

            定位规则：
            - diff 中形如 “>> src/main/App.kt:42” 的行是可定位坐标。
            - 当问题出现在紧随该坐标后的代码行或附近上下文时，JSON 的 file/line 必须使用这个坐标。
            - 不要返回不存在于变更文件列表中的 file。
            - 不要编造行号；无法确定具体位置时 line 返回 null。
            - 上方 JSON 示例里的 42 只是示例，实际输出必须替换成真实行号或 null。
        """.trimIndent()
    }

    private fun parseReviewIssues(content: String, reviewedFiles: List<String>): List<ReviewIssue> {
        val structuredIssues = parseStructuredReviewIssues(content, reviewedFiles)
        if (structuredIssues.isNotEmpty()) {
            return structuredIssues
        }
        return parseLegacyReviewIssues(content, reviewedFiles)
    }

    private fun parseStructuredReviewIssues(content: String, reviewedFiles: List<String>): List<ReviewIssue> {
        val block = Regex(
            "$STRUCTURED_RESULT_START\\s*([\\s\\S]*?)\\s*$STRUCTURED_RESULT_END"
        ).find(content)?.groupValues?.getOrNull(1)?.trim() ?: return emptyList()

        return try {
            val root = json.parseToJsonElement(block).jsonObject
            root["issues"]?.jsonArray.orEmpty().mapNotNull { element ->
                val issue = element.jsonObject
                val file = issue.stringValue("file")?.let { normalizeReviewedFile(it, reviewedFiles) }
                    ?: return@mapNotNull null
                ReviewIssue(
                    filePath = file,
                    line = issue["line"]?.jsonPrimitive?.intOrNull,
                    severity = normalizeSeverity(issue.stringValue("severity")),
                    title = issue.stringValue("title").orEmpty().ifBlank { "代码评审问题" },
                    message = issue.stringValue("message").orEmpty()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseLegacyReviewIssues(content: String, reviewedFiles: List<String>): List<ReviewIssue> {
        if (content.contains(NO_FINDINGS_MARKER)) {
            return emptyList()
        }

        val seen = mutableSetOf<String>()
        return content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val file = reviewedFiles.firstOrNull { filePath ->
                    line.contains(filePath) || line.contains(filePath.substringAfterLast('/'))
                } ?: return@mapNotNull null
                val title = line
                    .removePrefix("###")
                    .removePrefix("##")
                    .removePrefix("#")
                    .removePrefix("-")
                    .removePrefix("*")
                    .trim()
                    .trim('|')
                    .trim()
                val key = "$file:$title"
                if (title.length < 4 || !seen.add(key)) {
                    null
                } else {
                    ReviewIssue(
                        filePath = file,
                        line = Regex(""":(\d+)\b""").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull(),
                        severity = normalizeSeverity(line),
                        title = if (title.length > 120) title.take(117) + "..." else title,
                        message = line
                    )
                }
            }
            .take(20)
            .toList()
    }

    private fun normalizeReviewedFile(file: String, reviewedFiles: List<String>): String? {
        return reviewedFiles.firstOrNull { it == file }
            ?: reviewedFiles.firstOrNull { it.substringAfterLast('/') == file.substringAfterLast('/') }
    }

    private fun normalizeSeverity(value: String?): String {
        val text = value.orEmpty().uppercase()
        return when {
            text.contains("HIGH") || text.contains("严重") || text.contains("高") || text.contains("P0") || text.contains("P1") -> "HIGH"
            text.contains("LOW") || text.contains("低") || text.contains("P3") -> "LOW"
            else -> "MEDIUM"
        }
    }

    private fun JsonObject.stringValue(key: String): String? {
        val value = this[key] ?: return null
        return (value as? JsonPrimitive)?.contentOrNull
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
