package com.worklog.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.worklog.models.GitCommit
import git4idea.GitUtil
import git4idea.repo.GitRepository
import java.io.Reader
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Git 服务
 * 负责获取 Git 提交信息和代码变更
 */
@Service(Service.Level.PROJECT)
class GitService(private val project: Project) {

    private val log = Logger.getInstance(GitService::class.java)

    companion object {
        private val VALID_COMMIT_HASH = Regex("^[a-f0-9]{4,64}$")
        private const val COMMIT_FIELD_SEPARATOR = "\u001F"
        private const val MAX_GIT_OUTPUT_CHARS = 2_000_000
    }

    private fun requireValidCommitHash(hash: String) {
        require(hash.matches(VALID_COMMIT_HASH)) { "非法的 commit hash: $hash" }
    }

    /**
     * 安全执行 git 命令，确保进程流正确关闭
     * @return Pair<exitCode, stdout>，超时或异常返回 null
     */
    private fun executeGitCommand(
        command: Array<String>,
        workDir: java.io.File,
        timeoutSeconds: Long = 15
    ): Pair<Int, String>? {
        val process = ProcessBuilder(*command)
            .directory(workDir)
            .redirectErrorStream(false)
            .start()
        val executor = Executors.newFixedThreadPool(2)
        return try {
            val stdoutFuture = executor.submit<String> {
                process.inputStream.bufferedReader().use { readLimited(it, MAX_GIT_OUTPUT_CHARS) }
            }
            val stderrFuture = executor.submit<String> {
                process.errorStream.bufferedReader().use { readLimited(it, MAX_GIT_OUTPUT_CHARS) }
            }

            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                closeProcessStreams(process)
                process.waitFor(2, TimeUnit.SECONDS)
                null
            } else {
                val stdout = getFutureValue(stdoutFuture)
                getFutureValue(stderrFuture)
                Pair(process.exitValue(), stdout)
            }
        } catch (e: Exception) {
            process.destroyForcibly()
            closeProcessStreams(process)
            null
        } finally {
            executor.shutdownNow()
        }
    }

    private fun readLimited(reader: Reader, maxChars: Int): String {
        val output = StringBuilder(maxChars.coerceAtMost(64 * 1024))
        val buffer = CharArray(8192)
        var storedChars = 0

        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break

            val remaining = maxChars - storedChars
            if (remaining > 0) {
                val toAppend = minOf(count, remaining)
                output.append(buffer, 0, toAppend)
                storedChars += toAppend
            }
        }

        return output.toString()
    }

    private fun getFutureValue(future: Future<String>): String {
        return try {
            future.get(2, TimeUnit.SECONDS)
        } catch (_: Exception) {
            ""
        }
    }

    private fun closeProcessStreams(process: Process) {
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        runCatching { process.outputStream.close() }
    }

    /**
     * 获取当前 Git 用户的 email
     */
    fun getCurrentUserEmail(): String? {
        val repository = getGitRepository() ?: return null
        val result = executeGitCommand(
            arrayOf("git", "config", "user.email"),
            repository.root.toNioPath().toFile(),
            10
        ) ?: return null
        val email = result.second.trim()
        return if (result.first == 0 && email.isNotEmpty()) email else null
    }

    /**
     * 获取指定日期的 Git 提交记录（仅当前用户）
     */
    fun getCommitsByDate(date: LocalDate, includeCode: Boolean = false): List<GitCommit> {
        val repository = getGitRepository() ?: return emptyList()

        try {
            val currentUserEmail = getCurrentUserEmail() ?: return emptyList()

            val dateStr = date.toString()
            val nextDateStr = date.plusDays(1).toString()

            val gitCommand = arrayOf(
                "git", "log",
                "--all",
                "--author=$currentUserEmail",
                "--since=$dateStr 00:00:00",
                "--until=$nextDateStr 00:00:00",
                "--pretty=format:%H${COMMIT_FIELD_SEPARATOR}%h${COMMIT_FIELD_SEPARATOR}%s${COMMIT_FIELD_SEPARATOR}%an${COMMIT_FIELD_SEPARATOR}%ae${COMMIT_FIELD_SEPARATOR}%ct",
                "--name-only"
            )

            val result = executeGitCommand(gitCommand, repository.root.toNioPath().toFile(), 30)
                ?: return emptyList()
            if (result.first != 0) return emptyList()
            val output = result.second

            // 获取配置（只获取一次，避免重复解析）
            val settings = com.worklog.settings.AppSettingsState.getInstance()
            val excludedExtensions = settings.excludedFileExtensions
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .toSet()
            val excludedDirs = settings.excludedDirectories
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }

            // 解析输出
            val commits = mutableListOf<GitCommit>()
            val lines = output.lines().filter { it.isNotBlank() }

            var i = 0
            while (i < lines.size) {
                val line = lines[i]

                // 检查是否是提交信息行（包含管道符）
                if (line.contains(COMMIT_FIELD_SEPARATOR)) {
                    val parts = line.split(COMMIT_FIELD_SEPARATOR, limit = 6)
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
                        while (i < lines.size && !lines[i].contains(COMMIT_FIELD_SEPARATOR)) {
                            if (lines[i].isNotBlank()) {
                                val filePath = lines[i]
                                // 只包含应该被跟踪的文件类型（使用预解析的配置）
                                if (shouldIncludeFilePath(filePath, excludedExtensions, excludedDirs)) {
                                    files.add(filePath)
                                }
                            }
                            i++
                        }

                        // 如果需要代码差异且有有效文件
                        val diff = if (includeCode && files.isNotEmpty()) {
                            try {
                                getDiffForCommit(repository, hash, files)
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

                        continue
                    }
                }
                i++
            }

            log.info("总共找到 ${commits.size} 条提交记录")
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
        val settings = com.worklog.settings.AppSettingsState.getInstance()
        val excludedExts = settings.excludedFileExtensions
            .split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        val excludedDirs = settings.excludedDirectories
            .split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val maxSizeBytes = settings.maxFileSizeKb * 1024L

        return changeListManager.allChanges
            .mapNotNull { it.virtualFile }
            .distinct()
            .filter { shouldIncludeFile(it, excludedExts, excludedDirs, maxSizeBytes) }
    }

    private fun shouldIncludeFile(
        file: VirtualFile,
        excludedExtensions: Set<String>,
        excludedDirs: List<String>,
        maxSizeBytes: Long
    ): Boolean {
        val fileName = file.name.lowercase()
        val path = file.path.lowercase()

        val extension = fileName.substringAfterLast('.', "")
        if (extension in excludedExtensions) return false
        val normalizedPath = "/${path.trimStart('/')}"
        if (excludedDirs.any { normalizedPath.contains(it) || path.contains(it) }) return false

        try {
            if (file.length > maxSizeBytes) return false
        } catch (_: Exception) { }

        return true
    }

    /**
     * 判断文件路径是否应该包含在日志中（基于路径字符串）
     */
    private fun shouldIncludeFilePath(
        filePath: String,
        excludedExtensions: Set<String>,
        excludedDirs: List<String>
    ): Boolean {
        val path = filePath.lowercase()
        val fileName = path.substringAfterLast('/')

        // 检查文件扩展名
        val extension = fileName.substringAfterLast('.', "")
        if (extension in excludedExtensions) {
            return false
        }

        val normalizedPath = "/${path.trimStart('/')}"
        return !excludedDirs.any { normalizedPath.contains(it) || path.contains(it) }
    }

    /**
     * 获取未提交变更的信息（作为虚拟的GitCommit）
     */
    fun getUncommittedChangesAsCommit(includeCode: Boolean): GitCommit? {
        val uncommittedFiles = getUncommittedChanges()
        if (uncommittedFiles.isEmpty()) return null

        val diff = if (includeCode) getDiffForUncommittedFiles(uncommittedFiles) else null

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
    private fun getDiffForCommit(repository: GitRepository, commitHash: String, files: List<String>): String? {
        requireValidCommitHash(commitHash)
        if (files.isEmpty()) return null
        val command = mutableListOf("git", "show", commitHash, "--format=", "--unified=3", "--")
        command.addAll(files)
        val result = executeGitCommand(
            command.toTypedArray(),
            repository.root.toNioPath().toFile(),
            15
        ) ?: return null
        return if (result.first == 0) result.second else null
    }

    /**
     * 获取未提交文件的代码差异
     */
    fun getDiffForUncommittedFiles(files: List<VirtualFile>): String {
        val repository = getGitRepository() ?: return ""
        if (files.isEmpty()) return ""

        return try {
            val repoRoot = repository.root.toNioPath()
            val relativePaths = files.mapNotNull { file ->
                try {
                    val filePath = java.nio.file.Paths.get(file.path)
                    repoRoot.relativize(filePath).toString().replace("\\", "/")
                } catch (_: Exception) {
                    null
                }
            }
            if (relativePaths.isEmpty()) return ""

            val command = mutableListOf("git", "diff", "HEAD", "--")
            command.addAll(relativePaths)

            val result = executeGitCommand(
                command.toTypedArray(),
                repository.root.toNioPath().toFile(),
                30
            ) ?: return ""
            if (result.first != 0) return ""

            val output = result.second
            val maxDiffSize = 50000
            if (output.length > maxDiffSize) {
                output.take(maxDiffSize) + "\n\n... (diff 内容过长，已截断)"
            } else {
                output
            }
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

    // ---- Code Review 支持方法 ----

    data class ReviewInput(
        val files: List<String>,
        val diff: String,
        val truncated: Boolean
    )

    /**
     * 获取暂存区文件列表
     */
    fun getStagedFiles(): List<String> {
        val repository = getGitRepository() ?: return emptyList()
        val result = executeGitCommand(
            arrayOf("git", "diff", "--cached", "--name-only"),
            repository.root.toNioPath().toFile(),
            10
        ) ?: return emptyList()
        if (result.first != 0) return emptyList()
        val settings = com.worklog.settings.AppSettingsState.getInstance()
        val excludedExtensions = settings.excludedFileExtensions
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
        val excludedDirs = settings.excludedDirectories
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        return result.second.lines()
            .filter { it.isNotBlank() }
            .filter { shouldIncludeFilePath(it, excludedExtensions, excludedDirs) }
    }

    /**
     * 获取暂存区的评审输入（diff + 文件列表）
     */
    fun getStagedReviewInput(maxDiffChars: Int): ReviewInput {
        val repository = getGitRepository() ?: return ReviewInput(emptyList(), "", false)
        val files = getStagedFiles()
        if (files.isEmpty()) return ReviewInput(emptyList(), "", false)

        val command = mutableListOf("git", "diff", "--cached", "--unified=3", "--")
        command.addAll(files)

        val result = executeGitCommand(
            command.toTypedArray(),
            repository.root.toNioPath().toFile(),
            30
        ) ?: return ReviewInput(files, "", false)
        if (result.first != 0) return ReviewInput(files, "", false)

        val output = annotateDiffWithLineMarkers(result.second)
        val truncated = output.length > maxDiffChars
        val diff = if (truncated) output.take(maxDiffChars) + "\n\n... (diff 内容过长，已截断)" else output
        return ReviewInput(files, diff, truncated)
    }

    /**
     * 获取暂存区 diff 的 SHA-256 hash（用于去重）
     */
    fun getRawStagedDiffHash(): String {
        val repository = getGitRepository() ?: return ""
        val files = getStagedFiles()
        if (files.isEmpty()) return ""

        val command = mutableListOf("git", "diff", "--cached", "--unified=0", "--")
        command.addAll(files)

        val result = executeGitCommand(
            command.toTypedArray(),
            repository.root.toNioPath().toFile(),
            15
        ) ?: return ""
        if (result.first != 0 || result.second.isBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(result.second.trim().toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 获取指定提交的评审输入
     */
    fun getCommitReviewInput(commitHash: String, maxDiffChars: Int): ReviewInput {
        requireValidCommitHash(commitHash)
        val repository = getGitRepository() ?: return ReviewInput(emptyList(), "", false)
        val workDir = repository.root.toNioPath().toFile()

        val filesResult = executeGitCommand(
            arrayOf("git", "diff-tree", "--no-commit-id", "-r", "--name-only", commitHash),
            workDir, 10
        ) ?: return ReviewInput(emptyList(), "", false)
        val settings = com.worklog.settings.AppSettingsState.getInstance()
        val excludedExtensions = settings.excludedFileExtensions
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
        val excludedDirs = settings.excludedDirectories
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        val files = filesResult.second.lines()
            .filter { it.isNotBlank() }
            .filter { shouldIncludeFilePath(it, excludedExtensions, excludedDirs) }
        if (files.isEmpty()) return ReviewInput(emptyList(), "", false)

        val diffCommand = mutableListOf("git", "show", commitHash, "--format=", "--unified=3", "--")
        diffCommand.addAll(files)

        val diffResult = executeGitCommand(
            diffCommand.toTypedArray(),
            workDir, 15
        ) ?: return ReviewInput(files, "", false)

        val diffOutput = annotateDiffWithLineMarkers(diffResult.second)
        val truncated = diffOutput.length > maxDiffChars
        val diff = if (truncated) diffOutput.take(maxDiffChars) + "\n\n... (diff 内容过长，已截断)" else diffOutput
        return ReviewInput(files, diff, truncated)
    }

    private fun annotateDiffWithLineMarkers(diff: String): String {
        var currentFile: String? = null
        var newLineNumber: Int? = null
        return buildString(diff.length + diff.lines().size * 18) {
            diff.lineSequence().forEach { line ->
                when {
                    line.startsWith("+++ b/") -> {
                        currentFile = line.removePrefix("+++ b/")
                        newLineNumber = null
                        appendLine(line)
                    }
                    line.startsWith("@@") -> {
                        newLineNumber = Regex("""\+(\d+)""").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        appendLine(line)
                    }
                    currentFile != null && newLineNumber != null && line.startsWith("+") && !line.startsWith("+++") -> {
                        appendLine(">> ${currentFile}:${newLineNumber}")
                        appendLine(line)
                        newLineNumber = newLineNumber?.plus(1)
                    }
                    currentFile != null && newLineNumber != null && line.startsWith(" ") -> {
                        appendLine(">> ${currentFile}:${newLineNumber}")
                        appendLine(line)
                        newLineNumber = newLineNumber?.plus(1)
                    }
                    line.startsWith("-") && !line.startsWith("---") -> {
                        appendLine(line)
                    }
                    else -> appendLine(line)
                }
            }
        }.trimEnd()
    }

    /**
     * 获取提交信息
     */
    fun getCommitMessage(commitHash: String): String? {
        requireValidCommitHash(commitHash)
        val repository = getGitRepository() ?: return null
        val result = executeGitCommand(
            arrayOf("git", "log", "-1", "--format=%s", commitHash),
            repository.root.toNioPath().toFile(), 10
        ) ?: return null
        val output = result.second.trim()
        return if (result.first == 0 && output.isNotEmpty()) output else null
    }

    /**
     * 获取提交的短 hash
     */
    fun getCommitShortHash(commitHash: String): String {
        requireValidCommitHash(commitHash)
        val repository = getGitRepository() ?: return commitHash.take(7)
        val result = executeGitCommand(
            arrayOf("git", "log", "-1", "--format=%h", commitHash),
            repository.root.toNioPath().toFile(), 10
        ) ?: return commitHash.take(7)
        val output = result.second.trim()
        return if (result.first == 0 && output.isNotEmpty()) output else commitHash.take(7)
    }
}
