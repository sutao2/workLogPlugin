package com.worklog.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.worklog.models.WorkLog
import com.worklog.utils.GitCommitMetadata
import com.worklog.utils.MarkdownUtil
import com.worklog.utils.StorageUtil
import com.worklog.utils.WorkLogMetadata
import java.time.Instant
import java.time.LocalDate

/**
 * 工作日志服务
 * 负责工作日志的创建、读取、保存和管理
 */
@Service(Service.Level.PROJECT)
class WorkLogService(private val project: Project) {

    private val gitService: GitService
        get() = project.getService(GitService::class.java)

    /**
     * 创建新的工作日志
     */
    fun createWorkLog(
        date: LocalDate,
        includeCode: Boolean = false,
        includeUncommitted: Boolean = false
    ): WorkLog {
        // 获取 Git 提交记录
        val commits = gitService.getCommitsByDate(date, includeCode).toMutableList()

        // 仅在用户明确选择时，才将今天的未提交变更加入日志。
        if (date == LocalDate.now() && includeCode && includeUncommitted) {
            val uncommittedCommit = gitService.getUncommittedChangesAsCommit(includeCode)
            if (uncommittedCommit != null) {
                commits.add(uncommittedCommit)
            }
        }

        // 创建工作日志
        val workLog = WorkLog.create(
            date = date,
            commits = commits,
            hasCodeAccess = includeCode
        )

        // 生成初始内容
        workLog.content = MarkdownUtil.generateTemplate(date)

        return workLog
    }

    /**
     * 保存工作日志
     */
    fun saveWorkLog(workLog: WorkLog) {
        // 更新时间戳
        workLog.updatedAt = Instant.now()

        // 直接保存 workLog.content，不要重新生成
        // 因为 content 中已经包含了 AI 总结和完整内容
        StorageUtil.writeWorkLog(project, workLog.date, workLog.content)

        // 保存元数据
        val metadata = WorkLogMetadata(
            date = workLog.date.toString(),
            createdAt = workLog.createdAt.toString(),
            updatedAt = workLog.updatedAt.toString(),
            hasCodeAccess = workLog.hasCodeAccess,
            commitCount = workLog.gitCommits.size,
            gitCommits = workLog.gitCommits.map { commit ->
                GitCommitMetadata(
                    hash = commit.hash,
                    shortHash = commit.shortHash,
                    message = commit.message,
                    author = commit.author,
                    authorEmail = commit.authorEmail,
                    timestamp = commit.timestamp.toString(),
                    files = commit.files,
                    filesCount = commit.files.size
                )
            }
        )
        StorageUtil.writeMetadata(project, workLog.date, metadata)
    }

    /**
     * 加载指定日期的工作日志
     */
    fun loadWorkLog(date: LocalDate): WorkLog? {
        val content = StorageUtil.readWorkLog(project, date) ?: return null
        val metadata = StorageUtil.readMetadata(project, date)

        return if (metadata != null) {
            WorkLog(
                date = date,
                content = content,
                gitCommits = metadata.gitCommits.map { commit ->
                    com.worklog.models.GitCommit(
                        hash = commit.hash,
                        shortHash = commit.shortHash,
                        message = commit.message,
                        author = commit.author,
                        authorEmail = commit.authorEmail,
                        timestamp = Instant.parse(commit.timestamp),
                        files = if (commit.files.isNotEmpty()) {
                            commit.files
                        } else {
                            List(commit.filesCount) { index -> "${commit.shortHash}:file-$index" }
                        }
                    )
                },
                hasCodeAccess = metadata.hasCodeAccess,
                createdAt = Instant.parse(metadata.createdAt),
                updatedAt = Instant.parse(metadata.updatedAt)
            )
        } else {
            // 如果没有元数据，创建简单的工作日志
            WorkLog.create(date).apply {
                this.content = content
            }
        }
    }

    /**
     * 获取所有工作日志日期列表
     */
    fun getAllWorkLogDates(): List<LocalDate> {
        return StorageUtil.getAllWorkLogDates(project)
    }

    /**
     * 删除指定日期的工作日志
     */
    fun deleteWorkLog(date: LocalDate) {
        StorageUtil.deleteWorkLog(project, date)
    }

    /**
     * 检查指定日期是否存在工作日志
     */
    fun hasWorkLog(date: LocalDate): Boolean {
        return StorageUtil.hasWorkLog(project, date)
    }

    /**
     * 获取今日工作日志
     */
    fun getTodayWorkLog(): WorkLog? {
        return loadWorkLog(LocalDate.now())
    }

    /**
     * 检查今日是否已有工作日志
     */
    fun hasTodayWorkLog(): Boolean {
        return hasWorkLog(LocalDate.now())
    }

    /**
     * 更新工作日志内容（仅更新用户编辑的部分）
     */
    fun updateWorkLogContent(date: LocalDate, newContent: String) {
        val workLog = loadWorkLog(date) ?: WorkLog.create(date)
        workLog.content = newContent
        saveWorkLog(workLog)
    }
}
