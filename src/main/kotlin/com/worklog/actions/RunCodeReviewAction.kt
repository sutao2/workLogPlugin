package com.worklog.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.worklog.models.ReviewResult
import com.worklog.services.CodeReviewService
import com.worklog.services.GitService
import com.worklog.services.PreCommitHookService
import com.worklog.ui.CodeReviewResultDialog
import java.util.Collections
import java.util.WeakHashMap

class RunCodeReviewAction : DumbAwareAction("代码评审", "评审当前暂存区代码变更", AllIcons.General.InspectionsOK) {

    private val runningStates = Collections.synchronizedMap(WeakHashMap<Project, Boolean>())

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (isRunning(project)) {
            return
        }

        val gitService = project.getService(GitService::class.java)
        if (!gitService.hasGitRepository()) {
            Messages.showInfoMessage(project, "当前项目未检测到 Git 仓库，请确认项目已初始化 Git。", "代码评审")
            return
        }
        if (gitService.getStagedFiles().isEmpty()) {
            Messages.showInfoMessage(project, "当前没有已暂存的代码变更。\n\n请先使用 git add 暂存需要评审的文件。", "代码评审")
            return
        }

        setRunning(project, true)
        val commitMessage = extractCommitMessage(e)

        runStagedReviewInBackground(
            project = project,
            taskTitle = "代码评审",
            commitMessage = commitMessage,
            onCompleted = { result, snapshotKey ->
                try {
                    runCatching {
                        project.getService(PreCommitHookService::class.java).markApproved(snapshotKey)
                    }
                    showResult(project, ReviewExecutionResult(dialogResult = result))
                } finally {
                    setRunning(project, false)
                }
            },
            onError = { errorMessage ->
                try {
                    showResult(project, ReviewExecutionResult(errorMessage = errorMessage))
                } finally {
                    setRunning(project, false)
                }
            },
            onInfo = { infoMessage ->
                try {
                    showResult(project, ReviewExecutionResult(infoMessage = infoMessage))
                } finally {
                    setRunning(project, false)
                }
            }
        )
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.icon = AllIcons.General.InspectionsOK
        e.presentation.text = "代码评审"
        e.presentation.description = if (project != null && isRunning(project)) {
            "代码评审正在进行中"
        } else {
            "评审当前暂存区代码变更"
        }

        if (project == null) {
            e.presentation.isEnabled = false
            return
        }

        e.presentation.isEnabled = !isRunning(project)
    }

    override fun displayTextInToolbar(): Boolean {
        return false
    }

    private fun showResult(project: Project, result: ReviewExecutionResult) {
        result.infoMessage?.let {
            Messages.showInfoMessage(project, it, "代码评审")
            return
        }
        result.errorMessage?.let {
            Messages.showWarningDialog(project, it, "代码评审")
            return
        }
        result.dialogResult?.let {
            CodeReviewResultDialog(project, it).show()
        }
    }

    private fun extractCommitMessage(e: AnActionEvent): String? {
        val source = e.inputEvent?.component
        return when (source) {
            is javax.swing.text.JTextComponent -> source.text
            else -> null
        }
    }

    private fun setRunning(project: Project, running: Boolean) {
        runningStates[project] = running
        ActivityTracker.getInstance().inc()
    }

    private fun isRunning(project: Project): Boolean {
        return runningStates[project] == true
    }

    private data class ReviewExecutionResult(
        val dialogResult: ReviewResult? = null,
        val errorMessage: String? = null,
        val infoMessage: String? = null
    )

    companion object {
        fun runStagedReviewInBackground(
            project: Project,
            taskTitle: String,
            commitMessage: String?,
            onCompleted: (ReviewResult, String) -> Unit,
            onError: (String) -> Unit,
            onInfo: ((String) -> Unit)? = null
        ) {
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, taskTitle, true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.text = "收集已暂存代码变更..."
                    val reviewService = project.getService(CodeReviewService::class.java)
                    val gitService = project.getService(GitService::class.java)
                    if (gitService.getStagedFiles().isEmpty()) {
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            onInfo?.invoke("当前没有已暂存的代码变更可供评审。")
                        }
                        return
                    }

                    try {
                        indicator.text = "调用 AI 进行代码评审..."
                        val (context, result) = kotlinx.coroutines.runBlocking {
                            reviewService.reviewStagedChangesWithContext(commitMessage)
                        }
                        indicator.text = "整理评审结果..."
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            onCompleted(result, context.snapshotKey)
                        }
                    } catch (ex: com.intellij.openapi.progress.ProcessCanceledException) {
                        // 用户取消，不报错
                    } catch (ex: Exception) {
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            onError("代码评审失败: ${ex.message}")
                        }
                    }
                }
            })
        }
    }
}
