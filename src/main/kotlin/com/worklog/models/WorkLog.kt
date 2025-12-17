package com.worklog.models

import java.time.Instant
import java.time.LocalDate

/**
 * 工作日志数据模型
 */
data class WorkLog(
    val date: LocalDate,
    var content: String,              // Markdown 格式的日志内容
    var gitCommits: List<GitCommit>,  // Git 提交记录
    var hasCodeAccess: Boolean,        // 是否包含代码访问权限
    val createdAt: Instant,
    var updatedAt: Instant
) {
    companion object {
        /**
         * 创建新的工作日志
         */
        fun create(date: LocalDate, commits: List<GitCommit> = emptyList(), hasCodeAccess: Boolean = false): WorkLog {
            val now = Instant.now()
            return WorkLog(
                date = date,
                content = "",
                gitCommits = commits,
                hasCodeAccess = hasCodeAccess,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}
