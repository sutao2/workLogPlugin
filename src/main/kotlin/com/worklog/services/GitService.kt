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

                        // 收集这个提交的文件列表（过滤掉大型文件）
                        val files = mutableListOf<String>()
                        i++
                        while (i < lines.size && !lines[i].contains("|")) {
                            if (lines[i].isNotBlank()) {
                                val filePath = lines[i]
                                // 只包含应该被跟踪的文件类型
                                if (shouldIncludeFilePath(filePath)) {
                                    files.add(filePath)
                                } else {
                                    println("  跳过文件: $filePath")
                                }
                            }
                            i++
                        }

                        // 如果需要代码差异且有有效文件
                        val diff = if (includeCode && files.isNotEmpty()) {
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
            .filter { shouldIncludeFile(it) }
    }

    /**
     * 判断文件是否应该包含在日志中
     * 过滤掉大型二进制文件、模型文件等
     */
    private fun shouldIncludeFile(file: VirtualFile): Boolean {
        val fileName = file.name.lowercase()
        val path = file.path.lowercase()

        val settings = com.worklog.settings.AppSettingsState.getInstance()

        // 从配置中获取排除的文件扩展名
        val excludedExtensions = settings.excludedFileExtensions
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

        // 检查文件扩展名
        val extension = fileName.substringAfterLast('.', "")
        if (extension in excludedExtensions) {
            println("跳过大型/二进制文件: ${file.name}")
            return false
        }

        // 从配置中获取排除的目录
        val excludedDirs = settings.excludedDirectories
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }

        if (excludedDirs.any { path.contains(it) }) {
            println("跳过目录中的文件: ${file.path}")
            return false
        }

        // 检查文件大小（从配置读取限制）
        try {
            val maxSizeBytes = settings.maxFileSizeKb * 1024L
            if (file.length > maxSizeBytes) {
                println("跳过大文件 (${file.length / 1024}KB): ${file.name}")
                return false
            }
        } catch (e: Exception) {
            // 如果无法获取文件大小，保守处理，包含该文件
        }

        return true
    }

    /**
     * 判断文件路径是否应该包含在日志中（基于路径字符串）
     */
    private fun shouldIncludeFilePath(filePath: String): Boolean {
        val path = filePath.lowercase()
        val fileName = path.substringAfterLast('/')

        val settings = com.worklog.settings.AppSettingsState.getInstance()

        // 从配置中获取排除的文件扩展名
        val excludedExtensions = settings.excludedFileExtensions
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

        // 检查文件扩展名
        val extension = fileName.substringAfterLast('.', "")
        if (extension in excludedExtensions) {
            return false
        }

        // 从配置中获取排除的目录
        val excludedDirs = settings.excludedDirectories
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }

        return !excludedDirs.any { path.contains(it) }
    }

    /**
     * 获取未提交变更的信息（作为虚拟的GitCommit）
     */
    fun getUncommittedChangesAsCommit(includeCode: Boolean): GitCommit? {
        val uncommittedFiles = getUncommittedChanges()
        if (uncommittedFiles.isEmpty()) {
            println("没有未提交的变更（或所有变更都被过滤）")
            return null
        }

        println("发现 ${uncommittedFiles.size} 个未提交的有效文件变更")
        uncommittedFiles.forEach { file ->
            println("  - ${file.name}")
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
            // 如果没有文件，直接返回空
            if (files.isEmpty()) {
                return ""
            }

            // 获取相对于仓库根目录的路径，避免绝对路径问题
            val repoRoot = repository.root.toNioPath()
            val relativePaths = files.mapNotNull { file ->
                try {
                    val filePath = java.nio.file.Paths.get(file.path)
                    repoRoot.relativize(filePath).toString().replace("\\", "/")
                } catch (e: Exception) {
                    println("警告: 无法处理文件路径: ${file.path}, ${e.message}")
                    null
                }
            }

            if (relativePaths.isEmpty()) {
                println("警告: 没有有效的文件路径可以获取 diff")
                return ""
            }

            // 使用数组形式的命令，避免路径中的空格和特殊字符问题
            val command = mutableListOf("git", "diff", "HEAD", "--")
            command.addAll(relativePaths)

            println("执行 git diff 命令: ${command.joinToString(" ")}")

            val process = Runtime.getRuntime().exec(
                command.toTypedArray(),
                null,
                repository.root.toNioPath().toFile()
            )

            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()

            if (process.exitValue() != 0) {
                println("Git diff 命令失败 (退出码: ${process.exitValue()})")
                if (error.isNotBlank()) {
                    println("错误输出: $error")
                }
                return ""
            }

            // 限制 diff 输出的大小，避免超过 API 限制
            val maxDiffSize = 50000 // 50KB
            if (output.length > maxDiffSize) {
                val truncated = output.take(maxDiffSize)
                println("警告: diff 输出过大 (${output.length} 字符)，已截断到 $maxDiffSize 字符")
                return truncated + "\n\n... (diff 内容过长，已截断)"
            }

            println("成功获取 diff，大小: ${output.length} 字符")
            output
        } catch (e: Exception) {
            println("获取未提交文件 diff 时出错: ${e.message}")
            e.printStackTrace()
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
