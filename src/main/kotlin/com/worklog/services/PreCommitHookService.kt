package com.worklog.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.worklog.utils.StorageUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
private data class ReviewGateState(
    val approvedSnapshotKey: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Service(Service.Level.PROJECT)
class PreCommitHookService(private val project: Project) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val reviewService: CodeReviewService
        get() = project.getService(CodeReviewService::class.java)

    fun installOrUpdateHookIfNeeded() {
        if (!isEnforcementEnabled()) {
            runCatching { removeHookIfManaged() }
            return
        }

        val hookFile = getPreCommitHookFile()
        hookFile.parent?.createDirectories()
        if (hookFile.exists()) {
            val existing = runCatching { hookFile.readText() }.getOrNull().orEmpty()
            if (existing.isNotBlank() && !existing.contains(MANAGED_MARKER)) {
                throw IllegalStateException("检测到仓库已有自定义 pre-commit hook，已停止覆盖。请手动合并 WorkLog hook 逻辑。")
            }
        }
        hookFile.writeText(buildHookScript())
        hookFile.toFile().setExecutable(true)
    }

    fun markApproved(snapshotKey: String) {
        val stateFile = getGateStateFile()
        stateFile.parent?.createDirectories()
        stateFile.writeText(json.encodeToString(ReviewGateState(approvedSnapshotKey = snapshotKey)))
    }

    fun clearApproval() {
        val stateFile = getGateStateFile()
        stateFile.parent?.createDirectories()
        stateFile.writeText(json.encodeToString(ReviewGateState(approvedSnapshotKey = null)))
    }

    fun currentSnapshotKey(): String? {
        return try {
            reviewService.getStagedReviewContext(null).snapshotKey
        } catch (_: Exception) {
            null
        }
    }

    fun syncApprovalWithCurrentSnapshot() {
        val current = currentSnapshotKey()
        val approved = readApprovedSnapshotKey()
        if (current == null || approved == null || current != approved) {
            clearApproval()
        }
    }

    fun getHookStatus(): String {
        val hookFile = getPreCommitHookFile()
        if (!hookFile.exists()) {
            return "未安装"
        }
        val content = runCatching { hookFile.readText() }.getOrNull().orEmpty()
        return if (content.contains(MANAGED_MARKER)) {
            "已安装"
        } else {
            "检测到自定义 hook（未接管）"
        }
    }

    fun getApprovalStatus(): String {
        val approved = readApprovedSnapshotKey()
        return if (approved.isNullOrBlank()) "未通过评审" else "当前快照已通过评审"
    }

    fun getGateSummary(): String {
        return "Git hook: ${getHookStatus()} / 评审状态: ${getApprovalStatus()}"
    }

    private fun isEnforcementEnabled(): Boolean {
        val settings = com.worklog.settings.AppSettingsState.getInstance()
        return settings.reviewEnabled && settings.reviewAutoRunBeforeCommit && settings.allowCodeAccess
    }

    private fun readApprovedSnapshotKey(): String? {
        val stateFile = getGateStateFile()
        if (!stateFile.exists()) return null
        return try {
            json.decodeFromString<ReviewGateState>(stateFile.readText()).approvedSnapshotKey
        } catch (_: Exception) {
            null
        }
    }

    private fun getGateStateFile(): Path {
        return StorageUtil.getWorkLogDir(project).resolve("review-gate.json")
    }

    private fun getPreCommitHookFile(): Path {
        val baseDir = project.basePath ?: throw IllegalStateException("项目路径不存在")
        val gitPath = Paths.get(baseDir).resolve(".git")
        val gitDir = if (gitPath.isDirectory()) {
            gitPath
        } else if (gitPath.exists()) {
            val gitFileContent = gitPath.readText().trim()
            val rawGitDir = gitFileContent.removePrefix("gitdir:").trim()
            Paths.get(baseDir).resolve(rawGitDir).normalize()
        } else {
            throw IllegalStateException("未找到 Git 目录")
        }
        return gitDir.resolve("hooks").resolve("pre-commit")
    }

    private fun removeHookIfManaged() {
        val hookFile = getPreCommitHookFile()
        if (!hookFile.exists()) return
        val content = try {
            Files.readString(hookFile)
        } catch (_: Exception) {
            return
        }
        if (content.contains(MANAGED_MARKER)) {
            Files.deleteIfExists(hookFile)
        }
    }

    private fun buildHookScript(): String {
        val stateFile = getGateStateFile().toAbsolutePath().toString()
        return """
            #!/bin/sh
            # $MANAGED_MARKER
            export STATE_FILE="$stateFile"
            if [ ! -f "${'$'}STATE_FILE" ]; then
              echo "WorkLog: 当前提交未完成代码评审，已阻止提交。请先在 IDE 中执行代码评审。"
              exit 1
            fi
            APPROVED_KEY=${'$'}(python3 - <<'PY'
import json, os
path = os.environ.get('STATE_FILE')
try:
    with open(path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    print(data.get('approvedSnapshotKey') or '')
except Exception:
    print('')
PY
)
            if [ -z "${'$'}APPROVED_KEY" ]; then
              echo "WorkLog: 当前提交未完成代码评审，已阻止提交。请先在 IDE 中执行代码评审。"
              exit 1
            fi
            TMP_DIFF_FILE=${'$'}(mktemp)
            trap 'rm -f "${'$'}TMP_DIFF_FILE"' EXIT
            git diff --cached --unified=0 > "${'$'}TMP_DIFF_FILE"
            CURRENT_KEY=${'$'}(python3 - "${'$'}TMP_DIFF_FILE" <<'PY'
import hashlib, pathlib, sys
path = pathlib.Path(sys.argv[1])
content = path.read_text(encoding='utf-8').rstrip()
if content:
    print(hashlib.sha256(content.encode('utf-8')).hexdigest())
else:
    print('')
PY
)
            if [ -z "${'$'}CURRENT_KEY" ]; then
              echo "WorkLog: 当前没有已暂存的代码变更。"
              exit 1
            fi
            if [ "${'$'}APPROVED_KEY" != "${'$'}CURRENT_KEY" ]; then
              echo "WorkLog: 暂存内容已变化，需重新执行代码评审后才能提交。"
              exit 1
            fi
            exit 0
        """.trimIndent()
    }

    companion object {
        private const val MANAGED_MARKER = "WORKLOG_REVIEW_GATE"
    }
}
