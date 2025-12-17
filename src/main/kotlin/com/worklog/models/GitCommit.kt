package com.worklog.models

import java.time.Instant

/**
 * Git 提交信息数据模型
 */
data class GitCommit(
    val hash: String,
    val shortHash: String,
    val message: String,
    val author: String,
    val authorEmail: String,
    val timestamp: Instant,
    val files: List<String>,
    val diff: String? = null  // 代码差异内容，仅在用户允许时填充
)
