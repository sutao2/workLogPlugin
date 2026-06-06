package com.worklog.utils

import com.worklog.models.GitCommit
import com.worklog.models.WorkLog
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Markdown 格式化工具类
 */
object MarkdownUtil {
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE")
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val THINK_TAG_REGEX = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)
    private val REASONING_TAG_REGEX = Regex("<reasoning>.*?</reasoning>", RegexOption.DOT_MATCHES_ALL)
    private val CONDITIONAL_BLOCK_REGEX = Regex("\\{\\{#if hasCodeAccess}}([\\s\\S]*?)\\{\\{/if}}")
    private val CONDITIONAL_BLOCK_STRIP_REGEX = Regex("\\{\\{#if hasCodeAccess}}[\\s\\S]*?\\{\\{/if}}")

    /**
     * 生成工作日志模板
     */
    fun generateTemplate(date: LocalDate): String {
        return """
            # 工作日志 - ${date.format(DATE_FORMATTER)}

            ## 📋 工作总结

            <!-- 在这里填写今日工作总结 -->


            ## 📝 详细内容

            <!-- 在这里填写详细的工作内容 -->


        """.trimIndent()
    }

    /**
     * 格式化 Git 提交记录
     */
    fun formatGitCommits(commits: List<GitCommit>): String {
        if (commits.isEmpty()) {
            return "今日无 Git 提交记录。\n"
        }

        val sb = StringBuilder()
        sb.appendLine("## 💾 Git 提交记录")
        sb.appendLine()
        sb.appendLine("共 ${commits.size} 次提交：")
        sb.appendLine()

        commits.forEachIndexed { index, commit ->
            val localTime = commit.timestamp
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(TIME_FORMATTER)

            sb.appendLine("### ${index + 1}. ${commit.message}")
            sb.appendLine()
            sb.appendLine("- **提交哈希**: `${commit.shortHash}`")
            sb.appendLine("- **作者**: ${commit.author} <${commit.authorEmail}>")
            sb.appendLine("- **时间**: $localTime")
            sb.appendLine("- **文件数**: ${commit.files.size}")

            if (commit.files.isNotEmpty()) {
                sb.appendLine("- **修改文件**:")
                commit.files.take(10).forEach { file ->
                    sb.appendLine("  - `$file`")
                }
                if (commit.files.size > 10) {
                    sb.appendLine("  - ... 还有 ${commit.files.size - 10} 个文件")
                }
            }

            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * 格式化代码差异（如果用户允许读取代码）
     */
    fun formatCodeDiff(commits: List<GitCommit>, includeFullDiff: Boolean = false): String {
        val commitsWithDiff = commits.filter { it.diff != null }

        if (commitsWithDiff.isEmpty()) {
            return ""
        }

        val sb = StringBuilder()
        sb.appendLine("## 📄 代码变更")
        sb.appendLine()

        commitsWithDiff.forEach { commit ->
            sb.appendLine("### ${commit.message} (`${commit.shortHash}`)")
            sb.appendLine()

            if (includeFullDiff && commit.diff != null) {
                sb.appendLine("```diff")
                // 限制差异的长度，避免过长
                val diffLines = commit.diff.lines()
                if (diffLines.size > 100) {
                    sb.appendLine(diffLines.take(100).joinToString("\n"))
                    sb.appendLine("... (省略 ${diffLines.size - 100} 行)")
                } else {
                    sb.appendLine(commit.diff)
                }
                sb.appendLine("```")
            } else {
                sb.appendLine("修改了 ${commit.files.size} 个文件")
            }

            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * 生成完整的工作日志内容
     */
    fun generateFullWorkLog(
        workLog: WorkLog,
        aiSummary: String? = null,
        includeCodeDiff: Boolean = false
    ): String {
        val settings = com.worklog.settings.AppSettingsState.getInstance()
        var template = settings.workLogOutputTemplate

        // 替换日期
        val dateStr = workLog.date.format(DATE_FORMATTER)
        template = template.replace("{{date}}", dateStr)

        // 替换AI总结
        if (!aiSummary.isNullOrBlank()) {
            // 清理AI输出中可能包含的思考过程标签
            var cleanedSummary = aiSummary.trim()
            cleanedSummary = cleanedSummary.replace(THINK_TAG_REGEX, "")
            cleanedSummary = cleanedSummary.replace(REASONING_TAG_REGEX, "")
            cleanedSummary = cleanedSummary.trim()

            template = template.replace("{{ai_summary}}", cleanedSummary)
        } else {
            template = template.replace("{{ai_summary}}", "<!-- 无AI总结 -->")
        }

        // 替换Git提交记录
        val gitCommitsStr = if (workLog.gitCommits.isNotEmpty()) {
            formatGitCommits(workLog.gitCommits)
        } else {
            "今日无 Git 提交记录。"
        }
        template = template.replace("{{git_commits}}", gitCommitsStr)

        // 处理代码变更部分（条件模板）
        if (includeCodeDiff && workLog.hasCodeAccess) {
            val codeDiff = formatCodeDiff(workLog.gitCommits, includeFullDiff = false)
            template = template.replace(CONDITIONAL_BLOCK_REGEX) { matchResult ->
                val content = matchResult.groupValues[1]
                content.replace("{{code_changes}}", if (codeDiff.isNotBlank()) codeDiff else "无代码变更")
            }
        } else {
            // 移除条件块
            template = template.replace(CONDITIONAL_BLOCK_STRIP_REGEX, "")
        }

        return template.trim()
    }

    /**
     * 提取 Git 提交信息用于 AI 总结
     */
    fun extractCommitsForAI(commits: List<GitCommit>, includeCode: Boolean): String {
        val sb = StringBuilder()

        commits.forEach { commit ->
            sb.appendLine("提交: ${commit.message}")
            sb.appendLine("作者: ${commit.author}")
            sb.appendLine("文件: ${commit.files.joinToString(", ")}")

            if (includeCode && commit.diff != null) {
                sb.appendLine("代码变更:")
                val diff = commit.diff
                val cutoff = nthLineEndIndex(diff, 50)
                if (cutoff >= 0) {
                    sb.appendLine(diff.substring(0, cutoff))
                    if (cutoff < diff.length) {
                        sb.appendLine("... (省略)")
                    }
                } else {
                    sb.appendLine(diff)
                }
            }

            sb.appendLine()
        }

        return sb.toString()
    }

    private fun nthLineEndIndex(text: String, maxLines: Int): Int {
        var lineCount = 0
        text.forEachIndexed { index, ch ->
            if (ch == '\n') {
                lineCount++
                if (lineCount >= maxLines) {
                    return index
                }
            }
        }
        return -1
    }
}
