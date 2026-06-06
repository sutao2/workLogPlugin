package com.worklog.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.worklog.models.GitCommit
import com.worklog.services.git.GitCommandExecutor
import com.worklog.services.git.GitDiffAnnotator
import com.worklog.services.git.GitFileFilter
import com.worklog.services.git.GitFileFilterRules
import com.worklog.services.git.GitLogParser
import git4idea.GitUtil
import git4idea.repo.GitRepository
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

/**
 * Git 服务
 * 负责获取 Git 提交信息和代码变更
 */
@Service(Service.Level.PROJECT)
class GitService(private val project: Project) : Disposable {

    private val log = Logger.getInstance(GitService::class.java)
    private val commandExecutor = GitCommandExecutor()

    companion object {
        private val VALID_COMMIT_HASH = Regex("^[a-f0-9]{4,64}$")
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
        return commandExecutor.execute(command, workDir, timeoutSeconds)
            ?.let { it.exitCode to it.stdout }
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
                "--pretty=format:%H${GitLogParser.COMMIT_FIELD_SEPARATOR}%h${GitLogParser.COMMIT_FIELD_SEPARATOR}%s${GitLogParser.COMMIT_FIELD_SEPARATOR}%an${GitLogParser.COMMIT_FIELD_SEPARATOR}%ae${GitLogParser.COMMIT_FIELD_SEPARATOR}%ct",
                "--name-only"
            )

            val result = executeGitCommand(gitCommand, repository.root.toNioPath().toFile(), 30)
                ?: return emptyList()
            if (result.first != 0) return emptyList()
            val output = result.second

            // 获取配置（只获取一次，避免重复解析）
            val rules = currentFilterRules()
            val commits = GitLogParser.parseCommits(output) { filePath ->
                GitFileFilter.shouldIncludePath(filePath, rules)
            }.map { commit ->
                if (includeCode && commit.files.isNotEmpty()) {
                    commit.copy(diff = runCatching { getDiffForCommit(repository, commit.hash, commit.files) }.getOrNull())
                } else {
                    commit
                }
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
        val rules = currentFilterRules()

        return changeListManager.allChanges
            .mapNotNull { it.virtualFile }
            .distinct()
            .filter { GitFileFilter.shouldIncludeVirtualFile(it, rules) }
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

    override fun dispose() {
        commandExecutor.shutdown()
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
        val rules = currentFilterRules()
        return result.second.lines()
            .filter { it.isNotBlank() }
            .filter { GitFileFilter.shouldIncludePath(it, rules) }
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

        val output = GitDiffAnnotator.annotateWithLineMarkers(result.second)
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
        val rules = currentFilterRules()
        val files = filesResult.second.lines()
            .filter { it.isNotBlank() }
            .filter { GitFileFilter.shouldIncludePath(it, rules) }
        if (files.isEmpty()) return ReviewInput(emptyList(), "", false)

        val diffCommand = mutableListOf("git", "show", commitHash, "--format=", "--unified=3", "--")
        diffCommand.addAll(files)

        val diffResult = executeGitCommand(
            diffCommand.toTypedArray(),
            workDir, 15
        ) ?: return ReviewInput(files, "", false)

        val diffOutput = GitDiffAnnotator.annotateWithLineMarkers(diffResult.second)
        val truncated = diffOutput.length > maxDiffChars
        val diff = if (truncated) diffOutput.take(maxDiffChars) + "\n\n... (diff 内容过长，已截断)" else diffOutput
        return ReviewInput(files, diff, truncated)
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

    private fun currentFilterRules(): GitFileFilterRules {
        return GitFileFilterRules.from(com.worklog.settings.AppSettingsState.getInstance())
    }
}
