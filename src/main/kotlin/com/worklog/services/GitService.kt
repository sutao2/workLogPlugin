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
     * 获取当前 Git 用户的 email
     */
    fun getCurrentUserEmail(): String? {
        val repository = getGitRepository() ?: return null

        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("git", "config", "user.email"),
                null,
                repository.root.toNioPath().toFile()
            )

            val email = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            if (process.exitValue() == 0 && email.isNotEmpty()) {
                email
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取指定日期的 Git 提交记录（仅当前用户）
     */
    fun getCommitsByDate(date: LocalDate, includeCode: Boolean = false): List<GitCommit> {
        val repository = getGitRepository() ?: return emptyList()

        try {
            // 获取当前用户的 email
            val currentUserEmail = getCurrentUserEmail()
            if (currentUserEmail == null) {
                println("警告: 无法获取当前 Git 用户 email")
                return emptyList()
            }

            println("当前 Git 用户 email: $currentUserEmail")

            // 使用日期格式 YYYY-MM-DD，Git 完全支持
            val dateStr = date.toString()  // 例如: "2025-12-16"
            val nextDateStr = date.plusDays(1).toString()  // 例如: "2025-12-17"

            println("查询日期: $dateStr")

            // 直接使用 git log 命令，更可靠
            val gitCommand = arrayOf(
                "git", "log",
                "--all",  // 所有分支
                "--author=$currentUserEmail",  // 过滤当前用户
                "--since=$dateStr 00:00:00",  // 开始时间
                "--until=$nextDateStr 00:00:00",  // 结束时间
                "--pretty=format:%H|%h|%s|%an|%ae|%ct",  // 自定义格式：完整hash|短hash|消息|作者名|作者email|时间戳
                "--name-only"  // 显示文件名
            )

            println("执行命令: ${gitCommand.joinToString(" ")}")

            val process = Runtime.getRuntime().exec(
                gitCommand,
                null,
                repository.root.toNioPath().toFile()
            )

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            if (process.exitValue() != 0) {
                val error = process.errorStream.bufferedReader().readText()
                println("Git 命令执行失败: $error")
                return emptyList()
            }

            println("Git 输出:\n$output")

            // 解析输出
            val commits = mutableListOf<GitCommit>()
            val lines = output.lines().filter { it.isNotBlank() }

            var i = 0
            while (i < lines.size) {
                val line = lines[i]

                // 检查是否是提交信息行（包含管道符）
                if (line.contains("|")) {
                    val parts = line.split("|")
                    if (parts.size == 6) {
                        val hash = parts[0]
                        val shortHash = parts[1]
                        val message = parts[2]
                        val authorName = parts[3]
                        val authorEmail = parts[4]
                        val timestamp = Instant.ofEpochSecond(parts[5].toLong())

                        // 收集这个提交的文件列表
                        val files = mutableListOf<String>()
                        i++
                        while (i < lines.size && !lines[i].contains("|")) {
                            if (lines[i].isNotBlank()) {
                                files.add(lines[i])
                            }
                            i++
                        }

                        // 如果需要代码差异
                        val diff = if (includeCode) {
                            try {
                                getDiffForCommit(repository, hash)
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            null
                        }

                        commits.add(
                            GitCommit(
                                hash = hash,
                                shortHash = shortHash,
                                message = message,
                                author = authorName,
                                authorEmail = authorEmail,
                                timestamp = timestamp,
                                files = files,
                                diff = diff
                            )
                        )

                        println("✓ 找到提交: $shortHash - $message")
                        continue
                    }
                }
                i++
            }

            println("总共找到 ${commits.size} 条提交记录")
            return commits.sortedBy { it.timestamp }

        } catch (e: Exception) {
            println("获取提交记录时出错: ${e.message}")
            e.printStackTrace()
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
     * 获取未提交变更的信息（作为虚拟的GitCommit）
     */
    fun getUncommittedChangesAsCommit(includeCode: Boolean): GitCommit? {
        val uncommittedFiles = getUncommittedChanges()
        if (uncommittedFiles.isEmpty()) {
            return null
        }

        val diff = if (includeCode) {
            getDiffForUncommittedFiles(uncommittedFiles)
        } else {
            null
        }

        return GitCommit(
            hash = "uncommitted",
            shortHash = "未提交",
            message = "工作区未提交的变更 (${uncommittedFiles.size} 个文件)",
            author = System.getProperty("user.name") ?: "当前用户",
            authorEmail = "",
            timestamp = Instant.now(),
            files = uncommittedFiles.map { it.path },
            diff = diff
        )
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
