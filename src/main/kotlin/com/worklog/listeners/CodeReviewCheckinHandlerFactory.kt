package com.worklog.listeners

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.worklog.services.CodeReviewService
import com.worklog.services.PreCommitHookService
import com.worklog.settings.AppSettingsState
import com.worklog.ui.CodeReviewResultDialog
import java.util.concurrent.atomic.AtomicReference

class CodeReviewCheckinHandlerFactory : CheckinHandlerFactory() {
    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
        return CodeReviewCheckinHandler(panel)
    }
}

private class CodeReviewCheckinHandler(
    private val panel: CheckinProjectPanel
) : CheckinHandler() {

    override fun beforeCheckin(): ReturnResult {
        val settings = AppSettingsState.getInstance()
        if (!settings.reviewEnabled || !settings.reviewAutoRunBeforeCommit || !settings.allowCodeAccess) {
            return ReturnResult.COMMIT
        }

        val project = panel.project
        val reviewService = project.getService(CodeReviewService::class.java)

        if (!reviewService.hasStagedChanges()) {
            return ReturnResult.COMMIT
        }

        val commitMessage = panel.commitMessage?.takeIf { it.isNotBlank() }
        val resultRef = AtomicReference<com.worklog.models.ReviewResult?>()
        val snapshotKeyRef = AtomicReference<String?>()
        val errorRef = AtomicReference<String?>()

        val completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                val indicator = ProgressManager.getInstance().progressIndicator
                indicator.isIndeterminate = true
                indicator.text = "AI 代码评审中..."
                try {
                    val (context, result) = kotlinx.coroutines.runBlocking {
                        reviewService.reviewStagedChangesWithContext(commitMessage)
                    }
                    resultRef.set(result)
                    snapshotKeyRef.set(context.snapshotKey)
                } catch (e: Exception) {
                    errorRef.set(e.message ?: "未知错误")
                }
            },
            "代码评审",
            true,
            project
        )

        if (!completed) {
            // User cancelled the progress dialog — let them decide
            return ReturnResult.CANCEL
        }

        val error = errorRef.get()
        if (error != null) {
            val proceed = com.intellij.openapi.ui.Messages.showYesNoDialog(
                project,
                "代码评审失败：$error\n\n是否仍然继续提交？",
                "代码评审",
                "继续提交",
                "取消",
                com.intellij.icons.AllIcons.General.Warning
            )
            return if (proceed == com.intellij.openapi.ui.Messages.YES) ReturnResult.COMMIT else ReturnResult.CANCEL
        }

        val reviewResult = resultRef.get() ?: return ReturnResult.COMMIT

        // Show result dialog — "继续提交" callback sets userChoice to COMMIT
        val userChoice = AtomicReference(ReturnResult.CANCEL)
        val dialog = CodeReviewResultDialog(
            project = project,
            result = reviewResult,
            allowContinueCommit = true,
            onContinueCommit = {
                snapshotKeyRef.get()?.let { snapshotKey ->
                    runCatching {
                        project.getService(PreCommitHookService::class.java).markApproved(snapshotKey)
                    }
                }
                userChoice.set(ReturnResult.COMMIT)
            }
        )
        dialog.show()

        return userChoice.get()
    }
}
