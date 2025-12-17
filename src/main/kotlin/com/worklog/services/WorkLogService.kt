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
    fun createWorkLog(date: LocalDate, includeCode: Boolean = false): WorkLog {
        // 获取 Git 提交记录
        val commits = gitService.getCommitsByDate(date, includeCode)

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

        // 保存 Markdown 内容
        val fullContent = MarkdownUtil.generateFullWorkLog(
            workLog = workLog,
            aiSummary = null,  // AI 总结已经包含在 content 中
            includeCodeDiff = workLog.hasCodeAccess
        )
        StorageUtil.writeWorkLog(project, workLog.date, fullContent)

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
                gitCommits = emptyList(),  // 从元数据重建 Git 提交列表
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
        val workLog = loadWorkLog(date) ?: return
        workLog.content = newContent
        saveWorkLog(workLog)
    }
}
