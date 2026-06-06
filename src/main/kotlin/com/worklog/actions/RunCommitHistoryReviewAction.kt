package com.worklog.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.vcs.log.VcsLogDataKeys
import com.worklog.models.ReviewResult
import com.worklog.services.CodeReviewService
import com.worklog.ui.CodeReviewResultDialog

class RunCommitHistoryReviewAction : DumbAwareAction("评审提交代码", "评审所选历史提交的代码变更", AllIcons.Actions.Find) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
        val selectedCommits = selection?.commits.orEmpty()
        if (selectedCommits.isEmpty()) {
            Messages.showInfoMessage(project, "请先在提交记录中选择提交再执行代码评审。", "提交代码评审")
            return
        }

        val commitHashes = selectedCommits.map { it.hash.asString() }
        runCommitReview(project, commitHashes)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
        e.presentation.isEnabled = e.project != null && !selection?.commits.isNullOrEmpty()
    }

    private fun runCommitReview(project: Project, commitHashes: List<String>) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "评审提交代码", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "读取提交变更内容..."
                val result = executeReview(project, commitHashes, indicator)
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    showResult(project, commitHashes, result)
                }
            }

            override fun onThrowable(error: Throwable) {
                if (error is com.intellij.openapi.progress.ProcessCanceledException) return
                Messages.showWarningDialog(project, "提交代码评审失败: ${error.message}", "提交代码评审")
            }
        })
    }

    private fun executeReview(project: Project, commitHashes: List<String>, indicator: ProgressIndicator): ReviewExecutionResult {
        return try {
            indicator.text = if (commitHashes.size == 1) {
                "调用 AI 评审历史提交代码..."
            } else {
                "调用 AI 批量评审 ${commitHashes.size} 条提交..."
            }
            val reviewService = project.getService(CodeReviewService::class.java)
            val result = kotlinx.coroutines.runBlocking {
                if (commitHashes.size == 1) {
                    reviewService.reviewCommitByHash(commitHashes.first())
                } else {
                    reviewService.reviewCommitsByHash(commitHashes)
                }
            }
            indicator.text = "整理评审结果..."
            ReviewExecutionResult(dialogResult = result)
        } catch (ex: com.intellij.openapi.progress.ProcessCanceledException) {
            throw ex  // 重新抛出让框架处理
        } catch (ex: Exception) {
            ReviewExecutionResult(errorMessage = "提交代码评审失败: ${ex.message}")
        }
    }

    private fun showResult(project: Project, commitHashes: List<String>, result: ReviewExecutionResult) {
        result.errorMessage?.let {
            Messages.showWarningDialog(project, it, "提交代码评审")
            return
        }
        result.dialogResult?.let {
            CodeReviewResultDialog(project, it) {
                runCommitReview(project, commitHashes)
            }.show()
        }
    }

    private data class ReviewExecutionResult(
        val dialogResult: ReviewResult? = null,
        val errorMessage: String? = null
    )
}
