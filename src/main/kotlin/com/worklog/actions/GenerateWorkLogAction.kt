package com.worklog.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.worklog.services.WorkLogService
import com.worklog.settings.AppSettingsState
import com.worklog.ui.GenerateWorkLogDialog
import com.worklog.ui.WorkLogToolWindow
import com.worklog.utils.MarkdownUtil

/**
 * 生成工作日志的动作
 */
class GenerateWorkLogAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 显示生成对话框
        val dialog = GenerateWorkLogDialog(project)
        if (dialog.showAndGet()) {
            val date = dialog.getSelectedDate()
            val includeCode = dialog.isIncludeCode()
            val includeUncommitted = dialog.isIncludeUncommitted()

            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "生成工作日志", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.text = "正在获取 Git 提交记录..."
                    val workLogService = project.getService(WorkLogService::class.java)
                    val workLog = workLogService.createWorkLog(date, includeCode, includeUncommitted)

                    // 如果配置了 AI，调用 AI 生成总结
                    val settings = AppSettingsState.getInstance()
                    val aiSummary = if (settings.apiKeyCompat.isNotBlank() && workLog.gitCommits.isNotEmpty()) {
                        try {
                            indicator.text = "正在调用 AI 生成总结..."
                            val aiService = project.getService(com.worklog.services.AIService::class.java)
                            aiService.summarizeWorkSync(workLog.gitCommits, includeCode)
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        null
                    }

                    // 生成完整内容
                    indicator.text = "正在保存工作日志..."
                    val fullContent = MarkdownUtil.generateFullWorkLog(
                        workLog = workLog,
                        aiSummary = aiSummary,
                        includeCodeDiff = includeCode
                    )

                    workLog.content = fullContent
                    workLogService.saveWorkLog(workLog)

                    ApplicationManager.getApplication().invokeLater {
                        ToolWindowManager.getInstance(project).getToolWindow(WorkLogToolWindow.ID)?.show()
                        Messages.showInfoMessage(project, "工作日志生成成功！", "成功")
                    }
                }

                override fun onThrowable(error: Throwable) {
                    if (error is com.intellij.openapi.progress.ProcessCanceledException) return
                    Messages.showWarningDialog(project, "生成失败: ${error.message}", "错误")
                }
            })
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
    }
}
