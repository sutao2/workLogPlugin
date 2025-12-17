package com.worklog.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.worklog.models.GitCommit
import git4idea.GitUtil
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Git 服务
 * 负责获取 Git 提交信息和代码变更
 */
@Service(Service.Level.PROJECT)
class GitService(private val project: Project) {

    /**
     * 获取指定日期的 Git 提交记录
     */
    fun getCommitsByDate(date: LocalDate, includeCode: Boolean = false): List<GitCommit> {
        val repository = getGitRepository() ?: return emptyList()

        try {
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

            // 获取所有提交
            val commits = mutableListOf<GitCommit>()

            GitHistoryUtils.history(project, repository.root, "--all", "--since=${startOfDay.epochSecond}", "--until=${endOfDay.epochSecond}")
                .forEach { record ->
                    val commitTime = Instant.ofEpochSecond(record.timestamp)

                    // 只保留指定日期的提交
                    if (commitTime >= startOfDay && commitTime < endOfDay) {
                        val files = record.affectedPaths.map { it.path }

                        // 如果用户允许，获取代码差异
                        val diff = if (includeCode) {
                            try {
                                getDiffForCommit(repository, record.id.asString())
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            null
                        }

                        commits.add(
                            GitCommit(
                                hash = record.id.asString(),
                                shortHash = record.id.toShortString(),
                                message = record.subject,
                                author = record.author.name,
                                authorEmail = record.author.email,
                                timestamp = commitTime,
                                files = files,
                                diff = diff
                            )
                        )
                    }
                }

            return commits.sortedBy { it.timestamp }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    /**
     * 获取未提交的更改
     */
    fun getUncommittedChanges(): List<VirtualFile> {
        val changeListManager = ChangeListManager.getInstance(project)
        return changeListManager.allChanges
            .mapNotNull { it.virtualFile }
            .distinct()
    }

    /**
     * 获取指定提交的代码差异
     */
    private fun getDiffForCommit(repository: GitRepository, commitHash: String): String? {
        return try {
            val git = GitUtil.getRepositoryManager(project)
            // 使用 git show 命令获取提交的完整差异
            val process = Runtime.getRuntime().exec(
                arrayOf("git", "show", commitHash, "--format=", "--unified=3"),
                null,
                repository.root.toNioPath().toFile()
            )

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            if (process.exitValue() == 0) output else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取未提交文件的代码差异
     */
    fun getDiffForUncommittedFiles(files: List<VirtualFile>): String {
        val repository = getGitRepository() ?: return ""

        return try {
            val filePaths = files.joinToString(" ") { it.path }
            val process = Runtime.getRuntime().exec(
                "git diff HEAD $filePaths",
                null,
                repository.root.toNioPath().toFile()
            )

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            if (process.exitValue() == 0) output else ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 检查项目是否有 Git 仓库
     */
    fun hasGitRepository(): Boolean {
        return getGitRepository() != null
    }

    /**
     * 获取当前项目的 Git 仓库
     */
    private fun getGitRepository(): GitRepository? {
        val projectBaseDir = project.basePath?.let {
            com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(it)
        } ?: return null

        return GitUtil.getRepositoryManager(project).getRepositoryForRoot(projectBaseDir)
    }

    /**
     * 获取今日提交数量
     */
    fun getTodayCommitCount(): Int {
        return getCommitsByDate(LocalDate.now(), includeCode = false).size
    }

    /**
     * 检查今日是否有提交
     */
    fun hasTodayCommits(): Boolean {
        return getTodayCommitCount() > 0
    }
}
