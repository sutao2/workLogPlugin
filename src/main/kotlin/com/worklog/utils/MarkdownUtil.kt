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
        val sb = StringBuilder()

        // 标题
        sb.appendLine("# 工作日志 - ${workLog.date.format(DATE_FORMATTER)}")
        sb.appendLine()

        // AI 总结（如果有）
        if (!aiSummary.isNullOrBlank()) {
            sb.appendLine("## 🤖 AI 工作总结")
            sb.appendLine()
            sb.appendLine(aiSummary.trim())
            sb.appendLine()
            sb.appendLine()
        }

        // Git 提交记录
        if (workLog.gitCommits.isNotEmpty()) {
            sb.append(formatGitCommits(workLog.gitCommits))
            sb.appendLine()
        }

        // 代码差异（如果用户允许）
        if (includeCodeDiff && workLog.hasCodeAccess) {
            val codeDiff = formatCodeDiff(workLog.gitCommits, includeFullDiff = false)
            if (codeDiff.isNotBlank()) {
                sb.append(codeDiff)
                sb.appendLine()
            }
        }

        // 用户自定义内容
        if (workLog.content.isNotBlank()) {
            sb.appendLine("## 📝 详细内容")
            sb.appendLine()
            sb.appendLine(workLog.content)
            sb.appendLine()
        } else {
            sb.appendLine("## 📝 详细内容")
            sb.appendLine()
            sb.appendLine("<!-- 在这里填写详细的工作内容 -->")
            sb.appendLine()
        }

        return sb.toString()
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
                // 限制发送给 AI 的代码长度
                val diffLines = commit.diff.lines()
                sb.appendLine(diffLines.take(50).joinToString("\n"))
                if (diffLines.size > 50) {
                    sb.appendLine("... (省略)")
                }
            }

            sb.appendLine()
        }

        return sb.toString()
    }
}
